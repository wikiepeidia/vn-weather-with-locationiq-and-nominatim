
<h1 align="center">Weather VN (Thời Tiết VN)source code</h1>

<h4 align="center">Weather VN is a fork of Breezy Weather, optimized for Vietnamese addresses with LocationIQ and Nominatim integration.These both help each other in finding the addresses, making them 90% accurate, up to date after the July 2025 Merge!</h4>

## ✉️ Contact us

- If you'd like to report a bug or suggest a new feature, please open an issue on the [GitHub repository](https://github.com/wikiepeidia/vn-weather-with-locationiq-and-nominatim/issues).

## 📜 License

- [GNU Lesser General Public License v3.0](/LICENSE)
- This License does not grant any rights in the trademarks, service marks, or logos of any Contributor.
- Misrepresentation of the origin of that material is prohibited, and modified versions of such material must be marked in reasonable ways as different from the original version.

- The upstream project (Breezy Weather) is available at <https://github.com/breezy-weather/breezy-weather> and is licensed under LGPL-3.0.
- Reclarify to the devs: Hobby project for fun, powered ® by Claude
- remove Releases to incoperate with LICENSE and reduce my stress and giggles

## features new

- LocationIQ + Nominatim dual geocoding with regex-based VN token extraction , heavily finetuned for almost all kind of JSON responses from both APIs, with smart cross-validation

### xiaomi stuff related

- hyperos goofyahh ưatchdog because i hate it when it kill my app
- command to get weather app prisority has to be on the ADB shell (CMD)

```bash
cmd appops set io.github.wikiepeidia.vnweather RUN_IN_BACKGROUND allow
dumpsys deviceidle whitelist +io.github.wikiepeidia.vnweather
cmd appops set io.github.wikiepeidia.vnweather RUN_ANY_IN_BACKGROUND allow
cmd appops set io.github.wikiepeidia.vnweather WAKE_LOCK allow
cmd appops set io.github.wikiepeidia.vnweather SYSTEM_ALERT_WINDOW allow
```
