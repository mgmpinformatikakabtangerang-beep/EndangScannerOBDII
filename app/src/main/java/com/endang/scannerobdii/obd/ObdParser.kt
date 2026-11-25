package com.endang.scannerobdii.obd

import kotlin.math.max

data class TelemetrySnapshot(
    val rpm: Int? = null,
    val speedKmh: Double? = null,
    val coolantC: Int? = null,
    val intakeTempC: Int? = null,
    val mafGps: Double? = null,
    val mapKpa: Int? = null,
    val voltageV: Double? = null,
    val fuelLevelPidPercent: Double? = null,
    val fuelRateLph: Double? = null,
    val fuelSourceLabel: String = "Menunggu",
    val tripDistanceKm: Double = 0.0,
    val odometerKm: Double = 0.0,
    val estimatedFuelRemainingLiters: Double = 0.0,
    val fuelEconomyKmPerL: Double? = null,
    val dtcCodes: List<String> = emptyList()
)

data class FreezeFrameData(
    val rpm: Int? = null,
    val speedKmh: Double? = null,
    val coolantC: Int? = null,
    val mapKpa: Int? = null
)

object ObdParser {
    private const val STOICH_AFR = 14.7
    private const val GASOLINE_DENSITY_G_PER_L = 740.0
    private const val FUEL_DIVISOR = STOICH_AFR * GASOLINE_DENSITY_G_PER_L

    fun parseVoltage(raw: String): Double? {
        val cleaned = raw.replace(">", "").replace("V", "", ignoreCase = true).trim()
        return cleaned.toDoubleOrNull()
    }

    fun parseHexBytes(raw: String, expectedPrefix: String): List<Int>? {
        val tokens = raw
            .replace(">", "")
            .replace("\r", " ")
            .replace("\n", " ")
            .trim()
            .split(Regex("\\s+"))
            .filter { it.matches(Regex("[0-9A-Fa-f]{2}")) }

        val idx = tokens.indexOfFirst { it.equals(expectedPrefix, ignoreCase = true) }
        if (idx == -1) return null
        val payload = tokens.drop(idx + 1).map { it.toInt(16) }
        return if (payload.isEmpty()) null else payload
    }

    fun parseRpm(raw: String): Int? =
        parseHexBytes(raw, "0C")?.takeIf { it.size >= 2 }?.let { ((it[0] * 256) + it[1]) / 4 }

    fun parseSpeedKmh(raw: String): Double? =
        parseHexBytes(raw, "0D")?.takeIf { it.isNotEmpty() }?.let { it[0].toDouble() }

    fun parseCoolantC(raw: String): Int? =
        parseHexBytes(raw, "05")?.takeIf { it.isNotEmpty() }?.let { it[0] - 40 }

    fun parseIntakeTempC(raw: String): Int? =
        parseHexBytes(raw, "0F")?.takeIf { it.isNotEmpty() }?.let { it[0] - 40 }

    fun parseMafGps(raw: String): Double? =
        parseHexBytes(raw, "10")?.takeIf { it.size >= 2 }?.let { ((it[0] * 256) + it[1]) / 100.0 }

    fun parseMapKpa(raw: String): Int? =
        parseHexBytes(raw, "0B")?.takeIf { it.isNotEmpty() }?.let { it[0] }

    fun parseFuelLevelPercent(raw: String): Double? =
        parseHexBytes(raw, "2F")?.takeIf { it.isNotEmpty() }?.let { (it[0] * 100.0) / 255.0 }

    fun parseFuelRateDirectLph(raw: String): Double? =
        parseHexBytes(raw, "5E")?.takeIf { it.size >= 2 }?.let { ((it[0] * 256) + it[1]) / 20.0 }

    fun estimateFuelRateLph(
        direct015ELph: Double?,
        mafGps: Double?,
        mapKpa: Int?,
        rpm: Int?,
        intakeTempC: Int?,
        displacementLiters: Double,
        volumetricEfficiency: Double = 0.80
    ): Pair<Double?, String> {
        if (direct015ELph != null && direct015ELph >= 0.0) {
            return direct015ELph to "PID 015E"
        }
        if (mafGps != null && mafGps > 0.0) {
            val lph = (mafGps * 3600.0) / FUEL_DIVISOR
            return lph to "MAF 0110"
        }
        if (mapKpa != null && rpm != null && rpm > 0) {
            val tempK = ((intakeTempC ?: 35) + 273.15)
            val airDensityGPerL = (mapKpa * 28.97) / (8.314 * tempK)
            val airflowGps = ((rpm * displacementLiters * volumetricEfficiency) / 120.0) * airDensityGPerL
            val lph = (airflowGps * 3600.0) / FUEL_DIVISOR
            return lph to "MAP 010B"
        }
        return null to "Tidak Tersedia"
    }

    fun computeKmPerLiter(speedKmh: Double?, fuelRateLph: Double?, calibrationFactor: Double): Double? {
        if (speedKmh == null || fuelRateLph == null || speedKmh < 3.0 || fuelRateLph <= 0.05) return null
        return max(0.1, (speedKmh / fuelRateLph) * calibrationFactor)
    }

    fun parseDtcCodes(raw: String): List<String> {
        val hexBytes = raw
            .replace(">", "")
            .replace("\r", " ")
            .replace("\n", " ")
            .uppercase()
            .split(Regex("\\s+"))
            .filter { it.matches(Regex("[0-9A-F]{2}")) }

        val dataBytes = mutableListOf<Int>()
        var i = 0
        while (i < hexBytes.size) {
            if (hexBytes[i] == "43") {
                var j = i + 1
                while (j + 1 < hexBytes.size) {
                    val b1 = hexBytes[j].toInt(16)
                    val b2 = hexBytes[j + 1].toInt(16)
                    if (!(b1 == 0 && b2 == 0)) {
                        dataBytes.add(b1)
                        dataBytes.add(b2)
                    }
                    j += 2
                }
                break
            }
            i++
        }

        val codes = mutableListOf<String>()
        var k = 0
        while (k + 1 < dataBytes.size) {
            val b1 = dataBytes[k]
            val b2 = dataBytes[k + 1]
            val prefixChar = when ((b1 and 0xC0) ushr 6) {
                0 -> 'P'
                1 -> 'C'
                2 -> 'B'
                else -> 'U'
            }
            val d1 = (b1 and 0x30) ushr 4
            val d2 = b1 and 0x0F
            val d3 = (b2 and 0xF0) ushr 4
            val d4 = b2 and 0x0F
            codes.add("$prefixChar$d1$d2$d3$d4")
            k += 2
        }
        return codes.distinct()
    }

    fun parseFreezeFrame(
        rpmRaw: String,
        speedRaw: String,
        coolantRaw: String,
        mapRaw: String
    ): FreezeFrameData {
        val rpm = parseHexBytes(rpmRaw, "00")?.takeIf { it.size >= 2 }?.let { ((it[0] * 256) + it[1]) / 4 }
            ?: parseRpm(rpmRaw)
        val speed = parseHexBytes(speedRaw, "00")?.takeIf { it.isNotEmpty() }?.let { it[0].toDouble() }
            ?: parseSpeedKmh(speedRaw)
        val coolant = parseHexBytes(coolantRaw, "00")?.takeIf { it.isNotEmpty() }?.let { it[0] - 40 }
            ?: parseCoolantC(coolantRaw)
        val map = parseHexBytes(mapRaw, "00")?.takeIf { it.isNotEmpty() }?.let { it[0] }
            ?: parseMapKpa(mapRaw)

        return FreezeFrameData(
            rpm = rpm,
            speedKmh = speed,
            coolantC = coolant,
            mapKpa = map
        )
    }
}
