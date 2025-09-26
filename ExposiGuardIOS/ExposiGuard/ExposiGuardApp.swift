//
//  ExposiGuardApp.swift
//  ExposiGuard
//
//  Created on 26/09/2025
//  Copyright © 2025 ExposiGuard. All rights reserved.
//

import SwiftUI
import HealthKit

@main
struct ExposiGuardApp: App {
    @StateObject private var sensorManager = SensorManager()
    @StateObject private var healthManager = HealthManager()
    @StateObject private var dataManager = DataManager()
    
    var body: some Scene {
        WindowGroup {
            ContentView()
                .environmentObject(sensorManager)
                .environmentObject(healthManager)
                .environmentObject(dataManager)
                .onAppear {
                    setupApp()
                }
        }
    }
    
    private func setupApp() {
        // Initialize app components
        dataManager.initialize()
        
        // Request health permissions
        if HKHealthStore.isHealthDataAvailable() {
            healthManager.requestPermissions()
        }
        
        // Start background monitoring if permissions are granted
        sensorManager.startMonitoring()
    }
}