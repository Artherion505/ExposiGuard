//
//  BluetoothManager.swift
//  ExposiGuard
//
//  Created on 26/09/2025
//  Copyright © 2025 ExposiGuard. All rights reserved.
//

import Foundation
import CoreBluetooth
import Combine

/// Manages Bluetooth scanning for nearby device detection
class BluetoothManager: NSObject, ObservableObject {
    @Published var discoveredDevices: [BluetoothDevice] = []
    @Published var isScanning: Bool = false
    @Published var bluetoothState: CBManagerState = .unknown
    
    private var centralManager: CBCentralManager!
    private var scanTimer: Timer?
    private let scanDuration: TimeInterval = 10.0 // Scan for 10 seconds
    private let scanInterval: TimeInterval = 30.0 // Scan every 30 seconds
    
    override init() {
        super.init()
        centralManager = CBCentralManager(delegate: self, queue: nil)
    }
    
    func startScanning() {
        guard centralManager.state == .poweredOn else {
            print("⚠️ Bluetooth not available or powered off")
            return
        }
        
        guard !isScanning else {
            print("⚠️ Already scanning for Bluetooth devices")
            return
        }
        
        // Clear previous results
        discoveredDevices.removeAll()
        
        // Start scanning for all devices
        centralManager.scanForPeripherals(withServices: nil, options: [
            CBCentralManagerScanOptionAllowDuplicatesKey: false
        ])
        
        isScanning = true
        
        // Set up timer to stop scanning after duration
        scanTimer = Timer.scheduledTimer(withTimeInterval: scanDuration, repeats: false) { [weak self] _ in
            self?.stopCurrentScan()
            
            // Schedule next scan
            DispatchQueue.main.asyncAfter(deadline: .now() + (self?.scanInterval ?? 30.0) - (self?.scanDuration ?? 10.0)) {
                self?.startScanning()
            }
        }
        
        print("✅ Bluetooth scanning started")
    }
    
    func stopScanning() {
        stopCurrentScan()
        scanTimer?.invalidate()
        scanTimer = nil
        print("⏹️ Bluetooth scanning stopped completely")
    }
    
    private func stopCurrentScan() {
        guard isScanning else { return }
        
        centralManager.stopScan()
        isScanning = false
        print("⏸️ Current Bluetooth scan stopped")
    }
    
    /// Get device count by signal strength
    func getDevicesBySignalStrength() -> (strong: Int, medium: Int, weak: Int) {
        var strong = 0, medium = 0, weak = 0
        
        for device in discoveredDevices {
            switch device.signalStrength {
            case -50...0:
                strong += 1
            case -80..<(-50):
                medium += 1
            default:
                weak += 1
            }
        }
        
        return (strong, medium, weak)
    }
}

// MARK: - CBCentralManagerDelegate
extension BluetoothManager: CBCentralManagerDelegate {
    func centralManagerDidUpdateState(_ central: CBCentralManager) {
        bluetoothState = central.state
        
        switch central.state {
        case .poweredOn:
            print("✅ Bluetooth powered on")
        case .poweredOff:
            print("❌ Bluetooth powered off")
            stopScanning()
        case .unauthorized:
            print("❌ Bluetooth unauthorized")
        case .unsupported:
            print("❌ Bluetooth unsupported")
        default:
            print("⚠️ Bluetooth state: \(central.state.rawValue)")
        }
    }
    
    func centralManager(_ central: CBCentralManager, didDiscover peripheral: CBPeripheral, advertisementData: [String : Any], rssi RSSI: NSNumber) {
        
        let device = BluetoothDevice(
            identifier: peripheral.identifier.uuidString,
            name: peripheral.name ?? "Unknown Device",
            signalStrength: RSSI.intValue,
            discoveredAt: Date()
        )
        
        // Avoid duplicates
        if !discoveredDevices.contains(where: { $0.identifier == device.identifier }) {
            DispatchQueue.main.async {
                self.discoveredDevices.append(device)
            }
        }
    }
}