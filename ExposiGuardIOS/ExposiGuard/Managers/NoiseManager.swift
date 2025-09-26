//
//  NoiseManager.swift
//  ExposiGuard
//
//  Created on 26/09/2025
//  Copyright © 2025 ExposiGuard. All rights reserved.
//

import Foundation
import AVFoundation
import Combine

/// Manages ambient noise level monitoring using the device microphone
class NoiseManager: NSObject, ObservableObject {
    @Published var currentLevel: Double = 0.0
    @Published var isMonitoring: Bool = false
    @Published var averageLevel: Double = 0.0
    
    private var audioEngine: AVAudioEngine!
    private var inputNode: AVAudioInputNode!
    private var audioSession: AVAudioSession!
    
    private var levelTimer: Timer?
    private var levelHistory: [Double] = []
    private let maxHistoryCount = 10
    
    override init() {
        super.init()
        setupAudioEngine()
    }
    
    private func setupAudioEngine() {
        audioEngine = AVAudioEngine()
        audioSession = AVAudioSession.sharedInstance()
        inputNode = audioEngine.inputNode
    }
    
    func startMonitoring() {
        guard !isMonitoring else {
            print("⚠️ Noise monitoring already active")
            return
        }
        
        requestMicrophonePermission { [weak self] granted in
            guard granted else {
                print("❌ Microphone permission denied")
                return
            }
            
            DispatchQueue.main.async {
                self?.startAudioMonitoring()
            }
        }
    }
    
    private func requestMicrophonePermission(completion: @escaping (Bool) -> Void) {
        switch AVAudioSession.sharedInstance().recordPermission {
        case .granted:
            completion(true)
        case .denied:
            completion(false)
        case .undetermined:
            AVAudioSession.sharedInstance().requestRecordPermission { granted in
                completion(granted)
            }
        @unknown default:
            completion(false)
        }
    }
    
    private func startAudioMonitoring() {
        do {
            // Configure audio session
            try audioSession.setCategory(.record, mode: .measurement, options: .duckOthers)
            try audioSession.setActive(true, options: .notifyOthersOnDeactivation)
            
            // Configure input node
            let inputFormat = inputNode.outputFormat(forBus: 0)
            
            inputNode.installTap(onBus: 0, bufferSize: 1024, format: inputFormat) { [weak self] buffer, _ in
                self?.processAudioBuffer(buffer)
            }
            
            // Start audio engine
            try audioEngine.start()
            
            isMonitoring = true
            
            // Start timer for periodic updates
            levelTimer = Timer.scheduledTimer(withTimeInterval: 0.1, repeats: true) { [weak self] _ in
                self?.updateAverageLevel()
            }
            
            print("✅ Noise monitoring started")
            
        } catch {
            print("❌ Failed to start noise monitoring: \(error)")
        }
    }
    
    func stopMonitoring() {
        guard isMonitoring else { return }
        
        // Stop timer
        levelTimer?.invalidate()
        levelTimer = nil
        
        // Stop audio engine
        if audioEngine.isRunning {
            inputNode.removeTap(onBus: 0)
            audioEngine.stop()
        }
        
        // Deactivate audio session
        try? audioSession.setActive(false)
        
        isMonitoring = false
        currentLevel = 0.0
        averageLevel = 0.0
        levelHistory.removeAll()
        
        print("⏹️ Noise monitoring stopped")
    }
    
    private func processAudioBuffer(_ buffer: AVAudioPCMBuffer) {
        guard let channelData = buffer.floatChannelData?[0] else { return }
        
        let frameLength = Int(buffer.frameLength)
        var sum: Float = 0.0
        
        // Calculate RMS (Root Mean Square)
        for i in 0..<frameLength {
            sum += channelData[i] * channelData[i]
        }
        
        let rms = sqrt(sum / Float(frameLength))
        
        // Convert to decibels
        let decibels = 20 * log10(rms) + 90 // Add offset to get reasonable dB values
        
        DispatchQueue.main.async {
            self.currentLevel = max(0, min(120, Double(decibels))) // Clamp between 0-120 dB
        }
    }
    
    private func updateAverageLevel() {
        levelHistory.append(currentLevel)
        
        // Keep only recent history
        if levelHistory.count > maxHistoryCount {
            levelHistory.removeFirst()
        }
        
        // Calculate average
        if !levelHistory.isEmpty {
            averageLevel = levelHistory.reduce(0, +) / Double(levelHistory.count)
        }
    }
    
    /// Get noise level classification
    func getNoiseLevel() -> NoiseLevel {
        switch currentLevel {
        case 0..<40:
            return .quiet
        case 40..<60:
            return .moderate
        case 60..<80:
            return .loud
        default:
            return .veryLoud
        }
    }
}

enum NoiseLevel: String, CaseIterable {
    case quiet = "Quiet"
    case moderate = "Moderate"
    case loud = "Loud"
    case veryLoud = "Very Loud"
    
    var color: String {
        switch self {
        case .quiet: return "green"
        case .moderate: return "yellow"  
        case .loud: return "orange"
        case .veryLoud: return "red"
        }
    }
    
    var description: String {
        switch self {
        case .quiet: return "Quiet environment"
        case .moderate: return "Normal conversation level"
        case .loud: return "Noisy environment"
        case .veryLoud: return "Very noisy environment"
        }
    }
    
    var range: String {
        switch self {
        case .quiet: return "0-40 dB"
        case .moderate: return "40-60 dB"
        case .loud: return "60-80 dB"
        case .veryLoud: return "80+ dB"
        }
    }
}