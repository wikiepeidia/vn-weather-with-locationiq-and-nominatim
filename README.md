
<h1 align="center">Weather VN (Thời Tiết VN)</h1>

<h4 align="center">Weather VN is a fork of Breezy Weather, optimized for Vietnamese addresses with LocationIQ and Nominatim integration. Feature-rich, free and open source Material 3 Expressive weather app with support for forecast, observations, nowcasting, air quality, pollen, and alerts from more than 50 weather sources.</h4>

# ✉️ Contact us

- If you'd like to report a bug or suggest a new feature, please open an issue on the [GitHub repository](https://github.com/wikiepeidia/vn-weather-with-locationiq-and-nominatim/issues).

# 📜 License

- [GNU Lesser General Public License v3.0](/LICENSE)
- This License does not grant any rights in the trademarks, service marks, or logos of any Contributor.
- Misrepresentation of the origin of that material is prohibited, and modified versions of such material must be marked in reasonable ways as different from the original version.

Before creating a fork, check if the intent action `nodomain.freeyourgadget.gadgetbridge.ACTION_GENERIC_WEATHER` can cover your need (for example, you want to re-use our weather data in your own customized widget). It can be enabled from Settings > Widgets & Live Wallpaper > Data sharing. You can also [help testing our `ContentProvider` exposing the full weather data of Breezy Weather](https://github.com/breezy-weather/breezy-weather/discussions/2089).

Otherwise, remember to:

- Respect the project’s LICENSE
- Avoid confusion with the Breezy Weather app:
  - Do NOT use the `breezy` flag when compiling releases you plan to distribute
  - Change the app name in [`res_fork/values/strings.xml`](https://github.com/breezy-weather/breezy-weather/blob/main/app/src/res_fork/values/strings.xml)
  - Change the app icons in the [`res_fork`](https://github.com/breezy-weather/breezy-weather/blob/main/app/src/res_fork) folders
  - Avoid installation conflicts: change the `applicationId` in [`build.gradle.kts`](https://github.com/breezy-weather/breezy-weather/blob/main/app/build.gradle.kts#L25)

# TODO

- rebase the application to the latest version of Breezy Weather while maintaining the features of this fork. Try to probably not impersonating the original app, but rather making it clear that this is a fork optimized for Vietnamese users with specific features.
