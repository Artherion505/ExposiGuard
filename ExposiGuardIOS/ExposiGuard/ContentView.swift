//
//  ContentView.swift
//  ExposiGuard
//
//  Created on 26/09/2025
//  Copyright © 2025 ExposiGuard. All rights reserved.
//

import SwiftUI

struct ContentView: View {
    @EnvironmentObject var sensorManager: SensorManager
    @EnvironmentObject var healthManager: HealthManager
    @EnvironmentObject var dataManager: DataManager
    @State private var selectedTab = 0
    
    var body: some View {
        TabView(selection: $selectedTab) {
            HomeView()
                .tabItem {
                    Image(systemName: "house.fill")
                    Text("Home")
                }
                .tag(0)
            
            SensorView()
                .tabItem {
                    Image(systemName: "antenna.radiowaves.left.and.right")
                    Text("Sensors")
                }
                .tag(1)
            
            TrendsView()
                .tabItem {
                    Image(systemName: "chart.line.uptrend.xyaxis")
                    Text("Trends")
                }
                .tag(2)
            
            SettingsView()
                .tabItem {
                    Image(systemName: "gear")
                    Text("Settings")
                }
                .tag(3)
        }
        .accentColor(.blue)
    }
}