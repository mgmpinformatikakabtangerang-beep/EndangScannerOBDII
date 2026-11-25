package com.endang.scannerobdii.log

import android.content.Context
import android.os.Environment
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

object TripLogExporter {
    fun appendTripCsv(
        context: Context,
        vehicleLabel: String,
        odometerKm: Double,
        tripDistanceKm: Double,
        estimatedFuelUsedLiters: Double,
        dtcCodes: List<String>
    ): Result<String> {
        return try {
            val baseDir = context.getExternalFilesDir(Environment.DIRECTORY_DOCUMENTS) ?: context.filesDir
            if (!baseDir.exists()) baseDir.mkdirs()

            val file = File(baseDir, "EndangScannerOBDII_TripLog.csv")
            val isNewFile = !file.exists()
            val timestamp = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault()).format(Date())
            val avgKmPerL = if (estimatedFuelUsedLiters > 0.05 && tripDistanceKm > 0.05) {
                String.format(Locale.US, "%.2f", tripDistanceKm / estimatedFuelUsedLiters)
            } else {
                ""
            }
            val dtcJoined = dtcCodes.joinToString("|").replace(",", ";")

            val sb = StringBuilder()
            if (isNewFile) {
                sb.appendLine("timestamp,vehicle,odometer_km,trip_km,fuel_used_l,avg_km_per_l,dtc")
            }
            sb.appendLine(
                "\"$timestamp\",\"$vehicleLabel\"," +
                    "${String.format(Locale.US, "%.1f", odometerKm)}," +
                    "${String.format(Locale.US, "%.2f", tripDistanceKm)}," +
                    "${String.format(Locale.US, "%.2f", estimatedFuelUsedLiters)}," +
                    "$avgKmPerL,\"$dtcJoined\""
            )

            file.appendText(sb.toString())
            Result.success(file.absolutePath)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
}
