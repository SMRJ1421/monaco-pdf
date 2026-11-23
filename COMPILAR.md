# Compilar APK
1. `npx cap sync android`
2. En `android\`: `$env:JAVA_TOOL_OPTIONS="-Djdk.net.unixdomain.tmpdir=C:\jtmp"; .\gradlew.bat assembleDebug`
3. APK en `android\app\build\outputs\apk\debug\app-debug.apk`
