# Backtest Methodology & Friction Simulation

## 1. Look-Ahead Bias Prevention
- All entry and exit signals evaluate strictly on closed bar data ($t-1$ or bar close).
- Stop losses, take profits, and trailing stops evaluate against intraday high/low excursions without future leakage.

## 2. Realistic Execution Adjustments
- **Effective Entry Price**:
  $$P_{entry,eff} = P_{close} + \frac{\text{Spread}}{2} + (P_{close} \times \text{Slippage})$$
- **Effective Exit Price**:
  $$P_{exit,eff} = P_{close} - \frac{\text{Spread}}{2} - (P_{close} \times \text{Slippage})$$
- **Maker & Taker Fees**: Explicitly calculated and subtracted from cash equity at entry and exit.

## 3. Walk-Forward Windows
- Rolling multi-window partitions where out-of-sample data is strictly quarantined from parameter selection.
