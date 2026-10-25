# Cockpit: developer guide (Android, Kotlin + Jetpack Compose)

1. Android Studio > File > Open > pick this folder.
2. Wait for the Gradle sync to finish (it downloads Gradle 8.9 and the dependencies).
3. Connect a phone (USB debugging) or create an emulator, and press Run.

## What it has
- A garage with several cars; the car's brand changes the colours of the whole app (Theme.kt, `Brand` enum).
- Refuels with real L/100 km consumption (only between full tanks) and a chart.
- Service diary (date, km, type, who did it, parts and labour).
- Maintenance reminders by km and/or months; logging a service of the same type resets the reminder.
- Monthly expenses (fuel, mechanics, fixed costs) and cost per km.
- Data stored on the phone with Room (works offline).

## Fuel prices
The Fuel screen shows today's national average price for the car's fuel (cheapest, most expensive and number of stations).
It comes from `GET https://api.apiaberta.pt/v1/fuel/prices` (see FuelPrices.kt) and is cached on the phone so it shows offline.
The refuel form shows today's average next to the price per litre you paid. It is the national average, not a specific station's price.

## Brand logos
Order: local file, then online logo, then the brand's initial.

**Online (optional, Logo.dev).** The Clearbit Logo API shut down in December 2025, so it cannot be used. Logo.dev is the replacement:
1. Create a free account at logo.dev and copy the public key (starts with `pk_`).
2. Put it in the project's `gradle.properties`: `logoDevKey=pk_xxxxxxxx`
3. Sync. The app fetches each brand's logo (Coil) and caches it.
Some free plans require an attribution link to Logo.dev in the app; check their terms.

**Local.** Copy the brand file to `app/src/main/res/drawable/` named
logo_peugeot, logo_opel, logo_citroen, logo_renault, logo_vw, logo_bmw, logo_mercedes, logo_toyota, logo_ford, logo_fiat
(.png, .webp or .xml). It takes priority over the online one and works offline.

## Stations near me
On the Fuel screen, the "Stations near me" button asks for location and lists stations within the chosen radius (5, 10 or 20 km),
by price or by distance, with the difference from the national average and how much you save on a tank. Tapping a station opens it on the map.
Per-station prices come from DGEG (precoscombustiveis.dgeg.gov.pt, PesquisarPostos API). See Stations.kt.
The API Aberta route /v1/fuel/stations was tested and is unusable: it ignores location and returned February/March prices.

## Pick the car from the website catalogue
"New car" has a "Fill in from the website catalogue" button: Brand > Model > Year > Version.
It reads the /catalogoIndice index and the /catalogo/{key} record from the website's Firebase (public read, over REST, no login). See Catalog.kt.
It fills in brand (and the app colours), model, year, engine and fuel when clear. Everything stays editable.
Note: the current catalogue only has 13 brands (no Peugeot, Opel, Citroën, Renault, Fiat) and some technical data looks synthetic, so always check the fields before saving.

## Documents and deadlines
Maintenance > "Documents and deadlines": insurance, inspection, road tax (IUC), registration papers and others, with expiry date,
insurer, policy number, cost, notes and a photo/scan (stored only on the phone, in files/docs).
The colour turns green, yellow or red depending on the days left (by default it warns 30 days ahead).
Items that need attention also appear on Home. Maintenance reminders stay customisable
(name, km and/or months) with ready-made shortcuts (oil, timing belt, brakes, tyres...). The database moved to version 2
with a migration, so your existing data is safe.

## Notifications
The app notifies you, even when closed, about insurance/inspection expiring (on the chosen lead day, 7 days, 1 day, on the day, and when expired)
and about upcoming or overdue maintenance. It checks every 12 hours. On Android 13 or later, accept the notification permission when asked.

## Google sign-in and cloud backup (one-time Firebase setup)
It uses the same project as the website (carros-dc76c). Without these steps the app works the same, just without the cloud.
1. Firebase Console > project carros-dc76c > Project settings > "Add app" > Android.
   Package name: com.cockpit.app
   SHA-1: in Android Studio, Gradle tab (right) > app > Tasks > android > signingReport, and copy the debug variant's SHA1.
2. Authentication > Sign-in method > enable "Google".
3. Back in the Android app settings, download google-services.json (only after the SHA-1 is added and Google is enabled) and put it in the app/ folder (next to the app's build.gradle.kts).
4. Realtime Database > Rules: ADD (without deleting the website's rules) inside "rules":
     "diarioBordo": {
       "$uid": {
         ".read": "auth != null && auth.uid === $uid",
         ".write": "auth != null && auth.uid === $uid"
       }
     }
5. Sync in Android Studio and run. At the top of the app, the account icon opens the sign-in screen.
It stores cars, refuels, services, expenses, reminders and documents with their images (downsized to ~1400 px).
On a new phone, signing in with no cars makes the app pull everything from the cloud automatically.

## Estimated km
From the pace of recent months the app estimates today's km, uses it in reminders and asks you on Home to confirm or correct it. It can be turned off in Settings.

## Photos and invoices
Services and expenses accept a photo or invoice (kept on the phone and included in the cloud backup).

## Export
Menu ⋮ > Settings and export: a PDF of the history (good to show a buyer) or a CSV for Excel. There is also Compare cars.

## Shortcuts
Long-press the app icon: Refuel, Service, Expense, Stations and map.

## Fuel price sign, look and brands
- Fuel tab: black fuel-station sign panel with LED digits (last digit smaller) and a colour label per fuel. Your fuel is highlighted.
- Settings > Appearance: System / Dark / Light mode and digit colour (red, amber, green, ice blue, white, pink or the brand colour).
- 40+ brands. Logos for 34 brands ship inside the app (res/drawable/logo_*.xml, Simple Icons, CC0) and work offline.
- Without a logo file (shown via Logo.dev if you have the key, otherwise the initial): Mercedes-Benz, Cupra, Land Rover, Jaguar, Alfa Romeo, Lancia, Lexus, BYD.

## Fuel cards
"Cards" tab: keep loyalty cards (Galp, BP, Repsol, Moeve, Prio, Shell, Esso, Continente, Auchan, Intermarché). "Scan code with camera" uses Google Play Services' scanner (no camera permission; needs Play Services). At the station you open the card and the code shows large, with brightness at maximum. Cards are included in the cloud backup. Local logos: Shell, Continente, Auchan, Intermarché; the others via Logo.dev or the initial. The database moves to version 4 (automatic migration).

## English
Settings > Language: System, Portuguese or English. "System" uses Portuguese if the phone is in Portuguese, otherwise English. The texts are in Strings.kt (PT > EN dictionary) and phrases with values use t("pt", "en"). To add a new phrase: write tr("text in Portuguese") and add the translation to the map in Strings.kt. Note: categories already saved (Oil and filters, Insurance, IUC...) and the document type names stay in Portuguese.

## Community (phase 1)
People icon at the top. Needs Google sign-in. Each person creates an optional profile with a unique nickname (3 to 20 characters), chooses which cars to show and whether km appear exact or as a range. Only brand, model, year, fuel, km and average consumption are public (never plate, costs, documents or location). There is a list with nickname search and a profile can be deleted.
Before use: in the Firebase console (project app-dashboardcarros) > Realtime Database > Rules, paste the contents of REGRAS_FIREBASE.json and publish. These rules replace the previous ones and keep each user's private backup.
Messages (requests, accept, conversations, block and report) are phase 2.

## Community (phase 2): requests and messages
On someone's profile there is "Request to chat". The other person sees the request under "Requests" and accepts or declines. Once accepted, you talk by text messages (max 1000 characters, no photos) under "Conversations". There is Block and Report (a report stores the last 10 messages in /reports, which only you can see in the Firebase console). Deleting the profile also deletes the conversation list.
IMPORTANT: paste REGRAS_FIREBASE.json into the Realtime Database rules again (they changed). Limits: messages only show while the app is open (no new-message notification; that needs Cloud Functions and the Blaze plan); there is no per-minute message limit server-side.

## Station logos
In res/drawable-nodpi are the PNGs of the station brands you sent (galp, bp, repsol, moeve, prio) and vector logos for Shell, Continente, Auchan and Intermarché. To replace one, replace the file station_<key>.png keeping the name. Petroprix is there too. Esso's was missing at the time.

## Delete account
Account and cloud > "Delete account and cloud data": deletes the private backup, the public profile, the nickname, the conversation list and the app's Google account (Firebase Auth). Data on the phone stays. Messages already sent remain visible to whoever received them. If Firebase asks for a recent login, sign out, sign in again and repeat.

## Extra logos
Added to res/drawable-nodpi: BYD, Lexus, Jaguar, Land Rover, Alfa Romeo, Cupra, Mercedes-Benz and Esso. Lancia included: no car logo is missing now.

## Electric cars and charging stations
- Car > Fuel now has "Electric". The battery (kWh) replaces the tank, "Litres" become kWh and consumption is shown in kWh/100 km.
- On the Fuel tab, electric cars see the "Charging stations" card instead of the price sign.
- Chargers come from OpenStreetMap (Overpass API, no key): name, operator, power and connectors when available. Green on the map = fast (50 kW or more).
- The location is sent to the Overpass server only for the search (update your privacy policy if you publish the app).

## Old history, intro and animations
- The new forms (refuel, service, expense) have "Save and add another": it saves, clears the form and keeps the date, to enter history in a row.
- Settings > "Import history (CSV)": reads the same format as the export (Date;Type;Km;Description;Litres;Cost;Notes). Ignores duplicates. Dates accept dd/MM/yyyy or yyyy-MM-dd.
- Old entries no longer touch reminders: they only update a reminder if they are newer than the last one.
- A 4-screen intro on first launch (Onboarding.kt) and smooth transitions between tabs.

## Batch B: widget, OCR, price alert, local backup, moderation
- **Widget:** long-press the home screen > Widgets > Cockpit. Shows the car, the km and the most urgent reminder.
- **Scan invoice / odometer:** camera button in the refuel, service and expense forms, and in the km dialog. Text is read on the phone (ML Kit). Always check the values.
- **Price alert:** Settings > "Notify me when fuel gets cheaper". Uses the fuel highlighted on the price sign.
- **Local backup:** Settings > "Save backup to a file" / "Restore from a file". Includes photos and cards.
- **Limits (on the phone):** 20 messages per minute, 15 requests and 5 reports per day.
- **Reports panel:** paste the new REGRAS_FIREBASE.json in the console. Then create by hand in Realtime Database the node `admins/<your uid>` with value `true` (the uid is in Authentication > Users). Anyone in admins sees "Reports panel" in Community, with "Dismiss" and "Ban" (removes the profile and prevents posting again).
- **Profile:** "About you" field (up to 140 characters).
- New dependencies: ML Kit text-recognition (Gradle sync required).

## Hybrids
- New fuels: "Hybrid (Petrol 95)", "Hybrid (Diesel)", "Plug-in hybrid (Petrol 95)", "Plug-in hybrid (Diesel)". Prices and stations use the fuel in brackets.
- On plug-ins, the refuel form has "Electric charge (kWh)". Charges are excluded from fuel consumption and shown in the history with ⚡.
- The database moved to version 5 (automatic migration; nothing is lost).

## Tyres, budget, real cost, year summary and sale report
- **Tyres** (Maintenance tab): mounted and stored sets, km per set, wear, age and rotation reminders. Part of the cloud and file backups.
- **Monthly budget**, **purchase price**, **km at purchase** and **current value**: in the car form. A bar on Home, warnings at 80% and 100%, and a "Real cost of the car" card in Expenses.
- **Year summary**: button in Expenses, shareable as text.
- **Sale report**: Settings, a PDF with maintenance, tyres and deadlines. Plate and values are optional.
- The widget refreshes when records are saved.
- The database moved to version 6 (automatic migration).

## Batch C: price widget and sharper LEDs
- New "fuel prices" widget (PriceWidget.kt): a sign with the average prices, LED digits drawn as a bitmap, the app's LED colour and favourite fuel; tapping opens the app; it refreshes by itself and when prices are refreshed in the app.
- LEDs (Led.kt): reduced halo (less blur), solid and crisp segments.

## Batch D: interface polish
- Splash screen (androidx.core:core-splashscreen) with the icon, monochrome icon (Android 13+), preview of both widgets.
- Empty states with an illustration and an action button; shimmering placeholders instead of the spinning circle.
- Swipe left to delete refuels, services and expenses; "Saved" and "Record deleted · Undo" (snackbar).
- Accessibility: LED digits are read by TalkBack. On wide screens the content is centred (max 640 dp).

## Batch E: "Parking" tab
- New "Parking" tab (Parking.kt): save where you left the car (with a floor/spot note), take me to the car, and search for car parks within 1.5 km (OpenStreetMap/Overpass), with All/Free/Garage filters and a map.
- The stations map no longer has the parking buttons (the yellow car dot still shows).
- The navigation bar shows only the selected tab's name, to fit 6 tabs.

## Batch F: design touches
- Lighter colours (green/red of the alerts, sign labels), a "+"-only log button, a hint when there is no average yet, a blank plate becomes "Add plate" (tap to edit).

## Batch G: refuels and fuel names
- Refuel form: choose between "Odometer km" and "Km since last" (the app adds it to the last record and shows the odometer value).
- Price sign and widget: hide coloured and heating diesel; "Diesel B15 (biodiesel)" with a B15 label; the chosen fuel now ignores the hidden ones.

## Batch H: design
- Home: a "what matters now" card (the most urgent deadline or reminder, large), the month's spending with an animated counter, softer card outlines, larger tiles without outline.
- Refuel: a large summary (total and price per litre) and the station tucked under "More details".
- Expenses: tapping a column shows the month's detail (fuel, mechanics, fixed).

## Batch I: "modern car" look
- Gauge and odometer animate on open (the arc tip glows), cards with a gradient outline in the brand colour, brand ambient light at the top of screens.
- Home: "Car status" with animated service rings (instead of bars), staggered entrance of the blocks.

## Batch J: needle and brand colours
- Gauge with an animated needle; each brand now has 2nd and 3rd logo colours (Theme.kt, EXTRA map) used on the needle, the panel's outline and glow, and the title bars.

## Batch K
- Average right from the 1st refuel: optional field "km driven on the previous tank" (creates a starting point without litres).
- IUC (Maintenance): registration month + yearly total, shows the next deadline with the 2026/2027/2028+ rules. Secondary sources, the exact days are not confirmed: check the Finance Portal.
- Estimated cost of the next service in reminders (average of the last 3 in the same category).
- Shared car (Settings): 8-character code, manual merge sync. Deleting does not propagate. Paste the new REGRAS_FIREBASE.json in the console ("shared" node).
- Not compiled by me.

## Batch L
- Refuel form warnings: km lower than the last one, jump > 3,000 km, unusual price per litre, more than the tank, duplicate. They do not block saving.
- IUC notification (≤ 31 days and ≤ 7 days before the end of the payment month), if the registration month is set.
- Not compiled by me.

## Batch M
- In-app update via GitHub (see UPDATES.md). Version 1.1 (code 3). REQUEST_INSTALL_PACKAGES permission and a FileProvider for updates/.

## Batch N
- Version 1.2: "Cost per km" and "Month forecast" cards on the home screen.

## Batch O
- Version 1.3: abnormal consumption alert (a notification and a home card when consumption rises 15% or more).

## Batch P
- Version 1.4: "Getting started" card on the home screen (4 steps, ticks itself, can be dismissed), yearly PDF report (Year summary > Yearly report), shortcut to the phone's home screen (Settings). Long-press shortcuts on the icon already existed.

## Batch Q
- Version 1.5: "Refuel" widget (2x1, one tap opens the form) and a redesigned price widget (fuel name, min–max range, "cheapest" label, header with the date).

## Batch R
- Version 1.6: search in the histories (fuel, services, expenses; appears with 5+ records), optional app lock with the phone's PIN/pattern/fingerprint (Settings), "Reminders" widget (the most urgent maintenance and deadlines).

## Batch S
- Version 1.7: a visual pass with the mobile-app-ui-design skill: spacing on the 8/16 grid (cards 16, space between blocks 16), a reward moment when saving a refuel (shows the consumption and how it compares with your average).

## Batch T
- Version 1.8: direct photo in the app (Take photo + Gallery) in documents, service invoices and expenses.
- Cards: creating a card opens the QR/barcode scanner straight away; a new button reads the code from a gallery image (new `barcode-scanning` dependency).
- Redesigned forms: header with an X, fields grouped in sections, Save/Delete buttons always visible, date and list fields styled like text fields. Applied to refuel, service, expense, reminder, car, tyres, document and card.
