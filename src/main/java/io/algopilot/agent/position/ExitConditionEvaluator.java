package io.algopilot.agent.position;

import io.algopilot.agent.context.TradingContext;
import io.algopilot.agent.decision.TradeAction;
import java.math.BigDecimal;
import org.springframework.stereotype.Component;

@Component
public class ExitConditionEvaluator {
  private final StopLossManager stopLossManager;
  private final TakeProfitManager takeProfitManager;
  private final TrailingStopManager trailingStopManager;

  public ExitConditionEvaluator(
      StopLossManager stopLossManager,
      TakeProfitManager takeProfitManager,
      TrailingStopManager trailingStopManager
  ) {
    this.stopLossManager = stopLossManager;
    this.takeProfitManager = takeProfitManager;
    this.trailingStopManager = trailingStopManager;
  }

  public PositionExitEvaluation evaluate(
      PositionLifecycleRecord pos,
      BigDecimal currentPrice,
      TradingContext context
  ) {
    if (pos == null || !pos.isOpen() || currentPrice == null || currentPrice.compareTo(BigDecimal.ZERO) <= 0) {
      return PositionExitEvaluation.noExit();
    }

    // 1. Emergency Stop Check
    if (context != null && context.risk() != null && context.risk().isEmergencyStopped()) {
      return new PositionExitEvaluation(
          true, TradeAction.CLOSE, ExitReason.EMERGENCY_STOP, pos.currentQuantity(), currentPrice,
          "EMERGENCY STOP TRIGGERED: Global kill switch active."
      );
    }

    // 2. Reconciliation Safety Check
    if (context != null && context.reconciliation() != null && (context.reconciliation().criticalMismatchCount() > 0 || context.reconciliation().recoveryRequired())) {
      return new PositionExitEvaluation(
          true, TradeAction.CLOSE, ExitReason.RECONCILIATION_BLOCK, pos.currentQuantity(), currentPrice,
          "RECONCILIATION BLOCK: Critical broker state mismatch detected."
      );
    }

    // 3. Hard Stop Loss Evaluation
    if (stopLossManager.isStopTriggered(pos.side(), currentPrice, pos.currentStopLoss())) {
      return new PositionExitEvaluation(
          true, TradeAction.CLOSE, ExitReason.HARD_STOP_LOSS, pos.currentQuantity(), currentPrice,
          String.format("STOP LOSS TRIGGERED: entry=%.2f, current=%.2f, stop=%.2f",
              pos.entryPrice().doubleValue(), currentPrice.doubleValue(), pos.currentStopLoss().doubleValue())
      );
    }

    // 4. Take Profit Evaluation
    if (takeProfitManager.isTakeProfitTriggered(pos.side(), currentPrice, pos.takeProfit())) {
      return new PositionExitEvaluation(
          true, TradeAction.CLOSE, ExitReason.TAKE_PROFIT, pos.currentQuantity(), currentPrice,
          String.format("TAKE PROFIT TRIGGERED: entry=%.2f, current=%.2f, target=%.2f",
              pos.entryPrice().doubleValue(), currentPrice.doubleValue(), pos.takeProfit().doubleValue())
      );
    }

    // 5. Trailing Stop Evaluation
    if (pos.trailingStopPct() != null && pos.trailingStopPct().compareTo(BigDecimal.ZERO) > 0) {
      BigDecimal trailingStop = trailingStopManager.calculateTrailingStop(pos.side(), currentPrice, pos.highWaterMark(), pos.trailingStopPct());
      if (trailingStopManager.isTrailingStopTriggered(pos.side(), currentPrice, trailingStop)) {
        return new PositionExitEvaluation(
            true, TradeAction.CLOSE, ExitReason.TRAILING_STOP, pos.currentQuantity(), currentPrice,
            String.format("TRAILING STOP TRIGGERED: hwm=%.2f, current=%.2f, trailingStop=%.2f",
                pos.highWaterMark() != null ? pos.highWaterMark().doubleValue() : currentPrice.doubleValue(),
                currentPrice.doubleValue(), trailingStop.doubleValue())
        );
      }
    }

    // 6. Strategy Invalidation Evaluation (e.g. extreme adverse indicator shift)
    if (context != null && context.indicators() != null && context.indicators().rsi14() != null) {
      if ("BUY".equalsIgnoreCase(pos.side()) && context.indicators().rsi14().compareTo(new BigDecimal("20.00")) < 0) {
        return new PositionExitEvaluation(
            true, TradeAction.CLOSE, ExitReason.STRATEGY_INVALIDATION, pos.currentQuantity(), currentPrice,
            "STRATEGY INVALIDATION: Momentum collapsed (RSI < 20)."
        );
      }
    }

    return PositionExitEvaluation.noExit();
  }
}
