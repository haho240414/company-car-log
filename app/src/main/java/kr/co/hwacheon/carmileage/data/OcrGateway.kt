package kr.co.hwacheon.carmileage.data

import android.content.Context
import android.net.Uri
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.latin.TextRecognizerOptions
import kotlin.coroutines.resume
import kotlinx.coroutines.suspendCancellableCoroutine

data class OcrResult(
    val odometerKm: Int?,
    val confidence: Float,
    val message: String
)

interface MileageOcrService {
    suspend fun readOdometer(photoUri: String): OcrResult
}

class MlKitMileageOcrService(private val context: Context) : MileageOcrService {
    private val recognizer = TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS)

    override suspend fun readOdometer(photoUri: String): OcrResult {
        val image = runCatching {
            InputImage.fromFilePath(context, Uri.parse(photoUri))
        }.getOrElse { error ->
            return OcrResult(
                odometerKm = fallbackFileNameCandidate(photoUri),
                confidence = 0.2f,
                message = "사진을 읽지 못했습니다. 도착 누적거리를 직접 확인해 주세요. (${error.localizedMessage ?: "이미지 오류"})"
            )
        }

        val recognizedText = suspendCancellableCoroutine { continuation ->
            recognizer.process(image)
                .addOnSuccessListener { text ->
                    if (continuation.isActive) continuation.resume(text.text)
                }
                .addOnFailureListener {
                    if (continuation.isActive) continuation.resume("")
                }
        }

        val candidate = OdometerCandidateExtractor.extract(recognizedText)
            ?: fallbackFileNameCandidate(photoUri)

        return if (candidate != null) {
            OcrResult(
                odometerKm = candidate,
                confidence = if (recognizedText.isBlank()) 0.25f else 0.82f,
                message = "사진에서 ${candidate}km 후보를 찾았습니다. 저장 전에 계기판 숫자와 한 번만 비교해 주세요."
            )
        } else {
            OcrResult(
                odometerKm = null,
                confidence = 0f,
                message = "사진에서 누적거리 숫자를 찾지 못했습니다. 숫자가 화면 중앙에 크게 보이게 다시 찍거나 직접 입력해 주세요."
            )
        }
    }

    private fun fallbackFileNameCandidate(photoUri: String): Int? {
        val fileName = photoUri.substringAfterLast('/').substringBefore('?')
        return OdometerCandidateExtractor.extract(fileName)
    }
}

object OdometerCandidateExtractor {
    private val odometerHints = listOf("odo", "odometer", "total", "km", "㎞", "누적", "거리")
    private val noiseHints = listOf("trip", "range", "avg", "연비", "주행가능", "시간", "temp")

    fun extract(rawText: String): Int? {
        if (rawText.isBlank()) return null
        val lines = rawText
            .lineSequence()
            .map { normalizeOcrDigits(it) }
            .filter { it.isNotBlank() }
            .toList()
        val searchAreas = lines + normalizeOcrDigits(rawText.replace('\n', ' '))

        return searchAreas
            .flatMap { line -> candidatesFromLine(line) }
            .filter { it.value in 1_000..999_999 }
            .maxWithOrNull(compareBy<OdometerCandidate> { it.score }.thenBy { it.value })
            ?.value
    }

    private fun candidatesFromLine(line: String): List<OdometerCandidate> {
        val lower = line.lowercase()
        val hintScore = when {
            odometerHints.any { lower.contains(it) } -> 35
            noiseHints.any { lower.contains(it) } -> -20
            else -> 0
        }

        val compactMatches = Regex("""(?<!\d)(\d[\d,. ]{2,10}\d)(?!\d)""")
            .findAll(line)
            .mapNotNull { match ->
                val digits = match.value.filter(Char::isDigit)
                buildCandidate(digits, hintScore)
            }
            .toList()

        val plainMatches = Regex("""(?<!\d)(\d{4,7})(?!\d)""")
            .findAll(line)
            .mapNotNull { match -> buildCandidate(match.value, hintScore + 5) }
            .toList()

        return compactMatches + plainMatches
    }

    private fun buildCandidate(digits: String, hintScore: Int): OdometerCandidate? {
        if (digits.length !in 4..7) return null
        val value = digits.toIntOrNull() ?: return null
        if (value in 1900..2099) return null
        val lengthScore = when (digits.length) {
            5, 6 -> 30
            4 -> 18
            7 -> 12
            else -> 0
        }
        val magnitudeScore = when {
            value >= 10_000 -> 20
            value >= 3_000 -> 10
            else -> 0
        }
        return OdometerCandidate(value, hintScore + lengthScore + magnitudeScore)
    }

    private fun normalizeOcrDigits(value: String): String {
        return value
            .replace('O', '0')
            .replace('o', '0')
            .replace('I', '1')
            .replace('l', '1')
            .replace('|', '1')
            .replace('S', '5')
            .replace('s', '5')
            .replace('B', '8')
    }

    private data class OdometerCandidate(val value: Int, val score: Int)
}
