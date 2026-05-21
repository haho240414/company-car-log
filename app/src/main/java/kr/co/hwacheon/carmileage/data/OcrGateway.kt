package kr.co.hwacheon.carmileage.data

import kotlinx.coroutines.delay

data class OcrResult(
    val odometerKm: Int?,
    val confidence: Float,
    val message: String
)

interface MileageOcrService {
    suspend fun readOdometer(photoUri: String): OcrResult
}

class DemoMileageOcrService : MileageOcrService {
    override suspend fun readOdometer(photoUri: String): OcrResult {
        delay(650)
        val fileName = photoUri.substringAfterLast('/').substringBefore('?')
        val candidate = Regex("""(?<!\d)(\d{4,7})(?!\d)""")
            .findAll(fileName)
            .mapNotNull { it.value.toIntOrNull() }
            .firstOrNull()

        return if (candidate != null) {
            OcrResult(
                odometerKm = candidate,
                confidence = 0.72f,
                message = "데모 OCR이 파일명에서 $candidate km를 찾았습니다. 실제 배포에서는 서버 AI OCR 결과로 교체됩니다."
            )
        } else {
            OcrResult(
                odometerKm = null,
                confidence = 0f,
                message = "사진은 등록되었습니다. 서버 AI OCR 연결 전까지 도착 누적거리를 직접 확인해 주세요."
            )
        }
    }
}
