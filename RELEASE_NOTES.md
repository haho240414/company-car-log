# Release Notes

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
