//
//  TrendsView.swift
//  ExposiGuard
//
//  Created on 26/09/2025
//  Copyright © 2025 ExposiGuard. All rights reserved.
//

import SwiftUI

struct TrendsView: View {
    @EnvironmentObject var dataManager: DataManager
    @State private var selectedTimeframe: Timeframe = .week
    
    enum Timeframe: String, CaseIterable {
        case day = "24H"
        case week = "7D"
        case month = "30D"
    }
    
    var body: some View {
        NavigationView {
            ScrollView {
                VStack(spacing: 20) {
                    // Timeframe Picker
                    Picker("Timeframe", selection: $selectedTimeframe) {
                        ForEach(Timeframe.allCases, id: \.self) { timeframe in
                            Text(timeframe.rawValue).tag(timeframe)
                        }
                    }
                    .pickerStyle(SegmentedPickerStyle())
                    .padding(.horizontal)
                    
                    // Charts Section
                    if dataManager.dailyStats.isEmpty {
                        emptyStateView
                    } else {
                        chartsSection
                    }
                    
                    // Summary Statistics
                    summarySection
                }
                .padding(.vertical)
            }
            .navigationTitle("Trends")
        }
    }
    
    private var emptyStateView: some View {
        VStack(spacing: 16) {
            Image(systemName: "chart.line.uptrend.xyaxis")
                .font(.system(size: 60))
                .foregroundColor(.gray)
            
            Text("No Data Available")
                .font(.title2)
                .fontWeight(.medium)
            
            Text("Start monitoring to see your exposure trends over time.")
                .font(.subheadline)
                .foregroundColor(.secondary)
                .multilineTextAlignment(.center)
                .padding(.horizontal, 40)
        }
        .padding(.top, 60)
    }
    
    private var chartsSection: some View {
        VStack(spacing: 20) {
            // Magnetic Field Trend
            TrendChartCard(
                title: "Magnetic Field",
                unit: "µT",
                data: getFilteredData().map { $0.averageMagneticField },
                color: .blue
            )
            
            // Noise Level Trend
            TrendChartCard(
                title: "Noise Level",
                unit: "dB",
                data: getFilteredData().map { $0.averageNoiseLevel },
                color: .orange
            )
            
            // Bluetooth Devices Trend
            TrendChartCard(
                title: "Bluetooth Devices",
                unit: "devices",
                data: getFilteredData().map { Double($0.totalBluetoothDevices) },
                color: .purple
            )
        }
        .padding(.horizontal)
    }
    
    private var summarySection: some View {
        VStack(alignment: .leading, spacing: 16) {
            Text("Summary Statistics")
                .font(.headline)
                .padding(.horizontal)
            
            LazyVGrid(columns: [
                GridItem(.flexible()),
                GridItem(.flexible())
            ], spacing: 16) {
                
                if let avgMagnetic = getAverageMagnetic() {
                    SummaryCard(
                        title: "Avg Magnetic",
                        value: String(format: "%.1f µT", avgMagnetic),
                        trend: getTrend(for: getFilteredData().map { $0.averageMagneticField }),
                        icon: "dot.radiowaves.left.and.right"
                    )
                }
                
                if let avgNoise = getAverageNoise() {
                    SummaryCard(
                        title: "Avg Noise",
                        value: String(format: "%.0f dB", avgNoise),
                        trend: getTrend(for: getFilteredData().map { $0.averageNoiseLevel }),
                        icon: "speaker.wave.3"
                    )
                }
                
                if let totalBT = getTotalBluetooth() {
                    SummaryCard(
                        title: "Total BT",
                        value: "\(totalBT)",
                        trend: getTrend(for: getFilteredData().map { Double($0.totalBluetoothDevices) }),
                        icon: "bluetooth"
                    )
                }
                
                SummaryCard(
                    title: "Data Points",
                    value: "\(getTotalReadings())",
                    trend: .stable,
                    icon: "chart.bar.fill"
                )
            }
            .padding(.horizontal)
        }
    }
    
    private func getFilteredData() -> [DailyStats] {
        let days: Int
        switch selectedTimeframe {
        case .day: days = 1
        case .week: days = 7
        case .month: days = 30
        }
        
        let cutoffDate = Calendar.current.date(byAdding: .day, value: -days, to: Date()) ?? Date()
        return dataManager.dailyStats.filter { $0.date >= cutoffDate }
    }
    
    private func getAverageMagnetic() -> Double? {
        let data = getFilteredData()
        guard !data.isEmpty else { return nil }
        return data.map { $0.averageMagneticField }.reduce(0, +) / Double(data.count)
    }
    
    private func getAverageNoise() -> Double? {
        let data = getFilteredData()
        guard !data.isEmpty else { return nil }
        return data.map { $0.averageNoiseLevel }.reduce(0, +) / Double(data.count)
    }
    
    private func getTotalBluetooth() -> Int? {
        let data = getFilteredData()
        guard !data.isEmpty else { return nil }
        return data.map { $0.totalBluetoothDevices }.reduce(0, +)
    }
    
    private func getTotalReadings() -> Int {
        return getFilteredData().map { $0.readingCount }.reduce(0, +)
    }
    
    private func getTrend(for data: [Double]) -> TrendDirection {
        guard data.count >= 2 else { return .stable }
        
        let firstHalf = data.prefix(data.count / 2)
        let secondHalf = data.suffix(data.count / 2)
        
        let firstAvg = firstHalf.reduce(0, +) / Double(firstHalf.count)
        let secondAvg = secondHalf.reduce(0, +) / Double(secondHalf.count)
        
        let change = (secondAvg - firstAvg) / firstAvg
        
        if change > 0.1 {
            return .increasing
        } else if change < -0.1 {
            return .decreasing
        } else {
            return .stable
        }
    }
}

enum TrendDirection {
    case increasing, decreasing, stable
    
    var icon: String {
        switch self {
        case .increasing: return "arrow.up.right"
        case .decreasing: return "arrow.down.right"
        case .stable: return "minus"
        }
    }
    
    var color: Color {
        switch self {
        case .increasing: return .red
        case .decreasing: return .green
        case .stable: return .gray
        }
    }
}