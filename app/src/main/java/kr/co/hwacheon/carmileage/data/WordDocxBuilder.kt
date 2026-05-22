package kr.co.hwacheon.carmileage.data

import java.io.ByteArrayOutputStream
import java.time.DayOfWeek
import java.time.YearMonth
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream
import kr.co.hwacheon.carmileage.domain.TripLog
import kr.co.hwacheon.carmileage.domain.Vehicle

object WordDocxBuilder {
    private const val HEADER_FILL = "FFF2CC"
    private const val SUMMARY_FILL = "D9D9D9"
    private val columnWidths = listOf(
        1100,
        1200,
        850,
        850,
        900,
        950,
        1000,
        1150,
        950,
        1000,
        850,
        1050,
        750,
        750,
        700,
        700
    )

    fun build(vehicle: Vehicle, month: YearMonth, trips: List<TripLog>): ByteArray {
        val output = ByteArrayOutputStream()
        ZipOutputStream(output).use { zip ->
            zip.writeEntry("[Content_Types].xml", contentTypesXml())
            zip.writeEntry("_rels/.rels", packageRelationshipsXml())
            zip.writeEntry("word/document.xml", documentXml(vehicle, month, trips))
        }
        return output.toByteArray()
    }

    private fun ZipOutputStream.writeEntry(path: String, content: String) {
        putNextEntry(ZipEntry(path))
        write(content.toByteArray(Charsets.UTF_8))
        closeEntry()
    }

    private fun contentTypesXml(): String =
        """<?xml version="1.0" encoding="UTF-8" standalone="yes"?>
<Types xmlns="http://schemas.openxmlformats.org/package/2006/content-types">
  <Default Extension="rels" ContentType="application/vnd.openxmlformats-package.relationships+xml"/>
  <Default Extension="xml" ContentType="application/xml"/>
  <Override PartName="/word/document.xml" ContentType="application/vnd.openxmlformats-officedocument.wordprocessingml.document.main+xml"/>
</Types>"""

    private fun packageRelationshipsXml(): String =
        """<?xml version="1.0" encoding="UTF-8" standalone="yes"?>
<Relationships xmlns="http://schemas.openxmlformats.org/package/2006/relationships">
  <Relationship Id="rId1" Type="http://schemas.openxmlformats.org/officeDocument/2006/relationships/officeDocument" Target="word/document.xml"/>
</Relationships>"""

    private fun documentXml(vehicle: Vehicle, month: YearMonth, trips: List<TripLog>): String {
        val sortedTrips = trips.sortedWith(compareBy<TripLog> { it.usageDate }.thenBy { it.createdAtMillis })
        return """<?xml version="1.0" encoding="UTF-8" standalone="yes"?>
<w:document xmlns:w="http://schemas.openxmlformats.org/wordprocessingml/2006/main">
  <w:body>
    ${paragraph("중요 문서 무단 반출시 법적 제재를 받을 수 있으며 모든 출력물은 모니터링 됩니다.", size = 16, align = "center", color = "999999")}
    ${topInfoTable(month)}
    ${paragraph("1. 자동차등록번호 : ${vehicle.registrationNumber}", size = 24, spacingAfter = 120)}
    ${paragraph("2. 업무용 사용비율 계산", size = 24, spacingAfter = 80)}
    ${mainLogTable(sortedTrips)}
    ${paragraph("※ ③ 사용목적란은 '사업장방문(a)', '거래처방문(b)', '회의참석(c)', '판촉활동(d)', '출퇴근(e)', '교육훈련 기타(f)' 중 택1 하여 코드만 기록", size = 18, spacingBefore = 120)}
    <w:sectPr>
      <w:pgSz w:w="16838" w:h="11906" w:orient="landscape"/>
      <w:pgMar w:top="620" w:right="620" w:bottom="620" w:left="620" w:header="360" w:footer="360" w:gutter="0"/>
    </w:sectPr>
  </w:body>
</w:document>"""
    }

    private fun topInfoTable(month: YearMonth): String {
        val title = "${month.year}년 ( ${month.monthValue} )월 업무용승용차 운행기록부"
        return table(
            rows = listOf(
                TableRow(
                    cells = listOf(
                        Cell(title, span = 10, bold = true, size = 32),
                        Cell("사 무 소 명", span = 2, size = 20),
                        Cell("회천농업협동조합", span = 4, size = 20)
                    ),
                    height = 520
                ),
                TableRow(
                    cells = listOf(
                        Cell("(전월 누적 주행거리 :          km)", span = 10, size = 20),
                        Cell("사업자등록번호", span = 2, size = 20),
                        Cell("127-82-00510", span = 4, size = 20)
                    ),
                    height = 420
                )
            ),
            widths = columnWidths
        )
    }

    private fun mainLogTable(trips: List<TripLog>): String {
        val dataRows = trips.map { it.toDataRow() }
        val blankRows = List((12 - dataRows.size).coerceAtLeast(0)) { blankDataRow() }
        val totalDistance = trips.sumOf { it.drivingDistanceKm }
        val businessDistance = trips.sumOf { it.drivingDistanceKm }
        val ratio = if (totalDistance > 0) "100" else ""

        val rows = buildList {
            addAll(headerRows())
            addAll(dataRows)
            addAll(blankRows)
            add(
                TableRow(
                    cells = listOf(
                        Cell("", span = 10, fill = SUMMARY_FILL),
                        Cell("⑫총주행\n거리(km)", span = 2, fill = HEADER_FILL, bold = true),
                        Cell("⑬업무용\n주행거리\n누계(km)", span = 2, fill = HEADER_FILL, bold = true),
                        Cell("⑭업무사용\n비율(%)\n(⑬/⑫)", span = 2, fill = HEADER_FILL, bold = true)
                    ),
                    height = 640
                )
            )
            add(
                TableRow(
                    cells = listOf(
                        Cell("", span = 10, fill = SUMMARY_FILL),
                        Cell(totalDistance.toString(), span = 2, size = 18),
                        Cell(businessDistance.toString(), span = 2, size = 18),
                        Cell(ratio, span = 2, size = 18)
                    ),
                    height = 420
                )
            )
        }

        return table(rows = rows, widths = columnWidths)
    }

    private fun headerRows(): List<TableRow> = listOf(
        TableRow(
            cells = listOf(
                Cell("①사용일자\n(요일)", fill = HEADER_FILL, bold = true, verticalMerge = "restart"),
                Cell("②사용자", span = 3, fill = HEADER_FILL, bold = true),
                Cell("③사용목적", fill = HEADER_FILL, bold = true, verticalMerge = "restart"),
                Cell("운행 내역", span = 7, fill = HEADER_FILL, bold = true),
                Cell("⑪비고", fill = HEADER_FILL, bold = true, verticalMerge = "restart"),
                Cell("작성자", fill = HEADER_FILL, bold = true, verticalMerge = "restart"),
                Cell("확인", span = 2, fill = HEADER_FILL, bold = true)
            ),
            height = 520
        ),
        TableRow(
            cells = listOf(
                Cell(fill = HEADER_FILL, verticalMerge = "continue"),
                Cell("부서", fill = HEADER_FILL, bold = true, verticalMerge = "restart"),
                Cell("직책", fill = HEADER_FILL, bold = true, verticalMerge = "restart"),
                Cell("성명", fill = HEADER_FILL, bold = true, verticalMerge = "restart"),
                Cell(fill = HEADER_FILL, verticalMerge = "continue"),
                Cell("출 발", span = 2, fill = HEADER_FILL, bold = true),
                Cell("⑥경유지", fill = HEADER_FILL, bold = true, verticalMerge = "restart"),
                Cell("도 착", span = 2, fill = HEADER_FILL, bold = true),
                Cell("⑨주행거리\n(km)\n(⑧-⑤)", fill = HEADER_FILL, bold = true, verticalMerge = "restart"),
                Cell("⑩업무용\n주행거리\n누계(km)", fill = HEADER_FILL, bold = true, verticalMerge = "restart"),
                Cell(fill = HEADER_FILL, verticalMerge = "continue"),
                Cell(fill = HEADER_FILL, verticalMerge = "continue"),
                Cell("계원", fill = HEADER_FILL, bold = true, verticalMerge = "restart"),
                Cell("책임자", fill = HEADER_FILL, bold = true, verticalMerge = "restart")
            ),
            height = 560
        ),
        TableRow(
            cells = listOf(
                Cell(fill = HEADER_FILL, verticalMerge = "continue"),
                Cell(fill = HEADER_FILL, verticalMerge = "continue"),
                Cell(fill = HEADER_FILL, verticalMerge = "continue"),
                Cell(fill = HEADER_FILL, verticalMerge = "continue"),
                Cell(fill = HEADER_FILL, verticalMerge = "continue"),
                Cell("④출발지", fill = HEADER_FILL, bold = true),
                Cell("⑤출발시\n누적거리\n(km)", fill = HEADER_FILL, bold = true),
                Cell(fill = HEADER_FILL, verticalMerge = "continue"),
                Cell("⑦도착지", fill = HEADER_FILL, bold = true),
                Cell("⑧도착시\n누적거리\n(km)", fill = HEADER_FILL, bold = true),
                Cell(fill = HEADER_FILL, verticalMerge = "continue"),
                Cell(fill = HEADER_FILL, verticalMerge = "continue"),
                Cell(fill = HEADER_FILL, verticalMerge = "continue"),
                Cell(fill = HEADER_FILL, verticalMerge = "continue"),
                Cell(fill = HEADER_FILL, verticalMerge = "continue"),
                Cell(fill = HEADER_FILL, verticalMerge = "continue")
            ),
            height = 720
        )
    )

    private fun TripLog.toDataRow(): TableRow = TableRow(
        cells = listOf(
            Cell(usageDate.toExportDate(), size = 17),
            Cell(departmentName, size = 17),
            Cell(positionName, size = 17),
            Cell(writerName, size = 17),
            Cell(purpose, size = 17),
            Cell(departure, size = 17),
            Cell(startOdometerKm.toString(), size = 17),
            Cell(stopover, size = 17),
            Cell(destination, size = 17),
            Cell(endOdometerKm.toString(), size = 17),
            Cell(drivingDistanceKm.toString(), size = 17),
            Cell(monthlyBusinessDistanceKm.toString(), size = 17),
            Cell("", size = 17),
            Cell(writerName, size = 17),
            Cell("", size = 17),
            Cell("", size = 17)
        ),
        height = 520
    )

    private fun blankDataRow(): TableRow = TableRow(
        cells = List(columnWidths.size) { Cell("", size = 17) },
        height = 520
    )

    private fun table(rows: List<TableRow>, widths: List<Int>): String = buildString {
        append("""<w:tbl><w:tblPr><w:tblW w:w="${widths.sum()}" w:type="dxa"/><w:tblLayout w:type="fixed"/><w:tblBorders><w:top w:val="single" w:sz="6" w:space="0" w:color="666666"/><w:left w:val="single" w:sz="6" w:space="0" w:color="666666"/><w:bottom w:val="single" w:sz="6" w:space="0" w:color="666666"/><w:right w:val="single" w:sz="6" w:space="0" w:color="666666"/><w:insideH w:val="single" w:sz="4" w:space="0" w:color="888888"/><w:insideV w:val="single" w:sz="4" w:space="0" w:color="888888"/></w:tblBorders><w:tblCellMar><w:top w:w="60" w:type="dxa"/><w:left w:w="60" w:type="dxa"/><w:bottom w:w="60" w:type="dxa"/><w:right w:w="60" w:type="dxa"/></w:tblCellMar></w:tblPr>""")
        append("<w:tblGrid>")
        widths.forEach { width -> append("""<w:gridCol w:w="$width"/>""") }
        append("</w:tblGrid>")
        rows.forEach { row ->
            append("""<w:tr><w:trPr><w:trHeight w:val="${row.height}" w:hRule="atLeast"/></w:trPr>""")
            var columnIndex = 0
            row.cells.forEach { cell ->
                val width = widths.drop(columnIndex).take(cell.span).sum()
                append(cellXml(cell = cell, width = width))
                columnIndex += cell.span
            }
            append("</w:tr>")
        }
        append("</w:tbl>")
    }

    private fun cellXml(cell: Cell, width: Int): String {
        val gridSpan = if (cell.span > 1) """<w:gridSpan w:val="${cell.span}"/>""" else ""
        val fill = cell.fill?.let { """<w:shd w:fill="$it"/>""" }.orEmpty()
        val verticalMerge = when (cell.verticalMerge) {
            "restart" -> """<w:vMerge w:val="restart"/>"""
            "continue" -> "<w:vMerge/>"
            else -> ""
        }
        val bold = if (cell.bold) "<w:b/>" else ""
        return """<w:tc>
  <w:tcPr><w:tcW w:w="$width" w:type="dxa"/>$gridSpan$verticalMerge$fill<w:vAlign w:val="center"/></w:tcPr>
  <w:p>
    <w:pPr><w:jc w:val="${cell.align}"/></w:pPr>
    <w:r>
      <w:rPr>${fontXml()}$bold<w:sz w:val="${cell.size}"/></w:rPr>
      ${cell.text.textRuns()}
    </w:r>
  </w:p>
</w:tc>"""
    }

    private fun paragraph(
        text: String,
        size: Int,
        align: String = "left",
        color: String = "000000",
        spacingBefore: Int = 0,
        spacingAfter: Int = 0
    ): String =
        """<w:p>
  <w:pPr><w:jc w:val="$align"/><w:spacing w:before="$spacingBefore" w:after="$spacingAfter"/></w:pPr>
  <w:r>
    <w:rPr>${fontXml()}<w:color w:val="$color"/><w:sz w:val="$size"/></w:rPr>
    ${text.textRuns()}
  </w:r>
</w:p>"""

    private fun fontXml(): String =
        """<w:rFonts w:ascii="Malgun Gothic" w:hAnsi="Malgun Gothic" w:eastAsia="Malgun Gothic"/>"""

    private fun String.textRuns(): String =
        split("\n").joinToString("<w:br/>") { part ->
            """<w:t xml:space="preserve">${part.xmlEscaped()}</w:t>"""
        }

    private fun java.time.LocalDate.toExportDate(): String {
        val day = when (dayOfWeek) {
            DayOfWeek.MONDAY -> "월"
            DayOfWeek.TUESDAY -> "화"
            DayOfWeek.WEDNESDAY -> "수"
            DayOfWeek.THURSDAY -> "목"
            DayOfWeek.FRIDAY -> "금"
            DayOfWeek.SATURDAY -> "토"
            DayOfWeek.SUNDAY -> "일"
        }
        return "${monthValue.toString().padStart(2, '0')}.${dayOfMonth.toString().padStart(2, '0')}\n($day)"
    }

    private fun String.xmlEscaped(): String =
        replace("&", "&amp;")
            .replace("<", "&lt;")
            .replace(">", "&gt;")
            .replace("\"", "&quot;")
            .replace("'", "&apos;")

    private data class TableRow(
        val cells: List<Cell>,
        val height: Int
    )

    private data class Cell(
        val text: String = "",
        val span: Int = 1,
        val fill: String? = null,
        val bold: Boolean = false,
        val size: Int = 18,
        val align: String = "center",
        val verticalMerge: String? = null
    )
}
