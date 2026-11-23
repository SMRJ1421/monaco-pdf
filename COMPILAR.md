# Compilar y publicar

## Publicar una versión nueva (lo normal)
`.\publicar.ps1 1.0.1 "Qué cambió"`
Sube la versión, compila, hace commit + push y crea la Release en
https://github.com/SMRJ1421/monaco-pdf/releases con el APK.
La app avisa sola al abrirse si hay una versión más nueva.

## Solo compilar (sin publicar)
1. `npx cap sync android`
2. En `android\`: `$env:JAVA_TOOL_OPTIONS="-Djdk.net.unixdomain.tmpdir=C:\jtmp"; .\gradlew.bat assembleDebug`
3. APK en `android\app\build\outputs\apk\debug\app-debug.apk`
