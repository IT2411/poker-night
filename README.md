# Poker Night ♠️♥️♣️♦️

**Poker Night** is a local-first, cross-platform Texas Hold'em session manager and action recorder built with **Kotlin Multiplatform (KMP)** and **Compose Multiplatform (Material 3)**.

Designed for private home poker games without a dedicated dealer, Poker Night acts as the authoritative recorder: it manages clockwise seating, automatically rotates the dealer button, posts blinds, validates legal betting actions, splits main and side pots, handles uncalled excess refunds, manages chip loans, and maintains an auditable, append-only history with full undo and crash recovery.

**Current Release:** `v0.1.1` *(Baseline: `v0.1.0`)*

---

## 🚀 What's New in v0.1.1

- 🍔 **Top-Left Burger Navigation Drawer (☰)**:
  - Clean access to Live Table, Pot Breakdown, Hand History, Loans & Bank, and Session Reset.
  - Removed clutter from the top app bar for a focused game view.
- ⏱️ **Between-Hands Intermission Screen**:
  - Payout confirmation now opens a dedicated Intermission Screen rather than instantly dealing cards.
  - Shows last hand payouts, updated chip standings, and the next Dealer button position.
  - Allows the host to settle loans and adjust player attendance before dealing.
- 🏦 **Loan Management System (Bank & Player-to-Player)**:
  - Players can borrow chips from the **House Bank** or from **Another Player**.
  - Configurable interest rate from **0% to 10%** (default: `0%`).
  - Strict chip validation: Player loans verify the lending player has sufficient chips before transferring.
  - One-tap loan repayment when a player regains chips.
- 🪑 **Sit Out / Rejoin Toggles**:
  - Any player can be set to **Sitting Out** or **Active** between hands.
  - Sitting-out players are automatically bypassed during blind postings and card deals.
- 🔄 **Safe Undo Back to Intermission**:
  - Stepping back past the start of a hand safely cancels the active deal and returns to the Intermission screen without duplicate action ID conflicts.
  - Undo cannot mutate or corrupt previously completed hands behind Intermission.
- 🏷️ **Clear `(DEALER)` & `(NEXT DEALER)` Badges**:
  - Replaced poker shorthand `(BTN)` with crystal-clear dealer badges.
- 🪙 **5-Chip Minimum Increment**:
  - Minimum bet and raise increments set to 5 chips with synchronized numeric keypad and slider inputs.
- 🏆 **Reactive Winner Highlighting**:
  - Selected winners on the Showdown screen light up with emerald green cards, 3dp gold borders, and `🏆 WINNER` badges prior to confirmation.

---

## 🌟 Core Engine Features (v0.1.0 Baseline)

- **Authoritative Rules Engine**:
  - Exact integer-only arithmetic (`Long` chips) — strictly zero floating-point rounding errors.
  - Configurable chip denominations (default: `1 chip = ₹0.01`).
  - Standard betting actions: **Check**, **Fold**, **Call**, **Bet**, **Raise**, and **All-In**.
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
  - **Manual Winner Selection**: Operator taps to select pot winners with high-contrast feedback.
  - **7-Card Texas Hold'em Evaluator**: Ranks hands across all 10 poker categories (Royal Flush down to High Card).
  - **Table Chip Conservation**: Guarantees $\sum \text{Starting Stacks} = \sum \text{Current Stacks}$ across all hands.
- **Persistence & Crash Recovery**:
  - Append-only event sourcing with non-destructive reversal tombstones.
  - Replays effective action history to rebuild exact stacks, pots, and turn order.
  - Recovers uncompleted live hands and sessions upon app restart.

---

## 🏗️ Architecture & Module Structure

```text
poker-night/
├── shared/                       # Shared Kotlin Multiplatform domain & data
│   ├── src/commonMain/kotlin/
│   │   ├── com/pokernight/shared/model/       # Immutable domain entities (Card, Hand, Pot, Session, Loan, etc.)
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
| **App Version** | `0.1.1` (Build 2) |
| **Kotlin** | 2.1.10 |
| **Android Gradle Plugin (AGP)** | 8.8.2 |
| **Gradle Wrapper** | 8.12.1 |
| **Compose Multiplatform** | 1.7.3 (Compiler pinned via Kotlin 2.1.10) |
| **Java JDK** | OpenJDK 17 (JVM Toolchain pinned to 17) |
| **Serialization** | `kotlinx-serialization-json` 1.8.0 |
| **Coroutines** | `kotlinx-coroutines-core` 1.10.1 |
| **Formatting / Linter** | Spotless 7.0.2 with `ktlint` 1.5.0 |

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

## 📄 License & Specifications
- Requirements and specifications defined in [`SRS.md`](SRS.md).
- Poker rules and official terminology reference in [`RULES.md`](RULES.md).
- Architectural decision records documented in [`DECISIONS.md`](DECISIONS.md).
