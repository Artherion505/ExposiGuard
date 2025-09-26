//
//  SensorManager.swift
//  ExposiGuard
//
//  Created on 26/09/2025
//  Copyright © 2025 ExposiGuard. All rights reserved.
//

import Foundation
import CoreMotion
import Combine

/// Main sensor manager that coordinates all sensor monitoring
class SensorManager: ObservableObject {
    @Published var isMonitoring = false
    @Published var magneticField: Double = 0.0
    @Published var bluetoothDevices: [BluetoothDevice] = []
    @Published var noiseLevel: Double = 0.0
    
    private let magnetometerManager = MagnetometerManager()
    private let bluetoothManager = BluetoothManager()
    private let noiseManager = NoiseManager()
    
    private var cancellables = Set<AnyCancellable>()
    
    init() {
        setupSubscriptions()
    }
    
    private func setupSubscriptions() {
        // Subscribe to magnetometer updates
        magnetometerManager.$magneticField
            .receive(on: DispatchQueue.main)
            .assign(to: \.magneticField, on: self)
            .store(in: &cancellables)
        
        // Subscribe to Bluetooth updates
        bluetoothManager.$discoveredDevices
            .receive(on: DispatchQueue.main)
            .assign(to: \.bluetoothDevices, on: self)
            .store(in: &cancellables)
        
        // Subscribe to noise updates
        noiseManager.$currentLevel
            .receive(on: DispatchQueue.main)
            .assign(to: \.noiseLevel, on: self)
            .store(in: &cancellables)
    }
    
    func startMonitoring() {
        guard !isMonitoring else { return }
        
        isMonitoring = true
        
        // Start all sensor monitoring
        magnetometerManager.startMonitoring()
        bluetoothManager.startScanning()
        noiseManager.startMonitoring()
    }
    
    func stopMonitoring() {
        guard isMonitoring else { return }
        
        isMonitoring = false
        
        // Stop all sensor monitoring
        magnetometerManager.stopMonitoring()
        bluetoothManager.stopScanning()
        noiseManager.stopMonitoring()
    }
    
    /// Get current exposure reading
    func getCurrentExposureReading() -> ExposureReading {
        return ExposureReading(
            timestamp: Date(),
            magneticField: magneticField,
            bluetoothDeviceCount: bluetoothDevices.count,
            noiseLevel: noiseLevel
        )
    }
}