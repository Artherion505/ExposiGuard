//
//  HomeView.swift
//  ExposiGuard
//
//  Created on 26/09/2025
//  Copyright © 2025 ExposiGuard. All rights reserved.
//

import SwiftUI

struct HomeView: View {
    @EnvironmentObject var sensorManager: SensorManager
    @EnvironmentObject var healthManager: HealthManager
    @EnvironmentObject var dataManager: DataManager
    
    @State private var showingAlert = false
    @State private var alertTitle = ""
    @State private var alertMessage = ""
    
    var body: some View {
        NavigationView {
            ScrollView {
                VStack(spacing: 20) {
                    // Header
                    headerSection
                    
                    // Status Cards
                    statusCardsSection
                    
                    // Quick Stats
                    quickStatsSection
                    
                    // Recent Readings Chart
                    recentReadingsSection
                    
                    Spacer(minLength: 50)
                }
                .padding()
            }
            .navigationTitle("ExposiGuard")
            .navigationBarTitleDisplayMode(.large)
            .toolbar {
                ToolbarItem(placement: .navigationBarTrailing) {
                    Button(action: toggleMonitoring) {
                        Image(systemName: sensorManager.isMonitoring ? "pause.circle.fill" : "play.circle.fill")
                            .font(.title2)
                            .foregroundColor(sensorManager.isMonitoring ? .red : .green)
                    }
                }
            }
            .alert(alertTitle, isPresented: $showingAlert) {
                Button("OK") { }
            } message: {
                Text(alertMessage)
            }
        }
    }
    
    private var headerSection: some View {
        VStack(spacing: 12) {
            Image(systemName: "antenna.radiowaves.left.and.right")
                .font(.system(size: 50))
                .foregroundColor(.blue)
            
            Text("Environmental Monitoring")
                .font(.title2)
                .fontWeight(.medium)
            
            Text(sensorManager.isMonitoring ? "Monitoring Active" : "Monitoring Paused")
                .font(.subheadline)
                .foregroundColor(sensorManager.isMonitoring ? .green : .orange)
                .padding(.horizontal, 16)
                .padding(.vertical, 4)
                .background(
                    Capsule()
                        .fill(sensorManager.isMonitoring ? Color.green.opacity(0.1) : Color.orange.opacity(0.1))
                )
        }
        .padding(.bottom, 10)
    }
    
    private var statusCardsSection: some View {
        LazyVGrid(columns: [
            GridItem(.flexible()),
            GridItem(.flexible())
        ], spacing: 16) {
            
            // Magnetic Field Card
            StatusCard(
                title: "Magnetic Field",
                value: String(format: "%.1f µT", sensorManager.magneticField),
                status: getMagneticFieldStatus(),
                icon: "dot.radiowaves.left.and.right"
            )
            
            // Bluetooth Devices Card
            StatusCard(
                title: "Bluetooth Devices",
                value: "\(sensorManager.bluetoothDevices.count)",
                status: getBluetoothStatus(),
                icon: "bluetooth"
            )
            
            // Noise Level Card
            StatusCard(
                title: "Noise Level",
                value: String(format: "%.0f dB", sensorManager.noiseLevel),
                status: getNoiseStatus(),
                icon: "speaker.wave.3"
            )
            
            // Health Status Card
            StatusCard(
                title: "Health Status",
                value: healthManager.isAuthorized ? "Connected" : "Disconnected",
                status: healthManager.isAuthorized ? "Normal" : "Inactive",
                icon: "heart.fill"
            )
        }
    }
    
    private var quickStatsSection: some View {
        VStack(alignment: .leading, spacing: 12) {
            Text("Today's Summary")
                .font(.headline)
                .padding(.horizontal)
            
            if let todayStats = getTodayStats() {
                HStack(spacing: 20) {
                    StatItem(
                        label: "Avg Magnetic",
                        value: String(format: "%.1f µT", todayStats.averageMagneticField)
                    )
                    
                    Divider()
                        .frame(height: 30)
                    
                    StatItem(
                        label: "Max Noise",
                        value: String(format: "%.0f dB", todayStats.maxNoiseLevel)
                    )
                    
                    Divider()
                        .frame(height: 30)
                    
                    StatItem(
                        label: "BT Devices",
                        value: "\(todayStats.totalBluetoothDevices)"
                    )
                }
                .padding()
                .background(Color(.systemGray6))
                .cornerRadius(12)
                .padding(.horizontal)
            } else {
                Text("No data available for today")
                    .foregroundColor(.secondary)
                    .padding()
                    .frame(maxWidth: .infinity)
                    .background(Color(.systemGray6))
                    .cornerRadius(12)
                    .padding(.horizontal)
            }
        }
    }
    
    private var recentReadingsSection: some View {
        VStack(alignment: .leading, spacing: 12) {
            Text("Recent Activity")
                .font(.headline)
                .padding(.horizontal)
            
            if dataManager.exposureReadings.isEmpty {
                Text("No recent readings available")
                    .foregroundColor(.secondary)
                    .padding()
                    .frame(maxWidth: .infinity)
                    .background(Color(.systemGray6))
                    .cornerRadius(12)
                    .padding(.horizontal)
            } else {
                // Simple chart representation
                VStack(spacing: 8) {
                    ForEach(dataManager.exposureReadings.suffix(5)) { reading in
                        HStack {
                            Text(formatTime(reading.timestamp))
                                .font(.caption)
                                .frame(width: 60, alignment: .leading)
                            
                            VStack(alignment: .leading, spacing: 2) {
                                ProgressView(value: reading.magneticField / 100.0)
                                    .progressViewStyle(LinearProgressViewStyle(tint: getProgressColor(reading.magneticField)))
                                
                                Text("Magnetic: \(String(format: "%.1f", reading.magneticField)) µT")
                                    .font(.caption2)
                                    .foregroundColor(.secondary)
                            }
                        }
                        .padding(.horizontal)
                    }
                }
                .padding(.vertical)
                .background(Color(.systemGray6))
                .cornerRadius(12)
                .padding(.horizontal)
            }
        }
    }
    
    private func toggleMonitoring() {
        if sensorManager.isMonitoring {
            sensorManager.stopMonitoring()
            showAlert(title: "Monitoring Stopped", message: "Environmental monitoring has been paused.")
        } else {
            sensorManager.startMonitoring()
            showAlert(title: "Monitoring Started", message: "Environmental monitoring is now active.")
        }
    }
    
    private func showAlert(title: String, message: String) {
        alertTitle = title
        alertMessage = message
        showingAlert = true
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
    
    private func getTodayStats() -> DailyStats? {
        let today = Calendar.current.startOfDay(for: Date())
        return dataManager.dailyStats.first { Calendar.current.isDate($0.date, inSameDayAs: today) }
    }
    
    private func formatTime(_ date: Date) -> String {
        let formatter = DateFormatter()
        formatter.timeStyle = .short
        return formatter.string(from: date)
    }
    
    private func getProgressColor(_ value: Double) -> Color {
        switch value {
        case 0..<25: return .green
        case 25..<50: return .yellow
        case 50..<100: return .orange
        default: return .red
        }
    }
}

struct StatItem: View {
    let label: String
    let value: String
    
    var body: some View {
        VStack(spacing: 4) {
            Text(value)
                .font(.title3)
                .fontWeight(.semibold)
            
            Text(label)
                .font(.caption)
                .foregroundColor(.secondary)
        }
    }
}