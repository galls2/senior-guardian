# Senior Guardian

Android app that detects incoming phone calls, scores them for scam risk (known caller / blacklist checks), and responds with logging, SMS alerts, an on-screen warning, an alarm sound, and — for high-risk calls — auto-merging a trusted number into a conference call.

## Requirements

- JDK (Android Studio's bundled JBR works): `export JAVA_HOME="/path/to/Android Studio/jbr"`
- Android SDK platform-tools (`adb`) on your `PATH`
- A device or emulator with USB debugging enabled, connected and visible via `adb devices`

## Build, install & launch

PowerShell (Windows):

```powershell
$env:JAVA_HOME = "C:\Program Files\Android\Android Studio\jbr"
.\gradlew.bat installDebug
$adb = if ($env:ANDROID_HOME) { "$env:ANDROID_HOME\platform-tools\adb.exe" } else { "$env:LOCALAPPDATA\Android\Sdk\platform-tools\adb.exe" }
& $adb shell am start -n com.seniorguardian.app/.MainActivity
```

macOS/Linux:

```bash
export JAVA_HOME="/path/to/Android Studio/jbr"
./gradlew installDebug
adb shell am start -n com.seniorguardian.app/.MainActivity
```

On first launch, grant the requested permissions and set Senior Guardian as your **default phone/dialer app** (required to intercept calls and merge conferences).

## Viewing logs

There is no separate log server — logs go to `adb logcat` on the connected device:

```bash
adb logcat -c                 # clear old logs
adb logcat | grep -i guardian  # filter to this app's tags
```
