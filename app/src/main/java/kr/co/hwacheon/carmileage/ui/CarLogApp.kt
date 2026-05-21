@file:OptIn(androidx.compose.foundation.layout.ExperimentalLayoutApi::class)

package kr.co.hwacheon.carmileage.ui

import android.content.Intent
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AdminPanelSettings
import androidx.compose.material.icons.filled.CameraAlt
import androidx.compose.material.icons.filled.DirectionsCar
import androidx.compose.material.icons.filled.FileDownload
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.PhotoLibrary
import androidx.compose.material.icons.filled.Save
import androidx.compose.material.icons.filled.UploadFile
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import kr.co.hwacheon.carmileage.AppTab
import kr.co.hwacheon.carmileage.AppUiState
import kr.co.hwacheon.carmileage.CarLogViewModel
import kr.co.hwacheon.carmileage.LoginFormState
import kr.co.hwacheon.carmileage.TripEditorState
import kr.co.hwacheon.carmileage.domain.Department
import kr.co.hwacheon.carmileage.domain.ExportFormat
import kr.co.hwacheon.carmileage.domain.MileageCalculator
import kr.co.hwacheon.carmileage.domain.Position
import kr.co.hwacheon.carmileage.domain.TripLog
import kr.co.hwacheon.carmileage.domain.Vehicle

@Composable
fun CarLogApp(viewModel: CarLogViewModel) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }
    val context = LocalContext.current
    var cameraOpen by remember { mutableStateOf(false) }

    LaunchedEffect(uiState.screen.message) {
        val message = uiState.screen.message ?: return@LaunchedEffect
        snackbarHostState.showSnackbar(message)
        viewModel.clearMessage()
    }

    LaunchedEffect(uiState.screen.export.shareUri) {
        val shareUri = uiState.screen.export.shareUri ?: return@LaunchedEffect
        val shareIntent = Intent(Intent.ACTION_SEND).apply {
            type = uiState.screen.export.shareMimeType ?: "text/csv"
            putExtra(Intent.EXTRA_STREAM, Uri.parse(shareUri))
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        context.startActivity(
            Intent.createChooser(shareIntent, uiState.screen.export.shareTitle ?: "운행기록부 공유")
        )
        viewModel.clearShareRequest()
    }

    if (cameraOpen) {
        CameraCaptureScreen(
            onImageCaptured = { uri ->
                cameraOpen = false
                viewModel.onPhotoSelected(uri)
            },
            onClose = { cameraOpen = false }
        )
        return
    }

    if (uiState.data.currentUser == null) {
        LoginScreen(
            form = uiState.screen.login,
            busy = uiState.screen.busy,
            snackbarHostState = snackbarHostState,
            onChange = viewModel::updateLogin,
            onSubmit = viewModel::signIn
        )
    } else {
        MainScreen(
            uiState = uiState,
            snackbarHostState = snackbarHostState,
            onVehicleSelected = viewModel::selectVehicle,
            onTabSelected = viewModel::selectTab,
            onEditorChange = viewModel::updateEditor,
            onPhotoSelected = viewModel::onPhotoSelected,
            onOpenCamera = { cameraOpen = true },
            onSaveTrip = viewModel::saveTrip,
            onExportChange = viewModel::updateExport,
            onRequestExport = viewModel::requestExport
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun LoginScreen(
    form: LoginFormState,
    busy: Boolean,
    snackbarHostState: SnackbarHostState,
    onChange: ((LoginFormState) -> LoginFormState) -> Unit,
    onSubmit: () -> Unit
) {
    Scaffold(
        snackbarHost = { SnackbarHost(snackbarHostState) },
        topBar = {
            CenterAlignedTopAppBar(title = { Text("공유 차계부") })
        }
    ) { padding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            item {
                Text(
                    text = "초대코드와 사용자 정보를 입력하면 내 휴대폰에 카니발, 스타렉스, 넥쏘 차계부를 저장할 수 있습니다.",
                    style = MaterialTheme.typography.bodyLarge
                )
            }
            item {
                OutlinedTextField(
                    modifier = Modifier.fillMaxWidth(),
                    value = form.inviteCode,
                    onValueChange = { value -> onChange { it.copy(inviteCode = value) } },
                    label = { Text("초대코드") },
                    singleLine = true
                )
            }
            item {
                OutlinedTextField(
                    modifier = Modifier.fillMaxWidth(),
                    value = form.name,
                    onValueChange = { value -> onChange { it.copy(name = value) } },
                    label = { Text("성명") },
                    singleLine = true
                )
            }
            item {
                ChoiceField(
                    label = "부서",
                    value = form.department.label,
                    options = Department.entries.map { it.label },
                    onSelected = { label ->
                        val selected = Department.entries.first { it.label == label }
                        onChange { it.copy(department = selected) }
                    }
                )
            }
            if (form.department == Department.OTHER) {
                item {
                    OutlinedTextField(
                        modifier = Modifier.fillMaxWidth(),
                        value = form.customDepartment,
                        onValueChange = { value -> onChange { it.copy(customDepartment = value) } },
                        label = { Text("기타 부서") },
                        singleLine = true
                    )
                }
            }
            item {
                ChoiceField(
                    label = "직책",
                    value = form.position.label,
                    options = Position.entries.map { it.label },
                    onSelected = { label ->
                        val selected = Position.entries.first { it.label == label }
                        onChange { it.copy(position = selected) }
                    }
                )
            }
            if (form.position == Position.OTHER) {
                item {
                    OutlinedTextField(
                        modifier = Modifier.fillMaxWidth(),
                        value = form.customPosition,
                        onValueChange = { value -> onChange { it.copy(customPosition = value) } },
                        label = { Text("기타 직책") },
                        singleLine = true
                    )
                }
            }
            item {
                OutlinedTextField(
                    modifier = Modifier.fillMaxWidth(),
                    value = form.pin,
                    onValueChange = { value -> onChange { it.copy(pin = value.filter(Char::isDigit)) } },
                    label = { Text("PIN") },
                    singleLine = true,
                    visualTransformation = PasswordVisualTransformation(),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword)
                )
            }
            item {
                Button(
                    modifier = Modifier.fillMaxWidth(),
                    enabled = !busy,
                    onClick = onSubmit
                ) {
                    Text(if (busy) "연결 중..." else "차계부 시작")
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun MainScreen(
    uiState: AppUiState,
    snackbarHostState: SnackbarHostState,
    onVehicleSelected: (String) -> Unit,
    onTabSelected: (AppTab) -> Unit,
    onEditorChange: ((TripEditorState) -> TripEditorState) -> Unit,
    onPhotoSelected: (Uri) -> Unit,
    onOpenCamera: () -> Unit,
    onSaveTrip: () -> Unit,
    onExportChange: ((kr.co.hwacheon.carmileage.ExportFormState) -> kr.co.hwacheon.carmileage.ExportFormState) -> Unit,
    onRequestExport: () -> Unit
) {
    Scaffold(
        snackbarHost = { SnackbarHost(snackbarHostState) },
        topBar = {
            CenterAlignedTopAppBar(
                title = {
                    Text(
                        text = "업무용승용차 운행기록부",
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            )
        },
        bottomBar = {
            NavigationBar {
                AppTab.entries.forEach { tab ->
                    NavigationBarItem(
                        selected = uiState.screen.activeTab == tab,
                        onClick = { onTabSelected(tab) },
                        icon = {
                            Icon(
                                imageVector = when (tab) {
                                    AppTab.REGISTER -> Icons.Default.DirectionsCar
                                    AppTab.LOGS -> Icons.Default.History
                                    AppTab.EXPORT -> Icons.Default.FileDownload
                                    AppTab.ADMIN -> Icons.Default.AdminPanelSettings
                                },
                                contentDescription = tab.label
                            )
                        },
                        label = { Text(tab.label) }
                    )
                }
            }
        }
    ) { padding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            item {
                VehicleSelector(
                    vehicles = uiState.data.vehicles,
                    selectedVehicleId = uiState.screen.selectedVehicleId,
                    trips = uiState.data.trips,
                    onSelected = onVehicleSelected
                )
            }

            when (uiState.screen.activeTab) {
                AppTab.REGISTER -> item {
                    TripRegistrationPanel(
                        uiState = uiState,
                        onEditorChange = onEditorChange,
                        onPhotoSelected = onPhotoSelected,
                        onOpenCamera = onOpenCamera,
                        onSaveTrip = onSaveTrip
                    )
                }

                AppTab.LOGS -> {
                    item {
                        Text("운행 내역", style = MaterialTheme.typography.titleMedium)
                    }
                    items(uiState.selectedVehicleTrips) { trip ->
                        TripLogRow(trip = trip)
                    }
                    if (uiState.selectedVehicleTrips.isEmpty()) {
                        item {
                            EmptyText("아직 이 차량의 운행 기록이 없습니다.")
                        }
                    }
                }

                AppTab.EXPORT -> item {
                    ExportPanel(
                        uiState = uiState,
                        onChange = onExportChange,
                        onRequestExport = onRequestExport
                    )
                }

                AppTab.ADMIN -> item {
                    AdminPanel(uiState = uiState)
                }
            }
        }
    }
}

@Composable
private fun VehicleSelector(
    vehicles: List<Vehicle>,
    selectedVehicleId: String,
    trips: List<TripLog>,
    onSelected: (String) -> Unit
) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text("차량", style = MaterialTheme.typography.titleMedium)
        FlowRow(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            vehicles.forEach { vehicle ->
                val lastOdometer = MileageCalculator.nextStartOdometer(vehicle, trips)
                FilterChip(
                    selected = vehicle.id == selectedVehicleId,
                    onClick = { onSelected(vehicle.id) },
                    label = { Text("${vehicle.name} · ${lastOdometer}km") },
                    leadingIcon = { Icon(Icons.Default.DirectionsCar, contentDescription = null) }
                )
            }
        }
    }
}

@Composable
private fun TripRegistrationPanel(
    uiState: AppUiState,
    onEditorChange: ((TripEditorState) -> TripEditorState) -> Unit,
    onPhotoSelected: (Uri) -> Unit,
    onOpenCamera: () -> Unit,
    onSaveTrip: () -> Unit
) {
    val pickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickVisualMedia()
    ) { uri ->
        if (uri != null) onPhotoSelected(uri)
    }
    val vehicle = uiState.selectedVehicle
    val editor = uiState.screen.editor
    val estimatedDistance = editor.startOdometer.toIntOrNull()
        ?.let { start -> editor.endOdometer.toIntOrNull()?.let { end -> end - start } }

    Card(shape = RoundedCornerShape(8.dp), colors = CardDefaults.cardColors()) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Text(
                text = vehicle?.let { "${it.name} 운행 등록" } ?: "운행 등록",
                style = MaterialTheme.typography.titleMedium
            )
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(onClick = onOpenCamera, modifier = Modifier.weight(1f)) {
                    Icon(Icons.Default.CameraAlt, contentDescription = null)
                    Spacer(Modifier.width(6.dp))
                    Text("촬영")
                }
                OutlinedButton(
                    onClick = {
                        pickerLauncher.launch(
                            PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
                        )
                    },
                    modifier = Modifier.weight(1f)
                ) {
                    Icon(Icons.Default.PhotoLibrary, contentDescription = null)
                    Spacer(Modifier.width(6.dp))
                    Text("사진")
                }
            }
            StatusSurface(text = editor.photoStatus)
            OutlinedTextField(
                modifier = Modifier.fillMaxWidth(),
                value = editor.usageDate,
                onValueChange = { value -> onEditorChange { it.copy(usageDate = value) } },
                label = { Text("사용일자 (yyyy-MM-dd)") },
                singleLine = true
            )
            OutlinedTextField(
                modifier = Modifier.fillMaxWidth(),
                value = editor.stopover,
                onValueChange = { value -> onEditorChange { it.copy(stopover = value) } },
                label = { Text("경유지") },
                singleLine = true
            )
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                OutlinedTextField(
                    modifier = Modifier.weight(1f),
                    value = editor.startOdometer,
                    onValueChange = { value ->
                        if (editor.needsManualStart) {
                            onEditorChange { it.copy(startOdometer = value.filter(Char::isDigit)) }
                        }
                    },
                    label = { Text("출발 누적거리") },
                    readOnly = !editor.needsManualStart,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number)
                )
                OutlinedTextField(
                    modifier = Modifier.weight(1f),
                    value = editor.endOdometer,
                    onValueChange = { value ->
                        onEditorChange {
                            it.copy(
                                endOdometer = value.filter(Char::isDigit),
                                ocrConfirmed = false
                            )
                        }
                    },
                    label = { Text("도착 누적거리") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number)
                )
            }
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                AssistChip(onClick = {}, label = { Text("사용목적 업무용") })
                AssistChip(onClick = {}, label = { Text("출발지 본점") })
                AssistChip(onClick = {}, label = { Text("도착지 본점") })
            }
            Text(
                text = if (estimatedDistance != null && estimatedDistance >= 0) {
                    "예상 주행거리 ${estimatedDistance}km"
                } else {
                    "도착 누적거리는 출발 누적거리보다 커야 합니다."
                },
                color = if (estimatedDistance != null && estimatedDistance >= 0) {
                    MaterialTheme.colorScheme.primary
                } else {
                    MaterialTheme.colorScheme.error
                }
            )
            Button(
                modifier = Modifier.fillMaxWidth(),
                enabled = !uiState.screen.busy && !editor.isOcrRunning,
                onClick = onSaveTrip
            ) {
                Icon(Icons.Default.Save, contentDescription = null)
                Spacer(Modifier.width(6.dp))
                Text(if (uiState.screen.busy) "저장 중..." else "운행 기록 저장")
            }
        }
    }
}

@Composable
private fun ExportPanel(
    uiState: AppUiState,
    onChange: ((kr.co.hwacheon.carmileage.ExportFormState) -> kr.co.hwacheon.carmileage.ExportFormState) -> Unit,
    onRequestExport: () -> Unit
) {
    val export = uiState.screen.export
    Card(shape = RoundedCornerShape(8.dp)) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Text("월별 운행기록부 출력", style = MaterialTheme.typography.titleMedium)
            OutlinedTextField(
                modifier = Modifier.fillMaxWidth(),
                value = export.month,
                onValueChange = { value -> onChange { it.copy(month = value) } },
                label = { Text("출력 월 (yyyy-MM)") },
                singleLine = true
            )
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                ExportFormat.entries.forEach { format ->
                    FilterChip(
                        selected = export.format == format,
                        onClick = { onChange { it.copy(format = format) } },
                        label = { Text(format.label) },
                        leadingIcon = { Icon(Icons.Default.UploadFile, contentDescription = null) }
                    )
                }
            }
            Button(
                modifier = Modifier.fillMaxWidth(),
                enabled = !uiState.screen.busy,
                onClick = onRequestExport
            ) {
                Icon(Icons.Default.FileDownload, contentDescription = null)
                Spacer(Modifier.width(6.dp))
                Text(
                    if (export.format == ExportFormat.CSV) {
                        "Google Sheets로 공유"
                    } else {
                        "사진 양식으로 출력 요청"
                    }
                )
            }
            export.resultMessage?.let { StatusSurface(it) }
        }
    }
}

@Composable
private fun TripLogRow(trip: TripLog) {
    Card(shape = RoundedCornerShape(8.dp)) {
        Column(
            modifier = Modifier.padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(trip.usageDate.toString(), style = MaterialTheme.typography.titleSmall)
                Text("${trip.drivingDistanceKm}km", color = MaterialTheme.colorScheme.primary)
            }
            Text("${trip.departure} → ${trip.stopover} → ${trip.destination}")
            Text(
                "누적 ${trip.startOdometerKm}km → ${trip.endOdometerKm}km · 월 누계 ${trip.monthlyBusinessDistanceKm}km",
                style = MaterialTheme.typography.bodyMedium
            )
            Text(
                "${trip.departmentName} / ${trip.positionName} / ${trip.writerName}",
                style = MaterialTheme.typography.bodySmall
            )
        }
    }
}

@Composable
private fun AdminPanel(uiState: AppUiState) {
    Card(shape = RoundedCornerShape(8.dp)) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Text("관리", style = MaterialTheme.typography.titleMedium)
            Text("초대코드: ${kr.co.hwacheon.carmileage.BuildConfig.DEFAULT_INVITE_CODE}")
            Text("관리자 PIN 데모값: 0000")
            Text("차량별 기록 수")
            uiState.data.vehicles.forEach { vehicle ->
                val count = uiState.data.trips.count { it.vehicleId == vehicle.id }
                Text("${vehicle.name}: ${count}건 · ${vehicle.registrationNumber}")
            }
        }
    }
}

@Composable
private fun ChoiceField(
    label: String,
    value: String,
    options: List<String>,
    onSelected: (String) -> Unit
) {
    var expanded by remember { mutableStateOf(false) }
    Column {
        Text(label, style = MaterialTheme.typography.labelLarge)
        OutlinedButton(
            modifier = Modifier.fillMaxWidth(),
            onClick = { expanded = true }
        ) {
            Text(value)
        }
        DropdownMenu(
            expanded = expanded,
            onDismissRequest = { expanded = false }
        ) {
            options.forEach { option ->
                DropdownMenuItem(
                    text = { Text(option) },
                    onClick = {
                        expanded = false
                        onSelected(option)
                    }
                )
            }
        }
    }
}

@Composable
private fun StatusSurface(text: String) {
    Surface(
        color = MaterialTheme.colorScheme.surfaceVariant,
        shape = RoundedCornerShape(8.dp)
    ) {
        Text(
            modifier = Modifier.padding(12.dp),
            text = text,
            style = MaterialTheme.typography.bodyMedium
        )
    }
}

@Composable
private fun EmptyText(text: String) {
    Text(
        modifier = Modifier
            .fillMaxWidth()
            .height(96.dp)
            .padding(16.dp),
        text = text,
        color = MaterialTheme.colorScheme.outline
    )
}
