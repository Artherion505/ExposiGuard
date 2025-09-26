//
//  SensorView.swift
//  ExposiGuard
//
//  Created on 26/09/2025
//  Copyright © 2025 ExposiGuard. All rights reserved.
//

import SwiftUI

struct SensorView: View {
    @EnvironmentObject var sensorManager: SensorManager
    
    var body: some View {
        NavigationView {
            List {
                Section("Magnetic Field Sensor") {
                    SensorDetailRow(
                        title: "Current Reading",
                        value: String(format: "%.2f µT", sensorManager.magneticField),
                        status: getMagneticFieldStatus(),
                        icon: "dot.radiowaves.left.and.right"
                    )
                }
                
                Section("Bluetooth Scanner") {
                    SensorDetailRow(
                        title: "Devices Found",
                        value: "\(sensorManager.bluetoothDevices.count)",
                        status: getBluetoothStatus(),
                        icon: "bluetooth"
                    )
                    
                    ForEach(sensorManager.bluetoothDevices.prefix(5)) { device in
                        BluetoothDeviceRow(device: device)
                    }
                }
                
                Section("Noise Monitor") {
                    SensorDetailRow(
                        title: "Current Level",
                        value: String(format: "%.0f dB", sensorManager.noiseLevel),
                        status: getNoiseStatus(),
                        icon: "speaker.wave.3"
                    )
                }
            }
            .navigationTitle("Sensors")
            .refreshable {
                // Refresh sensor readings
                if !sensorManager.isMonitoring {
                    sensorManager.startMonitoring()
                    DispatchQueue.main.asyncAfter(deadline: .now() + 2) {
                        sensorManager.stopMonitoring()
                    }
                }
            }
        }
    }
    
    private func getMagneticFieldStatus() -> String {
        switch sensorManager.magneticField {
        case 0..<25: return "Low"
        case 25..<50: return "Moderate"
        case 50..<100: return "High"
        default: return "Very High"
        }
    }
    
    private func getBluetoothStatus() -> String {
        switch sensorManager.bluetoothDevices.count {
        case 0...2: return "Low"
        case 3...6: return "Moderate"
        case 7...10: return "High"
        default: return "Very High"
        }
    }
    
    private func getNoiseStatus() -> String {
        switch sensorManager.noiseLevel {
        case 0..<40: return "Quiet"
        case 40..<60: return "Moderate"
        case 60..<80: return "Loud"
        default: return "Very Loud"
        }
    }
}

struct SensorDetailRow: View {
    let title: String
    let value: String
    let status: String
    let icon: String
    
    var body: some View {
        HStack {
            Image(systemName: icon)
                .foregroundColor(.blue)
                .frame(width: 24)
            
            VStack(alignment: .leading, spacing: 2) {
                Text(title)
                    .font(.headline)
                
                Text(status)
                    .font(.caption)
                    .foregroundColor(.secondary)
            }
            
            Spacer()
            
            Text(value)
                .font(.title2)
                .fontWeight(.semibold)
        }
        .padding(.vertical, 4)
    }
}

struct BluetoothDeviceRow: View {
    let device: BluetoothDevice
    
    var body: some View {
        HStack {
            Image(systemName: "dot.radiowaves.left.and.right")
                .foregroundColor(Color(device.signalQuality.color))
                .frame(width: 20)
            
            VStack(alignment: .leading, spacing: 2) {
                Text(device.name)
                    .font(.subheadline)
                
                Text(device.signalQuality.rawValue)
                    .font(.caption)
                    .foregroundColor(.secondary)
            }
            
            Spacer()
            
            Text("\(device.signalStrength) dBm")
                .font(.caption)
                .foregroundColor(.secondary)
        }
        .padding(.leading, 20)
    }
}