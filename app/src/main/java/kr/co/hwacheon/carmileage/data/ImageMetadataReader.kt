package kr.co.hwacheon.carmileage.data

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.location.Geocoder
import android.location.Location
import android.location.LocationManager
import android.net.Uri
import androidx.core.content.ContextCompat
import androidx.exifinterface.media.ExifInterface
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import java.util.Locale

data class PhotoMetadata(
    val takenDate: LocalDate?,
    val stopoverSuggestion: String?
)

class ImageMetadataReader(private val context: Context) {
    private val exifDateFormat = DateTimeFormatter.ofPattern("yyyy:MM:dd HH:mm:ss")

    fun resolveTakenDate(uri: Uri): LocalDate? {
        return resolvePhotoMetadata(uri).takenDate
    }

    fun resolvePhotoMetadata(uri: Uri): PhotoMetadata {
        val exifMetadata = readExifMetadata(uri)
        val stopover = exifMetadata.stopoverSuggestion
            ?: resolveDeviceLocationStopover()
        return exifMetadata.copy(stopoverSuggestion = stopover)
    }

    private fun readExifMetadata(uri: Uri): PhotoMetadata {
        return runCatching {
            context.contentResolver.openInputStream(uri)?.use { stream ->
                val exif = ExifInterface(stream)
                val rawDate = exif.getAttribute(ExifInterface.TAG_DATETIME_ORIGINAL)
                    ?: exif.getAttribute(ExifInterface.TAG_DATETIME)
                val takenDate = rawDate?.let {
                    LocalDateTime.parse(it, exifDateFormat).toLocalDate()
                }
                val latLong = FloatArray(2)
                val stopover = if (exif.getLatLong(latLong)) {
                    reverseGeocode(latLong[0].toDouble(), latLong[1].toDouble())
                } else {
                    null
                }
                PhotoMetadata(takenDate = takenDate, stopoverSuggestion = stopover)
            }
        }.getOrNull() ?: PhotoMetadata(takenDate = null, stopoverSuggestion = null)
    }

    private fun resolveDeviceLocationStopover(): String? {
        if (!hasLocationPermission()) return null
        val locationManager = context.getSystemService(Context.LOCATION_SERVICE) as? LocationManager
            ?: return null
        val providers: List<String> = runCatching {
            locationManager.getProviders(true).toList()
        }.getOrDefault(emptyList())
        val bestLocation = providers
            .mapNotNull { provider ->
                runCatching { locationManager.getLastKnownLocation(provider) }.getOrNull()
            }
            .maxByOrNull(Location::getTime)
            ?: return null
        return reverseGeocode(bestLocation.latitude, bestLocation.longitude)
    }

    private fun hasLocationPermission(): Boolean {
        val fine = ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.ACCESS_FINE_LOCATION
        ) == PackageManager.PERMISSION_GRANTED
        val coarse = ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.ACCESS_COARSE_LOCATION
        ) == PackageManager.PERMISSION_GRANTED
        return fine || coarse
    }

    private fun reverseGeocode(latitude: Double, longitude: Double): String {
        val fallback = "%.5f, %.5f".format(Locale.US, latitude, longitude)
        return runCatching {
            val geocoder = Geocoder(context, Locale.KOREA)
            @Suppress("DEPRECATION")
            val address = geocoder.getFromLocation(latitude, longitude, 1)
                ?.firstOrNull()
            listOfNotNull(
                address?.adminArea,
                address?.locality ?: address?.subLocality,
                address?.thoroughfare ?: address?.featureName
            )
                .distinct()
                .joinToString(" ")
                .ifBlank { fallback }
        }.getOrDefault(fallback)
    }
}
