# ExposiGuard

ExposiGuard is a privacy-first Android app that empowers users to monitor and understand their daily exposure to environmental signals — all without internet access, ads, or trackers.
Main function is monitoring electromagnetic radiation (EMF) exposure.

### 📡 What does it detect?

- 🧲 Electromagnetic fields (EMF)
- 📶 WiFi networks
- 🔵 Bluetooth signals
- 📱 Cellular signals
- 🔊 Ambient noise
- 📱 Device SAR radiation

### ⚙️ Key Features

- 📈 Real-time EMF signal monitoring
- 📊 Exposure trend analysis
- 🚨 Configurable alerts
- 🌐 Intuitive Spanish interface
- 🧑‍⚕️ Optional health data integration
- 🔐 100% offline — no cloud, no tracking

FAQS
Does ExposiGuard send data to the cloud?
❌ No. ExposiGuard does not send any data to the cloud.
✅ Fully local: All functionality runs on your device
✅ No internet permission: The manifest includes no network access
✅ Analytics disabled: No telemetry is collected
✅ Privacy by design: Your data never leaves your device

🔒 How is my data stored and protected?
ExposiGuard uses secure local storage with Android’s built-in protection:
✅ Local database: Uses Room (SQLite) to store data only on your device
✅ No external servers: No remote communication or sync
✅ System-level protection: Android secures app data automatically
✅ Optional data: Health-related info is fully optional and stays local
Data location:
Database: /data/data/com.exposiguard.app/databases/
Config files: Stored only in the app’s internal storage

## Build

To build this project you will need:

1. **Android Studio** (Arctic Fox 2020.3.1 or higher)
2. **Android SDK** (API 35)
3. **JDK** (version 17 or higher)

### Option 1: Using Android Studio (Recommended)

1. Open the project in Android Studio
2. Wait for dependencies to sync
3. Go to `Build > Generate Signed Bundle / APK`
4. Select `APK` and follow the wizard
5. The APK will be generated in `app/build/outputs/apk/release/`

### Option 2: Using Gradle (Command Line)

```bash
# Configure SDK (if not in default path)
# Edit local.properties with your correct SDK path

# Build debug
./gradlew assembleDebug

# Build release (requires keystore)
./gradlew assembleRelease
```

### SDK Configuration

If build fails with "SDK location not found":
1. Install Android Studio if you don't have it
2. SDK is installed automatically with Android Studio
3. Typical path is: `C:\Users\[YourUser]\AppData\Local\Android\Sdk`
4. Edit `local.properties` if necessary

## License

This project is licensed under the MIT License - see the [LICENSE](LICENSE) file for details.



