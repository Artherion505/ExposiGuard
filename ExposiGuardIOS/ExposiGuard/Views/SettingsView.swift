//
//  SettingsView.swift
//  ExposiGuard
//
//  Created on 26/09/2025
//  Copyright © 2025 ExposiGuard. All rights reserved.
//

import SwiftUI

struct SettingsView: View {
    @EnvironmentObject var sensorManager: SensorManager
    @EnvironmentObject var healthManager: HealthManager
    @EnvironmentObject var dataManager: DataManager
    
    @State private var settings = AppSettings.default
    @State private var showingHealthPermissions = false
    @State private var showingDataExport = false
    
    var body: some View {
        NavigationView {
            Form {
                // Monitoring Settings
                Section("Monitoring") {
                    Toggle("Enable Monitoring", isOn: $settings.monitoringEnabled)
                        .onChange(of: settings.monitoringEnabled) { enabled in
                            if enabled {
                                sensorManager.startMonitoring()
                            } else {
                                sensorManager.stopMonitoring()
                            }
                        }
                    
                    Toggle("Enable Notifications", isOn: $settings.notificationsEnabled)
                }
                
                // Thresholds
                Section("Alert Thresholds") {
                    VStack(alignment: .leading, spacing: 8) {
                        Text("Magnetic Field: \(String(format: "%.0f", settings.magneticFieldThreshold)) µT")
                        Slider(value: $settings.magneticFieldThreshold, in: 10...200, step: 5)
                    }
                    
                    VStack(alignment: .leading, spacing: 8) {
                        Text("Noise Level: \(String(format: "%.0f", settings.noiseThreshold)) dB")
                        Slider(value: $settings.noiseThreshold, in: 40...100, step: 5)
                    }
                    
                    VStack(alignment: .leading, spacing: 8) {
                        Text("Bluetooth Devices: \(settings.bluetoothThreshold)")
                        Slider(value: Binding(
                            get: { Double(settings.bluetoothThreshold) },
                            set: { settings.bluetoothThreshold = Int($0) }
                        ), in: 1...20, step: 1)
                    }
                }
                
                // Health Integration
                Section("Health Integration") {
                    HStack {
                        Text("HealthKit Status")
                        Spacer()
                        Text(healthManager.isAuthorized ? "Connected" : "Not Connected")
                            .foregroundColor(healthManager.isAuthorized ? .green : .orange)
                    }
                    
                    if !healthManager.isAuthorized {
                        Button("Connect to HealthKit") {
                            healthManager.requestPermissions()
                        }
                    }
                    
                    Toggle("Sync to Health App", isOn: $settings.healthKitEnabled)
                        .disabled(!healthManager.isAuthorized)
                }
                
                // Data Management
                Section("Data Management") {
                    VStack(alignment: .leading, spacing: 8) {
                        Text("Retention Period: \(settings.dataRetentionDays) days")
                        Slider(value: Binding(
                            get: { Double(settings.dataRetentionDays) },
                            set: { settings.dataRetentionDays = Int($0) }
                        ), in: 7...90, step: 1)
                    }
                    
                    HStack {
                        Text("Total Readings")
                        Spacer()
                        Text("\(dataManager.exposureReadings.count)")
                            .foregroundColor(.secondary)
                    }
                    
                    Button("Export Data") {
                        showingDataExport = true
                    }
                    
                    Button("Clear All Data") {
                        clearAllData()
                    }
                    .foregroundColor(.red)
                }
                
                // App Information
                Section("About") {
                    HStack {
                        Text("Version")
                        Spacer()
                        Text("1.0.2")
                            .foregroundColor(.secondary)
                    }
                    
                    Link("Privacy Policy", destination: URL(string: "https://exposiguard.app/privacy")!)
                    Link("Support", destination: URL(string: "https://exposiguard.app/support")!)
                    
                    Button("Rate ExposiGuard") {
                        // Open App Store rating
                        if let url = URL(string: "itms-apps://itunes.apple.com/app/id123456789?action=write-review") {
                            UIApplication.shared.open(url)
                        }
                    }
                }
            }
            .navigationTitle("Settings")
            .sheet(isPresented: $showingDataExport) {
                DataExportView()
            }
        }
    }
    
    private func clearAllData() {
        // Clear Core Data
        let context = dataManager.persistentContainer.viewContext
        
        // Clear exposure readings
        let readingsRequest: NSFetchRequest<NSFetchRequestResult> = NSFetchRequest(entityName: "ExposureReadingEntity")
        let readingsDelete = NSBatchDeleteRequest(fetchRequest: readingsRequest)
        
        // Clear daily stats
        let statsRequest: NSFetchRequest<NSFetchRequestResult> = NSFetchRequest(entityName: "DailyStatsEntity")
        let statsDelete = NSBatchDeleteRequest(fetchRequest: statsRequest)
        
        do {
            try context.execute(readingsDelete)
            try context.execute(statsDelete)
            try context.save()
            
            // Clear in-memory data
            dataManager.exposureReadings.removeAll()
            dataManager.dailyStats.removeAll()
            
            print("✅ All data cleared successfully")
        } catch {
            print("❌ Failed to clear data: \(error)")
        }
    }
}

struct DataExportView: View {
    @Environment(\.presentationMode) var presentationMode
    @EnvironmentObject var dataManager: DataManager
    
    var body: some View {
        NavigationView {
            VStack(spacing: 20) {
                Image(systemName: "square.and.arrow.up")
                    .font(.system(size: 60))
                    .foregroundColor(.blue)
                
                Text("Export Your Data")
                    .font(.title2)
                    .fontWeight(.medium)
                
                Text("Export your exposure data as a CSV file for analysis or backup.")
                    .font(.subheadline)
                    .foregroundColor(.secondary)
                    .multilineTextAlignment(.center)
                    .padding(.horizontal, 40)
                
                Button("Export as CSV") {
                    exportData()
                }
                .buttonStyle(.borderedProminent)
                .controlSize(.large)
                
                Spacer()
            }
            .padding()
            .navigationTitle("Export Data")
            .navigationBarTitleDisplayMode(.inline)
            .toolbar {
                ToolbarItem(placement: .navigationBarTrailing) {
                    Button("Done") {
                        presentationMode.wrappedValue.dismiss()
                    }
                }
            }
        }
    }
    
    private func exportData() {
        // Create CSV content
        var csvContent = "Date,Time,Magnetic Field (µT),Bluetooth Devices,Noise Level (dB)\n"
        
        let formatter = DateFormatter()
        formatter.dateFormat = "yyyy-MM-dd,HH:mm:ss"
        
        for reading in dataManager.exposureReadings {
            let dateTime = formatter.string(from: reading.timestamp)
            csvContent += "\(dateTime),\(reading.magneticField),\(reading.bluetoothDeviceCount),\(reading.noiseLevel)\n"
        }
        
        // Save to temporary file and share
        let tempURL = FileManager.default.temporaryDirectory.appendingPathComponent("exposiguard_data.csv")
        
        do {
            try csvContent.write(to: tempURL, atomically: true, encoding: .utf8)
            
            // Share the file
            let activityController = UIActivityViewController(activityItems: [tempURL], applicationActivities: nil)
            
            if let windowScene = UIApplication.shared.connectedScenes.first as? UIWindowScene,
               let window = windowScene.windows.first {
                window.rootViewController?.present(activityController, animated: true)
            }
            
        } catch {
            print("❌ Failed to export data: \(error)")
        }
    }
}