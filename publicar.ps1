# Publica una versión nueva de Monaco PDF en GitHub.
# Uso: .\publicar.ps1 1.0.1 "Qué cambió"
param([Parameter(Mandatory)][string]$Version, [string]$Notas = "Versión $Version")
$ErrorActionPreference = 'Stop'
$r = $PSScriptRoot
Set-Location $r
if ($Version -notmatch '^\d+\.\d+\.\d+$') { throw "Versión inválida: usa el formato 1.2.3" }

# 1. Poner la versión en la app y en Android
"window.APP_VERSION = '$Version';" | Set-Content "$r\www\version.js" -Encoding ascii
$p = Get-Content "$r\package.json" -Raw
($p -replace '"version": "[^"]*"', "`"version`": `"$Version`"") | Set-Content "$r\package.json" -Encoding ascii -NoNewline
$g = Get-Content "$r\android\app\build.gradle" -Raw
$code = [int]([regex]::Match($g, 'versionCode (\d+)').Groups[1].Value) + 1
$g = $g -replace 'versionCode \d+', "versionCode $code" -replace 'versionName "[^"]*"', "versionName `"$Version`""
[IO.File]::WriteAllText("$r\android\app\build.gradle", $g)

# 2. Compilar
npx cap sync android
$env:JAVA_TOOL_OPTIONS = "-Djdk.net.unixdomain.tmpdir=C:\jtmp"
& "$r\android\gradlew.bat" -p "$r\android" assembleDebug -q
if ($LASTEXITCODE) { throw "Falló la compilación" }
Copy-Item "$r\android\app\build\outputs\apk\debug\app-debug.apk" "$r\MonacoPDF.apk" -Force

# 3. Subir código y publicar la Release con el APK
git add -A
git commit -m "Versión $Version"
git push
gh release create "v$Version" "$r\MonacoPDF.apk" --title "Monaco PDF $Version" --notes $Notas
Write-Host "Publicada v$Version" -ForegroundColor Green
