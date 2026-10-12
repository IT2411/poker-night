# Poker Night ♠️♥️♣️♦️

Poker Night is an offline-first mobile session tracker for in-person Texas Hold'em home poker games, built using Kotlin Multiplatform targeting Android and iOS. (IOS testing due, does not work currently.)

## Features (SRS v1.0 Target)
- Complete Texas Hold'em hand tracking from pre-flop to showdown.
- Dynamic clockwise seating and automatic dealer button rotation.
- Automated small/big blind assignment (including Heads-Up rules).
- Exact side-pot layer calculation and uncalled excess handling.
- Full undo, redo, and action-history auditability.
- Integer-only accounting without rounding errors.

## Project Structure
- `:shared` — Core domain rules, pot calculator, state reducer, and data models.
- `:androidApp` — Android UI application shell.
- `:iosApp` — Swift/SwiftUI iOS application shell consuming `SharedKit`.

## Building the Project

### Running Shared Unit Tests
```bash
./gradlew :shared:allTests