# ExposiGuard iOS

**Privacy-first iOS app for monitoring electromagnetic fields, Bluetooth exposure, and ambient noise — 100% offline, open-source, and tracker-free.**

## 🍎 iOS Version Features

### 📡 Supported Sensors
- **🧲 Magnetometer**: Detects magnetic fields using CoreMotion framework
- **🔵 Bluetooth Scanner**: Discovers nearby Bluetooth devices using Core Bluetooth
- **🔊 Noise Monitor**: Measures ambient sound levels using AVAudioEngine
- **❤️ HealthKit Integration**: Correlates exposure data with health metrics

### ⚙️ iOS-Specific Capabilities
- **SwiftUI Interface**: Modern, native iOS user interface
- **HealthKit Sync**: Optional integration with Apple Health app
- **Background Monitoring**: Limited background processing (iOS restrictions apply)
- **Core Data Storage**: Efficient local data persistence
- **Native Notifications**: iOS-native alert system

## 🚫 iOS Limitations

Due to iOS security and privacy restrictions, some features from the Android version are not available:

### Not Available on iOS:
- **WiFi Network Scanning**: iOS restricts access to nearby WiFi networks for privacy
- **Cellular Signal Info**: No access to cellular tower information or signal strength
- **SAR Calculations**: Device-specific SAR values are not accessible through public APIs
- **EMF Hardware Access**: No direct access to electromagnetic field sensors (only magnetometer)

### Limited Functionality:
- **Background Monitoring**: iOS severely limits background app execution
- **Bluetooth Scanning**: Periodic scanning only, not continuous
- **Microphone Access**: Requires explicit user permission and shows indicator

## 📋 Requirements

- **iOS 15.0+**
- **Xcode 14.0+**
- **Swift 5.7+**
- **iPhone/iPad with magnetometer, Bluetooth, and microphone**

## 🛠️ Setup Instructions

### 1. Open in Xcode
```bash
cd ExposiGuardIOS
open ExposiGuard.xcodeproj
```

### 2. Configure Bundle Identifier
1. Select the project in Xcode
2. Update the Bundle Identifier to your own (e.g., `com.yourname.exposiguard`)
3. Select your development team

### 3. Required Permissions
The app requires the following permissions (already configured in Info.plist):
- **Bluetooth**: To scan for nearby Bluetooth devices
- **Microphone**: To measure ambient noise levels
- **Motion**: To access magnetometer data
- **HealthKit** (Optional): To sync exposure data with Health app

### 4. Build and Run
1. Select your target device or simulator
2. Build and run the project (Cmd+R)

## 🏗️ Architecture

### Core Components
- **SensorManager**: Coordinates all sensor monitoring
- **MagnetometerManager**: Handles magnetic field detection via CoreMotion
- **BluetoothManager**: Manages Bluetooth device scanning via Core Bluetooth
- **NoiseManager**: Monitors ambient sound levels via AVAudioEngine
- **HealthManager**: Handles HealthKit integration for health correlation
- **DataManager**: Manages Core Data persistence and data export

### SwiftUI Views
- **HomeView**: Main dashboard with real-time readings
- **SensorView**: Detailed sensor information and device lists
- **TrendsView**: Historical data visualization and trends
- **SettingsView**: App configuration and data management

## 📊 Data Models

### ExposureReading
```swift
struct ExposureReading {
    let timestamp: Date
    let magneticField: Double    // µT (microtesla)
    let bluetoothDeviceCount: Int
    let noiseLevel: Double       // dB (decibels)
}
```

### HealthCorrelation
```swift
struct HealthCorrelation {
    let heartRate: Double
    let sleepHours: Double
    let stressLevel: Double     // 0.0 to 1.0
}
```

## 🔐 Privacy & Security

### Local Data Only
- ✅ All data stored locally using Core Data
- ✅ No cloud synchronization or external servers
- ✅ No analytics or telemetry
- ✅ Optional HealthKit integration (user controlled)

### Permissions
- 🔵 **Bluetooth**: Only for device discovery, no data access
- 🎤 **Microphone**: Only for noise level measurement, no recording
- 📱 **Motion**: Only for magnetometer readings
- ❤️ **Health**: Optional, user-controlled integration

## 🚀 Building for Distribution

### App Store Distribution
1. Configure signing certificates
2. Update version numbers
3. Test on physical devices
4. Submit to App Store Connect

### TestFlight Beta
1. Archive the app in Xcode
2. Upload to App Store Connect
3. Distribute via TestFlight

## 🔧 Development Notes

### Sensor Accuracy
- **Magnetometer**: Accuracy depends on device calibration and environmental interference
- **Bluetooth**: iOS limits scanning frequency to preserve battery
- **Noise**: Requires microphone permission and shows system indicator

### Background Limitations
- iOS severely restricts background execution
- Continuous monitoring only works when app is active
- Background App Refresh provides limited background processing

### Testing
- Test on physical devices (sensors not available in simulator)
- Verify permissions are properly requested
- Test HealthKit integration with real health data

## 📱 Supported Devices

### iPhone
- iPhone 6s and later (iOS 15+)
- All models with magnetometer support

### iPad
- iPad (5th generation) and later
- iPad Air 2 and later
- iPad Pro (all models)
- iPad mini 4 and later

## 🆘 Troubleshooting

### Common Issues
1. **Sensors not working**: Ensure app has proper permissions
2. **Bluetooth not scanning**: Check Bluetooth permissions and device state
3. **Noise not measuring**: Verify microphone permission granted
4. **HealthKit not connecting**: Check Health app permissions

### Debug Tips
- Use Xcode console to monitor log messages
- Test permissions on physical device
- Verify Core Data model matches entity definitions

## 📄 License

This iOS version maintains the same open-source license as the Android version. See LICENSE file for details.

## 🤝 Contributing

Contributions welcome! Please ensure:
- Code follows Swift style guidelines
- New features include proper error handling
- UI changes support both iPhone and iPad
- Privacy requirements are maintained

---

**Note**: This iOS version provides core functionality adapted to iOS capabilities and restrictions. Some features from the Android version are not available due to platform limitations.