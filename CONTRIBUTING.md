# Contributing to Poker Night

## Prerequisites
- JDK 17 (Azul Zulu or Eclipse Temurin recommended)
- Android Studio Ladybug or later / IntelliJ IDEA Ultimate
- Xcode 16+ (for iOS builds, macOS only)
- Git

## Code Style & Formatting
- Code formatting is enforced using Spotless with ktlint.
- Run format check: `./gradlew spotlessCheck`
- Automatically format code: `./gradlew spotlessApply`

## Git Workflow
- All functional changes must be covered by automated unit tests in `shared/src/commonTest`.
- Keep commits focused and reference requirement IDs (e.g., `FR-01`).