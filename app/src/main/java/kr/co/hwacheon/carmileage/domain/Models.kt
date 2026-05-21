package kr.co.hwacheon.carmileage.domain

import java.time.LocalDate
import java.time.YearMonth
import java.util.UUID

enum class Department(val label: String) {
    FINANCE_BRANCH("금융지점"),
    FINANCE_BUSINESS("금융사업본부"),
    MEMBER_SUPPORT("조합원지원본부"),
    PLANNING("기획실"),
    OTHER("기타입력");

    fun resolve(customValue: String): String =
        if (this == OTHER) customValue.trim().ifBlank { label } else label
}

enum class Position(val label: String) {
    BRANCH_MANAGER("지점장"),
    HEADQUARTERS_MANAGER("본부장"),
    OFFICE_MANAGER("실장"),
    TEAM_LEAD("팀장"),
    ASSISTANT_MANAGER("과장보"),
    SENIOR_CLERK("계장"),
    JUNIOR_CLERK("계장보"),
    OTHER("기타입력");

    fun resolve(customValue: String): String =
        if (this == OTHER) customValue.trim().ifBlank { label } else label
}

enum class ExportFormat(val label: String, val extension: String) {
    DOCX("Word 문서", "docx"),
    CSV("Google Sheets CSV", "csv"),
    XLSX("Excel", "xlsx"),
    PDF("PDF", "pdf")
}

data class Vehicle(
    val id: String,
    val name: String,
    val registrationNumber: String,
    val isActive: Boolean = true,
    val initialOdometerKm: Int = 0
)

data class UserProfile(
    val id: String = UUID.randomUUID().toString(),
    val name: String,
    val department: String,
    val position: String,
    val pinHash: String,
    val isAdmin: Boolean = false
)

data class TripLog(
    val id: String = UUID.randomUUID().toString(),
    val vehicleId: String,
    val vehicleName: String,
    val vehicleRegistrationNumber: String,
    val usageDate: LocalDate,
    val createdAtMillis: Long = System.currentTimeMillis(),
    val writerName: String,
    val departmentName: String,
    val positionName: String,
    val purpose: String = "업무용",
    val departure: String = "본점",
    val stopover: String,
    val destination: String = "본점",
    val startOdometerKm: Int,
    val endOdometerKm: Int,
    val drivingDistanceKm: Int,
    val monthlyBusinessDistanceKm: Int,
    val ocrConfirmed: Boolean,
    val photoUri: String?
)

data class ExportRequest(
    val vehicleId: String,
    val month: YearMonth,
    val format: ExportFormat
)

data class ExportResult(
    val fileName: String,
    val downloadUrl: String?,
    val message: String
)
