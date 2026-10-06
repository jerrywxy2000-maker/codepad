// swift-tools-version: 5.9
import PackageDescription

let package = Package(
    name: "CodePadHelper",
    platforms: [.macOS(.v12)],
    products: [
        .executable(name: "codepad-mac-helper", targets: ["CodePadHelper"])
    ],
    targets: [
        .target(
            name: "TouchBarBridge",
            publicHeadersPath: "include",
            linkerSettings: [
                .linkedFramework("AppKit"),
                .linkedFramework("CoreGraphics"),
                .linkedFramework("CoreImage"),
                .linkedFramework("IOSurface")
            ]
        ),
        .executableTarget(
            name: "CodePadHelper",
            dependencies: ["TouchBarBridge"],
            linkerSettings: [
                .linkedFramework("AVFoundation"),
                .linkedFramework("AudioToolbox"),
                .linkedFramework("CoreAudio")
            ]
        )
    ]
)
