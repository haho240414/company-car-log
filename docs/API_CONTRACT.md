# Server API Contract

The Android app is implemented against repository/OCR/export interfaces so the demo
implementation can be replaced by a real company cloud service without changing UI
or mileage calculation code.

## Authentication

`POST /v1/auth/invite-login`

Request:

```json
{
  "inviteCode": "HWACHEON-2026",
  "name": "홍길동",
  "pin": "1234",
  "department": "금융지점",
  "position": "지점장"
}
```

Response:

```json
{
  "token": "jwt-or-session-token",
  "user": {
    "id": "user-id",
    "name": "홍길동",
    "department": "금융지점",
    "position": "지점장",
    "isAdmin": false
  }
}
```

## Odometer OCR

`POST /v1/ocr/odometer`

Multipart form:

- `image`: odometer photo
- `vehicleId`: `carnival`, `starex`, or `nexo`

Response:

```json
{
  "odometerKm": 65123,
  "confidence": 0.94,
  "capturedAt": "2026-05-21T16:23:05+09:00"
}
```

If the AI cannot read the number confidently, return `odometerKm: null` and let the
app keep the photo while asking the user to type the value manually.

## Trip Logs

`GET /v1/trips?vehicleId=carnival&month=2026-05`

`POST /v1/trips`

```json
{
  "vehicleId": "carnival",
  "usageDate": "2026-05-21",
  "writerName": "홍길동",
  "departmentName": "금융지점",
  "positionName": "지점장",
  "purpose": "업무용",
  "departure": "본점",
  "stopover": "금융지점",
  "destination": "본점",
  "startOdometerKm": 65000,
  "endOdometerKm": 65123,
  "drivingDistanceKm": 123,
  "monthlyBusinessDistanceKm": 456,
  "ocrConfirmed": true,
  "photoId": "stored-photo-id"
}
```

The server should re-run the same mileage validations before saving:

- end odometer must be greater than or equal to start odometer
- monthly totals are scoped by vehicle and month
- vehicle logs do not affect other vehicles

## Exports

`POST /v1/exports`

```json
{
  "vehicleId": "carnival",
  "month": "2026-05",
  "format": "XLSX"
}
```

Response:

```json
{
  "fileName": "2026년_05월_카니발_업무용승용차_운행기록부.xlsx",
  "downloadUrl": "https://example.com/signed-download-url"
}
```

The export renderer should reproduce the paper form columns in order: usage date,
department, position, name, purpose, departure, start odometer, stopover,
destination, end odometer, driving distance, monthly business distance, remarks,
writer, clerk confirmation, responsible person confirmation.
