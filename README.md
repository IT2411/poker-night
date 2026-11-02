# Poker Night ♠️♥️♣️♦️

**Poker Night** is a local-first, cross-platform Texas Hold'em session manager and companion action recorder built with **Kotlin Multiplatform (KMP)** and **Compose Multiplatform (Material 3)**.

Designed for in-person home poker games where players hold physical cards and chips, Poker Night eliminates manual calculations and betting disputes: it manages seating order, rotates the dealer button, posts blinds, validates legal betting actions, computes main and side pots, handles uncalled excess refunds, tracks chip loans, and maintains an auditable, append-only history with full undo and crash recovery.

In **v2.0.0**, Poker Night introduces **Host-Authoritative Local Multiplayer**: guests can connect their own devices (via native Android app or any mobile browser on iOS/Android) to view the live pot, active bet, and stacks, and submit checks, calls, bets, raises, and folds directly from their phones over local Wi-Fi or hotspot—with zero internet connectivity required.

**Current Release:** `v2.0.0` *(Build 3)*

---

## 🌟 Key Features

### 🌐 Local Multiplayer & Zero-Install Web Client (v2.0.0)
- **Host-Authoritative Ktor Server**: The Host phone runs an embedded, background-resilient Ktor HTTP and WebSocket server on port `8080`.
- **Instant Camera QR & Room Code Joining**:
  - Scan the Host screen's QR code with any phone camera (iPhone Safari or Android Chrome) to join the room immediately.
  - Native app users can alternatively enter a 6-character room code (e.g. `FBE774`); the app performs parallel LAN subnet discovery to locate and connect to the Host in under a second.
- **Zero-Install Web App (iOS Safari & Android Chrome)**:
  - Mobile-optimized web client served directly from the Host phone's internal storage without external internet access.
  - Direct download button on the web client enables Android guests to download and install the native APK over local Wi-Fi.
- **Real-Time Bi-Directional Synchronization**:
  - Actions submitted from web browsers or companion apps update the Host screen and all connected clients simultaneously.
- **Permanent Host Seat Reservation**:
  - Seat 1 (Seat Index 0) is reserved exclusively for the device owner operating the Host phone. Remote guests cannot see or claim the Host seat.
- **Idempotency & Reconnection Resiliency**:
  - Client-generated action IDs prevent duplicate bets on network retries.
  - If a player's phone locks or sleeps, reconnecting with their cached seat token restores their exact status, chip stack, and active turn.

### 🃏 Table Management & Game Engine (v0.1.0 & v0.1.1)
- **Exact Integer-Only Arithmetic**: All chips, pots, and stacks are stored as 64-bit integers (`Long`)—strictly zero floating-point rounding errors.
- **Configurable Denominations**: Default: `1 chip = ₹0.01` (1 paise).
- **Comprehensive Betting Actions**: Check, Fold, Call, Bet, Raise, and All-In.
- **Voluntary Blind Folding**: Small Blind and Big Blind can voluntarily fold pre-flop; forced blinds remain in the pot.
- **5-Chip Minimum Increment**: Opening bets and raises step in multiples of 5 chips with synchronized numeric inputs and quick-preset buttons (`Min`, `+5`, `+10`, `1/2 Pot`, `Pot`).
- **Opponent Ceiling Cap**: Caps player bets and raises to the maximum possible amount remaining opponents can match.
- **Seating & Positions**:
  - Clockwise seating with dynamic skipping of sitting-out or busted players.
  - Automatic dealer rotation with clear `(DEALER)` and `(NEXT DEALER)` badges.
  - Standard 3+ player rules (Dealer $\rightarrow$ SB $\rightarrow$ BB $\rightarrow$ UTG) and Heads-Up rules (Dealer posts SB and acts first pre-flop).
- **Exact Pot & Side-Pot Layering (SRS 6.3)**:
  - Multi-tier pot calculations for any number of all-ins of different stack sizes.
  - **Adjacent Layer Merging**: Combines pot layers that share identical eligible players so folds do not create phantom side pots.
  - **Uncalled Excess Refunds**: Automatically refunds uncalled bets or raises directly to the bettor before showdown.
  - Split pots divided with standard odd-chip distribution (awarded clockwise from the dealer button).

### ⏱️ Intermission, Loans, & Showdown Evaluator
- **Between-Hands Intermission**: Payout confirmation leads to an Intermission screen to review hand results, manage attendance, and issue loans before dealing the next hand.
- **Sit Out / Rejoin Toggles**: Players can sit out or rejoin between hands; sitting-out players are skipped during blind postings and card deals.
- **Bank & Player-to-Player Loans**: Borrow chips from the house or another player at 0% to 10% interest with one-tap repayments.
- **Interactive Showdown Evaluator**:
  - Optional visual card picker to resolve disputed or complex showdowns (e.g. 99955 vs 555AA).
  - High-contrast suit and rank selector with automatic duplicate prevention across board and hole cards.
  - Integrated 7-card Texas Hold'em evaluator ranks hands and highlights winning players with a `🏆 WINNER` badge.
- **Persistence & Non-Destructive Undo**:
  - Append-only event sourcing with reversal tombstones.
  - Stepping back past the start of a hand safely returns to the Intermission screen without corrupting completed hand records.
  - Automatically recovers active sessions and live hands after unexpected app termination.

---

## 🏗️ Architecture & Module Structure

```text
poker-night/
├── shared/                       # Shared Kotlin Multiplatform domain & data
│   ├── src/commonMain/kotlin/
│   │   ├── com/pokernight/shared/model/       # Immutable domain models (Card, Hand, Pot, Session, Loan, etc.)
│   │   ├── com/pokernight/shared/domain/
│   │   │   ├── betting/                       # Live betting state machine & legal action validator
│   │   │   ├── evaluation/                    # 7-card poker hand evaluator
│   │   │   ├── history/                       # Deterministic state replayer & undo engine
│   │   │   ├── pot/                           # Side pot layering & uncalled excess refund algorithm
│   │   │   ├── progression/                   # Street transitions (Pre-flop, Flop, Turn, River, Showdown)
│   │   │   └── showdown/                      # Hand resolution & stack reconciliation
│   │   ├── com/pokernight/shared/data/        # Repositories & persistence coordinator
│   │   └── com/pokernight/shared/multiplayer/ # Protocol DTOs, message envelopes, and state sanitizer
│   ├── src/androidMain/kotlin/                # Ktor embedded server & multiplayer client engines
│   └── src/commonTest/kotlin/                 # Domain, contract, and 6-player multiplayer simulation tests
├── androidApp/                   # Android Application Shell
│   ├── src/main/assets/web/                   # Mobile web client assets (index.html, style.css, app.js)
│   └── src/main/kotlin/com/pokernight/android/
│       ├── MainActivity.kt                    # Lifecycle entry point & service binding
│       ├── network/                           # PokerHostService, QrCodeGenerator, NetworkUtils
│       └── ui/                                # Compose Material 3 screens & PokerViewModel
└── iosApp/                       # iOS Application Shell (SwiftUI consuming SharedKit)
```

---

## 🛠️ Tech Stack & Pinned Baseline

| Component | Pinned Version |
|---|---|
| **App Version** | `2.0.0` (Build 3) |
| **Kotlin** | 2.1.10 |
| **Android Gradle Plugin (AGP)** | 8.8.2 |
| **Gradle Wrapper** | 8.12.1 |
| **Compose Multiplatform** | 1.7.3 (Compiler pinned via Kotlin 2.1.10) |
| **Ktor Server & Client** | 3.0.3 (CIO Engine, WebSockets, ContentNegotiation) |
| **Java JDK** | OpenJDK 17 (JVM Toolchain pinned to 17) |
| **QR Code Engine** | ZXing Core 3.5.3 |
| **Serialization** | `kotlinx-serialization-json` 1.8.0 |
| **Coroutines** | `kotlinx-coroutines-core` 1.10.1 |
| **Formatting / Linter** | Spotless 7.0.2 with `ktlint` 1.5.0 |

---

## 🧪 Testing & Quality Gates

Run code formatting check:
```bash
./gradlew spotlessCheck
```

Apply automatic code formatting:
```bash
./gradlew spotlessApply
```

Run all unit, contract, and 6-player multiplayer integration tests:
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
1. Run a local download server on your computer:
   ```bash
   cd androidApp/build/outputs/apk/debug && python3 -m http.server 8000
   ```
2. On your phone's browser, navigate to `http://<YOUR_COMPUTER_IP>:8000/androidApp-debug.apk` and tap install.

---

## 📄 License & Specifications
- Requirements and functional specifications defined in [`SRS.md`](SRS.md).
- Poker rules and official terminology reference in [`RULES.md`](RULES.md).
- Architectural decision records documented in [`DECISIONS.md`](DECISIONS.md).