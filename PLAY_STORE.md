# Cockpit: Google Play kit

## Name and descriptions
**Name (max 30):** Cockpit: car logbook

**Short description (max 80):**
Fuel, maintenance, expenses and deadlines for your car in one place.
(PT: Consumos, manutenção, despesas e prazos do teu carro num só sítio.)

**Full description (EN):**
Cockpit is your car's logbook. Log refuels, services, tyres, insurance, road tax and inspection, and see how much you spend and how much you consume.

• Average consumption, cost per km and monthly spending, with charts
• Reminders for oil, inspection, insurance and road tax, even with the app closed
• Fuel prices near you and a station map (DGEG data)
• Electric cars: charging in kWh and a charger map (OpenStreetMap)
• Scan invoices and the odometer with the camera
• Fuel loyalty cards with barcodes
• Home screen widgets
• Old history: import a CSV or add several entries in a row
• Backup to a file or to your Google account
• Optional community: show your car with a nickname and chat with other drivers
• Portuguese and English, light and dark mode, 40+ brands with their colours

Your data stays on your phone. The cloud and the community only work if you sign in with Google, and they are optional.

**Full description (PT):** same list in Portuguese (see the Portuguese store listing).

## "Data safety" form
- Approximate location: used for stations and chargers, not stored by us. Chargers are requested from the Overpass server (OpenStreetMap), which receives the position.
- Personal information (email, nickname) and car data: only with Google sign-in and with backup or community enabled; encrypted in transit; users can delete their account and data in the app.
- Photos: stay on the phone; they go to the cloud only if backup is turned on.
- Community messages: stored in Firebase between the two users.
- No ads, no data sales.
- Privacy policy link: the published document (required).

## Content rating
The app has messages between users: answer "yes" to user-generated content. Minimum age for the community: 16 (already asked in the app). It has reporting, blocking and moderation.

## Before publishing
1. Google Play developer account (USD 25, one-off).
2. Generate the signed file: Android Studio > Build > Generate Signed App Bundle (AAB). Create a keystore and KEEP it with the password in two places: if you lose it you cannot update the app.
3. Add the release keystore's SHA-1 fingerprint in Firebase (Project settings > your Android app), otherwise Google sign-in fails in the store version.
4. 512x512 icon, 1024x500 feature graphic, at least 2 phone screenshots.
5. A privacy policy with a public URL, and a contact email.
6. New developer accounts: closed test with 12 testers for 14 days before you can publish to production.
7. Switch the Firebase rules to the ones in REGRAS_FIREBASE.json and create the `admins` node.
8. Replace the Logo.dev key that was left exposed.
