//
//  DataManager.swift
//  ExposiGuard
//
//  Created on 26/09/2025
//  Copyright © 2025 ExposiGuard. All rights reserved.
//

import Foundation
import CoreData
import Combine

/// Manages local data storage and persistence
class DataManager: ObservableObject {
    @Published var exposureReadings: [ExposureReading] = []
    @Published var dailyStats: [DailyStats] = []
    
    lazy var persistentContainer: NSPersistentContainer = {
        let container = NSPersistentContainer(name: "ExposiGuardDataModel")
        container.loadPersistentStores { [weak self] _, error in
            if let error = error {
                print("❌ Core Data error: \(error)")
            } else {
                print("✅ Core Data loaded successfully")
            }
        }
        return container
    }()
    
    private var saveTimer: Timer?
    
    init() {
        // Auto-save every 30 seconds
        saveTimer = Timer.scheduledTimer(withTimeInterval: 30.0, repeats: true) { [weak self] _ in
            self?.saveContext()
        }
    }
    
    func initialize() {
        loadRecentData()
    }
    
    private func loadRecentData() {
        // Load last 24 hours of data
        let endDate = Date()
        let startDate = Calendar.current.date(byAdding: .day, value: -1, to: endDate)!
        
        exposureReadings = fetchExposureReadings(from: startDate, to: endDate)
        dailyStats = fetchDailyStats(days: 7) // Last 7 days
    }
    
    /// Save an exposure reading
    func saveExposureReading(_ reading: ExposureReading) {
        let context = persistentContainer.viewContext
        
        let entity = NSEntityDescription.entity(forEntityName: "ExposureReadingEntity", in: context)!
        let readingEntity = NSManagedObject(entity: entity, insertInto: context)
        
        readingEntity.setValue(reading.id, forKey: "id")
        readingEntity.setValue(reading.timestamp, forKey: "timestamp")
        readingEntity.setValue(reading.magneticField, forKey: "magneticField")
        readingEntity.setValue(reading.bluetoothDeviceCount, forKey: "bluetoothDeviceCount")
        readingEntity.setValue(reading.noiseLevel, forKey: "noiseLevel")
        
        // Add to local array
        exposureReadings.append(reading)
        
        // Keep only recent readings in memory (last 1000)
        if exposureReadings.count > 1000 {
            exposureReadings.removeFirst(exposureReadings.count - 1000)
        }
        
        saveContext()
    }
    
    /// Fetch exposure readings for a date range
    func fetchExposureReadings(from startDate: Date, to endDate: Date) -> [ExposureReading] {
        let context = persistentContainer.viewContext
        let request: NSFetchRequest<NSManagedObject> = NSFetchRequest(entityName: "ExposureReadingEntity")
        
        request.predicate = NSPredicate(format: "timestamp >= %@ AND timestamp <= %@", startDate as NSDate, endDate as NSDate)
        request.sortDescriptors = [NSSortDescriptor(key: "timestamp", ascending: true)]
        
        do {
            let results = try context.fetch(request)
            return results.compactMap { entity in
                guard let id = entity.value(forKey: "id") as? UUID,
                      let timestamp = entity.value(forKey: "timestamp") as? Date,
                      let magneticField = entity.value(forKey: "magneticField") as? Double,
                      let bluetoothDeviceCount = entity.value(forKey: "bluetoothDeviceCount") as? Int,
                      let noiseLevel = entity.value(forKey: "noiseLevel") as? Double else {
                    return nil
                }
                
                return ExposureReading(
                    id: id,
                    timestamp: timestamp,
                    magneticField: magneticField,
                    bluetoothDeviceCount: bluetoothDeviceCount,
                    noiseLevel: noiseLevel
                )
            }
        } catch {
            print("❌ Failed to fetch exposure readings: \(error)")
            return []
        }
    }
    
    /// Calculate and save daily statistics
    func calculateDailyStats(for date: Date) {
        let calendar = Calendar.current
        let startOfDay = calendar.startOfDay(for: date)
        let endOfDay = calendar.date(byAdding: .day, value: 1, to: startOfDay)!
        
        let readings = fetchExposureReadings(from: startOfDay, to: endOfDay)
        
        guard !readings.isEmpty else { return }
        
        let avgMagneticField = readings.map { $0.magneticField }.reduce(0, +) / Double(readings.count)
        let maxMagneticField = readings.map { $0.magneticField }.max() ?? 0
        let avgNoiseLevel = readings.map { $0.noiseLevel }.reduce(0, +) / Double(readings.count)
        let maxNoiseLevel = readings.map { $0.noiseLevel }.max() ?? 0
        let totalBluetoothDevices = readings.map { $0.bluetoothDeviceCount }.reduce(0, +)
        
        let stats = DailyStats(
            date: startOfDay,
            averageMagneticField: avgMagneticField,
            maxMagneticField: maxMagneticField,
            averageNoiseLevel: avgNoiseLevel,
            maxNoiseLevel: maxNoiseLevel,
            totalBluetoothDevices: totalBluetoothDevices,
            readingCount: readings.count
        )
        
        saveDailyStats(stats)
    }
    
    private func saveDailyStats(_ stats: DailyStats) {
        let context = persistentContainer.viewContext
        
        // Check if stats for this date already exist
        let request: NSFetchRequest<NSManagedObject> = NSFetchRequest(entityName: "DailyStatsEntity")
        request.predicate = NSPredicate(format: "date == %@", stats.date as NSDate)
        
        do {
            let existingStats = try context.fetch(request)
            let entity = existingStats.first ?? {
                let newEntity = NSEntityDescription.entity(forEntityName: "DailyStatsEntity", in: context)!
                return NSManagedObject(entity: newEntity, insertInto: context)
            }()
            
            entity.setValue(stats.id, forKey: "id")
            entity.setValue(stats.date, forKey: "date")
            entity.setValue(stats.averageMagneticField, forKey: "averageMagneticField")
            entity.setValue(stats.maxMagneticField, forKey: "maxMagneticField")
            entity.setValue(stats.averageNoiseLevel, forKey: "averageNoiseLevel")
            entity.setValue(stats.maxNoiseLevel, forKey: "maxNoiseLevel")
            entity.setValue(stats.totalBluetoothDevices, forKey: "totalBluetoothDevices")
            entity.setValue(stats.readingCount, forKey: "readingCount")
            
            saveContext()
            
        } catch {
            print("❌ Failed to save daily stats: \(error)")
        }
    }
    
    /// Fetch daily statistics
    func fetchDailyStats(days: Int) -> [DailyStats] {
        let context = persistentContainer.viewContext
        let request: NSFetchRequest<NSManagedObject> = NSFetchRequest(entityName: "DailyStatsEntity")
        
        let endDate = Date()
        let startDate = Calendar.current.date(byAdding: .day, value: -days, to: endDate)!
        
        request.predicate = NSPredicate(format: "date >= %@", startDate as NSDate)
        request.sortDescriptors = [NSSortDescriptor(key: "date", ascending: true)]
        
        do {
            let results = try context.fetch(request)
            return results.compactMap { entity in
                guard let id = entity.value(forKey: "id") as? UUID,
                      let date = entity.value(forKey: "date") as? Date,
                      let avgMagneticField = entity.value(forKey: "averageMagneticField") as? Double,
                      let maxMagneticField = entity.value(forKey: "maxMagneticField") as? Double,
                      let avgNoiseLevel = entity.value(forKey: "averageNoiseLevel") as? Double,
                      let maxNoiseLevel = entity.value(forKey: "maxNoiseLevel") as? Double,
                      let totalBluetoothDevices = entity.value(forKey: "totalBluetoothDevices") as? Int,
                      let readingCount = entity.value(forKey: "readingCount") as? Int else {
                    return nil
                }
                
                return DailyStats(
                    id: id,
                    date: date,
                    averageMagneticField: avgMagneticField,
                    maxMagneticField: maxMagneticField,
                    averageNoiseLevel: avgNoiseLevel,
                    maxNoiseLevel: maxNoiseLevel,
                    totalBluetoothDevices: totalBluetoothDevices,
                    readingCount: readingCount
                )
            }
        } catch {
            print("❌ Failed to fetch daily stats: \(error)")
            return []
        }
    }
    
    private func saveContext() {
        let context = persistentContainer.viewContext
        
        if context.hasChanges {
            do {
                try context.save()
            } catch {
                print("❌ Failed to save context: \(error)")
            }
        }
    }
    
    deinit {
        saveTimer?.invalidate()
        saveContext()
    }
}