//
//  HealthManager.swift
//  ExposiGuard
//
//  Created on 26/09/2025
//  Copyright © 2025 ExposiGuard. All rights reserved.
//

import Foundation
import HealthKit
import Combine

/// Manages HealthKit integration for health data correlation
class HealthManager: ObservableObject {
    @Published var isAuthorized: Bool = false
    @Published var heartRate: Double = 0.0
    @Published var stressLevel: Double = 0.0
    @Published var sleepHours: Double = 0.0
    
    private let healthStore = HKHealthStore()
    private var heartRateQuery: HKObserverQuery?
    
    // Health data types we want to read
    private let readTypes: Set<HKSampleType> = [
        HKQuantityType.quantityType(forIdentifier: .heartRate)!,
        HKQuantityType.quantityType(forIdentifier: .sleepAnalysis)!,
        HKCategoryType.categoryType(forIdentifier: .mindfulSession)!
    ]
    
    // Health data types we want to write (exposure data)
    private let writeTypes: Set<HKSampleType> = [
        HKQuantityType.quantityType(forIdentifier: .environmentalAudioExposure)!
    ]
    
    init() {
        checkHealthKitAvailability()
    }
    
    private func checkHealthKitAvailability() {
        guard HKHealthStore.isHealthDataAvailable() else {
            print("❌ HealthKit not available on this device")
            return
        }
        
        checkAuthorizationStatus()
    }
    
    func requestPermissions() {
        guard HKHealthStore.isHealthDataAvailable() else {
            print("❌ HealthKit not available")
            return
        }
        
        healthStore.requestAuthorization(toShare: writeTypes, read: readTypes) { [weak self] success, error in
            DispatchQueue.main.async {
                if success {
                    self?.isAuthorized = true
                    self?.startHealthMonitoring()
                    print("✅ HealthKit permissions granted")
                } else {
                    print("❌ HealthKit permissions denied: \(error?.localizedDescription ?? "Unknown error")")
                }
            }
        }
    }
    
    private func checkAuthorizationStatus() {
        let heartRateType = HKQuantityType.quantityType(forIdentifier: .heartRate)!
        let status = healthStore.authorizationStatus(for: heartRateType)
        
        DispatchQueue.main.async {
            self.isAuthorized = (status == .sharingAuthorized)
            if self.isAuthorized {
                self.startHealthMonitoring()
            }
        }
    }
    
    private func startHealthMonitoring() {
        startHeartRateMonitoring()
        fetchRecentSleepData()
    }
    
    private func startHeartRateMonitoring() {
        guard let heartRateType = HKQuantityType.quantityType(forIdentifier: .heartRate) else { return }
        
        let query = HKObserverQuery(sampleType: heartRateType, predicate: nil) { [weak self] _, _, error in
            if let error = error {
                print("❌ Heart rate monitoring error: \(error)")
                return
            }
            
            self?.fetchLatestHeartRate()
        }
        
        healthStore.execute(query)
        heartRateQuery = query
        
        // Also fetch initial value
        fetchLatestHeartRate()
    }
    
    private func fetchLatestHeartRate() {
        guard let heartRateType = HKQuantityType.quantityType(forIdentifier: .heartRate) else { return }
        
        let sortDescriptor = NSSortDescriptor(key: HKSampleSortIdentifierEndDate, ascending: false)
        let query = HKSampleQuery(
            sampleType: heartRateType,
            predicate: nil,
            limit: 1,
            sortDescriptors: [sortDescriptor]
        ) { [weak self] _, samples, error in
            
            guard let sample = samples?.first as? HKQuantitySample else { return }
            
            let heartRateUnit = HKUnit.count().unitDivided(by: .minute())
            let heartRate = sample.quantity.doubleValue(for: heartRateUnit)
            
            DispatchQueue.main.async {
                self?.heartRate = heartRate
            }
        }
        
        healthStore.execute(query)
    }
    
    private func fetchRecentSleepData() {
        guard let sleepType = HKCategoryType.categoryType(forIdentifier: .sleepAnalysis) else { return }
        
        let calendar = Calendar.current
        let endDate = Date()
        let startDate = calendar.date(byAdding: .day, value: -1, to: endDate)!
        
        let predicate = HKQuery.predicateForSamples(withStart: startDate, end: endDate, options: .strictEndDate)
        let sortDescriptor = NSSortDescriptor(key: HKSampleSortIdentifierEndDate, ascending: false)
        
        let query = HKSampleQuery(
            sampleType: sleepType,
            predicate: predicate,
            limit: HKObjectQueryNoLimit,
            sortDescriptors: [sortDescriptor]
        ) { [weak self] _, samples, error in
            
            guard let sleepSamples = samples as? [HKCategorySample] else { return }
            
            var totalSleepTime: TimeInterval = 0
            
            for sample in sleepSamples {
                if sample.value == HKCategoryValueSleepAnalysis.asleep.rawValue {
                    totalSleepTime += sample.endDate.timeIntervalSince(sample.startDate)
                }
            }
            
            let sleepHours = totalSleepTime / 3600.0 // Convert to hours
            
            DispatchQueue.main.async {
                self?.sleepHours = sleepHours
            }
        }
        
        healthStore.execute(query)
    }
    
    /// Write exposure data to HealthKit
    func writeExposureData(noiseLevel: Double, date: Date = Date()) {
        guard isAuthorized else { return }
        guard let audioExposureType = HKQuantityType.quantityType(forIdentifier: .environmentalAudioExposure) else { return }
        
        let quantity = HKQuantity(unit: HKUnit.decibelAWeighted(), doubleValue: noiseLevel)
        let sample = HKQuantitySample(
            type: audioExposureType,
            quantity: quantity,
            start: date,
            end: date
        )
        
        healthStore.save(sample) { success, error in
            if success {
                print("✅ Exposure data written to HealthKit")
            } else {
                print("❌ Failed to write exposure data: \(error?.localizedDescription ?? "Unknown error")")
            }
        }
    }
    
    /// Get health correlation data
    func getHealthCorrelation() -> HealthCorrelation {
        return HealthCorrelation(
            heartRate: heartRate,
            sleepHours: sleepHours,
            stressLevel: calculateStressLevel()
        )
    }
    
    private func calculateStressLevel() -> Double {
        // Simple stress calculation based on heart rate and sleep
        let normalHeartRate: Double = 70
        let normalSleep: Double = 8
        
        let heartRateStress = max(0, (heartRate - normalHeartRate) / normalHeartRate)
        let sleepStress = max(0, (normalSleep - sleepHours) / normalSleep)
        
        return min(1.0, (heartRateStress + sleepStress) / 2.0)
    }
    
    deinit {
        if let query = heartRateQuery {
            healthStore.stop(query)
        }
    }
}