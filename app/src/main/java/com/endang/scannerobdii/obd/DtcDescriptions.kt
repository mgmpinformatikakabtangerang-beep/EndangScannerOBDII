package com.endang.scannerobdii.obd

object DtcDescriptions {
    private val descriptions: Map<String, String> = mapOf(
        "P0100" to "Gangguan sirkuit aliran udara masuk (MAF)",
        "P0102" to "Sinyal MAF terlalu rendah",
        "P0105" to "Gangguan sirkuit tekanan manifold (MAP)",
        "P0110" to "Gangguan sirkuit suhu udara masuk (IAT)",
        "P0115" to "Gangguan sirkuit suhu cairan pendingin mesin (ECT)",
        "P0120" to "Gangguan sirkuit sensor posisi throttle (TPS)",
        "P0130" to "Gangguan sirkuit sensor O2 Bank 1 Sensor 1",
        "P0133" to "Respons sensor O2 Bank 1 Sensor 1 lambat",
        "P0171" to "Campuran bahan bakar terlalu kurus (Lean) Bank 1",
        "P0172" to "Campuran bahan bakar terlalu kaya (Rich) Bank 1",
        "P0201" to "Gangguan sirkuit injektor silinder 1",
        "P0202" to "Gangguan sirkuit injektor silinder 2",
        "P0203" to "Gangguan sirkuit injektor silinder 3",
        "P0204" to "Gangguan sirkuit injektor silinder 4",
        "P0300" to "Pembakaran tidak merata (Misfire) acak / multi-silinder",
        "P0301" to "Misfire silinder 1",
        "P0302" to "Misfire silinder 2",
        "P0303" to "Misfire silinder 3",
        "P0304" to "Misfire silinder 4",
        "P0325" to "Gangguan sirkuit Knock Sensor Bank 1",
        "P0335" to "Gangguan sirkuit sensor posisi poros engkol (CKP)",
        "P0340" to "Gangguan sirkuit sensor posisi poros nok (CMP)",
        "P0420" to "Efisiensi catalytic converter rendah Bank 1",
        "P0500" to "Gangguan sensor kecepatan kendaraan (VSS)",
        "P0505" to "Gangguan sistem kontrol putaran idle (IAC)",
        "P0560" to "Tegangan sistem / aki tidak normal",
        "P0600" to "Gangguan komunikasi serial / ECU",
        "P0700" to "Permintaan indikator kerusakan transmisi (TCM)"
    )

    fun lookup(code: String): String {
        val normalized = code.trim().uppercase()
        if (descriptions.containsKey(normalized)) {
            return descriptions.getValue(normalized)
        }
        val category = when (normalized.firstOrNull()) {
            'P' -> "Powertrain (Mesin / Transmisi)"
            'C' -> "Chassis (Sasis / Rem / Kemudi)"
            'B' -> "Body (Bodi / Kelistrikan Kabin)"
            'U' -> "Network (Jaringan Komunikasi / CAN Bus)"
            else -> "OBD-II"
        }
        return "Kode $category umum — cek manual servis pabrikan untuk detail spesifik"
    }
}
