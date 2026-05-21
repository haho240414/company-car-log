package kr.co.hwacheon.carmileage.data

import java.security.MessageDigest
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

    suspend fun requestExport(request: ExportRequest): Result<ExportResult>
}

class DemoCompanyCloudRepository : CompanyCarLogRepository {
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

    private val _state = MutableStateFlow(CompanyCarLogState(vehicles = seedVehicles))
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
                current.copy(
                    trips = (current.trips + trip)
                        .sortedWith(
                            compareByDescending<TripLog> { it.usageDate }
                                .thenByDescending { it.createdAtMillis }
                        )
                )
            }
        }
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
}
