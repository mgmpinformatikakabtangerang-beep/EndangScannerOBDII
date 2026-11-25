package com.endang.scannerobdii.model

data class VehicleProfile(
    val id: String,
    val brand: String,
    val model: String,
    val tankCapacityLiters: Double,
    val engineDisplacementLiters: Double,
    val initialOdometerKm: Double,
    val fuelCalibrationFactor: Double = 1.0
)

object VehicleCatalog {
    val defaults = listOf(
        VehicleProfile("vios_g2", "Toyota", "Vios Gen 2 (NCP93)", 42.0, 1.5, 0.0, 1.0),
        VehicleProfile("avanza_13", "Toyota", "Avanza 1.3", 45.0, 1.3, 0.0, 1.0),
        VehicleProfile("innova_20", "Toyota", "Kijang Innova 2.0", 55.0, 2.0, 0.0, 1.0),
        VehicleProfile("jazz_ge8", "Honda", "Jazz GE8", 40.0, 1.5, 0.0, 1.0),
        VehicleProfile("brio_12", "Honda", "Brio 1.2", 35.0, 1.2, 0.0, 1.0),
        VehicleProfile("xenia_13", "Daihatsu", "Xenia 1.3", 45.0, 1.3, 0.0, 1.0),
        VehicleProfile("ertiga_15", "Suzuki", "Ertiga 1.5", 45.0, 1.5, 0.0, 1.0),
        VehicleProfile("custom", "Custom", "Profil Mobil Lain", 45.0, 1.5, 0.0, 1.0)
    )
}
