# mlbb-auto-cooking-bot

Fase 1: aplikasi Android (Kotlin) yang menjadikan HP Android sebagai **mouse Bluetooth HID** untuk iPhone tanpa jailbreak. Hanya diagnostik; belum ada logika memasak.

- Controller: Redmi Note 12 Pro 4G (Android 28+)
- Target: iPhone (mouse lewat AssistiveTouch)
- Tanpa root, PC, API berbayar, atau server eksternal
- Memakai API resmi [`BluetoothHidDevice`](https://developer.android.com/reference/android/bluetooth/BluetoothHidDevice)

## Status

Kompilasi dan unit test dijalankan di GitHub Actions. **Belum diuji di perangkat fisik.** Hasil uji fisik harus dicatat sendiri memakai daftar di bawah.

## Mengunduh APK

1. Buka tab **Actions** repo ini, pilih run **Build Debug APK** yang hijau terbaru.
2. Di bagian **Artifacts**, unduh `mlbb-hid-phase1-debug-apk` (berkas zip berisi `app-debug.apk`).
3. Ekstrak zip, pindahkan `app-debug.apk` ke Redmi.

## Instalasi di Redmi

1. Buka `app-debug.apk` lewat File Manager.
2. Izinkan "Instal dari sumber ini" bila diminta, lalu instal.
3. Buka aplikasi **MLBB HID Test**, izinkan Bluetooth ("Perangkat sekitar") dan notifikasi.

## Persiapan iPhone

1. Pengaturan > Aksesibilitas > Sentuh > **AssistiveTouch** > aktifkan.
2. Di menu AssistiveTouch yang sama, buka **Perangkat Penunjuk** (Pointer Devices) untuk melihat perangkat mouse setelah tersambung.

## Pengujian fisik

1. Di Redmi tekan **Connect**. Konfirmasi dialog "terlihat oleh perangkat lain" (120 detik).
2. Di iPhone: Pengaturan > Bluetooth, pilih **MLBB Mouse**, pasangkan.
3. Pastikan status di aplikasi berubah menjadi `Terhubung: ...` dan log menampilkan `TERHUBUNG`.
4. Jika iPhone sudah pernah dipasangkan: pilih iPhone di dropdown, lalu **Connect**.
5. Buka layar Home iPhone, lalu uji berurutan:

| Uji | Hasil yang diharapkan | Lulus? |
|---|---|---|
| Pairing + status Terhubung | Status & log benar | |
| **Move** | Kursor bergerak membentuk kotak ±150 unit | |
| **Test Click** | Satu ketukan di posisi kursor | |
| **Test Drag** | Tahan, geser kanan lalu bawah, lepas | |
| **Press** lalu **Move** lalu **Release** | Drag manual | |
| **EMERGENCY STOP** saat Test Drag berjalan | Gerakan berhenti, tombol terlepas | |
| Matikan Bluetooth saat terhubung | Log mencatat putus, status Mati | |
| **Disconnect** | Status Mati, notifikasi hilang | |
| Aplikasi di background 5 menit | Sesi tetap hidup (notifikasi ada) | |

Tombol **STOP** juga ada di notifikasi.

## Keterbatasan yang diketahui

- Ini mouse **relatif**: posisi kursor tidak absolut. Untuk fase berikutnya perlu strategi "homing" (geser ke pojok dulu).
- Sebagian ROM (MIUI/HyperOS) mungkin membatasi profil HID Device. Belum diverifikasi di Redmi Note 12 Pro 4G; jika `registerApp -> false` atau tidak pernah `TERHUBUNG`, laporkan isi log.
- Kecepatan kursor di iPhone mengikuti pengaturan Tracking Speed AssistiveTouch.

## Build lokal

```
./gradlew testDebugUnitTest assembleDebug
```

Perlu JDK 17 dan Android SDK 34. APK ada di `app/build/outputs/apk/debug/`.
