package com.endang.scannerobdii

import android.Manifest
import android.annotation.SuppressLint
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothManager
import android.content.Context
import android.content.Intent
import android.graphics.BitmapFactory
import android.net.Uri
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.endang.scannerobdii.log.TripLogExporter
import com.endang.scannerobdii.model.VehicleCatalog
import com.endang.scannerobdii.obd.DtcDescriptions
import com.endang.scannerobdii.obd.Elm327ConnectionManager
import com.endang.scannerobdii.obd.FreezeFrameData
import com.endang.scannerobdii.obd.ObdParser
import com.endang.scannerobdii.obd.TelemetrySnapshot
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import java.util.Locale

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val bluetoothManager = getSystemService(Context.BLUETOOTH_SERVICE) as BluetoothManager
        val adapter: BluetoothAdapter? = bluetoothManager.adapter

        setContent {
            EndangScannerTheme {
                HeadUnitScannerScreen(bluetoothAdapter = adapter)
            }
        }
    }
}

@Composable
fun EndangScannerTheme(content: @Composable () -> Unit) {
    val darkScheme = darkColorScheme(
        primary = Color(0xFF00E5FF),
        secondary = Color(0xFF76FF03),
        background = Color(0xFF0B0F17),
        surface = Color(0xFF151C28),
        onBackground = Color.White,
        onSurface = Color.White
    )
    MaterialTheme(colorScheme = darkScheme, content = content)
}

@SuppressLint("MissingPermission")
@Composable
fun HeadUnitScannerScreen(bluetoothAdapter: BluetoothAdapter?) {
    val context = LocalContext.current
    val prefs = remember { context.getSharedPreferences("endang_obd_prefs", Context.MODE_PRIVATE) }
    val connectionManager = remember { Elm327ConnectionManager(bluetoothAdapter) }

    var selectedVehicle by remember {
        val savedId = prefs.getString("vehicle_id", VehicleCatalog.defaults.first().id)
        val base = VehicleCatalog.defaults.find { it.id == savedId } ?: VehicleCatalog.defaults.first()
        mutableStateOf(
            base.copy(
                tankCapacityLiters = prefs.getFloat("tank_cap", base.tankCapacityLiters.toFloat()).toDouble(),
                initialOdometerKm = prefs.getFloat("odo_base", base.initialOdometerKm.toFloat()).toDouble(),
                fuelCalibrationFactor = prefs.getFloat("fuel_cal", base.fuelCalibrationFactor.toFloat()).toDouble()
            )
        )
    }

    var pairedDevices by remember { mutableStateOf<List<BluetoothDevice>>(emptyList()) }
    var selectedMac by remember { mutableStateOf(prefs.getString("elm_mac", "") ?: "") }
    var isConnected by remember { mutableStateOf(false) }
    var statusText by remember { mutableStateOf("Belum terhubung ke ELM327 v2.1") }

    var tripKm by remember { mutableStateOf(prefs.getFloat("trip_km", 0f).toDouble()) }
    var fuelUsedTripLiters by remember { mutableStateOf(prefs.getFloat("fuel_used_trip", 0f).toDouble()) }
    var fuelRemainingLiters by remember {
        mutableStateOf(prefs.getFloat("fuel_liters", (selectedVehicle.tankCapacityLiters * 0.5).toFloat()).toDouble())
    }
    var manualFuelInput by remember { mutableStateOf(String.format(Locale.US, "%.1f", fuelRemainingLiters)) }
    var manualOdoInput by remember { mutableStateOf(String.format(Locale.US, "%.1f", selectedVehicle.initialOdometerKm)) }
    var bgUriString by remember { mutableStateOf(prefs.getString("bg_uri", null)) }

    var dtcList by remember { mutableStateOf<List<String>>(emptyList()) }
    var dtcStatus by remember { mutableStateOf("Belum dipindai") }
    var freezeFrame by remember { mutableStateOf<FreezeFrameData?>(null) }
    var logStatusText by remember { mutableStateOf("Siap mencatat log trip") }

    var telemetry by remember {
        mutableStateOf(
            TelemetrySnapshot(
                odometerKm = selectedVehicle.initialOdometerKm + tripKm,
                estimatedFuelRemainingLiters = fuelRemainingLiters
            )
        )
    }

    fun refreshPairedDevices() {
        pairedDevices = bluetoothAdapter?.bondedDevices?.toList().orEmpty()
        if (selectedMac.isBlank() && pairedDevices.isNotEmpty()) {
            selectedMac = pairedDevices.first().address
        }
    }

    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) {
        refreshPairedDevices()
    }

    val bgPickerLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocument()
    ) { uri: Uri? ->
        if (uri != null) {
            try {
                context.contentResolver.takePersistableUriPermission(
                    uri,
                    Intent.FLAG_GRANT_READ_URI_PERMISSION
                )
            } catch (_: Exception) {}
            bgUriString = uri.toString()
            prefs.edit().putString("bg_uri", bgUriString).apply()
        }
    }

    LaunchedEffect(Unit) {
        val needed = buildList {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                add(Manifest.permission.BLUETOOTH_CONNECT)
                add(Manifest.permission.BLUETOOTH_SCAN)
            }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                add(Manifest.permission.READ_MEDIA_IMAGES)
            } else {
                add(Manifest.permission.READ_EXTERNAL_STORAGE)
            }
        }
        permissionLauncher.launch(needed.toTypedArray())
        refreshPairedDevices()
    }

    LaunchedEffect(isConnected, selectedMac) {
        if (!isConnected || selectedMac.isBlank()) return@LaunchedEffect
        var lastTickMs = System.currentTimeMillis()

        while (isConnected) {
            val result = withContext(Dispatchers.IO) {
                val now = System.currentTimeMillis()
                val dtHours = ((now - lastTickMs).coerceAtMost(5000L)) / 3_600_000.0
                lastTickMs = now

                val voltRaw = connectionManager.sendCommand("ATRV", 150L)
                val rpmRaw = connectionManager.sendCommand("010C", 150L)
                val spdRaw = connectionManager.sendCommand("010D", 150L)
                val cltRaw = connectionManager.sendCommand("0105", 150L)
                val iatRaw = connectionManager.sendCommand("010F", 150L)
                val mafRaw = connectionManager.sendCommand("0110", 150L)
                val mapRaw = connectionManager.sendCommand("010B", 150L)
                val fuelLvlRaw = connectionManager.sendCommand("012F", 150L)
                val fuelRateRaw = connectionManager.sendCommand("015E", 150L)

                val voltage = ObdParser.parseVoltage(voltRaw)
                val rpm = ObdParser.parseRpm(rpmRaw)
                val speed = ObdParser.parseSpeedKmh(spdRaw)
                val coolant = ObdParser.parseCoolantC(cltRaw)
                val intakeTempC = ObdParser.parseIntakeTempC(iatRaw)
                val mafGps = ObdParser.parseMafGps(mafRaw)
                val mapKpa = ObdParser.parseMapKpa(mapRaw)
                val fuelPidPercent = ObdParser.parseFuelLevelPercent(fuelLvlRaw)
                val directFuelRate = ObdParser.parseFuelRateDirectLph(fuelRateRaw)

                val (fuelRate, fuelSource) = ObdParser.estimateFuelRateLph(
                    direct015ELph = directFuelRate,
                    mafGps = mafGps,
                    mapKpa = mapKpa,
                    rpm = rpm,
                    intakeTempC = intakeTempC,
                    displacementLiters = selectedVehicle.engineDisplacementLiters
                )

                val addedKm = if (speed != null && speed > 0.5) speed * dtHours else 0.0
                val updatedTrip = tripKm + addedKm
                val updatedOdo = selectedVehicle.initialOdometerKm + updatedTrip

                val usedThisTick = if (fuelRate != null && fuelRate > 0.0) {
                    (fuelRate * dtHours) / selectedVehicle.fuelCalibrationFactor
                } else 0.0

                val updatedFuelLiters = when {
                    fuelPidPercent != null -> (fuelPidPercent / 100.0) * selectedVehicle.tankCapacityLiters
                    usedThisTick > 0.0 -> (fuelRemainingLiters - usedThisTick).coerceIn(0.0, selectedVehicle.tankCapacityLiters)
                    else -> fuelRemainingLiters
                }

                val kmPerL = ObdParser.computeKmPerLiter(
                    speedKmh = speed,
                    fuelRateLph = fuelRate,
                    calibrationFactor = selectedVehicle.fuelCalibrationFactor
                )

                Pair(
                    TelemetrySnapshot(
                        rpm = rpm,
                        speedKmh = speed,
                        coolantC = coolant,
                        intakeTempC = intakeTempC,
                        mafGps = mafGps,
                        mapKpa = mapKpa,
                        voltageV = voltage,
                        fuelLevelPidPercent = fuelPidPercent,
                        fuelRateLph = fuelRate,
                        fuelSourceLabel = fuelSource,
                        tripDistanceKm = updatedTrip,
                        odometerKm = updatedOdo,
                        estimatedFuelRemainingLiters = updatedFuelLiters,
                        fuelEconomyKmPerL = kmPerL
                    ),
                    usedThisTick
                )
            }

            val snap = result.first
            val usedTick = result.second
            tripKm = snap.tripDistanceKm
            fuelRemainingLiters = snap.estimatedFuelRemainingLiters
            fuelUsedTripLiters += usedTick
            telemetry = snap

            prefs.edit()
                .putFloat("trip_km", tripKm.toFloat())
                .putFloat("fuel_liters", fuelRemainingLiters.toFloat())
                .putFloat("fuel_used_trip", fuelUsedTripLiters.toFloat())
                .apply()

            delay(350L)
        }
    }

    val customBgBitmap: ImageBitmap? = remember(bgUriString) {
        bgUriString?.let { raw ->
            try {
                context.contentResolver.openInputStream(Uri.parse(raw))?.use { stream ->
                    BitmapFactory.decodeStream(stream)?.asImageBitmap()
                }
            } catch (_: Exception) {
                null
            }
        }
    }

    Box(modifier = Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) {
        if (customBgBitmap != null) {
            Image(
                bitmap = customBgBitmap,
                contentDescription = "Latar Belakang Head Unit",
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize()
            )
            Box(modifier = Modifier.fillMaxSize().background(Color(0xB80B0F17)))
        }

        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(14.dp)
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column {
                    Text("EndangScannerOBDII", fontSize = 22.sp, fontWeight = FontWeight.Bold, color = Color(0xFF00E5FF))
                    Text(statusText, fontSize = 13.sp, color = Color(0xFFB0BEC5))
                }
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Button(onClick = { refreshPairedDevices() }) {
                        Text("Scan BT")
                    }
                    Button(
                        onClick = {
                            if (isConnected) {
                                connectionManager.disconnect()
                                isConnected = false
                                statusText = "Terputus dari ELM327"
                            } else if (selectedMac.isNotBlank()) {
                                statusText = "Menghubungkan ke $selectedMac..."
                                isConnected = connectionManager.connect(selectedMac)
                                statusText = if (isConnected) "Terhubung ELM327 v2.1 ($selectedMac)" else "Gagal konek ELM327"
                            }
                        }
                    ) {
                        Text(if (isConnected) "Putuskan" else "Hubungkan OBD")
                    }
                    OutlinedButton(onClick = { bgPickerLauncher.launch(arrayOf("image/*")) }) {
                        Text("Foto Latar")
                    }
                }
            }

            if (pairedDevices.isNotEmpty()) {
                LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    items(pairedDevices) { dev ->
                        val active = dev.address == selectedMac
                        FilterChip(
                            selected = active,
                            onClick = {
                                selectedMac = dev.address
                                prefs.edit().putString("elm_mac", dev.address).apply()
                            },
                            label = { Text("${dev.name ?: "ELM327"} (${dev.address})") }
                        )
                    }
                }
            }

            Text("Pilih Merek & Model Mobil", fontSize = 15.sp, fontWeight = FontWeight.SemiBold)
            LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                items(VehicleCatalog.defaults) { item ->
                    val active = item.id == selectedVehicle.id
                    FilterChip(
                        selected = active,
                        onClick = {
                            selectedVehicle = item
                            manualOdoInput = String.format(Locale.US, "%.1f", item.initialOdometerKm)
                            prefs.edit()
                                .putString("vehicle_id", item.id)
                                .putFloat("tank_cap", item.tankCapacityLiters.toFloat())
                                .putFloat("odo_base", item.initialOdometerKm.toFloat())
                                .putFloat("fuel_cal", item.fuelCalibrationFactor.toFloat())
                                .apply()
                        },
                        label = { Text("${item.brand} ${item.model} (${item.tankCapacityLiters.toInt()}L)") }
                    )
                }
            }

            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                MetricTile("RPM", telemetry.rpm?.toString() ?: "--", "rpm", Modifier.weight(1f))
                MetricTile("KECEPATAN", telemetry.speedKmh?.let { String.format(Locale.US, "%.0f", it) } ?: "--", "km/jam", Modifier.weight(1f))
                MetricTile("SUHU MESIN", telemetry.coolantC?.toString() ?: "--", "°C", Modifier.weight(1f))
                MetricTile("AKI (ATRV)", telemetry.voltageV?.let { String.format(Locale.US, "%.1f", it) } ?: "--", "Volt", Modifier.weight(1f))
            }

            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                val tankPercent = telemetry.fuelLevelPidPercent
                    ?: ((fuelRemainingLiters / selectedVehicle.tankCapacityLiters.coerceAtLeast(1.0)) * 100.0)
                MetricTile(
                    title = "ISI TANGKI",
                    value = String.format(Locale.US, "%.1f L", fuelRemainingLiters),
                    unit = String.format(Locale.US, "%.0f%% / %.0fL", tankPercent, selectedVehicle.tankCapacityLiters),
                    modifier = Modifier.weight(1f)
                )
                MetricTile(
                    title = "ODOMETER",
                    value = String.format(Locale.US, "%.1f", selectedVehicle.initialOdometerKm + tripKm),
                    unit = "km total",
                    modifier = Modifier.weight(1f)
                )
                MetricTile(
                    title = "JARAK TEMPUH",
                    value = String.format(Locale.US, "%.2f", tripKm),
                    unit = "km trip",
                    modifier = Modifier.weight(1f)
                )
                MetricTile(
                    title = "KONSUMSI BBM",
                    value = telemetry.fuelEconomyKmPerL?.let { String.format(Locale.US, "%.1f", it) } ?: "--",
                    unit = "km/L (${telemetry.fuelSourceLabel})",
                    modifier = Modifier.weight(1f)
                )
            }

            Card(
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                shape = RoundedCornerShape(12.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth().padding(12.dp),
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    OutlinedTextField(
                        value = manualFuelInput,
                        onValueChange = { manualFuelInput = it },
                        label = { Text("Isi Tangki (L)") },
                        singleLine = true,
                        modifier = Modifier.weight(1f)
                    )
                    Button(onClick = {
                        val parsed = manualFuelInput.toDoubleOrNull()
                        if (parsed != null) {
                            fuelRemainingLiters = parsed.coerceIn(0.0, selectedVehicle.tankCapacityLiters)
                            prefs.edit().putFloat("fuel_liters", fuelRemainingLiters.toFloat()).apply()
                        }
                    }) {
                        Text("Set BBM")
                    }

                    OutlinedTextField(
                        value = manualOdoInput,
                        onValueChange = { manualOdoInput = it },
                        label = { Text("Odometer Awal (km)") },
                        singleLine = true,
                        modifier = Modifier.weight(1f)
                    )
                    Button(onClick = {
                        val parsed = manualOdoInput.toDoubleOrNull()
                        if (parsed != null) {
                            selectedVehicle = selectedVehicle.copy(initialOdometerKm = parsed)
                            prefs.edit().putFloat("odo_base", parsed.toFloat()).apply()
                        }
                    }) {
                        Text("Set Odo")
                    }

                    OutlinedButton(onClick = {
                        tripKm = 0.0
                        fuelUsedTripLiters = 0.0
                        prefs.edit()
                            .putFloat("trip_km", 0f)
                            .putFloat("fuel_used_trip", 0f)
                            .apply()
                    }) {
                        Text("Reset Trip")
                    }
                }
            }

            Card(
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                shape = RoundedCornerShape(12.dp)
            ) {
                Column(
                    modifier = Modifier.fillMaxWidth().padding(12.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column {
                            Text("DIAGNOSTIK DTC & LOG TRIP", fontSize = 13.sp, fontWeight = FontWeight.Bold, color = Color(0xFF00E5FF))
                            Text("Status DTC: $dtcStatus", fontSize = 12.sp, color = Color(0xFFB0BEC5))
                        }
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            Button(
                                enabled = isConnected,
                                onClick = {
                                    val rawDtc = connectionManager.sendCommand("03", 300L)
                                    val found = ObdParser.parseDtcCodes(rawDtc)
                                    dtcList = found
                                    dtcStatus = if (found.isEmpty()) "Tidak ada kode DTC (Normal)" else "Ditemukan ${found.size} kode"

                                    val ffRpm = connectionManager.sendCommand("020C00", 200L)
                                    val ffSpd = connectionManager.sendCommand("020D00", 200L)
                                    val ffClt = connectionManager.sendCommand("020500", 200L)
                                    val ffMap = connectionManager.sendCommand("020B00", 200L)
                                    freezeFrame = ObdParser.parseFreezeFrame(ffRpm, ffSpd, ffClt, ffMap)
                                }
                            ) {
                                Text("Baca DTC & FF")
                            }
                            OutlinedButton(
                                enabled = isConnected,
                                onClick = {
                                    connectionManager.sendCommand("04", 350L)
                                    dtcList = emptyList()
                                    freezeFrame = null
                                    dtcStatus = "Perintah hapus DTC (04) dikirim"
                                }
                            ) {
                                Text("Hapus DTC")
                            }
                            Button(
                                onClick = {
                                    val res = TripLogExporter.appendTripCsv(
                                        context = context,
                                        vehicleLabel = "${selectedVehicle.brand} ${selectedVehicle.model}",
                                        odometerKm = selectedVehicle.initialOdometerKm + tripKm,
                                        tripDistanceKm = tripKm,
                                        estimatedFuelUsedLiters = fuelUsedTripLiters,
                                        dtcCodes = dtcList
                                    )
                                    logStatusText = res.fold(
                                        onSuccess = { path -> "Tersimpan: $path" },
                                        onFailure = { err -> "Gagal simpan CSV: ${err.message}" }
                                    )
                                }
                            ) {
                                Text("Export CSV")
                            }
                        }
                    }

                    if (dtcList.isNotEmpty()) {
                        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                            dtcList.forEach { code ->
                                Text(
                                    text = "$code — ${DtcDescriptions.lookup(code)}",
                                    fontSize = 14.sp,
                                    fontWeight = FontWeight.SemiBold,
                                    color = Color(0xFFFFAB40)
                                )
                            }
                        }
                    }

                    freezeFrame?.let { ff ->
                        Text(
                            text = "Freeze Frame — RPM: ${ff.rpm ?: "--"} | Speed: ${ff.speedKmh?.toInt() ?: "--"} km/j | Coolant: ${ff.coolantC ?: "--"}°C | MAP: ${ff.mapKpa ?: "--"} kPa",
                            fontSize = 13.sp,
                            color = Color(0xFF80DEEA)
                        )
                    }

                    Text(text = logStatusText, fontSize = 12.sp, color = Color(0xFFB0BEC5))
                }
            }
        }
    }
}

@Composable
fun MetricTile(title: String, value: String, unit: String, modifier: Modifier = Modifier) {
    Card(
        modifier = modifier.clickable {},
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface.copy(alpha = 0.92f)),
        shape = RoundedCornerShape(12.dp)
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            Text(title, fontSize = 11.sp, color = Color(0xFF90A4AE), fontWeight = FontWeight.Bold)
            Spacer(modifier = Modifier.height(4.dp))
            Text(value, fontSize = 24.sp, fontWeight = FontWeight.ExtraBold, color = Color(0xFF76FF03))
            Text(unit, fontSize = 12.sp, color = Color(0xFFCFD8DC))
        }
    }
}
