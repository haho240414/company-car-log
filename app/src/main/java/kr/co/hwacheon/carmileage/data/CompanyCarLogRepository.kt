package kr.co.hwacheon.carmileage.data

import android.content.Context
import java.security.MessageDigest
import java.time.LocalDate
import java.time.YearMonth
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update
import kr.co.hwacheon.carmileage.BuildConfig
import kr.co.hwacheon.carmileage.domain.Department
import kr.co.hwacheon.carmileage.domain.ExportFormat
import kr.co.hwacheon.carmileage.domain.ExportRequest
import kr.co.hwacheon.carmileage.domain.ExportResult
import kr.co.hwacheon.carmileage.domain.MileageCalculator
import kr.co.hwacheon.carmileage.domain.Position
import kr.co.hwacheon.carmileage.domain.TripLog
import kr.co.hwacheon.carmileage.domain.UserProfile
import kr.co.hwacheon.carmileage.domain.Vehicle
import org.json.JSONArray
import org.json.JSONObject

data class CompanyCarLogState(
    val currentUser: UserProfile? = null,
    val vehicles: List<Vehicle> = emptyList(),
    val trips: List<TripLog> = emptyList()
)

interface CompanyCarLogRepository {
    val state: StateFlow<CompanyCarLogState>

    suspend fun signIn(
        inviteCode: String,
        name: String,
        pin: String,
        department: Department,
        customDepartment: String,
        position: Position,
        customPosition: String
    ): Result<UserProfile>

    suspend fun saveTrip(
        vehicle: Vehicle,
        usageDate: java.time.LocalDate,
        stopover: String,
        startOdometerKm: Int,
        endOdometerKm: Int,
        ocrConfirmed: Boolean,
        photoUri: String?
    ): Result<TripLog>

    suspend fun deleteTrip(tripId: String): Result<Unit>

    suspend fun updateVehicleRegistration(
        vehicleId: String,
        registrationNumber: String
    ): Result<Vehicle>

    suspend fun requestExport(request: ExportRequest): Result<ExportResult>
}

class LocalCompanyCarLogRepository(context: Context) : CompanyCarLogRepository {
    private val prefs = context.applicationContext.getSharedPreferences(
        "company_car_log",
        Context.MODE_PRIVATE
    )

    private val seedVehicles = listOf(
        Vehicle(
            id = "carnival",
            name = "카니발",
            registrationNumber = "30마 3144",
            initialOdometerKm = 0
        ),
        Vehicle(
            id = "starex",
            name = "스타렉스",
            registrationNumber = "차량번호 입력 필요",
            initialOdometerKm = 0
        ),
        Vehicle(
            id = "nexo",
            name = "넥쏘",
            registrationNumber = "차량번호 입력 필요",
            initialOdometerKm = 0
        )
    )

    private val _state = MutableStateFlow(
        CompanyCarLogState(
            currentUser = loadUser(),
            vehicles = loadVehicles(),
            trips = loadTrips()
        )
    )
    override val state: StateFlow<CompanyCarLogState> = _state

    override suspend fun signIn(
        inviteCode: String,
        name: String,
        pin: String,
        department: Department,
        customDepartment: String,
        position: Position,
        customPosition: String
    ): Result<UserProfile> {
        delay(250)
        if (inviteCode.trim() != BuildConfig.DEFAULT_INVITE_CODE) {
            return Result.failure(IllegalArgumentException("초대코드가 올바르지 않습니다."))
        }
        if (name.isBlank()) {
            return Result.failure(IllegalArgumentException("성명을 입력해 주세요."))
        }
        if (pin.length < 4) {
            return Result.failure(IllegalArgumentException("PIN은 4자리 이상으로 입력해 주세요."))
        }

        val user = UserProfile(
            name = name.trim(),
            department = department.resolve(customDepartment),
            position = position.resolve(customPosition),
            pinHash = sha256(pin),
            isAdmin = pin == "0000"
        )
        _state.update { it.copy(currentUser = user) }
        persistUser(user)
        return Result.success(user)
    }

    override suspend fun saveTrip(
        vehicle: Vehicle,
        usageDate: java.time.LocalDate,
        stopover: String,
        startOdometerKm: Int,
        endOdometerKm: Int,
        ocrConfirmed: Boolean,
        photoUri: String?
    ): Result<TripLog> {
        delay(250)
        val user = state.value.currentUser
            ?: return Result.failure(IllegalStateException("로그인이 필요합니다."))
        if (stopover.isBlank()) {
            return Result.failure(IllegalArgumentException("경유지를 입력해 주세요."))
        }

        return runCatching {
            MileageCalculator.buildTrip(
                vehicle = vehicle,
                user = user,
                usageDate = usageDate,
                stopover = stopover,
                startOdometerKm = startOdometerKm,
                endOdometerKm = endOdometerKm,
                ocrConfirmed = ocrConfirmed,
                photoUri = photoUri,
                existingLogs = state.value.trips
            )
        }.onSuccess { trip ->
            _state.update { current ->
                val updatedTrips = (current.trips + trip).sortedForDisplay()
                persistTrips(updatedTrips)
                current.copy(trips = updatedTrips)
            }
        }
    }

    override suspend fun deleteTrip(tripId: String): Result<Unit> {
        delay(150)
        val existing = state.value.trips
        if (existing.none { it.id == tripId }) {
            return Result.failure(IllegalArgumentException("삭제할 운행 기록을 찾을 수 없습니다."))
        }
        val updatedTrips = existing.filterNot { it.id == tripId }
            .recalculateMonthlyTotals()
            .sortedForDisplay()
        persistTrips(updatedTrips)
        _state.update { it.copy(trips = updatedTrips) }
        return Result.success(Unit)
    }

    override suspend fun updateVehicleRegistration(
        vehicleId: String,
        registrationNumber: String
    ): Result<Vehicle> {
        delay(150)
        val cleaned = registrationNumber.trim()
        if (cleaned.isBlank()) {
            return Result.failure(IllegalArgumentException("차량번호를 입력해 주세요."))
        }
        val currentVehicles = state.value.vehicles
        val vehicle = currentVehicles.firstOrNull { it.id == vehicleId }
            ?: return Result.failure(IllegalArgumentException("차량을 찾을 수 없습니다."))
        val updatedVehicle = vehicle.copy(registrationNumber = cleaned)
        val updatedVehicles = currentVehicles.map {
            if (it.id == vehicleId) updatedVehicle else it
        }
        persistVehicles(updatedVehicles)
        _state.update { it.copy(vehicles = updatedVehicles) }
        return Result.success(updatedVehicle)
    }

    override suspend fun requestExport(request: ExportRequest): Result<ExportResult> {
        delay(300)
        val vehicle = state.value.vehicles.firstOrNull { it.id == request.vehicleId }
            ?: return Result.failure(IllegalArgumentException("차량을 찾을 수 없습니다."))
        val month = request.month.toKoreanFileNamePart()
        val fileName = "${month}_${vehicle.name}_업무용승용차_운행기록부.${request.format.extension}"
        return Result.success(
            ExportResult(
                fileName = fileName,
                downloadUrl = null,
                message = "서버 연결 후 이 요청은 실제 ${request.format.label} 파일 다운로드로 바뀝니다."
            )
        )
    }

    private fun sha256(value: String): String {
        val bytes = MessageDigest.getInstance("SHA-256").digest(value.toByteArray())
        return bytes.joinToString("") { "%02x".format(it) }
    }

    private fun YearMonth.toKoreanFileNamePart(): String =
        "${year}년_${monthValue.toString().padStart(2, '0')}월"

    private fun persistUser(user: UserProfile) {
        prefs.edit()
            .putString(KEY_USER, user.toJson().toString())
            .apply()
    }

    private fun persistVehicles(vehicles: List<Vehicle>) {
        val payload = JSONArray().apply {
            vehicles.forEach { put(it.toJson()) }
        }
        prefs.edit()
            .putString(KEY_VEHICLES, payload.toString())
            .apply()
    }

    private fun persistTrips(trips: List<TripLog>) {
        val payload = JSONArray().apply {
            trips.forEach { put(it.toJson()) }
        }
        prefs.edit()
            .putString(KEY_TRIPS, payload.toString())
            .apply()
    }

    private fun loadVehicles(): List<Vehicle> {
        val raw = prefs.getString(KEY_VEHICLES, null) ?: return seedVehicles
        return runCatching {
            val array = JSONArray(raw)
            val persisted = buildList {
                for (index in 0 until array.length()) {
                    add(array.getJSONObject(index).toVehicle())
                }
            }.associateBy { it.id }
            seedVehicles.map { persisted[it.id] ?: it }
        }.getOrDefault(seedVehicles)
    }

    private fun loadUser(): UserProfile? {
        val raw = prefs.getString(KEY_USER, null) ?: return null
        return runCatching { JSONObject(raw).toUserProfile() }.getOrNull()
    }

    private fun loadTrips(): List<TripLog> {
        val raw = prefs.getString(KEY_TRIPS, null) ?: return emptyList()
        return runCatching {
            val array = JSONArray(raw)
            buildList {
                for (index in 0 until array.length()) {
                    add(array.getJSONObject(index).toTripLog())
                }
            }.sortedForDisplay()
        }.getOrDefault(emptyList())
    }

    private fun List<TripLog>.sortedForDisplay(): List<TripLog> =
        sortedWith(
            compareByDescending<TripLog> { it.usageDate }
                .thenByDescending { it.createdAtMillis }
        )

    private fun List<TripLog>.recalculateMonthlyTotals(): List<TripLog> {
        return groupBy { it.vehicleId to YearMonth.from(it.usageDate) }
            .values
            .flatMap { group ->
                var total = 0
                group.sortedWith(compareBy<TripLog> { it.usageDate }.thenBy { it.createdAtMillis })
                    .map { trip ->
                        total += trip.drivingDistanceKm
                        trip.copy(monthlyBusinessDistanceKm = total)
                    }
            }
    }

    private fun Vehicle.toJson(): JSONObject = JSONObject().apply {
        put("id", id)
        put("name", name)
        put("registrationNumber", registrationNumber)
        put("isActive", isActive)
        put("initialOdometerKm", initialOdometerKm)
    }

    private fun JSONObject.toVehicle(): Vehicle = Vehicle(
        id = getString("id"),
        name = getString("name"),
        registrationNumber = getString("registrationNumber"),
        isActive = optBoolean("isActive", true),
        initialOdometerKm = optInt("initialOdometerKm", 0)
    )

    private fun UserProfile.toJson(): JSONObject = JSONObject().apply {
        put("id", id)
        put("name", name)
        put("department", department)
        put("position", position)
        put("pinHash", pinHash)
        put("isAdmin", isAdmin)
    }

    private fun JSONObject.toUserProfile(): UserProfile = UserProfile(
        id = getString("id"),
        name = getString("name"),
        department = getString("department"),
        position = getString("position"),
        pinHash = getString("pinHash"),
        isAdmin = optBoolean("isAdmin", false)
    )

    private fun TripLog.toJson(): JSONObject = JSONObject().apply {
        put("id", id)
        put("vehicleId", vehicleId)
        put("vehicleName", vehicleName)
        put("vehicleRegistrationNumber", vehicleRegistrationNumber)
        put("usageDate", usageDate.toString())
        put("createdAtMillis", createdAtMillis)
        put("writerName", writerName)
        put("departmentName", departmentName)
        put("positionName", positionName)
        put("purpose", purpose)
        put("departure", departure)
        put("stopover", stopover)
        put("destination", destination)
        put("startOdometerKm", startOdometerKm)
        put("endOdometerKm", endOdometerKm)
        put("drivingDistanceKm", drivingDistanceKm)
        put("monthlyBusinessDistanceKm", monthlyBusinessDistanceKm)
        put("ocrConfirmed", ocrConfirmed)
        put("photoUri", photoUri)
    }

    private fun JSONObject.toTripLog(): TripLog = TripLog(
        id = getString("id"),
        vehicleId = getString("vehicleId"),
        vehicleName = getString("vehicleName"),
        vehicleRegistrationNumber = getString("vehicleRegistrationNumber"),
        usageDate = LocalDate.parse(getString("usageDate")),
        createdAtMillis = optLong("createdAtMillis", System.currentTimeMillis()),
        writerName = getString("writerName"),
        departmentName = getString("departmentName"),
        positionName = getString("positionName"),
        purpose = optString("purpose", "업무용"),
        departure = optString("departure", "본점"),
        stopover = getString("stopover"),
        destination = optString("destination", "본점"),
        startOdometerKm = getInt("startOdometerKm"),
        endOdometerKm = getInt("endOdometerKm"),
        drivingDistanceKm = getInt("drivingDistanceKm"),
        monthlyBusinessDistanceKm = getInt("monthlyBusinessDistanceKm"),
        ocrConfirmed = optBoolean("ocrConfirmed", false),
        photoUri = optString("photoUri").takeIf { it.isNotBlank() && it != "null" }
    )

    private companion object {
        const val KEY_USER = "user"
        const val KEY_VEHICLES = "vehicles"
        const val KEY_TRIPS = "trips"
    }
}
