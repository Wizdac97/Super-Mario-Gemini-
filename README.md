# Super Plumber Odyssey — Android Platformer

A native Kotlin Android platform game. The project includes custom sprite rendering, physics, sound, touch controls, save data, and multiple handcrafted missions.

## Download the APK

Open the repository's **Releases** page and download the latest **installable debug APK**. GitHub Actions also keeps the APKs as downloadable workflow artifacts.

## Build

Every push to `main` builds the debug and release variants using JDK 17, Android SDK 34, and Gradle 8.7.

- **Debug APK:** signed with Android's standard debug key and installable on a device.
- **Release APK:** generated unsigned. A private signing key is required before distributing a production release or publishing to Google Play.

## Project

- Application ID: `com.superplumber.odyssey`
- Minimum Android version: Android 7.0 (API 24)
- Compile / target SDK: 34
