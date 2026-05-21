package kr.co.hwacheon.carmileage.data

import android.content.Context
import android.net.Uri
import androidx.exifinterface.media.ExifInterface
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter

class ImageMetadataReader(private val context: Context) {
    private val exifDateFormat = DateTimeFormatter.ofPattern("yyyy:MM:dd HH:mm:ss")

    fun resolveTakenDate(uri: Uri): LocalDate? {
        return runCatching {
            context.contentResolver.openInputStream(uri)?.use { stream ->
                val exif = ExifInterface(stream)
                val rawDate = exif.getAttribute(ExifInterface.TAG_DATETIME_ORIGINAL)
                    ?: exif.getAttribute(ExifInterface.TAG_DATETIME)
                rawDate?.let {
                    LocalDateTime.parse(it, exifDateFormat).toLocalDate()
                }
            }
        }.getOrNull()
    }
}
