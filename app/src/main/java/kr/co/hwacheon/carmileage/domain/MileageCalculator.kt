package kr.co.hwacheon.carmileage.domain

import java.time.LocalDate
import java.time.YearMonth

object MileageCalculator {
    fun nextStartOdometer(vehicle: Vehicle, logs: List<TripLog>): Int {
        return logs
            .filter { it.vehicleId == vehicle.id }
            .maxWithOrNull(compareBy<TripLog> { it.usageDate }.thenBy { it.createdAtMillis })
            ?.endOdometerKm
            ?: vehicle.initialOdometerKm
    }

    fun drivingDistance(startOdometerKm: Int, endOdometerKm: Int): Int {
        require(startOdometerKm >= 0) { "출발 누적거리는 0 이상이어야 합니다." }
        require(endOdometerKm >= 0) { "도착 누적거리는 0 이상이어야 합니다." }
        require(endOdometerKm >= startOdometerKm) {
            "도착시 누적거리는 출발시 누적거리보다 작을 수 없습니다."
        }
        return endOdometerKm - startOdometerKm
    }

    fun monthlyBusinessTotal(
        vehicleId: String,
        usageDate: LocalDate,
        logs: List<TripLog>,
        addedDistanceKm: Int = 0,
        excludingTripId: String? = null
    ): Int {
        val month = YearMonth.from(usageDate)
        return logs
            .asSequence()
            .filter { it.vehicleId == vehicleId }
            .filter { excludingTripId == null || it.id != excludingTripId }
            .filter { YearMonth.from(it.usageDate) == month }
            .sumOf { it.drivingDistanceKm } + addedDistanceKm
    }

    fun buildTrip(
        vehicle: Vehicle,
        user: UserProfile,
        usageDate: LocalDate,
        stopover: String,
        startOdometerKm: Int,
        endOdometerKm: Int,
        ocrConfirmed: Boolean,
        photoUri: String?,
        existingLogs: List<TripLog>
    ): TripLog {
        val distance = drivingDistance(startOdometerKm, endOdometerKm)
        val monthlyTotal = monthlyBusinessTotal(
            vehicleId = vehicle.id,
            usageDate = usageDate,
            logs = existingLogs,
            addedDistanceKm = distance
        )
        return TripLog(
            vehicleId = vehicle.id,
            vehicleName = vehicle.name,
            vehicleRegistrationNumber = vehicle.registrationNumber,
            usageDate = usageDate,
            writerName = user.name,
            departmentName = user.department,
            positionName = user.position,
            stopover = stopover.trim(),
            startOdometerKm = startOdometerKm,
            endOdometerKm = endOdometerKm,
            drivingDistanceKm = distance,
            monthlyBusinessDistanceKm = monthlyTotal,
            ocrConfirmed = ocrConfirmed,
            photoUri = photoUri
        )
    }
}
