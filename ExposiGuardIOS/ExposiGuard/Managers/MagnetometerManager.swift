//
//  MagnetometerManager.swift
//  ExposiGuard
//
//  Created on 26/09/2025
//  Copyright © 2025 ExposiGuard. All rights reserved.
//

import Foundation
import CoreMotion
import Combine

/// Manages magnetometer sensor for magnetic field detection
class MagnetometerManager: ObservableObject {
    @Published var magneticField: Double = 0.0
    @Published var isActive: Bool = false
    
    private let motionManager = CMMotionManager()
    private let updateInterval: TimeInterval = 0.1 // 10 Hz
    
    private var operationQueue = OperationQueue()
    
    init() {
        operationQueue.maxConcurrentOperationCount = 1
        operationQueue.name = "MagnetometerQueue"
    }
    
    func startMonitoring() {
        guard motionManager.isMagnetometerAvailable else {
            print("⚠️ Magnetometer not available on this device")
            return
        }
        
        guard !motionManager.isMagnetometerActive else {
            print("⚠️ Magnetometer already active")
            return
        }
        
        motionManager.magnetometerUpdateInterval = updateInterval
        
        motionManager.startMagnetometerUpdates(to: operationQueue) { [weak self] (data, error) in
            guard let self = self, let magnetometerData = data else {
                if let error = error {
                    print("❌ Magnetometer error: \(error)")
                }
                return
            }
            
            // Calculate magnetic field magnitude in microtesla (µT)
            let x = magnetometerData.magneticField.x
            let y = magnetometerData.magneticField.y
            let z = magnetometerData.magneticField.z
            
            let magnitude = sqrt(x * x + y * y + z * z) * 1_000_000 // Convert to µT
            
            DispatchQueue.main.async {
                self.magneticField = magnitude
                self.isActive = true
            }
        }
        
        print("✅ Magnetometer monitoring started")
    }
    
    func stopMonitoring() {
        guard motionManager.isMagnetometerActive else { return }
        
        motionManager.stopMagnetometerUpdates()
        
        DispatchQueue.main.async {
            self.isActive = false
            self.magneticField = 0.0
        }
        
        print("⏹️ Magnetometer monitoring stopped")
    }
    
    /// Get current magnetic field strength classification
    func getFieldStrength() -> FieldStrength {
        switch magneticField {
        case 0..<25:
            return .low
        case 25..<50:
            return .moderate
        case 50..<100:
            return .high
        default:
            return .veryHigh
        }
    }
}

enum FieldStrength: String, CaseIterable {
    case low = "Low"
    case moderate = "Moderate" 
    case high = "High"
    case veryHigh = "Very High"
    
    var color: String {
        switch self {
        case .low: return "green"
        case .moderate: return "yellow"
        case .high: return "orange"
        case .veryHigh: return "red"
        }
    }
    
    var description: String {
        switch self {
        case .low: return "Normal background levels"
        case .moderate: return "Slightly elevated"
        case .high: return "Elevated levels detected"
        case .veryHigh: return "High levels detected"
        }
    }
}