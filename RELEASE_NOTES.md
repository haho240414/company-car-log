# Release Notes

## v0.1.3 - Photo location and reliable CSV export

### Added

- Photo metadata reader now uses EXIF GPS or the device's last known location to suggest the stopover field.
- Camera permission flow asks for optional location permission so in-app photos can still use current location when EXIF GPS is unavailable.
- Export screen shows the selected month's saved records before exporting.
- CSV export saves to the phone's Downloads folder and opens Android sharing for Gmail, KakaoTalk, Google Sheets, and similar apps.

### Notes

- GPS is only available when the photo contains location metadata or location permission is granted.
- Google Sheets-style export is CSV-first in this version; real XLSX/PDF generation still needs the planned server implementation.

## v0.1.2 - OCR and Google Sheets CSV

### Added

- Bundled on-device ML Kit OCR for odometer photos.
- Odometer-specific candidate extraction that prefers mileage-like numbers and ignores common noise.
- Google Sheets compatible CSV sharing from the monthly export screen.
- Higher quality camera capture for OCR.

### Notes

- OCR still asks the user to confirm the detected value before saving.
- CSV export opens through Android's share sheet; choose Google Sheets to use it like a spreadsheet.

## v0.1.1 - Installable phone test build

### Added

- GitHub Actions workflow that builds an installable debug APK.
- Release APK upload automation for quick phone installation.
- On-device local persistence for user profile and trip logs, so one phone can test real entry flows without a cloud server.

### Notes

- Records are stored only on the installed phone in this version.
- Deleting the app deletes the saved records.
- Shared company-wide storage still requires the planned server/Firebase integration.

## v0.1.0 - Initial shared car log app

### Added

- Android app scaffold using Kotlin, Jetpack Compose, CameraX, and Photo Picker.
- Invite-code login with name, department, position, and PIN.
- Vehicle-specific trip logs for 카니발, 스타렉스, and 넥쏘.
- Odometer photo registration flow with demo OCR boundary for future server AI OCR.
- Automatic start odometer selection from the selected vehicle's previous trip.
- Driving distance and monthly business-distance total calculation.
- Monthly Excel/PDF export request UI matching the paper logbook structure.
- Server API contract for authentication, OCR, trip logs, and exports.
- Unit tests for mileage calculation and monthly totals.

### Known Limitations

- Real shared cloud storage is represented by a demo repository implementation.
- Real AI OCR and Excel/PDF generation require a server API and credentials.
- This local machine does not currently have JDK, Gradle, or Android SDK configured, so Android build/test validation must be run in Android Studio or CI.
