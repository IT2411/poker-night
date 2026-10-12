# Architectural Decision Records (ADRs)

## ADR-001: Integer-Only Chip Arithmetic
- **Status:** Accepted
- **Context:** Binary floating-point arithmetic (IEEE 754) introduces rounding inaccuracies in financial tracking.
- **Decision:** All chip values, blind values, and bets are stored as `Long` or `Int` values representing indivisible units (1 unit = 1 chip, fractional denominations stored in minor currency units e.g., paise / cents). Floating point numbers are strictly disallowed in domain logic.

## ADR-002: Action-Sourced Authoritative State
- **Status:** Accepted
- **Context:** Home games require robust undo/correction capabilities and audit trails.
- **Decision:** State transitions are pure deterministic functions derived from sequences of validated `ActionEvent` instances: `reduce(State, Action) -> State`. Stacks and pots are calculated derivatives of this history.

## ADR-003: Shared Domain Engine in Kotlin Multiplatform
- **Status:** Accepted
- **Context:** Both Android and iOS must share 100% identical betting, pot calculation, and validation logic.
- **Decision:** All domain logic, rule evaluators, and side-pot algorithms reside in the `:shared` module without platform-specific dependencies.