# EndangScannerOBDII

Aplikasi Android Head Unit untuk membaca data **ELM327 OBD-II v2.1 (1801)** via Bluetooth Classic (SPP), dilengkapi:
- Pemilihan **Merek & Model Mobil** (Toyota Vios Gen 2, Avanza, Innova, Honda Jazz, Brio, Daihatsu Xenia, Suzuki Ertiga, Custom)
- Pembacaan **RPM, Kecepatan, Suhu Mesin, Tegangan Aki (`ATRV`)**
- Perhitungan **Isi Tangki Bensin (L & %)** (PID `012F` atau kalibrasi manual + konsumsi)
- Perhitungan **Odometer Total**, **Jarak Tempuh Trip**, dan **Konsumsi BBM (km/L)** dengan fallback otomatis (`015E` -> `MAF 0110` -> `MAP 010B`)
- Pembacaan & penghapusan **DTC (Mode 03 / 04)** dengan keterangan bahasa Indonesia + **Freeze Frame (Mode 02)**
- Ekspor log perjalanan ke **CSV** & penggantian **foto latar belakang** head unit

## Cara Build APK Langsung di GitHub (Tanpa Android Studio)
1. Ekstrak file `EndangScannerOBDII.zip`.
2. Buat repository baru di GitHub bernama `EndangScannerOBDII`.
3. Unggah seluruh isi folder `EndangScannerOBDII` (termasuk folder `.github/workflows/build-apk.yml`) ke repository tersebut.
4. Buka tab **Actions** di GitHub, jalankan **Build EndangScannerOBDII APK**, lalu unduh file `EndangScannerOBDII-APK` (`app-debug.apk`).
