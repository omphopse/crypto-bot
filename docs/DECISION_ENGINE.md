# Structured LLM / Reasoner Decision Engine

## 1. Trust Boundary & Zero Execution Authority
The LLM / Reasoner decision engine serves exclusively as an advisory hypothesis generator.
- **Zero Execution Power**: The decision engine does NOT place orders, does NOT invoke `ExecutionGateway`, and does NOT interface directly with broker adapters.
- **Strict Boundary**:
  `TradingContext` ➔ `LLM / Reasoner` ➔ `StructuredTradeDecision` ➔ `StructuredDecisionValidator` ➔ **PERSIST ONLY**.

## 2. Structured Decision Schema
The output from the reasoner is strictly typed as `StructuredTradeDecision`:
- `decision`: `NO_ACTION`, `BUY`, `SELL`, `HOLD`, `CLOSE`, `REDUCE`.
- `symbol`: Validated against context market symbol.
- `confidence`: Bounded $[0.0, 1.0]$.
- `quantity`, `referencePrice`: Positive numerical values.
- `stopLoss`, `takeProfit`: Directionally validated against market entry.
- `evidenceReferences`: Explicit UUID references to ingested `ResearchEvidence` (synthetic/hallucinated IDs rejected).
- `contextHash`: SHA-256 fingerprint verified against original `TradingContext`.

## 3. Decision Validation Rules
`StructuredDecisionValidator` deterministically enforces:
1. Context ID and context hash matching.
2. Numeric boundary constraints.
3. Symbol and strategy version matching.
4. Stop-loss and take-profit logic ($stopLoss < entry < takeProfit$ for BUY).
5. Evidence provenance (rejects synthetic/hallucinated evidence).
6. Safety gate enforcement (disallows BUY/SELL if safety gates are triggered).

## 4. Cost & Rate Gating
- `AiCostLimiter` enforces rate caps (max 20 requests/minute, max 500 requests/day).
- Daily cost budget caps (default $10.00 USD/day).
- If budget or rate limit is reached, gracefully produces `NO_ACTION` with `ValidationStatus.FAILED`.
