# Strategy Discovery, Candidate Generation & Lifecycle Management

## 1. Overview
The Strategy Discovery subsystem systematically explores quantitative hypotheses across diverse strategy families (`MOMENTUM`, `MEAN_REVERSION`, `BREAKOUT`, `TREND_FOLLOWING`, `VOLATILITY`, `STATISTICAL`) without look-ahead bias or overfitting.

---

## 2. Deterministic Candidate Generation & De-duplication
- **Reproducible Grid Search**: Parameters across fast EMA, slow EMA, RSI thresholds, stops, and take profits are combined deterministically.
- **SHA-256 Fingerprinting**: Unique candidate fingerprint generated on `(family, symbol, timeframe, parameterSet)` prevents duplicate candidate creation.

---

## 3. Candidate Lifecycle State Machine
$$\text{GENERATED} \longrightarrow \text{BACKTESTING} \longrightarrow \text{VALIDATING} \longrightarrow \text{ROBUSTNESS\_CHECK} \longrightarrow \begin{cases} \text{PAPER\_PENDING} \longrightarrow \text{PAPER\_RUNNING} \longrightarrow \text{QUALIFIED} \\ \text{REJECTED} / \text{RETIRED} \end{cases}$$

- **Non-Bypassable Progression**: Candidates can only advance to `PAPER_PENDING` if classified as `ROBUST`.
- **Human Invariant**: Operators retain absolute override authority to reject, pause, or retire candidates at any lifecycle state.
