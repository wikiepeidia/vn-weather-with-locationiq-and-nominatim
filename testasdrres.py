import json
import os
import re
import sys
import time
import urllib.parse
import urllib.request
from concurrent.futures import ThreadPoolExecutor
from typing import Any, Dict, List, Optional, Tuple


DEFAULT_LAT = 21.82646
DEFAULT_LON = 106.76771
USER_AGENT = "BreezyWeatherAddressTester/1.0"

NOMINATIM_BASE = "https://nominatim.openstreetmap.org/reverse"
LOCATIONIQ_BASE = "https://us1.locationiq.com/v1/reverse.php"

# Mirrors NominatimService.kt:
# Pattern.compile("(?iu)^(?:xã|phường|đặc\\s*khu|xa|phuong|dac\\s*khu)\\s+.*")
VN_SUB_PROVINCE_REGEX = re.compile(
	r"^(?:xã|phường|đặc\s*khu|xa|phuong|dac\s*khu)\s+.*",
	flags=re.IGNORECASE,
)


def load_env_file(path: str) -> None:
	if not os.path.exists(path) or not os.path.isfile(path):
		return

	with open(path, "r", encoding="utf-8") as f:
		for raw_line in f:
			line = raw_line.strip()
			if not line or line.startswith("#") or "=" not in line:
				continue
			key, value = line.split("=", 1)
			key = key.strip()
			value = value.strip().strip('"').strip("'")
			if key and key not in os.environ:
				os.environ[key] = value


def load_locationiq_key() -> Optional[str]:
	# Priority: existing env -> .env/locationiq.env -> .env
	load_env_file(os.path.join(".env", "locationiq.env"))
	load_env_file(".env")
	return os.getenv("LOCATIONIQ_KEY")


def parse_coords_from_text(text: str) -> Tuple[float, float]:
	# Accepts: "21.82646, 106.76771" or "21.82646 106.76771"
	m = re.match(r"^\s*([+-]?\d+(?:\.\d+)?)\s*[,\s]\s*([+-]?\d+(?:\.\d+)?)\s*$", text)
	if not m:
		raise ValueError("Invalid format. Use: lat, lon")

	lat = float(m.group(1))
	lon = float(m.group(2))
	if not (-90 <= lat <= 90 and -180 <= lon <= 180):
		raise ValueError("Invalid coordinate range.")
	return lat, lon


def get_coordinates() -> Tuple[float, float]:
	skip_next = False
	argv: List[str] = []
	for idx, a in enumerate(sys.argv[1:]):
		if skip_next:
			skip_next = False
			continue
		if a in ("--repeat",):
			skip_next = True
			continue
		if a.startswith("--"):
			continue
		argv.append(a)
	if argv:
		return parse_coords_from_text(" ".join(argv))

	try:
		pasted = input("Paste lat, lon (Enter for default 21.82646, 106.76771): ").strip()
	except EOFError:
		pasted = ""

	if not pasted:
		return DEFAULT_LAT, DEFAULT_LON
	return parse_coords_from_text(pasted)


def fetch_json(url: str, params: Dict[str, Any]) -> Dict[str, Any]:
	query = urllib.parse.urlencode(params)
	req = urllib.request.Request(
		f"{url}?{query}",
		headers={
			"User-Agent": USER_AGENT,
			"Accept-Language": "vi-VN",
		},
	)
	with urllib.request.urlopen(req, timeout=20) as resp:
		return json.loads(resp.read().decode("utf-8"))


def is_clean(info: Dict[str, Any]) -> bool:
	country = (info.get("country_code") or "").upper()
	city = info.get("city")
	if country == "VN":
		return bool(city and VN_SUB_PROVINCE_REGEX.match(city))
	return True


def pick_best_vn_part(parts: List[str]) -> Tuple[Optional[str], List[str]]:
	matches = [p for p in parts if VN_SUB_PROVINCE_REGEX.match(p)]
	if not matches:
		return None, []

	# Must pick the last valid Xa/Phuong/Dac Khu component in original order.
	# This handles both directions of old/new administrative naming.
	return matches[-1], matches


def run_algorithm_self_tests() -> None:
	cases = [
		("Phường A, Xã B , blabla", "Xã B"),
		("Xã B, Phường A, blabla", "Phường A"),
		("Ủy ban nhân dân Phường A, Phường A, Xã B", "Xã B"),
		("Xã C, Phường D, Xã C", "Xã C"),
	]

	print("--- Algorithm self-tests ---")
	for raw, expected in cases:
		parts = [p.strip() for p in raw.split(",")]
		picked, _ = pick_best_vn_part(parts)
		ok = picked == expected
		print(f"{raw} -> {picked} | expected={expected} | {'OK' if ok else 'FAIL'}")
	print()


def get_arg_int(flag: str, default: int) -> int:
	if flag not in sys.argv:
		return default
	idx = sys.argv.index(flag)
	if idx + 1 >= len(sys.argv):
		return default
	try:
		return int(sys.argv[idx + 1])
	except ValueError:
		return default


def fetch_nominatim(lat: float, lon: float) -> Dict[str, Any]:
	return fetch_json(
		NOMINATIM_BASE,
		{
			"lat": lat,
			"lon": lon,
			"zoom": 13,
			"format": "jsonv2",
		},
	)


def fetch_locationiq(lat: float, lon: float, key: str) -> Dict[str, Any]:
	return fetch_json(
		LOCATIONIQ_BASE,
		{
			"key": key,
			"lat": lat,
			"lon": lon,
			"zoom": 18,
			"format": "json",
		},
	)


def pick_final(liq: Optional[Dict[str, Any]], nom: Optional[Dict[str, Any]]) -> Tuple[Optional[Dict[str, Any]], str]:
	if liq:
		return liq, "LocationIQ priority"
	if nom:
		return nom, "Nominatim fallback (LocationIQ missing)"
	return None, "No valid result"


def run_realtime_round(lat: float, lon: float, locationiq_key: Optional[str], round_idx: int) -> None:
	start = time.perf_counter()
	liq_raw: Optional[Dict[str, Any]] = None
	nom_raw: Optional[Dict[str, Any]] = None
	liq_err: Optional[str] = None
	nom_err: Optional[str] = None

	with ThreadPoolExecutor(max_workers=2) as executor:
		nom_future = executor.submit(fetch_nominatim, lat, lon)
		liq_future = executor.submit(fetch_locationiq, lat, lon, locationiq_key) if locationiq_key else None

		try:
			nom_raw = nom_future.result(timeout=25)
		except Exception as e:
			nom_err = str(e)

		if liq_future:
			try:
				liq_raw = liq_future.result(timeout=25)
			except Exception as e:
				liq_err = str(e)

	liq = convert_location(liq_raw, is_locationiq_source=True) if liq_raw else None
	nom = convert_location(nom_raw, is_locationiq_source=False) if nom_raw else None
	final, reason = pick_final(liq, nom)

	elapsed_ms = int((time.perf_counter() - start) * 1000)
	print(f"\n===== Round {round_idx} ({elapsed_ms} ms) =====")
	print("LocationIQ raw:", (liq_raw.get("display_name") if liq_raw else "<error/skipped>"))
	if liq_err:
		print("LocationIQ error:", liq_err)
	print("Nominatim raw:", (nom_raw.get("display_name") if nom_raw else "<error>"))
	if nom_err:
		print("Nominatim error:", nom_err)

	if liq:
		print("LocationIQ city:", liq.get("city"), "| clean:", is_clean(liq), "| candidates:", liq.get("vn_candidates"))
	if nom:
		print("Nominatim city:", nom.get("city"), "| clean:", is_clean(nom), "| candidates:", nom.get("vn_candidates"))

	if final:
		print("Selected source:", final.get("source"), "| reason:", reason)
		print("Selected city:", final.get("city"))
	else:
		print("Selected source: <none>")
		print("Reason:", reason)


def convert_location(result: Dict[str, Any], is_locationiq_source: bool) -> Optional[Dict[str, Any]]:
	address = result.get("address") or {}
	country_code = address.get("country_code")
	if not country_code:
		return None

	city = address.get("town") or result.get("name")
	district = address.get("village")
	vn_candidates: List[str] = []

	if country_code.lower() == "vn":
		display_name = result.get("display_name") or ""
		if display_name:
			parts = [p.strip() for p in re.split(r"[,，]", display_name)]
			best_part, vn_candidates = pick_best_vn_part(parts)

			if best_part:
				city = best_part
				district = None
			elif is_locationiq_source:
				# Keep Kotlin fallback for LocationIQ.
				fallback = parts[0].strip() if parts else None
				if fallback:
					city = fallback
					district = None

	return {
		"lat": result.get("lat"),
		"lon": result.get("lon"),
		"country_code": (country_code or "").upper(),
		"country": address.get("country"),
		"admin1": address.get("state"),
		"admin2": address.get("county"),
		"admin3": address.get("municipality"),
		"city": city,
		"district": district,
		"display_name": result.get("display_name"),
		"source": "LocationIQ" if is_locationiq_source else "Nominatim",
		"vn_candidates": vn_candidates,
	}


def main() -> None:
	if "--self-test" in sys.argv:
		run_algorithm_self_tests()
		return

	lat, lon = get_coordinates()
	locationiq_key = load_locationiq_key()
	repeat = max(1, get_arg_int("--repeat", 1))

	print(f"Testing coordinates: {lat}, {lon}\n")
	print("LocationIQ key source:", ".env/.env env var" if locationiq_key else "missing")
	print("Mode: realtime concurrent calls (simulates both APIs helping each other)")
	print("Rounds:", repeat)

	for i in range(1, repeat + 1):
		run_realtime_round(lat, lon, locationiq_key, i)


if __name__ == "__main__":
	main()
