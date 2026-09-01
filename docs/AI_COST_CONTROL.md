# AI Cost Accounting, Token Attribution & Budget Governance

## 1. Objectives & Principles
- **Financial Bound Invariant**: Prevents autonomous execution loops from incurring unbounded inference or research costs.
- **Fail-Closed Gate**: If cost governance fails or budget status is undetermined, AI requests are rejected while deterministic risk and stop mechanisms remain fully active.

## 2. Granular Token Attribution
Inference token usage is attributed across 7 distinct prompt sections:
- `systemInstructions`: Operational guidelines and guardrails (~300 tokens).
- `marketContext`: Price, bids, asks, OHLC, and indicator values (~250 tokens).
- `strategyContext`: Active version rules and parameters (~150 tokens).
- `portfolioContext`: Cash, market value, exposure, and unrealized P&L (~150 tokens).
- `riskContext`: Drawdown, exposure bounds, and kill-switch state (~100 tokens).
- `researchEvidence`: Verified web/news factor summaries (~250 tokens).
- `outputTokens`: Structured JSON trade hypothesis (~150 tokens).

## 3. Dynamic Budget Throttling
- **`NORMAL`** ($< 70\%$ budget): Standard execution frequency.
- **`THROTTLED`** ($70\% - 99\%$ budget): Throttles research scrape frequency and decision interval.
- **`BLOCKED`** ($\ge 100\%$ budget): Rejects AI inference calls with `AI_BUDGET_EXCEEDED` without compromising open position stops or risk protections.

## 4. Deduplication & Caching
- Prevents duplicate AI inferences for identical `(botId, contextHash, strategyVersionId)` within a 10-second freshness window.
