# Software Requirements Specification (SRS)
## Poker Night — Cross-Platform Poker Session Manager

**Document version:** 1.0  
**Date:** 13 October 2026  
**Platforms:** Android and iOS  
**Game variant:** Texas Hold'em  
**Primary use case:** Private, in-person poker games with rotating dealers  
**Document status:** Initial development specification

---

## 1. Introduction

### 1.1 Purpose

Poker Night is a mobile application designed to manage in-person poker sessions. It records the complete sequence of actions during each hand, calculates betting obligations, tracks player chip stacks, separates main and side pots, and maintains a persistent history of the session.

The application is intended for groups that play poker at home without a dedicated dealer. Players rotate the dealer position clockwise, while a host or designated operator records the actions on a mobile device.

The primary objective is to eliminate manual calculations and disputes about betting amounts, all-in contributions, pot eligibility, and session results.

### 1.2 Scope

The application shall provide:

- Session creation and player management.
- Clockwise seating order and rotating dealer positions.
- Automatic small and big blind assignment.
- Complete Texas Hold'em hand tracking from pre-flop to showdown.
- Betting controls with automatic call calculations.
- Validation of legal actions and betting order.
- Automatic main-pot and side-pot calculation.
- Player chip-stack and contribution tracking.
- Community-card and optional hole-card entry.
- Hand results, split pots, and stack updates.
- Complete action history, replay, undo, and correction.
- Persistent local storage and recovery after application interruption.
- Native Android and iOS application builds using shared Kotlin code.

### 1.3 Out of scope for the initial release

The initial release will not include:

- Online poker gameplay or public matchmaking.
- Real-time synchronization between multiple phones.
- Automated card recognition through a camera.
- Cash deposits, withdrawals, or payment processing.
- An online account system or mandatory cloud backend.
- Computer-controlled opponents.
- Advanced player analytics or poker strategy recommendations.

The application records an in-person game; it does not replace the physical cards, chips, or players.

### 1.4 Terminology

| Term | Definition |
|---|---|
| Session | An entire poker night containing one or more hands. |
| Hand | One complete deal, from blind posting to pot distribution. |
| Street | A betting stage: pre-flop, flop, turn, or river. |
| Dealer button | The position that determines blind positions and action order. |
| Street contribution | Chips a player has contributed during the current betting street. |
| Hand contribution | A player's total contribution across all streets of the current hand. |
| Main pot | The primary pot that all eligible non-folded contributors can contest. |
| Side pot | A separate pot created when players contribute different amounts because of all-ins. |
| All-in | A player commits their entire remaining stack. |
| Amount to call | The additional amount needed to match the current live bet. |
| Host | The person operating the app during the physical game. |

---

## 2. Overall Description

### 2.1 Product perspective

Poker Night will be a local-first mobile application. Its game engine, betting rules, accounting, and session history will be implemented in shared Kotlin code.

The initial release will use one device as the authoritative game recorder. This avoids making internet connectivity or a backend server necessary during a poker night.

### 2.2 User roles

**Host/operator**

- Creates and configures sessions.
- Enters players and seating order.
- Starts hands and records actions.
- Enters community cards and showdown results.
- Corrects mistakes when necessary.
- Reviews session history.

**Players**

- Participate in the physical game.
- May inspect the displayed betting amounts and pot calculations.
- Do not need separate accounts or devices.

### 2.3 Assumptions

- The game uses standard Texas Hold'em rules.
- The game has a configurable small blind and big blind.
- All players start with known chip stacks.
- Chip denominations are consistent within a session.
- A single device records the authoritative action history.
- The host may operate the app while the physical game continues.
- The application supports a manually recorded showdown even when hole cards have not been entered.

### 2.4 Constraints

- All money and chip calculations must use exact integer arithmetic.
- Game rules must not depend on UI state or platform-specific code.
- A completed action must be persisted before it is considered committed.
- The Android and iOS versions must use the same rules engine.
- No mandatory internet connection is permitted for core gameplay.

---

## 3. Technology Stack and Version Policy

The application shall use established, explicitly pinned versions instead of automatically adopting the newest available releases.

### 3.1 Recommended baseline

| Component | Version / requirement |
|---|---|
| Kotlin | 2.2.20 |
| Kotlin Multiplatform Gradle Plugin | 2.2.20 |
| Compose Multiplatform | 1.9.3 |
| Android Gradle Plugin | 8.11.1 |
| Gradle Wrapper | 8.14.3 |
| Room | 2.7.2 |
| Kotlin Symbol Processing (KSP) | A release explicitly compatible with Kotlin 2.2.20 |
| UI framework | Compose Multiplatform with Material 3 |
| Local database | SQLite through Room KMP |
| Asynchronous operations | Kotlin Coroutines |
| State management | Kotlin Flow and StateFlow |
| Serialization | Kotlin Serialization, pinned to a compatible stable release |
| Android application language | Kotlin |
| iOS integration | Kotlin/Native framework hosted by an Xcode application target |
| Build configuration | Gradle Kotlin DSL and version catalog |

**Version policy:** The table defines the initial candidate baseline, not a promise that every dependency combination has already been integration-tested. The first project milestone must build a minimal Android and iOS application with this exact matrix. Any incompatibility must be resolved by selecting a documented compatible patch release and updating the version catalog before feature development begins. No alpha, beta, release-candidate, or EAP dependencies shall be used in the production build.

Official compatibility references:

- Kotlin Multiplatform compatibility: https://kotlinlang.org/docs/multiplatform/multiplatform-compatibility-guide.html
- Compose Multiplatform releases: https://kotlinlang.org/docs/multiplatform/compose-compatibility-and-versioning.html
- Room releases: https://developer.android.com/jetpack/androidx/releases/room

### 3.2 Supported operating systems

The initial product targets are:

- Android 8.0 or later (API level 26+).
- iOS 16.0 or later.

These are product support targets and must be validated against the selected toolchain during the initial build.

Android can be developed and built on Linux, Windows, or macOS. iOS builds and signing require a Mac with a compatible Xcode installation.

### 3.3 Architecture principles

The project shall:

1. Keep all poker rules in a shared domain layer.
2. Keep persistence behind a repository interface.
3. Separate UI state from authoritative game state.
4. Use immutable state snapshots and validated actions.
5. Avoid floating-point arithmetic for chips or money.
6. Maintain deterministic results for identical action histories.
7. Keep platform-specific code limited to application entry points, lifecycle integration, and platform services.

---

## 4. Functional Requirements

Requirements use the identifiers `FR-XX` for traceability and testing.

### 4.1 Session management

**FR-01 — Create session**

The application shall allow the host to create a session with a name, creation timestamp, game variant, blind structure, chip denomination, and starting player list.

**FR-02 — Player management**

The host shall be able to add players, assign display names, edit names before or between hands, and remove players who are not participating in the active hand.

**FR-03 — Seating order**

The host shall establish a clockwise seating order. This order shall remain fixed throughout a hand.

**FR-04 — Initial dealer selection**

The host shall select the first dealer button when starting a session.

**FR-05 — Dealer rotation**

The dealer button shall move one active seat clockwise after each completed hand. The small blind and big blind shall be assigned automatically.

For a normal game with three or more active players, the next active seat clockwise after the dealer posts the small blind, and the following active seat posts the big blind.

For heads-up play, the dealer posts the small blind and the other player posts the big blind.

**FR-06 — Session continuation**

The host shall be able to pause a session, close the application, and resume the session with its recorded state intact.

**FR-07 — Between-hand changes**

The host may update player participation or seating before the next hand. Such changes must not rewrite the history of completed hands.

### 4.2 Chip denomination and accounting

**FR-08 — Configurable chip denomination**

The application shall allow the host to define the monetary value of a chip.

The default denomination shall be:

- 1 chip = ₹0.01.
- 5 chips = ₹0.05.
- 100 chips = ₹1.00.

**FR-09 — Exact arithmetic**

All contributions, stacks, pot amounts, and payouts shall be stored as integer chip counts.

The app shall never use binary floating-point numbers for financial calculations.

**FR-10 — Monetary display**

Amounts shall be displayed in chips and, where useful, their equivalent in Indian rupees.

**FR-11 — Stack consistency**

The app shall ensure that a player cannot commit more chips than their remaining stack, except through a separately recorded correction.

All-in actions shall commit the player's entire remaining stack.

### 4.3 Starting a hand

**FR-12 — Start hand**

The host shall start a new hand after the preceding hand has been completed or explicitly cancelled.

**FR-13 — Post blinds**

The application shall automatically post the small and big blinds, deducting the appropriate amounts from the players' stacks. If a blind exceeds a player's available stack, the actual contribution shall be limited to the available stack.

**FR-14 — Hole cards**

The application shall support two private cards per player in Texas Hold'em. Hole-card entry shall be optional for ordinary action tracking and mandatory only if automatic hand evaluation is requested.

**FR-15 — Pre-flop starting player**

Pre-flop action shall begin with the first eligible player clockwise after the big blind. The heads-up exception shall be handled according to standard Texas Hold'em rules.

### 4.4 Betting actions

**FR-16 — Supported actions**

The application shall support:

- Check.
- Fold.
- Call.
- Bet.
- Raise.
- All-in.

Each action shall be associated with a player, street, timestamp or sequence number, and the resulting game state.

**FR-17 — Check**

A player may check only when there is no outstanding amount to call.

**FR-18 — Fold**

A player may fold when facing a bet. The application shall mark the player as folded for the current hand and exclude them from winning any pot, while retaining all previously committed chips in the pot.

**FR-19 — Call**

The application shall calculate the amount required to call as the current live bet minus the player's current-street contribution.

The call shall be capped at the player's remaining stack. If the player's entire remaining stack is insufficient to call, the action shall be recorded as an all-in call.

**FR-20 — Bet**

A player may make an opening bet only when the current street has no live bet. The app shall validate the minimum legal bet, except where a valid short all-in is permitted.

**FR-21 — Raise incrementer**

The app shall provide a numeric incrementer for the total amount to which a player is raising, rather than merely the additional chips being added.

For example, if the current live bet is 10 chips, a raise-to value of 30 chips means that the player's total contribution on the current street becomes 30 chips.

The app shall calculate the player's additional contribution and update the amount to call for other players automatically.

**FR-22 — Minimum raise validation**

The application shall enforce the minimum legal raise according to standard Texas Hold'em rules. The minimum raise is based on the size of the previous full bet or raise.

A short all-in that is smaller than a full raise may be permitted, but it shall not automatically reopen betting for players who have already acted unless standard rules permit it.

**FR-23 — Turn order**

Only the player whose turn it is may submit a normal betting action. Invalid actions shall be rejected with a clear explanation.

**FR-24 — Reopening action**

When a raise occurs, action shall return to players who still need to respond. The app shall track which players have acted and whether betting has been reopened.

**FR-25 — Betting-round completion**

A betting round shall end only when all remaining players have responded to the outstanding betting action and the round is complete under standard rules.

The app shall not advance merely because all displayed contributions happen to be equal if an eligible player still has a pending decision.

### 4.5 Street progression

**FR-26 — Pre-flop completion**

When pre-flop betting is complete and at least two players remain eligible, the application shall advance to the flop.

If everyone except one player folds, that player wins the pot without a showdown.

**FR-27 — Flop**

The application shall support entry of three community cards and begin a new betting street.

**FR-28 — Turn**

Once flop betting is complete, the application shall support entry of the fourth community card and begin a new betting street.

**FR-29 — River**

Once turn betting is complete, the application shall support entry of the fifth community card and begin the final betting street.

**FR-30 — Street contribution reset**

At the start of each new street, each player's current-street contribution shall reset to zero. The player's total hand contribution and all existing pots shall remain unchanged.

The previous street's bet shall not become the amount to call on the next street.

**FR-31 — Post-flop starting player**

After the flop, turn, and river are revealed, action shall begin with the first eligible non-folded player clockwise after the dealer button.

If that player has no chips remaining, action shall move to the next eligible player.

**FR-32 — Check-through**

If every eligible player checks and no bet is made, the application shall automatically allow the street to end and the next community card to be revealed.

**FR-33 — All-in runout**

If all remaining players are all-in and no further betting is possible, the application shall allow the remaining community cards to be revealed without requesting nonexistent actions.

### 4.6 Pot and side-pot calculation

**FR-34 — Contribution tracking**

The application shall record every contribution to the pot, including blinds, calls, bets, raises, and all-ins.

**FR-35 — Main pot calculation**

The application shall calculate the main pot from the lowest contribution level shared by all contributing players.

**FR-36 — Side-pot calculation**

When contributions exceed the amount another player can match, the app shall create separate side pots from the successive contribution levels.

Each pot shall contain:

- Its total chip amount.
- The contributing players.
- The players eligible to win it.
- The amount contributed by each player at that level.

**FR-37 — Folded-player contributions**

Folded players' chips shall remain in the relevant pot layers. Folded players shall not be eligible to win any pot.

**FR-38 — Uncalled excess**

If a player's contribution cannot be matched by any opponent, the unmatched excess shall be returned to that player instead of being incorrectly included in a contested pot.

**FR-39 — Multiple all-ins**

The application shall support any number of all-in players with different stack sizes. The pot calculator shall produce the correct number of main and side pots.

**FR-40 — Independent eligibility**

The app shall determine winners separately for each pot. A player may win the main pot but be ineligible for one or more side pots.

**FR-41 — Split pots**

The application shall support multiple winners for a pot and divide the pot according to the configured odd-chip rule.

**FR-42 — Pot audit**

The host shall be able to inspect how every pot was calculated, including the contribution levels, player eligibility, and amount returned as unmatched excess.

### 4.7 Showdown and payouts

**FR-43 — Manual winner selection**

The host shall be able to select the winner or winners for each pot independently.

**FR-44 — Optional card evaluation**

The application may evaluate entered hole cards and community cards using standard Texas Hold'em hand rankings.

**FR-45 — Card validation**

When automatic evaluation is enabled, the application shall reject duplicate cards and invalid card combinations.

**FR-46 — Payout distribution**

The app shall distribute the pot amount among the selected winners and update their stacks.

**FR-47 — Stack reconciliation**

The app shall verify that the total chips held by players plus chips held in any explicitly recorded bank or reserve remain consistent with the session's chip accounting.

**FR-48 — Hand completion**

Once all pots have been awarded, the application shall mark the hand complete, store the result, and prepare the next hand.

### 4.8 History, undo, and recovery

**FR-49 — Action log**

The app shall store a sequential record of every committed action, including the actor, action type, amount, street, and resulting state.

**FR-50 — Hand replay**

The host shall be able to inspect a completed hand in chronological order.

**FR-51 — Undo**

The host shall be able to undo the most recent action. The game state shall be reconstructed consistently from the remaining action history.

**FR-52 — Correction**

The host shall be able to correct a mistaken entry. Corrections shall be auditable and shall not silently overwrite the original action.

**FR-53 — Crash recovery**

The application shall persist every committed action and recover the last consistent state after unexpected termination.

**FR-54 — Session history**

The host shall be able to review completed hands, player results, total pots, and final stacks.

---

## 5. User Interface Requirements

### 5.1 Session setup screen

The screen shall provide:

- Session name.
- Player list.
- Clockwise seating arrangement.
- Initial stack for each player.
- Small and big blind values.
- Chip denomination.
- Initial dealer selection.
- Start-session action.

### 5.2 Live hand screen

The live screen shall prioritize quick actions and readable values.

It shall display:

- Current hand number and street.
- Dealer, small blind, and big blind positions.
- Current player to act.
- Community cards, if revealed.
- Current pot and side-pot summary.
- Current live bet.
- Player names and remaining stacks.
- Each player's current-street and total hand contribution.
- Amount each player needs to call.
- Legal action buttons.

The current player's available actions shall be visually distinct. Illegal actions shall be disabled or rejected with an explanation.

### 5.3 Raise interface

The raise interface shall display:

- Current live bet.
- Minimum legal raise-to amount.
- Raise-to incrementer.
- Total amount the player will commit.
- Remaining stack after the action.
- Confirmation of an all-in action where appropriate.

### 5.4 Pot overview screen

The pot overview shall display the main pot and each side pot separately.

Each pot shall show its amount, contributors, eligible players, and payout status. The interface shall not imply that every player can win every pot.

### 5.5 Showdown screen

The showdown screen shall allow the host to:

- Enter or reveal community cards.
- Optionally enter hole cards.
- Review the main and side pots.
- Select winners for each pot.
- Confirm payouts.
- Complete the hand.

### 5.6 Session history screen

The history screen shall list completed hands with their results. Selecting a hand shall display its full action sequence, community cards, contributions, pot calculations, and payouts.

### 5.7 Accessibility and usability

- All important controls shall be usable on common mobile screen sizes.
- Text shall remain readable under indoor lighting.
- Controls shall have clear labels and sufficient touch targets.
- Color shall not be the sole indicator of state.
- Critical actions shall provide immediate feedback.
- Undo and correction controls shall be accessible during a hand.

---

## 6. Business Rules and Poker Logic

### 6.1 Current bet and contribution model

The game engine shall maintain, for each player:

- Starting stack for the hand.
- Remaining stack.
- Total hand contribution.
- Current-street contribution.
- Folded status.
- All-in status.
- Whether the player has acted since the most recent full raise.

The engine shall also maintain the current live bet and the minimum legal raise increment.

The amount to call shall be:

`max(0, current live bet - player's current-street contribution)`

The actual amount a player can call shall be capped by their remaining stack.

### 6.2 Betting completion

A street is complete when all non-folded players who can still make a decision have responded to the current betting action, all required contributions have been matched or appropriately capped by all-in stacks, and no legal action remains pending.

If only one player remains after folds, that player wins immediately.

If multiple players remain but no player can make another bet, the game shall proceed to the next required reveal or showdown.

### 6.3 Side-pot algorithm

The engine shall derive pots from total hand contributions, not merely from the most recent betting street.

For the contribution levels of all players, sort the distinct positive contribution amounts in ascending order.

For each level:

1. Determine the difference between the current level and the previous level.
2. Count all players whose total contributions reach at least that level.
3. Multiply the level difference by that player count to calculate the pot layer.
4. Include all contributors at that level, including folded players.
5. Mark only non-folded players who contributed at that level as eligible, subject to the game's valid contribution and all-in rules.
6. Combine adjacent layers only when doing so preserves the same eligibility set and correct accounting.

Unmatched excess shall be returned before final pot distribution when required.

The implementation shall be tested against scenarios involving multiple all-ins, folds, short calls, tied winners, and different contribution levels.

### 6.4 Payout accounting

For every completed hand:

`Total contributions = Total payouts + Returned unmatched contributions`

All amounts shall be exact integer chip values.

For a split pot, the application shall distribute the pot according to the selected odd-chip rule. The initial odd-chip rule shall be documented and configurable if the group uses a different house convention.

---

## 7. Data Model

The following entities define the minimum logical data model.

### 7.1 Session

Fields:

- `sessionId`
- `name`
- `createdAt`
- `status`
- `chipValueInMinorUnits`
- `smallBlind`
- `bigBlind`
- `startingDealerPlayerId`
- `currentDealerPlayerId`
- `rulesVersion`

### 7.2 Player

Fields:

- `playerId`
- `displayName`
- `createdAt`

### 7.3 SessionPlayer

Fields:

- `sessionPlayerId`
- `sessionId`
- `playerId`
- `seatIndex`
- `startingStack`
- `currentStack`
- `activeStatus`

### 7.4 Hand

Fields:

- `handId`
- `sessionId`
- `handNumber`
- `dealerPlayerId`
- `smallBlindPlayerId`
- `bigBlindPlayerId`
- `currentStreet`
- `status`
- `communityCards`
- `startedAt`
- `completedAt`

### 7.5 HandPlayer

Fields:

- `handPlayerId`
- `handId`
- `playerId`
- `startingStack`
- `remainingStack`
- `totalContribution`
- `streetContribution`
- `folded`
- `allIn`
- `holeCards`, nullable

### 7.6 ActionEvent

Fields:

- `actionId`
- `handId`
- `sequenceNumber`
- `playerId`, nullable for system actions
- `street`
- `actionType`
- `amount`
- `createdAt`
- `actionPayload`
- `reversalOfActionId`, nullable

Actions shall be append-only at the history level. Undo shall create a reversal or a new consistent history version rather than silently deleting evidence of a committed action.

### 7.7 Pot

Fields:

- `potId`
- `handId`
- `potType`
- `amount`
- `eligiblePlayerIds`
- `contributorBreakdown`
- `status`

Pot values may be calculated from action history and cached for display. Derived pot records shall not become an independent source of truth that conflicts with the recorded contributions.

### 7.8 Payout

Fields:

- `payoutId`
- `handId`
- `potId`
- `playerId`
- `amount`
- `payoutType`

### 7.9 Persistence requirements

The database shall support transactions for committing actions and their resulting state. Database schema changes shall use explicit migrations.

The app shall store enough information to rebuild the current game state from the action history. A corrupted or inconsistent state shall be detected rather than silently accepted.

---

## 8. Architecture

### 8.1 Proposed module structure

`composeApp/`

Shared Compose Multiplatform user interface.

`shared/domain/`

- Poker entities.
- Betting rules.
- Action validation.
- Street transitions.
- Pot and side-pot calculations.
- Payout distribution.
- Game-state reducer.

`shared/data/`

- Room database.
- Database entities and migrations.
- Session repository.
- Hand-history repository.
- Persistence and recovery.

`shared/presentation/`

- View models.
- UI state.
- Action dispatch.
- Session and hand workflows.

`androidApp/`

Android application entry point and platform-specific configuration.

`iosApp/`

iOS application target, lifecycle integration, and shared Kotlin framework integration.

### 8.2 Game engine

The core engine shall expose a validated action interface.

Conceptually:

`applyAction(currentState, action) -> newState or validationError`

The engine shall:

1. Validate the requested action.
2. Confirm that the correct player is acting.
3. Validate the action amount and betting rules.
4. Apply the action to an immutable state.
5. Recalculate derived betting and pot information.
6. Determine whether the street or hand has completed.
7. Produce a deterministic state transition.
8. Persist the action and resulting state atomically.

The engine shall be independent of Compose, Android APIs, UIKit, and the database implementation.

### 8.3 Offline-first operation

All required session and hand data shall be stored locally. The app shall not require network access to create sessions, record actions, calculate pots, or view history.

Cloud backup and multi-device synchronization may be introduced in a later release, but they shall not be required by the initial architecture.

---

## 9. Non-Functional Requirements

### 9.1 Correctness

- All betting rules shall be deterministic.
- Illegal actions shall not mutate the committed state.
- Pot calculations shall be reproducible from the action history.
- Chip accounting shall reconcile after every completed hand.
- The same action sequence shall produce the same result on Android and iOS.

### 9.2 Performance

On a supported mid-range device:

- Common betting actions should update the interface within 100 ms under normal conditions.
- Pot recalculation should complete within 100 ms for a typical home game.
- Session history should load within 1 second for a session containing 1,000 recorded actions, subject to device performance.

These are acceptance targets to be measured during testing.

### 9.3 Reliability

- Every committed action shall survive normal application termination.
- A failed transaction shall not leave a partially applied action.
- The app shall restore the last consistent state after a crash.
- Undo shall restore the correct prior game state.

### 9.4 Security and privacy

- No account or internet connection shall be required.
- Session records shall remain local unless the user explicitly exports them or opts into a future synchronization feature.
- The app shall not collect unnecessary personal information.
- Exported session files shall not contain sensitive device information.

### 9.5 Maintainability

- Core rules shall be covered by automated tests.
- Dependencies shall be pinned and upgraded deliberately.
- Database migrations shall be tested.
- Platform-specific UI code shall not duplicate betting logic.
- Business rules and house-rule deviations shall be documented.

---

## 10. Testing and Acceptance Criteria

### 10.1 Session setup

- A session can be created with at least two players.
- The dealer, small blind, and big blind positions are assigned correctly.
- Dealer rotation works across consecutive hands.
- Heads-up blind assignment follows the correct exception.

### 10.2 Betting

- A check is rejected when a player owes chips.
- A call correctly calculates the difference between the current live bet and the player's contribution.
- A raise updates the current live bet and all outstanding amounts to call.
- An illegal minimum raise is rejected unless it is a valid short all-in.
- A folded player cannot act again in the same hand.
- A street does not end while a valid response remains pending.

### 10.3 Street transitions

- The flop contains exactly three community cards.
- The turn adds exactly one card.
- The river adds exactly one card.
- Street contributions reset at each transition.
- Total hand contributions remain unchanged when a new street begins.
- A hand ends immediately when all but one player fold.
- All-in situations advance without requesting impossible betting actions.

### 10.4 Side-pot scenarios

**Scenario A: Four players with different contributions**

- Player A contributes 50 chips.
- Player B contributes 120 chips.
- Player C contributes 200 chips.
- Player D contributes 200 chips.

Expected results:

- Main pot: 200 chips, eligible players A, B, C, and D.
- Side pot 1: 210 chips, eligible players B, C, and D.
- Side pot 2: 160 chips, eligible players C and D.
- Total: 570 chips.

**Scenario B: Folded contributor**

A player contributes chips and later folds. The contribution remains in the pot, but the folded player cannot win any pot.

**Scenario C: Uncalled excess**

A player commits more chips than any opponent can match. The unmatched excess is returned, and the contested pots are calculated using the matched contribution levels.

**Scenario D: Multiple all-ins**

Three or more players go all-in with different stack sizes. The application calculates the main and all applicable side pots independently.

**Scenario E: Split pot**

Two eligible players tie for a pot. The application divides the pot correctly and applies the configured odd-chip rule.

### 10.5 Persistence and recovery

- Force-closing the application after a committed action preserves that action.
- Reopening a session restores stacks, street, player turn, contributions, and pot calculations.
- Undo restores the correct previous state.
- Database migrations preserve existing sessions.
- A failed action transaction leaves the game in its previous valid state.

### 10.6 Cross-platform acceptance

The same domain-layer test suite shall run for the Android/JVM and iOS/Native targets wherever supported.

A shared test scenario shall produce identical action histories, chip stacks, pot eligibility, and payouts on both platforms.

---

## 11. Development Plan

### Phase 1 — Project foundation

- Create the Kotlin Multiplatform project.
- Configure the pinned toolchain.
- Build and run Android and iOS starter applications.
- Establish the shared module structure.
- Set up automated tests and continuous integration where feasible.

**Exit criteria:** Both platform builds work, and the shared module can be tested.

### Phase 2 — Poker domain engine

- Define session, player, hand, street, and action models.
- Implement blinds, dealer rotation, and turn order.
- Implement check, fold, call, bet, raise, and all-in.
- Implement legal-action validation and street completion.
- Implement main-pot and side-pot calculation.
- Implement payout and chip reconciliation.

**Exit criteria:** Domain tests pass for normal betting, raises, all-ins, folds, and split pots.

### Phase 3 — Local persistence

- Implement the Room database.
- Implement repositories and transactions.
- Persist action history.
- Implement recovery and undo.
- Add schema migrations and persistence tests.

**Exit criteria:** Sessions survive application restart and state can be reconstructed reliably.

### Phase 4 — Core interface

- Build session setup.
- Build player seating and dealer selection.
- Build the live hand screen.
- Build betting incrementers and action controls.
- Build the pot overview and showdown screens.
- Build history, undo, and correction workflows.

**Exit criteria:** A complete hand can be recorded through the interface.

### Phase 5 — Integration and testing

- Validate complete hands from pre-flop through river.
- Test complex all-in and side-pot cases.
- Test interruption and recovery.
- Test the same scenarios on Android and iOS.
- Conduct real-world trials with a home poker group.

**Exit criteria:** All critical acceptance tests pass on both platforms.

### Phase 6 — Release preparation

- Finalize application identity and icons.
- Prepare Android release signing.
- Configure iOS signing and distribution.
- Test supported operating-system versions.
- Prepare privacy information and store listings.
- Create a versioned release and backup/export guidance.

**Exit criteria:** The application can be distributed and installed on supported Android and iOS devices.

---

## 12. Future Enhancements

The following features may be considered after the initial release:

- Multiple devices joining the same session.
- QR-code session joining.
- Automatic card-based winner evaluation.
- Exportable hand histories.
- Session statistics and leaderboards.
- Configurable house rules.
- Blind timers and tournament mode.
- Optional encrypted backups and cloud synchronization.

These enhancements shall not delay the initial delivery of the complete local hand tracker.

---

## 13. Final Acceptance Definition

Version 1.0 shall be considered complete when a host can:

1. Create a session and enter players in clockwise order.
2. Select the initial dealer and configure blinds.
3. Start a hand and automatically post the blinds.
4. Record every legal betting action from pre-flop through river.
5. See each player's stack, contributions, and amount to call.
6. Advance streets only when betting is complete.
7. Handle folds, all-ins, unmatched contributions, and multiple side pots correctly.
8. Record a showdown and distribute the pots.
9. Review the complete hand history and correct mistakes.
10. Start the next hand with the correct dealer rotation and updated stacks.
11. Close and reopen the application without losing the session.
12. Perform the same workflows on Android and iOS with equivalent game-engine results.

**The governing principle is that the recorded action history is authoritative.** The visible stacks, betting state, pot breakdown, and payouts must always be consistent with that history.
