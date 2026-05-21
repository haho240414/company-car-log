package kr.co.hwacheon.carmileage

import android.app.Application
import android.net.Uri
import androidx.core.content.FileProvider
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import java.io.File
import java.time.LocalDate
import java.time.YearMonth
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kr.co.hwacheon.carmileage.data.CompanyCarLogState
import kr.co.hwacheon.carmileage.data.ImageMetadataReader
import kr.co.hwacheon.carmileage.data.LocalCompanyCarLogRepository
import kr.co.hwacheon.carmileage.data.MileageOcrService
import kr.co.hwacheon.carmileage.data.MlKitMileageOcrService
import kr.co.hwacheon.carmileage.domain.Department
import kr.co.hwacheon.carmileage.domain.ExportFormat
import kr.co.hwacheon.carmileage.domain.ExportRequest
import kr.co.hwacheon.carmileage.domain.MileageCalculator
import kr.co.hwacheon.carmileage.domain.Position
import kr.co.hwacheon.carmileage.domain.Vehicle

enum class AppTab(val label: String) {
    REGISTER("등록"),
    LOGS("기록"),
    EXPORT("출력"),
    ADMIN("관리")
}

data class LoginFormState(
    val inviteCode: String = BuildConfig.DEFAULT_INVITE_CODE,
    val name: String = "",
    val pin: String = "",
    val department: Department = Department.FINANCE_BRANCH,
    val customDepartment: String = "",
    val position: Position = Position.BRANCH_MANAGER,
    val customPosition: String = ""
)

data class TripEditorState(
    val usageDate: String = LocalDate.now().toString(),
    val stopover: String = "",
    val startOdometer: String = "0",
    val endOdometer: String = "",
    val photoUri: String? = null,
    val photoStatus: String = "도착 후 계기판 사진을 촬영하거나 선택해 주세요.",
    val ocrConfirmed: Boolean = false,
    val isOcrRunning: Boolean = false,
    val needsManualStart: Boolean = true
)

data class ExportFormState(
    val month: String = YearMonth.now().toString(),
    val format: ExportFormat = ExportFormat.XLSX,
    val resultMessage: String? = null,
    val shareUri: String? = null,
    val shareMimeType: String? = null,
    val shareTitle: String? = null
)

data class ScreenState(
    val selectedVehicleId: String = "carnival",
    val activeTab: AppTab = AppTab.REGISTER,
    val login: LoginFormState = LoginFormState(),
    val editor: TripEditorState = TripEditorState(),
    val export: ExportFormState = ExportFormState(),
    val busy: Boolean = false,
    val message: String? = null
)

data class AppUiState(
    val data: CompanyCarLogState,
    val screen: ScreenState
) {
    val selectedVehicle: Vehicle?
        get() = data.vehicles.firstOrNull { it.id == screen.selectedVehicleId }

    val selectedVehicleTrips
        get() = data.trips.filter { it.vehicleId == screen.selectedVehicleId }
}

class CarLogViewModel(application: Application) : AndroidViewModel(application) {
    private val repository = LocalCompanyCarLogRepository(application)
    private val ocrService: MileageOcrService = MlKitMileageOcrService(application)
    private val metadataReader = ImageMetadataReader(application)
    private val _screen = MutableStateFlow(ScreenState())

    val uiState: StateFlow<AppUiState> = combine(repository.state, _screen) { data, screen ->
        AppUiState(data = data, screen = screen)
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000),
        initialValue = AppUiState(repository.state.value, _screen.value)
    )

    init {
        refreshStartOdometer(vehicleId = _screen.value.selectedVehicleId)
    }

    fun updateLogin(transform: (LoginFormState) -> LoginFormState) {
        _screen.update { it.copy(login = transform(it.login)) }
    }

    fun signIn() {
        val login = _screen.value.login
        viewModelScope.launch {
            setBusy(true)
            repository.signIn(
                inviteCode = login.inviteCode,
                name = login.name,
                pin = login.pin,
                department = login.department,
                customDepartment = login.customDepartment,
                position = login.position,
                customPosition = login.customPosition
            ).fold(
                onSuccess = {
                    _screen.update {
                        it.copy(
                            busy = false,
                            activeTab = AppTab.REGISTER,
                            message = "${it.login.name}님, 공유 차계부에 연결되었습니다."
                        )
                    }
                    refreshStartOdometer(_screen.value.selectedVehicleId)
                },
                onFailure = { error ->
                    setMessage(error.message ?: "로그인에 실패했습니다.")
                    setBusy(false)
                }
            )
        }
    }

    fun selectVehicle(vehicleId: String) {
        _screen.update { it.copy(selectedVehicleId = vehicleId) }
        refreshStartOdometer(vehicleId)
    }

    fun selectTab(tab: AppTab) {
        _screen.update { it.copy(activeTab = tab) }
    }

    fun updateEditor(transform: (TripEditorState) -> TripEditorState) {
        _screen.update { it.copy(editor = transform(it.editor)) }
    }

    fun onPhotoSelected(uri: Uri) {
        val takenDate = metadataReader.resolveTakenDate(uri) ?: LocalDate.now()
        val uriText = uri.toString()
        _screen.update {
            it.copy(
                editor = it.editor.copy(
                    usageDate = takenDate.toString(),
                    photoUri = uriText,
                    photoStatus = "사진을 등록했습니다. 계기판 누적거리 OCR을 확인하는 중입니다.",
                    isOcrRunning = true,
                    ocrConfirmed = false
                )
            )
        }

        viewModelScope.launch {
            val result = ocrService.readOdometer(uriText)
            _screen.update { current ->
                current.copy(
                    editor = current.editor.copy(
                        endOdometer = result.odometerKm?.toString() ?: current.editor.endOdometer,
                        photoStatus = result.message,
                        isOcrRunning = false,
                        ocrConfirmed = result.odometerKm != null
                    )
                )
            }
        }
    }

    fun saveTrip() {
        val state = uiState.value
        val vehicle = state.selectedVehicle ?: run {
            setMessage("차량을 선택해 주세요.")
            return
        }
        val editor = state.screen.editor
        val usageDate = runCatching { LocalDate.parse(editor.usageDate.trim()) }.getOrNull()
        if (usageDate == null) {
            setMessage("사용일자는 yyyy-MM-dd 형식으로 입력해 주세요.")
            return
        }
        val start = editor.startOdometer.toIntOrNull()
        val end = editor.endOdometer.toIntOrNull()
        if (start == null || end == null) {
            setMessage("출발/도착 누적거리를 숫자로 입력해 주세요.")
            return
        }

        viewModelScope.launch {
            setBusy(true)
            repository.saveTrip(
                vehicle = vehicle,
                usageDate = usageDate,
                stopover = editor.stopover,
                startOdometerKm = start,
                endOdometerKm = end,
                ocrConfirmed = editor.ocrConfirmed,
                photoUri = editor.photoUri
            ).fold(
                onSuccess = { trip ->
                    _screen.update {
                        it.copy(
                            busy = false,
                            editor = TripEditorState(
                                usageDate = LocalDate.now().toString(),
                                startOdometer = trip.endOdometerKm.toString(),
                                needsManualStart = false
                            ),
                            message = "${trip.vehicleName} ${trip.drivingDistanceKm}km 운행을 저장했습니다."
                        )
                    }
                },
                onFailure = { error ->
                    setBusy(false)
                    setMessage(error.message ?: "운행 기록을 저장하지 못했습니다.")
                }
            )
        }
    }

    fun updateExport(transform: (ExportFormState) -> ExportFormState) {
        _screen.update { it.copy(export = transform(it.export)) }
    }

    fun requestExport() {
        val state = uiState.value
        val vehicle = state.selectedVehicle ?: run {
            setMessage("차량을 선택해 주세요.")
            return
        }
        val month = runCatching { YearMonth.parse(state.screen.export.month.trim()) }.getOrNull()
        if (month == null) {
            setMessage("출력 월은 yyyy-MM 형식으로 입력해 주세요.")
            return
        }

        viewModelScope.launch {
            setBusy(true)
            if (state.screen.export.format == ExportFormat.CSV) {
                createAndShareCsv(vehicle = vehicle, month = month)
                return@launch
            }
            repository.requestExport(
                ExportRequest(
                    vehicleId = vehicle.id,
                    month = month,
                    format = state.screen.export.format
                )
            ).fold(
                onSuccess = { result ->
                    _screen.update {
                        it.copy(
                            busy = false,
                            export = it.export.copy(
                                resultMessage = "${result.fileName}\n${result.message}"
                            )
                        )
                    }
                },
                onFailure = { error ->
                    setBusy(false)
                    setMessage(error.message ?: "출력 요청에 실패했습니다.")
                }
            )
        }
    }

    fun clearShareRequest() {
        _screen.update {
            it.copy(
                export = it.export.copy(
                    shareUri = null,
                    shareMimeType = null,
                    shareTitle = null
                )
            )
        }
    }

    fun clearMessage() {
        _screen.update { it.copy(message = null) }
    }

    private fun refreshStartOdometer(vehicleId: String) {
        val data = repository.state.value
        val vehicle = data.vehicles.firstOrNull { it.id == vehicleId } ?: return
        val hasLogs = data.trips.any { it.vehicleId == vehicleId }
        val nextStart = MileageCalculator.nextStartOdometer(vehicle, data.trips)
        _screen.update {
            it.copy(
                editor = it.editor.copy(
                    startOdometer = nextStart.toString(),
                    needsManualStart = !hasLogs
                )
            )
        }
    }

    private fun setBusy(value: Boolean) {
        _screen.update { it.copy(busy = value) }
    }

    private fun setMessage(value: String) {
        _screen.update { it.copy(message = value) }
    }

    private fun createAndShareCsv(vehicle: Vehicle, month: YearMonth) {
        val trips = repository.state.value.trips
            .filter { it.vehicleId == vehicle.id && YearMonth.from(it.usageDate) == month }
            .sortedWith(compareBy { it.usageDate })
        val fileName = "${month}_${vehicle.name}_운행기록부.csv".replace(" ", "_")
        val directory = File(getApplication<Application>().cacheDir, "exports").also { it.mkdirs() }
        val file = File(directory, fileName)
        file.writeText(buildCsv(vehicle, month, trips), Charsets.UTF_8)
        val uri = FileProvider.getUriForFile(
            getApplication(),
            "${getApplication<Application>().packageName}.fileprovider",
            file
        )

        _screen.update {
            it.copy(
                busy = false,
                export = it.export.copy(
                    resultMessage = "CSV 파일을 만들었습니다. 공유 화면에서 Google Sheets를 선택하면 스프레드시트처럼 볼 수 있습니다.",
                    shareUri = uri.toString(),
                    shareMimeType = "text/csv",
                    shareTitle = fileName
                )
            )
        }
    }

    private fun buildCsv(
        vehicle: Vehicle,
        month: YearMonth,
        trips: List<kr.co.hwacheon.carmileage.domain.TripLog>
    ): String {
        val rows = mutableListOf<List<String>>()
        rows += listOf("${month.year}년 ${month.monthValue}월 업무용승용차 운행기록부")
        rows += listOf("차량", vehicle.name, "차량번호", vehicle.registrationNumber)
        rows += listOf(
            "사용일자",
            "부서",
            "직책",
            "성명",
            "사용목적",
            "출발지",
            "출발시 누적거리(km)",
            "경유지",
            "도착지",
            "도착시 누적거리(km)",
            "주행거리(km)",
            "업무용 주행거리 누계(km)"
        )
        trips.forEach { trip ->
            rows += listOf(
                trip.usageDate.toString(),
                trip.departmentName,
                trip.positionName,
                trip.writerName,
                trip.purpose,
                trip.departure,
                trip.startOdometerKm.toString(),
                trip.stopover,
                trip.destination,
                trip.endOdometerKm.toString(),
                trip.drivingDistanceKm.toString(),
                trip.monthlyBusinessDistanceKm.toString()
            )
        }
        return "\uFEFF" + rows.joinToString("\n") { row ->
            row.joinToString(",") { cell -> cell.toCsvCell() }
        }
    }

    private fun String.toCsvCell(): String {
        val escaped = replace("\"", "\"\"")
        return if (escaped.any { it == ',' || it == '"' || it == '\n' }) {
            "\"$escaped\""
        } else {
            escaped
        }
    }
}
