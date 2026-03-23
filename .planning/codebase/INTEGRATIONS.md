# External Integrations

**Analysis Date:** 2026-03-23

---

## Location & Geocoding APIs

### Nominatim / LocationIQ (primary geocoder — VN focus)

- **Service:** OpenStreetMap Nominatim + LocationIQ (unified service)
- **Implementation:** `app/src/main/java/org/breezyweather/sources/nominatim/NominatimService.kt`
- **API interface:** `app/src/main/java/org/breezyweather/sources/nominatim/NominatimApi.kt`
- **JSON models:** `app/src/main/java/org/breezyweather/sources/nominatim/json/`
- **Base URLs:**
  - Nominatim (default/free): `https://nominatim.openstreetmap.org/`
  - LocationIQ (if key present): `https://us1.locationiq.com/v1/`
- **Auth:** No key for Nominatim; LocationIQ uses `pk.xxx` prefix API key stored at runtime in `SourceConfigStore` (user-entered in settings, NOT a build-time key)
- **Features:** Forward location search (`/search`), reverse geocoding (`/reverse`)
- **Special VN behavior:** When a LocationIQ key is configured, both APIs are called **concurrently** (parallel `Observable.zip`). LocationIQ result is preferred; Nominatim serves as fallback. Vietnamese ward/commune names (`Xã`, `Phường`, `Đặc Khu`) are extracted from `display_name` using `vnSubProvinceRegex`. Only tokens starting with those prefixes (preceded by `,` in the display string) are accepted — this prevents incorrectly capturing strings like "Ủy ban nhân dân Phường ...".
- **Key detection:** `isLocationIqKey(value)` checks `value.startsWith("pk.")`

### Open-Meteo Geocoding

- **Base URL:** `https://geocoding-api.open-meteo.com/`
- **API interface:** `app/src/main/java/org/breezyweather/sources/openmeteo/OpenMeteoGeocodingApi.kt`
- **Auth:** None (free)
- **Features:** Forward city search

### GeoNames

- **Implementation:** `app/src/src_nonfreenet/org/breezyweather/sources/geonames/`
- **Auth:** API key via `BuildConfig.GEO_NAMES_KEY` (set in `local.properties` as `breezy.geonames.key`)
- **Features:** Location search

### Baidu IP Location

- **Implementation:** `app/src/src_nonfreenet/org/breezyweather/sources/baiduip/`
- **Auth:** API key via `BuildConfig.BAIDU_IP_LOCATION_AK` (set in `local.properties` as `breezy.baiduip.key`)
- **Features:** IP-based location for China

### Android Platform Geocoder

- **Implementation:** `app/src/main/java/org/breezyweather/sources/android/AndroidLocationService.kt`
- **Auth:** None (native OS API)
- **Features:** GPS location, platform reverse geocoding

---

## Weather Data APIs

### Open-Meteo (Free — default weather source)

- **Base URLs:** `https://api.open-meteo.com/` (forecast), `https://air-quality-api.open-meteo.com/` (AQ)
- **API interfaces:** `app/src/main/java/org/breezyweather/sources/openmeteo/OpenMeteoForecastApi.kt`, `OpenMeteoAirQualityApi.kt`
- **Auth:** None (free tier, no key required)
- **Features:** Hourly/daily forecast, current, air quality
- **Flavor:** Both `basic` and `freenet`

### BrightSky (Germany — DWD data)

- **Base URL:** `https://api.brightsky.dev/` (configurable)
- **Implementation:** `app/src/main/java/org/breezyweather/sources/brightsky/BrightSkyService.kt`
- **Auth:** None (free)
- **Features:** Forecast, alerts
- **Flavor:** Both

### Met.no (Norway Meteorological Institute)

- **Implementation:** `app/src/src_nonfreenet/org/breezyweather/sources/metno/`
- **Auth:** None (free, `User-Agent` header required)
- **Features:** Nordic forecast

### NWS (US National Weather Service)

- **Implementation:** `app/src/src_nonfreenet/org/breezyweather/sources/nws/`
- **Auth:** None (free US government API)
- **Features:** US regional forecast, alerts

### SMHI (Sweden)

- **Implementation:** `app/src/src_nonfreenet/org/breezyweather/sources/smhi/`
- **Auth:** None (free)

### OpenWeather (requires API key)

- **Base URL:** Configured in service at runtime
- **Stub:** `app/src/main/java/org/breezyweather/sources/openweather/OpenWeatherServiceStub.kt`
- **Implementation:** `app/src/src_freenet/org/breezyweather/sources/openweather/OpenWeatherService.kt` (freenet stub), `app/src/src_nonfreenet/org/breezyweather/sources/openweather/` (full JSON models)
- **Auth:** API key via `BuildConfig.OPEN_WEATHER_KEY` (`breezy.openweather.key` in `local.properties`)
- **Features:** Forecast, current conditions, air quality

### AccuWeather (requires API key)

- **Base URLs:** `https://dataservice.accuweather.com/` (developer), `https://api.accuweather.com/` (enterprise)
- **Implementation:** `app/src/src_nonfreenet/org/breezyweather/sources/accu/AccuService.kt`
- **Stub:** `app/src/main/java/org/breezyweather/sources/accu/AccuServiceStub.kt`
- **Auth:** API key stored in `SourceConfigStore` (runtime user-entered)
- **Features:** Forecast, current, AQI, alerts

### Météo-France / MF (requires API key + JWT)

- **Implementation:** `app/src/src_nonfreenet/org/breezyweather/sources/mf/`
- **Auth:** `BuildConfig.MF_WSFT_KEY` + `BuildConfig.MF_WSFT_JWT_KEY` — JWT token signed with JJWT library
- **Features:** French weather forecast

### Met Office (UK, requires API key)

- **Implementation:** `app/src/src_nonfreenet/org/breezyweather/sources/metoffice/`
- **Auth:** `BuildConfig.MET_OFFICE_KEY` (`breezy.metoffice.key`)
- **Features:** UK weather forecast

### Pirate Weather (requires API key)

- **API interface:** `app/src/main/java/org/breezyweather/sources/pirateweather/PirateWeatherApi.kt`
- **Implementation:** `app/src/src_nonfreenet/org/breezyweather/sources/pirateweather/`
- **Auth:** `BuildConfig.PIRATE_WEATHER_KEY` (`breezy.pirateweather.key`)
- **Features:** Weather forecast (DarkSky-compatible API)

### KNMI (Netherlands)

- **Base URLs:** `https://api.app.knmi.cloud/` (prod), `https://api.app.dev.knmi.cloud/` (demo/debug)
- **API interface:** `app/src/main/java/org/breezyweather/sources/knmi/KnmiApi.kt`
- **Implementation:** `app/src/src_nonfreenet/org/breezyweather/sources/knmi/`
- **Auth:** API key via `SourceConfigStore`

### AEMET (Spain)

- **Base URL:** `https://opendata.aemet.es/opendata/`
- **Implementation:** `app/src/src_nonfreenet/org/breezyweather/sources/aemet/AemetService.kt`
- **Auth:** API key

### Infoplaza (Netherlands)

- **Implementation:** `app/src/src_nonfreenet/org/breezyweather/sources/infoplaza/`
- **Auth:** `BuildConfig.INFOPLAZA_KEY`

### Met.ie (Ireland)

- **Implementation:** `app/src/src_nonfreenet/org/breezyweather/sources/metie/`
- **Auth:** `BuildConfig.MET_IE_KEY`

### CWA (Taiwan Central Weather Administration)

- **Implementation:** `app/src/src_nonfreenet/org/breezyweather/sources/cwa/`
- **Auth:** `BuildConfig.CWA_KEY`

### ECCC (Environment and Climate Change Canada)

- **Implementation:** `app/src/src_nonfreenet/org/breezyweather/sources/eccc/`
- **Auth:** `BuildConfig.ECCC_KEY`

### BMKG (Indonesia)

- **Implementation:** `app/src/src_nonfreenet/org/breezyweather/sources/bmkg/`
- **Auth:** `BuildConfig.BMKG_KEY`

### Polleninfo

- **Implementation:** `app/src/src_nonfreenet/org/breezyweather/sources/polleninfo/`
- **Auth:** `BuildConfig.POLLENINFO_KEY`

---

## Air Quality APIs

### Atmo France (multiple regional variants)

- **Base URLs:**
  - Atmo France: `https://admindata.atmo-france.org/openapi/`
  - Atmo Aura: `https://api.atmo-aura.fr/air2go/v3/`
  - Atmo Grand Est: `https://api.atmo-grandest.eu/airtogo/v1/`
  - Atmo HDF: `https://api.atmo-hdf.fr/airtogo/`
  - Atmo Sud: `https://api.atmosud.org/air2go/v1/cartes/`
- **Auth:** Regional API keys (`BuildConfig.ATMO_GRAND_EST_KEY`, `ATMO_HDF_KEY`, `ATMO_SUD_KEY`)
- **Implementation:** `app/src/src_nonfreenet/org/breezyweather/sources/atmo/`

### EPDHK (Hong Kong Environmental Protection)

- **Implementation:** `app/src/src_nonfreenet/org/breezyweather/sources/epdhk/`
- **Auth:** None

### Recosante (France pollen + air quality)

- **Implementation:** `app/src/src_nonfreenet/org/breezyweather/sources/recosante/`
- **Auth:** None

---

## National Meteorological Services (Free)

All implementations are dual-located in `src/src_freenet/` (stubs or lite) and `src/src_nonfreenet/` (full):

| Service | Country | Directory |
|---------|---------|-----------|
| GeoSphere Austria | Austria | `sources/geosphereat/` |
| BMD | Bangladesh | `sources/bmd/` |
| FMI | Finland | `sources/fmi/` |
| HKO | Hong Kong | `sources/hko/` |
| Ilmateenistus | Estonia | `sources/ilmateenistus/` |
| IMD | India | `sources/imd/` |
| IMS | Israel | `sources/ims/` |
| IPMA | Portugal | `sources/ipma/` |
| IPSB | — | `sources/ipsb/` |
| JMA | Japan | `sources/jma/` |
| LHMT | Lithuania | `sources/lhmt/` |
| LVGMC | Latvia | `sources/lvgmc/` |
| Meteo AM | Italy | `sources/meteoam/` |
| MétéoLux | Luxembourg | `sources/meteolux/` |
| MGM | Turkey | `sources/mgm/` |
| NAMEM | Namibia | `sources/namem/` |
| NCDR | Taiwan | `sources/ncdr/` |
| NCEI | USA (NOAA historical) | `sources/ncei/` |
| NLSC | Taiwan | `sources/nlsc/` |
| PAGASA | Philippines | `sources/pagasa/` |
| SMG | Macau | `sources/smg/` |
| EKUK | — | `sources/ekuk/` |

### ClimWeb (Multiple African Countries)

- **Base URLs:** Varies per country (e.g., Burkina Faso: `https://www.meteoburkina.bf/`)
- **API interface:** `app/src/main/java/org/breezyweather/sources/climweb/ClimWebApi.kt`
- **Countries:** Burkina Faso, Togo, Malawi, Niger, Gambia, Ethiopia, Ghana, Burundi, Guinea-Bissau, Mali, Benin, Chad, Congo, Zimbabwe, Seychelles, Sudan, South Sudan
- **Implementation:** `app/src/main/java/org/breezyweather/sources/climweb/`

### FPAS (KDE Alerts)

- **Base URL:** `https://alerts.kde.org/` (configurable)
- **API interface:** `app/src/main/java/org/breezyweather/sources/fpas/FpasJsonApi.kt`, `FpasXmlApi.kt`

---

## Data Storage

**Primary Database:**

- SQLDelight 2.3.2 with custom Android SQLite driver
- Schema: `data/src/main/sqldelight/breezyweather/data/` (tables: `Locations`, `Weathers`, `Dailys`, `Hourlys`, `Minutelys`, `Alerts`, `Normals`)
- Repository layer: `data/src/main/java/breezyweather/data/location/LocationRepository.kt`, `data/src/main/java/breezyweather/data/weather/WeatherRepository.kt`
- DI wiring: `app/src/main/java/org/breezyweather/common/di/DbModule.kt`

**HTTP Cache:**

- OkHttp disk cache at `app.cacheDir/http_cache`, 50 MiB limit
- Configured in `app/src/main/java/org/breezyweather/common/di/HttpModule.kt`

---

## Authentication & Identity

**No user authentication system.** The app is client-only with no account login.

**API Key management:**

- Build-time keys: injected via `BuildConfig` from `local.properties` (see STACK.md)
- Runtime user keys: stored via `SourceConfigStore` (Android `SharedPreferences`-backed) — used for AccuWeather, Nominatim/LocationIQ instance URL and key, KNMI, etc.
- LocationIQ key detection: `value.startsWith("pk.")` in `NominatimService`

---

## Update Checker

- **GitHub Releases API:** `https://api.github.com/repos/{org}/{repo}/releases/latest`
- **Implementation:** `app/src/main/java/org/breezyweather/background/updater/data/GithubApi.kt`, `ReleaseService.kt`
- **Auth:** None (unauthenticated public API calls)
- **Org/repo:** Configured via `gradle.properties` (`breezy.github.org`, `breezy.github.repo`)

---

## Geographic Data

**Natural Earth (bundled):**

- Country boundary data at `app/work/ne_50m_admin_0_countries.json`
- Used by `NaturalEarthConfigPlugin` and sources for country matching
- Implementation: `app/src/main/java/org/breezyweather/sources/naturalearth/`

---

## Broadcast / Data Sharing

**Breezy Data Sharing Lib:**

- `com.github.breezy-weather:breezy-weather-data-sharing-lib` (git hash `09c0e4dd`)
- Used for broadcasting weather data to other apps/widgets
- Implementation: `app/src/main/java/org/breezyweather/background/provider/WeatherContentProvider.kt`

---

## Monitoring & Observability

**Error Tracking:** None (no Crashlytics, Sentry, etc.)

**Logging:**

- OkHttp `HttpLoggingInterceptor` — HTTP traffic logging in debug builds
- Android `Logcat` — standard Android logging

**CI/CD & Deployment:**

- Hosting: GitHub (source + releases)
- CI Pipeline: Not detected in workspace (no `.github/workflows/` examined)

---

## Environment Configuration

**Required build-time `local.properties` keys** (all optional — missing keys disable that source):

- `sdk.dir` — Android SDK path (mandatory for building)
- `breezy=true` — enable Breezy branding (optional)
- `breezy.openweather.key` — OpenWeather API key
- `breezy.pirateweather.key` — Pirate Weather API key
- `breezy.metoffice.key` — UK Met Office key
- `breezy.mf.key` + `breezy.mf.jwtKey` — Météo-France keys
- `breezy.baiduip.key` — Baidu IP location
- `breezy.geonames.key` — GeoNames geocoding
- `breezy.cwa.key`, `breezy.eccc.key`, `breezy.bmkg.key` — national services
- `breezy.metie.key`, `breezy.polleninfo.key`, `breezy.infoplaza.key`
- `breezy.atmograndest.key`, `breezy.atmohdf.key`, `breezy.atmosud.key`

**Runtime user-configured keys** (via app Settings → Sources):

- LocationIQ API key (entered as `pk.xxx` — triggers dual Nominatim+LocationIQ mode)
- AccuWeather API key
- KNMI API key
- Any other `ConfigurableSource` instances

---

*Integration audit: 2026-03-23*
