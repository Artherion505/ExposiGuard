//
//  Models.swift
//  ExposiGuard
//
//  Created on 26/09/2025
//  Copyright © 2025 ExposiGuard. All rights reserved.
//

import Foundation

// MARK: - Core Data Models

/// Represents a single exposure reading
struct ExposureReading: Identifiable, Codable {
    let id: UUID
    let timestamp: Date
    let magneticField: Double // in microtesla (µT)
    let bluetoothDeviceCount: Int
    let noiseLevel: Double // in decibels (dB)
    
    init(
        id: UUID = UUID(),
        timestamp: Date = Date(),
        magneticField: Double,
        bluetoothDeviceCount: Int,
        noiseLevel: Double
    ) {
        self.id = id
        self.timestamp = timestamp
        self.magneticField = magneticField
        self.bluetoothDeviceCount = bluetoothDeviceCount
        self.noiseLevel = noiseLevel
    }
}

/// Daily statistics summary
struct DailyStats: Identifiable, Codable {
    let id: UUID
    let date: Date
    let averageMagneticField: Double
    let maxMagneticField: Double
    let averageNoiseLevel: Double
    let maxNoiseLevel: Double
    let totalBluetoothDevices: Int
    let readingCount: Int
    
    init(
        id: UUID = UUID(),
        date: Date,
        averageMagneticField: Double,
        maxMagneticField: Double,
        averageNoiseLevel: Double,
        maxNoiseLevel: Double,
        totalBluetoothDevices: Int,
        readingCount: Int
    ) {
        self.id = id
        self.date = date
        self.averageMagneticField = averageMagneticField
        self.maxMagneticField = maxMagneticField
        self.averageNoiseLevel = averageNoiseLevel
        self.maxNoiseLevel = maxNoiseLevel
        self.totalBluetoothDevices = totalBluetoothDevices
        self.readingCount = readingCount
    }
}

/// Bluetooth device information
struct BluetoothDevice: Identifiable, Codable {
    let id = UUID()
    let identifier: String
    let name: String
    let signalStrength: Int // RSSI value
    let discoveredAt: Date
    
    var signalQuality: SignalQuality {
        switch signalStrength {
        case -50...0:
            return .excellent
        case -60..<(-50):
            return .good
        case -70..<(-60):
            return .fair
        case -80..<(-70):
            return .poor
        default:
            return .veryPoor
        }
    }
}

enum SignalQuality: String, CaseIterable {
    case excellent = "Excellent"
    case good = "Good"
    case fair = "Fair"
    case poor = "Poor"
    case veryPoor = "Very Poor"
    
    var color: String {
        switch self {
        case .excellent: return "green"
        case .good: return "lightgreen"
        case .fair: return "yellow"
        case .poor: return "orange"
        case .veryPoor: return "red"
        }
    }
}

/// Health correlation data
struct HealthCorrelation: Codable {
    let heartRate: Double
    let sleepHours: Double
    let stressLevel: Double // 0.0 to 1.0
    
    var stressDescription: String {
        switch stressLevel {
        case 0.0..<0.3:
            return "Low stress"
        case 0.3..<0.6:
            return "Moderate stress"
        case 0.6..<0.8:
            return "High stress"
        default:
            return "Very high stress"
        }
    }
}

/// App settings
struct AppSettings: Codable {
    var monitoringEnabled: Bool = true
    var healthKitEnabled: Bool = false
    var notificationsEnabled: Bool = true
    var dataRetentionDays: Int = 30
    var magneticFieldThreshold: Double = 50.0 // µT
    var noiseThreshold: Double = 70.0 // dB
    var bluetoothThreshold: Int = 10 // device count
    
    static let `default` = AppSettings()
}