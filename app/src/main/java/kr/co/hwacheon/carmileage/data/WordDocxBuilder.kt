package kr.co.hwacheon.carmileage.data

import java.io.ByteArrayOutputStream
import java.time.YearMonth
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream
import kr.co.hwacheon.carmileage.domain.TripLog
import kr.co.hwacheon.carmileage.domain.Vehicle

object WordDocxBuilder {
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
        val rows = buildRows(vehicle, trips)
        return """<?xml version="1.0" encoding="UTF-8" standalone="yes"?>
<w:document xmlns:w="http://schemas.openxmlformats.org/wordprocessingml/2006/main">
  <w:body>
    ${paragraph("${month.year}년 ${month.monthValue}월 업무용승용차 운행기록부", bold = true, size = 32)}
    ${paragraph("차량: ${vehicle.name}    차량번호: ${vehicle.registrationNumber}", bold = true, size = 22)}
    ${table(rows)}
    ${paragraph("※ 이 문서는 공유 차계부 앱에서 생성되었습니다.", size = 18)}
    <w:sectPr>
      <w:pgSz w:w="16838" w:h="11906" w:orient="landscape"/>
      <w:pgMar w:top="720" w:right="720" w:bottom="720" w:left="720" w:header="450" w:footer="450" w:gutter="0"/>
    </w:sectPr>
  </w:body>
</w:document>"""
    }

    private fun buildRows(vehicle: Vehicle, trips: List<TripLog>): List<List<String>> {
        val rows = mutableListOf(
            listOf(
                "사용일자",
                "부서",
                "직책",
                "성명",
                "사용목적",
                "출발지",
                "출발km",
                "경유지",
                "도착지",
                "도착km",
                "주행km",
                "월누계"
            )
        )
        if (trips.isEmpty()) {
            rows += listOf("", "", "", "", "", "", "", "저장된 기록 없음", "", "", "", "")
        } else {
            rows += trips.map { trip ->
                listOf(
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
        }
        val total = trips.sumOf { it.drivingDistanceKm }
        rows += listOf("", "", "", "", "", "", "", "${vehicle.name} 합계", "", "", total.toString(), total.toString())
        return rows
    }

    private fun paragraph(text: String, bold: Boolean = false, size: Int = 20): String {
        val boldXml = if (bold) "<w:b/>" else ""
        return """<w:p>
  <w:r>
    <w:rPr>$boldXml<w:sz w:val="$size"/></w:rPr>
    <w:t>${text.xmlEscaped()}</w:t>
  </w:r>
</w:p>"""
    }

    private fun table(rows: List<List<String>>): String {
        return buildString {
            append("""<w:tbl><w:tblPr><w:tblW w:w="0" w:type="auto"/><w:tblBorders><w:top w:val="single" w:sz="4" w:space="0" w:color="888888"/><w:left w:val="single" w:sz="4" w:space="0" w:color="888888"/><w:bottom w:val="single" w:sz="4" w:space="0" w:color="888888"/><w:right w:val="single" w:sz="4" w:space="0" w:color="888888"/><w:insideH w:val="single" w:sz="4" w:space="0" w:color="888888"/><w:insideV w:val="single" w:sz="4" w:space="0" w:color="888888"/></w:tblBorders></w:tblPr>""")
            rows.forEachIndexed { rowIndex, row ->
                append("<w:tr>")
                row.forEach { cell ->
                    append(cell(cell, header = rowIndex == 0))
                }
                append("</w:tr>")
            }
            append("</w:tbl>")
        }
    }

    private fun cell(text: String, header: Boolean): String {
        val shading = if (header) """<w:shd w:fill="EDE7DD"/>""" else ""
        val bold = if (header) "<w:b/>" else ""
        return """<w:tc>
  <w:tcPr><w:tcW w:w="1200" w:type="dxa"/>$shading</w:tcPr>
  <w:p><w:r><w:rPr>$bold<w:sz w:val="18"/></w:rPr><w:t>${text.xmlEscaped()}</w:t></w:r></w:p>
</w:tc>"""
    }

    private fun String.xmlEscaped(): String =
        replace("&", "&amp;")
            .replace("<", "&lt;")
            .replace(">", "&gt;")
            .replace("\"", "&quot;")
            .replace("'", "&apos;")
}
