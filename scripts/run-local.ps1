<#
  Start ShopSphere locally on Windows.

  What it does:
    1. Points JAVA_HOME at a JDK 21 (Maven's default JAVA_HOME here is 17,
       which fails with "invalid target release: 21").
    2. Loads KEY=VALUE pairs from the project .env into the current process.
       Variables already set in the shell win, so you can override inline, e.g.
         $env:DB_PASSWORD="my-real-mysql-password"; .\scripts\run-local.ps1
    3. Runs "mvn spring-boot:run".

  Usage:
    .\scripts\run-local.ps1           # start the app
    .\scripts\run-local.ps1 -Check    # print what would be loaded, then exit
#>
param([switch]$Check)

$ErrorActionPreference = "Stop"
$root = Split-Path -Parent $PSScriptRoot
Set-Location $root

# ---------- 1) JDK 21 ----------
function Test-Jdk21([string]$jdkPath) {
    if (-not $jdkPath) { return $false }
    if (-not (Test-Path (Join-Path $jdkPath "bin\java.exe"))) { return $false }
    # read the "release" file instead of running java -version:
    # a native command writing to stderr would abort the script under ErrorActionPreference=Stop
    $rel = Join-Path $jdkPath "release"
    if (Test-Path $rel) {
        $hit = Select-String -Path $rel -Pattern '^JAVA_VERSION="([^"]+)"' | Select-Object -First 1
        if ($hit) { return $hit.Matches[0].Groups[1].Value.StartsWith("21") }
    }
    return ($jdkPath -match 'jdk-?21')
}

if (-not (Test-Jdk21 $env:JAVA_HOME)) {
    $found = $null
    foreach ($pattern in @("C:\Program Files\Microsoft\jdk-21*",
                           "C:\Program Files\Java\jdk-21*",
                           "C:\Program Files\Eclipse Adoptium\jdk-21*",
                           "$env:USERPROFILE\.jdks\*21*")) {
        $hit = Get-Item $pattern -ErrorAction SilentlyContinue | Select-Object -First 1
        if ($hit -and (Test-Jdk21 $hit.FullName)) { $found = $hit.FullName; break }
    }
    if ($found) {
        $env:JAVA_HOME = $found
        Write-Host "JAVA_HOME -> $found" -ForegroundColor Cyan
    } else {
        Write-Warning "JDK 21 not found. Install it or set JAVA_HOME manually, otherwise Maven fails with 'invalid target release: 21'."
    }
}

# ---------- 2) .env ----------
$envFile = Join-Path $root ".env"
$loaded = @()
if (Test-Path $envFile) {
    foreach ($raw in (Get-Content $envFile -Encoding UTF8)) {
        $line = $raw.Trim()
        if (-not $line -or $line.StartsWith("#") -or -not $line.Contains("=")) { continue }
        $idx = $line.IndexOf("=")
        $key = $line.Substring(0, $idx).Trim()
        $val = $line.Substring($idx + 1).Trim()
        if (-not (Get-Item "Env:$key" -ErrorAction SilentlyContinue)) {
            Set-Item -Path "Env:$key" -Value $val
            $loaded += $key
        } else {
            $loaded += "$key (kept from shell)"
        }
    }
} else {
    Write-Warning ".env not found. Copy .env.example to .env first."
}

# ---------- 3) summary ----------
Write-Host ""
Write-Host "=== configuration ===" -ForegroundColor Cyan
Write-Host ("DB_URL        : " + $(if ($env:DB_URL) { $env:DB_URL } else { "jdbc:mysql://localhost:3306/shopsphere (default)" }))
Write-Host ("DB_USERNAME   : " + $(if ($env:DB_USERNAME) { $env:DB_USERNAME } else { "root (default)" }))
$dbPwd = if ($env:DB_PASSWORD) { "*" * $env:DB_PASSWORD.Length } else { "(empty)" }
Write-Host ("DB_PASSWORD   : " + $dbPwd)
Write-Host ("MAIL_USERNAME : " + $(if ($env:MAIL_USERNAME) { $env:MAIL_USERNAME } else { "(empty -> mails will be SKIPPED)" }))
$mailPwd = if ($env:MAIL_PASSWORD) { "*" * $env:MAIL_PASSWORD.Length } else { "(empty)" }
Write-Host ("MAIL_PASSWORD : " + $mailPwd)
Write-Host ("APP_BASE_URL  : " + $(if ($env:APP_BASE_URL) { $env:APP_BASE_URL } else { "http://localhost:8080 (default)" }))
Write-Host ""

if ($Check) {
    Write-Host "Check only, nothing started." -ForegroundColor Yellow
    return
}

if ($env:MAIL_USERNAME -and $env:MAIL_PASSWORD) {
    Write-Host "Mail: enabled, real SMTP will be used." -ForegroundColor Green
} else {
    Write-Host "Mail: not configured, every mail will be recorded as SKIPPED." -ForegroundColor Yellow
}
Write-Host "Starting... (http://localhost:8080 once you see 'Started ShopSphereApplication')" -ForegroundColor Green
Write-Host ""

mvn spring-boot:run
