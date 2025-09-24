# ExposiGuard

Android application for monitoring electromagnetic radiation (EMF) exposure.

[![ko-fi](https://ko-fi.com/img/githubbutton_sm.svg)](https://ko-fi.com/Q5Q61JMR4M)

## Description

ExposiGuard is an application that helps users monitor and understand their daily exposure to different sources of electromagnetic radiation, including:

- Electromagnetic fields (EMF)
- WiFi networks
- Bluetooth signals
- Cellular signals
- Ambient noise
- Device SAR radiation

## Features

- Real-time EMF signal monitoring
- Exposure trend analysis
- Configurable alerts
- Intuitive Spanish interface
- Integrated health data (optional)

## License

This project is licensed under the MIT License - see the [LICENSE](LICENSE) file for details.

## Copyright

Copyright (c) 2025 David L. All rights reserved.

The authorship and intellectual property of this code belongs exclusively to David L. The code is available under MIT license for personal and non-commercial use, always maintaining attribution to the original author.

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

## F-Droid Submission

ExposiGuard is available on F-Droid, the free and open source Android app repository.

### Automated Submission Script

For easy F-Droid submission, use the provided PowerShell script:

```powershell
# Run the submission script (replace YOUR_USERNAME with your GitLab username)
.\submit-to-fdroid.ps1 -GitLabUsername "YOUR_USERNAME"

# Or run interactively
.\submit-to-fdroid.ps1
```

The script will:

1. Clone your F-Droid data fork
2. Create a feature branch
3. Copy the metadata file
4. Commit and push changes
5. Provide instructions for creating the merge request

### Manual Submission

If you prefer manual submission:

1. Fork `https://gitlab.com/fdroid/fdroiddata`
2. Clone your fork: `git clone https://gitlab.com/YOUR_USERNAME/fdroiddata.git`
3. Create branch: `git checkout -b exposiguard-submission`
4. Copy metadata: `cp metadata/com.exposiguard.app.yml fdroiddata/metadata/`
5. Commit: `git add metadata/com.exposiguard.app.yml && git commit -m "Add ExposiGuard"`
6. Push: `git push origin exposiguard-submission`
7. Create merge request at: `https://gitlab.com/YOUR_USERNAME/fdroiddata/-/merge_requests/new`

### Metadata File

The F-Droid metadata file is located at `metadata/com.exposiguard.app.yml` and contains:

- App information and description
- Build configuration
- Repository links
- Version information

For more information, see [F-DROID_SUBMISSION.md](F-DROID_SUBMISSION.md).

## Contribution

This project is open source but does not accept external contributions. Any modifications must be made by the original author.

