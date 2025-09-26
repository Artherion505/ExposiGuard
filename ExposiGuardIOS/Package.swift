// swift-tools-version: 5.9
// The swift-tools-version declares the minimum version of Swift required to build this package.

import PackageDescription

let package = Package(
    name: "ExposiGuard",
    platforms: [
        .iOS(.v15)
    ],
    products: [
        .library(
            name: "ExposiGuard",
            targets: ["ExposiGuard"]),
    ],
    dependencies: [
        // Add any external dependencies here
    ],
    targets: [
        .target(
            name: "ExposiGuard",
            dependencies: [],
            path: "ExposiGuard",
            resources: [
                .process("Resources")
            ]
        ),
        .testTarget(
            name: "ExposiGuardTests",
            dependencies: ["ExposiGuard"],
            path: "ExposiGuardTests"
        ),
    ]
)