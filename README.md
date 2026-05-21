# 공유형 Android 차계부

회사 사람들이 같은 앱으로 카니발, 스타렉스, 넥쏘 운행기록부를 등록하고 월별 Word/CSV 문서로 내보낼 수 있는 Android 앱입니다.

## 구현 범위

- Kotlin + Jetpack Compose Android 앱
- CameraX 촬영 화면과 Android Photo Picker 사진 선택
- 초대코드 + 성명 + PIN 로그인 화면
- 부서/직책 선택 및 기타 입력
- 차량별 운행 등록과 기록 조회
- 도착 후 계기판 사진 1장 등록 흐름
- ML Kit 기반 기기 내 계기판 숫자 OCR
- 사진 EXIF 날짜/GPS 또는 기기 위치를 활용한 사용일자/경유지 자동 입력
- 출발 누적거리 자동 입력: 같은 차량의 직전 도착 누적거리 사용
- 주행거리 및 월 업무용 주행거리 누계 계산
- 월별 기록 화면 미리보기, Downloads Word/CSV 저장, Gmail/카카오톡/Google Sheets 공유
- 관리 화면에서 카니발, 스타렉스, 넥쏘 차량번호 입력 및 저장
- 잘못 저장한 운행기록 삭제
- 서버 교체를 위한 Repository/OCR/Export 인터페이스 경계
- 계산 로직 단위 테스트

## 현재 데모 동작

실제 공용 클라우드와 서버 기반 PDF 생성은 자격증명과 배포 환경이 필요하므로 아직 연결 전입니다. 다만 휴대폰 한 대에서 테스트할 수 있도록 사용자 정보와 운행기록은 앱 내부 로컬 저장소에 남고, Word/CSV 파일은 폰에서 바로 생성됩니다.

- 초대코드: `HWACHEON-2026`
- 관리자 데모 PIN: `0000`
- 사진 OCR: ML Kit 번들 OCR로 사진 속 숫자를 인식하고, 계기판 누적거리 후보를 자동 입력합니다. 저장 전 실제 숫자와 한 번 비교해 주세요.
- 내보내기: Word `.docx` 또는 CSV 파일을 Downloads 폴더에 저장하고, 동시에 공유 화면을 열어 메일/카카오톡 등으로 보낼 수 있습니다.
- Google Sheets: CSV 공유를 선택하면 안드로이드 공유 화면에서 Google Sheets로 열 수 있습니다.
- 저장: 현재 버전은 설치한 휴대폰 안에만 저장되며, 앱 삭제 시 기록도 삭제됩니다.

실서버 구현 시 `LocalCompanyCarLogRepository`를 공용 클라우드 API 구현체로 교체하면 UI와 계산 로직은 그대로 사용할 수 있습니다. API 형태는 [docs/API_CONTRACT.md](docs/API_CONTRACT.md)에 정리되어 있습니다.

## Android Studio에서 실행

1. Android Studio에서 이 폴더를 엽니다.
2. Gradle Sync를 실행합니다.
3. Android SDK 35와 JDK 17을 사용합니다.
4. 에뮬레이터 또는 Android 기기에서 `app` 구성을 실행합니다.

## 테스트

Android 빌드 환경이 있는 곳에서 Android Studio의 Gradle 패널로 `testDebugUnitTest`를 실행하거나, Gradle이 설치되어 있다면 다음을 실행합니다.

```powershell
gradle testDebugUnitTest
```

테스트는 차량별 거리 분리, 첫 기록의 초기 누적거리, 도착거리 검증, 월별 누계 리셋, 기본 고정값 저장, Word 문서 생성 구조를 확인합니다.
