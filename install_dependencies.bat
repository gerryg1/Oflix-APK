@echo off
echo ===================================================
echo Menginstal Dependensi Pengembangan Android (Oflix)
echo ===================================================
echo.

echo Memeriksa hak akses Administrator...
net session >nul 2>&1
if %errorLevel% == 0 (
    echo Hak akses Administrator terkonfirmasi.
) else (
    echo [PERINGATAN] Silakan jalankan script ini sebagai Administrator.
    echo Klik kanan file ini lalu pilih "Run as Administrator".
    pause
    exit /b
)

echo.
echo 1. Menginstal Java Development Kit (OpenJDK 17)...
winget install --id Microsoft.OpenJDK.17 --silent --accept-package-agreements --accept-source-agreements
if %errorLevel% neq 0 (
    echo [ERROR] Gagal menginstal OpenJDK 17. Silakan periksa koneksi internet atau pasang secara manual.
) else (
    echo [SUKSES] OpenJDK 17 berhasil diinstal.
)

echo.
echo 2. Menginstal Gradle...
winget install --id Gradle.Gradle --silent --accept-package-agreements --accept-source-agreements
if %errorLevel% neq 0 (
    echo [ERROR] Gagal menginstal Gradle. Silakan periksa koneksi internet atau pasang secara manual.
) else (
    echo [SUKSES] Gradle berhasil diinstal.
)

echo.
echo ===================================================
echo Instalasi selesai!
echo SILAKAN RESTART TERMINAL / CMD / POWERSHELL ANDA.
echo Setelah terminal dibuka kembali, Anda bisa menjalankan:
echo.
echo     gradle wrapper
echo.
echo ===================================================
pause
