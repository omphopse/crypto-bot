# Economic Viability & Cost-to-Edge Analysis

## 1. True Economic Net Result
A strategy is only economically viable if its edge exceeds not just market fees and slippage, but also AI reasoning, research, and infrastructure overheads:

$$\text{EconomicNetResult} = \text{NetP\&L} - \text{AiInferenceCosts} - \text{ResearchCosts} - \text{EstimatedInfraCosts}$$

## 2. Negative Cost Edge Warning
If a strategy's average gross profit per trade is less than or equal to its average trading frictional cost:
$$\text{GrossEdge} \le \text{TotalFrictionCost}$$
The system flags `NEGATIVE_COST_EDGE` and warns against autonomous deployment regardless of win rate.
