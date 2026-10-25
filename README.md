# Cockpit

A logbook for your car, on Android. Fuel consumption, maintenance, expenses, deadlines, fuel prices and parking spots, all on your phone and themed with your car brand's colours.

[**Download the latest version (APK)**](https://github.com/JoaoAntunesss/cockpit/releases/latest)

## Features

- **Fuel:** log refuels (partial ones too) and get your real consumption. Flags odd values (km, price, duplicates) and shows how each refuel compares with your average. Supports electric and hybrid cars (plug-in included).
- **Maintenance:** reminders by km and by time (oil, timing belt, tyres...), a service diary with invoice photos and a cost estimate for the next one.
- **Deadlines:** insurance, inspection and road tax (IUC), with alerts even when the app is closed. IUC follows the new calendar (2026, 2027 and 2028 onwards).
- **Expenses:** monthly charts, cost per km, month forecast, monthly budget, year summary and a yearly PDF report.
- **Prices:** national average per fuel (DGEG) shown on an LED price sign, price-drop alerts, and fuel stations and chargers near you.
- **Parking:** save where you parked, get guided back to the car and search nearby car parks.
- **Widgets:** your car, fuel prices, one-tap "Refuel" and reminders.
- **Cards:** fuel and loyalty cards with barcodes.
- **Extras:** shared car, community, search in your histories, lock with your phone's PIN or fingerprint, backup to a file and to the cloud, CSV import, car sale report, Portuguese and English.
- **In-app updates:** Settings > Check for updates.

<!-- Screenshots: add PNGs to docs/screenshots and reference them here, for example:
<img src="docs/screenshots/home.png" width="240"> -->

## Install

1. Open the [Releases](https://github.com/JoaoAntunesss/cockpit/releases) page on your phone and download `cockpit.apk`.
2. Open the file. Android asks you to allow installing unknown apps: accept.
3. From then on the app updates itself through Settings > Check for updates.

## Build

Requirements: a recent Android Studio and JDK 17.

1. Open the folder in Android Studio and let Gradle sync.
2. Optional: for Google sign-in and cloud backup, create a Firebase project and put your `google-services.json` in `app/` (never commit it; it is already in `.gitignore`). Without it the app still builds and works with local data only.
3. Optional: put your public Logo.dev key in `gradle.properties` (`logoDevKey=...`).
4. Run.

More details in [GUIDE.md](GUIDE.md). Publishing new versions: [UPDATES.md](UPDATES.md). Google Play checklist: [PLAY_STORE.md](PLAY_STORE.md). Database rules: [REGRAS_FIREBASE.json](REGRAS_FIREBASE.json).

## Tech stack

Kotlin, Jetpack Compose (Material 3), Room, WorkManager, Firebase (Auth and Realtime Database), OpenStreetMap (osmdroid and Overpass), ML Kit (invoice scanning) and Coil.

## Privacy

Your data stays on your phone. It only leaves if you sign in with Google and turn on cloud backup, or if you use the community or the shared car. Location is used only for nearby stations, chargers and car parks, and to take you back to your car.

## Credits

Maps © OpenStreetMap contributors. Fuel prices: DGEG (Portugal). Brand icons: Simple Icons and Logo.dev.
