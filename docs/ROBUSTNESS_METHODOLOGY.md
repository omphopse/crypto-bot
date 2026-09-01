# Robustness Scoring & Multi-Faceted Stress Testing

## 1. Multi-Tier Stress Testing Dimensions
1. **Cost Stress**: Simulations run across $1.0\times, 1.5\times, 2.0\times, 3.0\times$ baseline exchange commissions and spreads.
2. **Slippage Stress**: Frictional slippage scaled across $1.0\times, 1.5\times, 2.0\times, 3.0\times$ market volatility multiples.
3. **Latency Stress**: Execution delays simulated across $10\text{ms}, 50\text{ms}, 100\text{ms}, 250\text{ms}, 500\text{ms}$.

---

## 2. Robustness Classification Tags
- `ROBUST`: Positive net expectancy across all stress tiers and acceptable drawdown.
- `FRAGILE`: Positive baseline return but becomes unprofitable under $1.5\times - 2.0\times$ costs.
- `OVERFIT`: Suspiciously high in-sample performance that collapses during out-of-sample or parameter sweeps.
- `NEGATIVE_COST_EDGE`: Gross profit edge is completely consumed by fees, spread, and slippage.
- `INSUFFICIENT_DATA`: Less than required minimum trade sample ($< 10-30$ trades).
