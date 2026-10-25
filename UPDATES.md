# In-app updates (GitHub)

The app looks for the latest *Release* of `JoaoAntunesss/cockpit`, compares the tag's version with the installed one and, if it is higher, downloads the attached `.apk` and opens the installer. Your data is kept.

## Publishing a new version
1. In `app/build.gradle.kts` bump `versionCode` (e.g. 4) and `versionName` (e.g. "1.2").
2. Android Studio: Build > Generate Signed App Bundle / APK > APK. Create a keystore the first time and **keep a backup of it**: every version must be signed with the same key, otherwise Android refuses the update. Choose the release variant.
3. On GitHub: Releases > Draft a new release. Tag `v1.2` (same as versionName, with a leading v). Pick any title. Write the release notes in the description (they are shown in the app). Drag the `.apk` into the attachments area. Publish release.
4. On the phone: Settings > Check for updates. The app also notifies once a day when it opens.

## Notes
- The first time you install this way, Android asks you to allow "install unknown apps" for this app.
- Play Protect may show a warning because the APK does not come from the Play Store.
- If the first installed version was signed with a different key than later ones, it must be uninstalled first (you lose local data; make a backup).
- Public repository = the code is visible. `google-services.json` must not be committed (already in `.gitignore`).
