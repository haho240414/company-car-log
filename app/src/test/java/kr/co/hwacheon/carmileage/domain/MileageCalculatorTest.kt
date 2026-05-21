package kr.co.hwacheon.carmileage.domain

import java.time.LocalDate
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test

class MileageCalculatorTest {
    private val carnival = Vehicle("carnival", "카니발", "30마 3144", initialOdometerKm = 1000)
    private val starex = Vehicle("starex", "스타렉스", "미등록", initialOdometerKm = 500)
    private val user = UserProfile(
        name = "홍길동",
        department = "금융지점",
        position = "지점장",
        pinHash = "hash"
    )

    @Test
    fun nextStartOdometerUsesSameVehicleOnly() {
        val logs = listOf(
            trip(carnival, "2026-05-01", 1000, 1040),
            trip(starex, "2026-05-02", 500, 560)
        )

        assertEquals(1040, MileageCalculator.nextStartOdometer(carnival, logs))
        assertEquals(560, MileageCalculator.nextStartOdometer(starex, logs))
    }

    @Test
    fun firstTripFallsBackToVehicleInitialOdometer() {
        assertEquals(1000, MileageCalculator.nextStartOdometer(carnival, emptyList()))
    }

    @Test
    fun rejectsEndOdometerBeforeStartOdometer() {
        assertThrows(IllegalArgumentException::class.java) {
            MileageCalculator.drivingDistance(startOdometerKm = 1200, endOdometerKm = 1199)
        }
    }

    @Test
    fun monthlyBusinessTotalResetsByMonth() {
        val logs = listOf(
            trip(carnival, "2026-05-01", 1000, 1040),
            trip(carnival, "2026-05-21", 1040, 1100),
            trip(carnival, "2026-06-01", 1100, 1125)
        )

        assertEquals(
            110,
            MileageCalculator.monthlyBusinessTotal(
                vehicleId = carnival.id,
                usageDate = LocalDate.parse("2026-05-31"),
                logs = logs,
                addedDistanceKm = 10
            )
        )
        assertEquals(
            30,
            MileageCalculator.monthlyBusinessTotal(
                vehicleId = carnival.id,
                usageDate = LocalDate.parse("2026-06-02"),
                logs = logs,
                addedDistanceKm = 5
            )
        )
    }

    @Test
    fun buildTripStoresComputedDistanceAndMonthlyTotal() {
        val existing = listOf(trip(carnival, "2026-05-01", 1000, 1040))
        val built = MileageCalculator.buildTrip(
            vehicle = carnival,
            user = user,
            usageDate = LocalDate.parse("2026-05-21"),
            stopover = "금융지점",
            startOdometerKm = 1040,
            endOdometerKm = 1088,
            ocrConfirmed = true,
            photoUri = "content://photo",
            existingLogs = existing
        )

        assertEquals(48, built.drivingDistanceKm)
        assertEquals(88, built.monthlyBusinessDistanceKm)
        assertEquals("업무용", built.purpose)
        assertEquals("본점", built.departure)
        assertEquals("본점", built.destination)
    }

    private fun trip(vehicle: Vehicle, date: String, start: Int, end: Int): TripLog {
        return MileageCalculator.buildTrip(
            vehicle = vehicle,
            user = user,
            usageDate = LocalDate.parse(date),
            stopover = "경유지",
            startOdometerKm = start,
            endOdometerKm = end,
            ocrConfirmed = true,
            photoUri = null,
            existingLogs = emptyList()
        )
    }
}
