package kr.co.hwacheon.carmileage.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class OdometerCandidateExtractorTest {
    @Test
    fun extractsPlainOdometerNumber() {
        assertEquals(65123, OdometerCandidateExtractor.extract("ODO 65123 km"))
    }

    @Test
    fun extractsNumberWithSeparators() {
        assertEquals(123456, OdometerCandidateExtractor.extract("TOTAL\n123,456 km"))
    }

    @Test
    fun prefersOdometerHintOverTripNoise() {
        val text = """
            TRIP 1234
            ODO 56789 km
        """.trimIndent()

        assertEquals(56789, OdometerCandidateExtractor.extract(text))
    }

    @Test
    fun ignoresYearLikeNumber() {
        assertNull(OdometerCandidateExtractor.extract("2026"))
    }
}
