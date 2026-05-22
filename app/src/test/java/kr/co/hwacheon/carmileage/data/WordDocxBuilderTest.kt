package kr.co.hwacheon.carmileage.data

import java.io.ByteArrayInputStream
import java.time.YearMonth
import java.util.zip.ZipInputStream
import kotlin.text.Charsets
import kr.co.hwacheon.carmileage.domain.MileageCalculator
import kr.co.hwacheon.carmileage.domain.UserProfile
import kr.co.hwacheon.carmileage.domain.Vehicle
import org.junit.Assert.assertTrue
import org.junit.Test

class WordDocxBuilderTest {
    @Test
    fun buildsDocxWithDocumentXml() {
        val vehicle = Vehicle("carnival", "카니발", "30마 3144")
        val user = UserProfile(
            name = "홍길동",
            department = "금융지점",
            position = "지점장",
            pinHash = "hash"
        )
        val trip = MileageCalculator.buildTrip(
            vehicle = vehicle,
            user = user,
            usageDate = java.time.LocalDate.parse("2026-05-21"),
            stopover = "금융지점",
            startOdometerKm = 1000,
            endOdometerKm = 1042,
            ocrConfirmed = true,
            photoUri = null,
            existingLogs = emptyList()
        )

        val bytes = WordDocxBuilder.build(vehicle, YearMonth.parse("2026-05"), listOf(trip))
        val entries = unzipEntries(bytes)

        assertTrue(entries.containsKey("[Content_Types].xml"))
        assertTrue(entries.containsKey("_rels/.rels"))
        assertTrue(entries.containsKey("word/document.xml"))
        val documentXml = entries.getValue("word/document.xml")
        assertTrue(documentXml.contains("업무용승용차 운행기록부"))
        assertTrue(documentXml.contains("사 무 소 명"))
        assertTrue(documentXml.contains("①사용일자"))
        assertTrue(documentXml.contains("⑤출발시"))
        assertTrue(documentXml.contains("⑫총주행"))
        assertTrue(documentXml.contains("30마 3144"))
    }

    private fun unzipEntries(bytes: ByteArray): Map<String, String> {
        val entries = mutableMapOf<String, String>()
        ZipInputStream(ByteArrayInputStream(bytes)).use { zip ->
            var entry = zip.nextEntry
            while (entry != null) {
                entries[entry.name] = zip.readBytes().toString(Charsets.UTF_8)
                entry = zip.nextEntry
            }
        }
        return entries
    }
}
