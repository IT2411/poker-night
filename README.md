# Poker Night ♠️♥️♣️♦️

**Poker Night** is a local-first, cross-platform Texas Hold'em session manager and action recorder built with **Kotlin Multiplatform (KMP)** and **Compose Multiplatform (Material 3)**.

Designed for private home poker games without a dedicated dealer, Poker Night acts as the authoritative recorder: it manages clockwise seating, automatically rotates the dealer button, posts blinds, validates legal betting actions, splits main and side pots, handles uncalled excess refunds, and maintains an auditable, append-only history with full undo and crash recovery.

---

## 🌟 Key Features

- **Authoritative Rules Engine**:
  - Exact integer-only arithmetic (`Long` chips) — strictly zero floating-point rounding errors.
  - Configurable chip denominations (default: `1 chip = ₹0.01`).
  - Standard betting actions: **Check**, **Fold**, **Call**, **Bet**, **Raise**, and **All-In**.
  - Minimum raise increment enforced in **5-chip increments**.
  - Caps player bets and raises to the maximum possible amount opponents can match.
- **Seating & Positions**:
  - Fixed clockwise seating with dynamic skipping of sitting-out or busted players.
  - Automatic dealer button rotation after every completed hand.
  - Standard 3+ player positions (Dealer $\rightarrow$ SB $\rightarrow$ BB $\rightarrow$ UTG).
  - Heads-Up exception handling (Dealer posts SB and acts first pre-flop).
- **Exact Pot & Side-Pot Layering (SRS 6.3)**:
  - Multi-tier pot calculations for any number of all-ins of different stack sizes.
  - **Adjacent Layer Merging**: Combines pot layers that share identical eligible players so folds do not create phantom side pots.
  - **Uncalled Excess Refunds**: Automatically refunds uncalled bets/raises to the bettor before showdown.
  - Split pots with standard odd-chip resolution (awarded clockwise from the dealer button).
- **Hand Resolution & Showdown**:
  - **Manual Winner Selection**: Operator taps to select pot winners with high-contrast glowing feedback.
  - **7-Card Texas Hold'em Evaluator**: Ranks hands across all 10 poker categories (Royal Flush down to High Card).
  - **Table Chip Conservation**: Guarantees $\sum \text{Starting Stacks} = \sum \text{Current Stacks}$ across all hands.
- **Persistence, Undo & Crash Recovery**:
  - Append-only event sourcing with non-destructive reversal tombstones.
  - Replays effective action history to rebuild exact stacks, pots, and turn order.
  - Recovers uncompleted live hands and sessions upon app restart.
- **Modern Material 3 Interface**:
  - High-contrast poker table dark theme with legible text and cards.
  - Dual currency display: **Chips** and **₹ INR** (e.g. `500 chips (₹5.00)`).
  - Direct **numeric keypad input** + synchronized slider and quick preset buttons (`Min`, `1/2 Pot`, `Pot`, `All-In`).

---

## 🏗️ Architecture & Module Structure

```text
poker-night/
├── shared/                       # Shared Kotlin Multiplatform domain & data
│   ├── src/commonMain/kotlin/
│   │   ├── com/pokernight/shared/model/       # Immutable domain entities (Card, Hand, Pot, Session, etc.)
│   │   ├── com/pokernight/shared/domain/
│   │   │   ├── betting/                       # Live betting state machine & legal action validator
│   │   │   ├── evaluation/                    # 7-card poker hand evaluator
│   │   │   ├── history/                       # Deterministic state replayer & undo engine
│   │   │   ├── pot/                           # Side pot layering & uncalled excess refund algorithm
│   │   │   ├── progression/                   # Street transitions (Pre-flop, Flop, Turn, River, Showdown)
│   │   │   └── showdown/                      # Hand resolution & stack reconciliation
│   │   └── com/pokernight/shared/data/        # Repositories & persistence coordinator
│   └── src/commonTest/kotlin/                 # Domain & End-to-End integration test suite (40+ tests)
├── androidApp/                   # Android Application Shell
│   └── src/main/kotlin/
│       └── com/pokernight/android/
│           ├── MainActivity.kt                # ComponentActivity entry point
│           └── ui/
│               ├── PokerViewModel.kt          # UI state holder & domain coordinator
│               └── PokerScreens.kt            # Compose Material 3 UI screens
└── iosApp/                       # iOS Application Shell (SwiftUI consuming SharedKit)
```

---

## 🛠️ Tech Stack & Pinned Baseline

| Component | Pinned Version |
|---|---|
| **Kotlin** | 2.1.10 |
| **Android Gradle Plugin (AGP)** | 8.8.2 |
| **Gradle Wrapper** | 8.12.1 |
| **Compose Multiplatform** | 1.7.3 (Compiler pinned via Kotlin 2.1.10) |
| **Java JDK** | OpenJDK 17 (JVM Toolchain pinned to 17) |
| **Serialization** | `kotlinx-serialization-json` 1.8.0 |
| **Coroutines** | `kotlinx-coroutines-core` 1.10.1 |
| **Formatting / Linter** | Spotless 7.0.2 with `ktlint` 1.5.0 |

---

## 🚀 Getting Started

### Prerequisites
- **JDK 17** (`sudo apt install openjdk-17-jdk` on Ubuntu/Debian)
- **Android SDK Platform 35** and Build-Tools `35.0.0`
- Android Studio Ladybug / Koala or IntelliJ IDEA

### Environment Setup (Linux/macOS)
Load the included environment helper:
```bash
source ./env.sh
```

---

## 🧪 Testing & Quality Gates

Run formatting check:
```bash
./gradlew spotlessCheck
```

Apply automatic code formatting:
```bash
./gradlew spotlessApply
```

Run all unit tests and the 10-scenario End-to-End test suite:
```bash
./gradlew :shared:testDebugUnitTest
```

---

## 📦 Building the Android APK

Build the standalone debug APK:
```bash
./gradlew :androidApp:assembleDebug
```

The APK file will be generated at:
```text
androidApp/build/outputs/apk/debug/androidApp-debug.apk
```

### Installing on Device

**Option 1: Via ADB (USB Cable)**
```bash
adb install -r androidApp/build/outputs/apk/debug/androidApp-debug.apk
adb shell am start -n com.pokernight.android/.MainActivity
```

**Option 2: Wireless Local Download (Wi-Fi)**
1. Run a quick download server on your PC:
   ```bash
   cd androidApp/build/outputs/apk/debug && python3 -m http.server 8000
   ```
2. On your phone's browser, go to `http://<YOUR_PC_IP>:8000/androidApp-debug.apk` and tap install.

---

## 📋 Verified Test Scenarios

The test suite in `shared/src/commonTest/` verifies:
- [x] **Scenario 01**: Session creation, custom blinds, initial dealer selection, and clockwise seating.
- [x] **Scenario 02**: Complete hand progression from Pre-Flop through Flop, Turn, River, and Showdown.
- [x] **Scenario 03**: Automatic dealer button rotation and blind posting across consecutive hands.
- [x] **Scenario 04**: Legality of folds, raises, all-ins, and short all-in calls.
- [x] **Scenario 05**: Multi-level all-ins generating Main, Side 1, and Side 2 pots (SRS Scenario A).
- [x] **Scenario 06**: Contributions from folded players retained in pot layers while excluding them from winning.
- [x] **Scenario 07**: Split pot division, odd-chip distribution clockwise from dealer, and uncalled excess returns.
- [x] **Scenario 08**: App crash/kill recovery during an active hand restoring exact stacks and live bets.
- [x] **Scenario 09**: Non-destructive action undoing with audit tombstone tracking.
- [x] **Scenario 10**: Total table chip conservation across multiple completed hands.

---

## 📄 License & Specifications
- Requirements and specifications defined in [`SRS.md`](SRS.md).
- Poker rules and official terminology reference in [`RULES.md`](RULES.md).
- Architectural decision records documented in [`DECISIONS.md`](DECISIONS.md).
