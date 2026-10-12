# Poker Night — Game Rules & Official Terminology Reference

**Variant:** No-Limit Texas Hold'em (Cash Game / Home Session)  
**Standard Deck:** 52 cards (ranks 2-A, 4 suits).

## 1. Terminology

- **Dealer Button (BTN):** Designates the nominal dealer. Moves clockwise after every completed hand.
- **Small Blind (SB):** Forced bet posted by the player directly clockwise from the BTN (in 3+ player games).
- **Big Blind (BB):** Forced bet posted by the player directly clockwise from the SB. Usually 2x SB.
- **Heads-Up Rules (2 players):** The BTN posts the Small Blind and acts first pre-flop. The other player posts the Big Blind. Post-flop, the Big Blind acts first.
- **Street Contribution:** Total chips put in by a player during the active betting round.
- **Total Hand Contribution:** Sum of all chips committed across pre-flop, flop, turn, and river.
- **Live Bet:** The highest current street contribution facing the table.
- **Amount to Call:** `max(0, Live Bet - Player Street Contribution)`.

## 2. Permitted Actions

1. **Check:** Pass the action when `Amount to Call == 0`.
2. **Fold:** Surrender cards and forfeit all claims to any pot.
3. **Call:** Match the outstanding live bet up to the player's remaining stack.
4. **Bet:** First voluntary chips committed on a post-flop street when `Live Bet == 0`.
5. **Raise:** Increase the live bet. Specified as the "raise-to" total street contribution.
6. **All-In:** Commit 100% of the player's remaining stack.

## 3. Betting Limits and Reopening

- **Minimum Full Raise:** The raise increment must be at least equal to the difference of the previous bet/raise on that street:
  `MinRaiseTo = CurrentLiveBet + (CurrentLiveBet - PreviousLiveBet)`
- **Short All-In:** An all-in that does not meet the minimum full raise is still permitted, but does **not** reopen betting to players who have already acted unless subsequent full raises occur.

## 4. Pot Layering & Side Pots

Pots are computed strictly from **Total Hand Contributions**:
1. Distinct contribution levels are ordered from lowest to highest.
2. For level difference `Δ`, pot value increases by `Δ * (number of players reaching this level)`.
3. Folded players contribute chips to the pots, but are marked ineligible to win.
4. Uncalled excess (the difference between the highest contribution and the second-highest contribution when no one calls) is refunded directly to the player before pot resolution.