# ALGOPILOT — AI Provider Security & Architecture Audit

**Document Date**: September 8, 2026  
**Auditor**: Antigravity Autonomous AI Safety & Security Team  
**Scope**: Verification of Google Gemini LLM Provider, Key Isolation, Structured Output Schema, Fail-Safe Boundaries, and Cost Governance.

---

## 1. Provider Architecture & Authority Boundary

```
   ┌────────────────────────────────────────────────────────┐
   │                   TRADING CONTEXT                      │
   │  - Market Data (Freshness Verified)                    │
   │  - Strategy Definition (Immutable Version ID)          │
   │  - Portfolio & Risk Limits                             │
   │  - Deterministic Scanner Findings                      │
   │  - External Research (Marked UNTRUSTED)                │
   │  - SHA-256 Context Hash Fingerprint                    │
   └──────────────────────────┬─────────────────────────────┘
                              │
                              ▼
   ┌────────────────────────────────────────────────────────┐
   │             REAL GOOGLE GEMINI PROVIDER                │
   │         (`GeminiLLMDecisionProvider.java`)             │
   │  - Model: gemini-2.5-flash / gemini-1.5-flash          │
   │  - Temperature: 0.1 (Deterministic JSON output)        │
   │  - Zero Execution Authority (Proposal Only)            │
   │  - Key isolation from logs, contexts, and DB           │
   └──────────────────────────┬─────────────────────────────┘
                              │
                              ▼
   ┌────────────────────────────────────────────────────────┐
   │             STRUCTURED DECISION VALIDATOR              │
   │         (`StructuredDecisionValidator.java`)           │
   │  - Context Hash Verification (Prevents Replay)         │
   │  - Strategy Version Verification                       │
   │  - Symbol Matching Gate                                │
   │  - Confidence Bounds Gate (0.00 to 1.00)               │
   │  - Sizing & Stop Loss / Take Profit Sanity             │
   │  - Provenance Check on Research References             │
   └──────────────────────────┬─────────────────────────────┘
                              │
                              ▼
   ┌────────────────────────────────────────────────────────┐
   │              DETERMINISTIC RISK ENGINE                 │
   │                (`RiskEngine.java`)                     │
   │  - 20% Single Symbol Cap / 80% Portfolio Cap           │
   │  - 5% Daily Loss Stop / Emergency Kill Switch          │
   └────────────────────────────────────────────────────────┘
```

---

## 2. Security & Secret Isolation Verification

| Security Rule | Verification Evidence | Status |
| :--- | :--- | :---: |
| **No Hardcoded Keys** | `GeminiLLMDecisionProvider.java` reads `GEMINI_API_KEY` strictly via Spring `@Value` injection from environment. Zero API keys stored in Git. | **PASS** |
| **No Secrets in Logs** | All HTTP request/response loggers redact API key query parameters. Non-200 responses log status code without query string. | **PASS** |
| **No Secrets in Trading Context** | `TradingContext.java` contains market observations, strategy parameters, and indicators only. Never contains provider credentials. | **PASS** |
| **No Secrets in Audit Ledger** | `AuditEventWriter.java` serializes decision output and metadata only. Keys are omitted. | **PASS** |
| **Fail-Safe on Missing Key** | When `GEMINI_API_KEY` is missing or blank, the provider returns `TradeAction.NO_ACTION` with `ValidationStatus.FAILED`. No orders are generated. | **PASS** |
| **Fail-Safe on HTTP Errors (429/500)**| Any non-200 HTTP response immediately returns `TradeAction.NO_ACTION` and flags the decision for audit review. | **PASS** |
| **Fail-Safe on Malformed JSON** | If model output fails schema validation, `StructuredDecisionValidator.java` rejects the proposal before it reaches the RiskEngine. | **PASS** |
| **Token & Cost Governance** | `AiCostLimiter.java` enforces hard limits (20 req/min, 500 req/day, \$10.00 daily spend limit). | **PASS** |

---

## 3. Model Token Cost Attribution

* **Input Pricing**: \$0.075 / 1M tokens (\$0.000000075 / token)
* **Output Pricing**: \$0.300 / 1M tokens (\$0.000000300 / token)
* **Typical Request Overhead**:
  * Input tokens: $\approx 450$ tokens ($\$0.0000338$)
  * Output tokens: $\approx 150$ tokens ($\$0.0000450$)
  * **Cost per Cycle**: $\approx \$0.0000788$ ($< 0.01$ cents)
  * **12-Hour 15s Cycle Total Cost**: $2,880 \text{ cycles} \times \$0.0000788 = \mathbf{\$0.227}$ (Well within \$10.00 budget ceiling).
