//
//  StatusCard.swift
//  ExposiGuard
//
//  Created on 26/09/2025
//  Copyright © 2025 ExposiGuard. All rights reserved.
//

import SwiftUI

struct StatusCard: View {
    let title: String
    let value: String
    let status: String
    let icon: String
    
    private var statusColor: Color {
        switch status.lowercased() {
        case "low", "quiet", "normal":
            return .green
        case "moderate", "fair", "good":
            return .yellow
        case "high", "loud":
            return .orange
        case "very high", "very loud", "poor", "very poor":
            return .red
        case "connected":
            return .blue
        default:
            return .gray
        }
    }
    
    var body: some View {
        VStack(spacing: 12) {
            HStack {
                Image(systemName: icon)
                    .font(.title2)
                    .foregroundColor(statusColor)
                
                Spacer()
                
                Circle()
                    .fill(statusColor)
                    .frame(width: 8, height: 8)
            }
            
            VStack(alignment: .leading, spacing: 4) {
                Text(title)
                    .font(.caption)
                    .foregroundColor(.secondary)
                    .frame(maxWidth: .infinity, alignment: .leading)
                
                Text(value)
                    .font(.title3)
                    .fontWeight(.semibold)
                    .frame(maxWidth: .infinity, alignment: .leading)
                
                Text(status)
                    .font(.caption)
                    .foregroundColor(statusColor)
                    .frame(maxWidth: .infinity, alignment: .leading)
            }
        }
        .padding()
        .background(Color(.systemBackground))
        .cornerRadius(12)
        .shadow(color: .black.opacity(0.1), radius: 2, x: 0, y: 1)
    }
}