$gradleVersion = "8.5"
$downloadUrl = "https://services.gradle.org/distributions/gradle-$gradleVersion-bin.zip"
$installDir = "C:\Gradle"
$zipPath = "$env:TEMP\gradle-$gradleVersion.zip"

Write-Host "=============================================="
Write-Host "Menginstal Gradle $gradleVersion Secara Otomatis"
Write-Host "=============================================="
Write-Host ""

# 1. Memeriksa apakah Java terinstal
if (-not (Get-Command java -ErrorAction SilentlyContinue)) {
    Write-Host "[PERINGATAN] Java (JDK) tidak terdeteksi di PATH."
    Write-Host "Pastikan Anda sudah menginstal JDK 17 terlebih dahulu."
    Write-Host "Tekan tombol apa saja untuk melanjutkan..."
    $null = [System.Console]::ReadKey($true)
} else {
    $javaVersion = & java -version 2>&1 | Out-String
    Write-Host "[SUKSES] Java terdeteksi."
}

# 2. Mengunduh Gradle Zip dari URL Resmi
Write-Host "Mengunduh Gradle dari $downloadUrl..."
try {
    # Set TLS 1.2 untuk download yang aman
    [Net.ServicePointManager]::SecurityProtocol = [Net.SecurityProtocolType]::Tls12
    Invoke-WebRequest -Uri $downloadUrl -OutFile $zipPath -UserAgent "Mozilla/5.0"
    Write-Host "[SUKSES] Unduhan selesai."
} catch {
    Write-Host "[ERROR] Gagal mengunduh Gradle: $_"
    Read-Host "Tekan Enter untuk keluar..."
    exit
}

# 3. Mengekstrak Gradle Zip
if (-not (Test-Path $installDir)) {
    try {
        New-Item -ItemType Directory -Path $installDir | Out-Null
    } catch {
        Write-Host "[ERROR] Gagal membuat direktori $installDir. Silakan jalankan script ini sebagai Administrator."
        Read-Host "Tekan Enter untuk keluar..."
        exit
    }
}

Write-Host "Mengekstrak Gradle ke $installDir..."
try {
    Expand-Archive -Path $zipPath -DestinationPath $installDir -Force
    Write-Host "[SUKSES] Ekstraksi selesai."
} catch {
    Write-Host "[ERROR] Gagal mengekstrak file zip: $_"
    Read-Host "Tekan Enter untuk keluar..."
    exit
}

# Mencari folder bin di dalam hasil ekstraksi
$extractedFolder = Get-ChildItem -Path $installDir -Directory | Where-Object { $_.Name -like "gradle-*" } | Select-Object -First 1
if (-not $extractedFolder) {
    Write-Host "[ERROR] Tidak dapat menemukan folder gradle di $installDir"
    Read-Host "Tekan Enter untuk keluar..."
    exit
}
$binPath = Join-Path $extractedFolder.FullName "bin"

# 4. Menambahkan Gradle ke User PATH Environment Variable
Write-Host "Menambahkan Gradle ke User PATH..."
$oldPath = [Environment]::GetEnvironmentVariable("Path", "User")
if ($oldPath -notlike "*$binPath*") {
    $newPath = "$oldPath;$binPath"
    [Environment]::SetEnvironmentVariable("Path", $newPath, "User")
    Write-Host "[SUKSES] Gradle berhasil ditambahkan ke PATH: $binPath"
} else {
    Write-Host "[INFO] Gradle sudah ada di dalam PATH."
}

# Membersihkan file zip sementara
if (Test-Path $zipPath) {
    Remove-Item -Path $zipPath -Force
}

Write-Host ""
Write-Host "=============================================="
Write-Host "Instalasi Gradle selesai!"
Write-Host "SILAKAN BUKA TERMINAL / POWERSHELL BARU."
Write-Host "Kemudian jalankan perintah berikut di folder proyek Anda:"
Write-Host ""
Write-Host "    gradle wrapper"
Write-Host ""
Write-Host "=============================================="
Read-Host "Tekan Enter untuk menutup..."
