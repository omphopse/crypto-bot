package io.algopilot.agent.position;

import io.algopilot.adapter.ExecutionGateway;
import io.algopilot.adapter.OrderSubmissionResult;
import io.algopilot.agent.context.ContextBuilderService;
import io.algopilot.agent.context.PositionContext;
import io.algopilot.agent.context.TradingContext;
import io.algopilot.agent.decision.TradeAction;
import io.algopilot.agent.execution.PositionDecisionService;
import io.algopilot.agent.execution.StrategyValidationService;
import io.algopilot.agent.state.AutonomousMode;
import io.algopilot.audit.AuditEventWriter;
import io.algopilot.bot.Bot;
import io.algopilot.bot.BotStore;
import io.algopilot.order.OrderRecord;
import io.algopilot.order.OrderService;
import io.algopilot.portfolio.Position;
import io.algopilot.portfolio.PositionStore;
import io.algopilot.reconciliation.service.ReconciliationService;
import io.algopilot.risk.RiskDecisionRequest;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Clock;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class PositionMonitorService {
  private static final Logger log = LoggerFactory.getLogger(PositionMonitorService.class);

  private final PositionStore positionStore;
  private final PositionLifecycleStore lifecycleStore;
  private final ContextBuilderService contextBuilder;
  private final ExitConditionEvaluator exitEvaluator;
  private final TrailingStopManager trailingStopManager;
  private final PositionDecisionService positionDecisionService;
  private final OrderService orderService;
  private final ExecutionGateway executionGateway;
  private final ReconciliationService reconciliationService;
  private final BotStore botStore;
  private final AuditEventWriter audit;
  private final io.algopilot.fill.FillIngestionService fillIngestionService;
  private final io.algopilot.adapter.alpaca.AlpacaPaperAdapter alpacaAdapter;
  private final Clock clock;

  @org.springframework.beans.factory.annotation.Autowired
  public PositionMonitorService(
      PositionStore positionStore,
      PositionLifecycleStore lifecycleStore,
      ContextBuilderService contextBuilder,
      ExitConditionEvaluator exitEvaluator,
      TrailingStopManager trailingStopManager,
      PositionDecisionService positionDecisionService,
      OrderService orderService,
      ExecutionGateway executionGateway,
      ReconciliationService reconciliationService,
      BotStore botStore,
      AuditEventWriter audit,
      @org.springframework.beans.factory.annotation.Autowired(required = false) io.algopilot.fill.FillIngestionService fillIngestionService,
      @org.springframework.beans.factory.annotation.Autowired(required = false) io.algopilot.adapter.alpaca.AlpacaPaperAdapter alpacaAdapter,
      @org.springframework.beans.factory.annotation.Autowired(required = false) Clock clock
  ) {
    this.positionStore = positionStore;
    this.lifecycleStore = lifecycleStore;
    this.contextBuilder = contextBuilder;
    this.exitEvaluator = exitEvaluator;
    this.trailingStopManager = trailingStopManager;
    this.positionDecisionService = positionDecisionService;
    this.orderService = orderService;
    this.executionGateway = executionGateway;
    this.reconciliationService = reconciliationService;
    this.botStore = botStore;
    this.audit = audit;
    this.fillIngestionService = fillIngestionService;
    this.alpacaAdapter = alpacaAdapter;
    this.clock = clock != null ? clock : Clock.systemUTC();
  }

  public PositionMonitorService(
      PositionStore positionStore,
      PositionLifecycleStore lifecycleStore,
      ContextBuilderService contextBuilder,
      ExitConditionEvaluator exitEvaluator,
      TrailingStopManager trailingStopManager,
      PositionDecisionService positionDecisionService,
      OrderService orderService,
      ExecutionGateway executionGateway,
      ReconciliationService reconciliationService,
      BotStore botStore,
      AuditEventWriter audit,
      Clock clock
  ) {
    this(positionStore, lifecycleStore, contextBuilder, exitEvaluator, trailingStopManager,
        positionDecisionService, orderService, executionGateway, reconciliationService,
        botStore, audit, null, null, clock);
  }

  @Transactional
  public List<PositionExitEvent> monitorBotPositions(UUID botId) {
    Instant now = clock.instant();
    List<PositionExitEvent> exitEvents = new ArrayList<>();

    Optional<Bot> botOpt = botStore.findById(botId);
    if (botOpt.isEmpty()) {
      log.warn("Bot not found for position monitoring: {}", botId);
      return exitEvents;
    }
    Bot bot = botOpt.get();

    TradingContext context = contextBuilder.buildContext(botId);
    List<Position> activePositions = positionStore.findByBotId(botId.toString());

    for (Position p : activePositions) {
      if (p.quantity().compareTo(BigDecimal.ZERO) == 0) continue;

      // 1. Retrieve or initialize PositionLifecycleRecord
      PositionLifecycleRecord lifecycle = lifecycleStore.findLifecycleByPositionId(p.id())
          .orElseGet(() -> {
            BigDecimal stop = p.averageEntryPrice().multiply(new BigDecimal("0.98")).setScale(6, RoundingMode.HALF_UP);
            BigDecimal tp = p.averageEntryPrice().multiply(new BigDecimal("1.05")).setScale(6, RoundingMode.HALF_UP);
            BigDecimal trailPct = new BigDecimal("0.02"); // 2% trailing stop default
            PositionLifecycleRecord created = new PositionLifecycleRecord(
                p.id(), botId, bot.strategyVersionId(), p.symbol(), "BUY",
                p.quantity(), p.quantity(), p.averageEntryPrice(),
                stop, stop, tp, trailPct, p.averageEntryPrice(),
                PositionLifecycleState.MONITORING, now, null, now
            );
            return lifecycleStore.saveLifecycle(created);
          });

      // 2. Current market price
      BigDecimal currentPrice = context.market() != null && context.market().lastPrice().compareTo(BigDecimal.ZERO) > 0
          ? context.market().lastPrice()
          : p.averageEntryPrice();

      // 3. High Water Mark & Trailing Stop Updates
      BigDecimal newHwm = lifecycle.highWaterMark() != null ? lifecycle.highWaterMark().max(currentPrice) : currentPrice;
      BigDecimal newStop = lifecycle.currentStopLoss();
      if (lifecycle.trailingStopPct() != null && lifecycle.trailingStopPct().compareTo(BigDecimal.ZERO) > 0) {
        BigDecimal ratchetedStop = trailingStopManager.calculateTrailingStop(lifecycle.side(), currentPrice, newHwm, lifecycle.trailingStopPct());
        if (ratchetedStop != null && (newStop == null || ratchetedStop.compareTo(newStop) > 0)) {
          newStop = ratchetedStop;
          lifecycleStore.saveStopRecord(new PositionStopRecord(
              UUID.randomUUID(), p.id(), botId, lifecycle.currentStopLoss(), newStop,
              "TRAILING_STOP_RATCHET", null, now
          ));
        }
      }

      lifecycle = new PositionLifecycleRecord(
          lifecycle.positionId(), lifecycle.botId(), lifecycle.strategyVersionId(),
          lifecycle.symbol(), lifecycle.side(), lifecycle.initialQuantity(), lifecycle.currentQuantity(),
          lifecycle.entryPrice(), lifecycle.initialStopLoss(), newStop, lifecycle.takeProfit(),
          lifecycle.trailingStopPct(), newHwm, PositionLifecycleState.MONITORING,
          lifecycle.openedAt(), lifecycle.closedAt(), now
      );
      lifecycleStore.saveLifecycle(lifecycle);

      // 4. Compute P&L, MFE, MAE, holding time
      BigDecimal unrealizedPnl = currentPrice.subtract(p.averageEntryPrice()).multiply(p.quantity());
      BigDecimal mfe = newHwm.subtract(p.averageEntryPrice()).multiply(p.quantity()).max(BigDecimal.ZERO);
      BigDecimal mae = p.averageEntryPrice().subtract(currentPrice).multiply(p.quantity()).max(BigDecimal.ZERO);
      long holdingTimeMs = java.time.Duration.between(lifecycle.openedAt(), now).toMillis();

      PositionSnapshot snapshot = new PositionSnapshot(
          UUID.randomUUID(), p.id(), botId, p.symbol(), p.quantity(), p.averageEntryPrice(),
          currentPrice, currentPrice.multiply(p.quantity()), unrealizedPnl, p.realizedPnl(),
          newStop, lifecycle.takeProfit(), newStop, mfe, mae, holdingTimeMs, now
      );
      lifecycleStore.saveSnapshot(snapshot);

      // 5. Deterministic Exit Evaluation
      PositionExitEvaluation eval = exitEvaluator.evaluate(lifecycle, currentPrice, context);

      // 6. If no deterministic exit, evaluate AI position decision
      if (!eval.shouldExit() && positionDecisionService != null) {
        PositionContext pCtx = new PositionContext(
            p.symbol(), lifecycle.side(), p.quantity(), p.averageEntryPrice(), currentPrice,
            currentPrice.multiply(p.quantity()), p.averageEntryPrice().multiply(p.quantity()),
            unrealizedPnl, p.realizedPnl()
        );
        Optional<TradeAction> aiAction = positionDecisionService.evaluateExit(pCtx, context);
        if (aiAction.isPresent() && aiAction.get() == TradeAction.CLOSE) {
          eval = new PositionExitEvaluation(
              true, TradeAction.CLOSE, ExitReason.AI_DECISION_CLOSE, p.quantity(), currentPrice,
              "AI DECISION CLOSE: Reasoner proposed thesis exit."
          );
        } else if (aiAction.isPresent() && aiAction.get() == TradeAction.REDUCE) {
          BigDecimal halfQty = p.quantity().divide(new BigDecimal("2"), 6, RoundingMode.HALF_UP);
          eval = new PositionExitEvaluation(
              true, TradeAction.REDUCE, ExitReason.AI_DECISION_REDUCE, halfQty, currentPrice,
              "AI DECISION REDUCE: Reasoner proposed 50% risk reduction."
          );
        }
      }

      // 7. Execute Exit if triggered
      if (eval.shouldExit()) {
        log.info("POSITION EXIT TRIGGERED for bot {} symbol {}: {} - {}", botId, p.symbol(), eval.reason(), eval.explanation());

        if (context.autonomousMode() == AutonomousMode.OBSERVE_ONLY) {
          PositionExitEvent obsEvent = new PositionExitEvent(
              UUID.randomUUID(), p.id(), botId, "POSITION_EXIT_OBSERVE_ONLY", eval.reason(),
              eval.exitQuantity(), currentPrice, unrealizedPnl, null, now
          );
          lifecycleStore.saveExitEvent(obsEvent);
          audit.record("AGENT", botId.toString(), "POSITION_EXIT_OBSERVE_ONLY", "POSITION", p.id().toString(),
              Map.of("reason", eval.reason().name(), "explanation", eval.explanation()));
          exitEvents.add(obsEvent);
          continue;
        }

        // PAPER_AUTONOMOUS / DEMO_AUTONOMOUS Execution
        String exitClientOrderId = "exit-" + UUID.randomUUID().toString().substring(0, 8);
        BigDecimal currentHeldQty = p.quantity();
        BigDecimal exitQty = eval.exitQuantity();

        // If performing a full CLOSE and Alpaca adapter is available, align exit quantity with actual broker position
        if (eval.action() == TradeAction.CLOSE && alpacaAdapter != null) {
          try {
            var brokerPositions = alpacaAdapter.fetchPositions(bot.broker(), bot.executionMode(), botId.toString());
            for (var bp : brokerPositions) {
              if (bp.symbol().equalsIgnoreCase(p.symbol()) || bp.symbol().replace("/", "").equalsIgnoreCase(p.symbol().replace("/", ""))) {
                if (bp.quantity().compareTo(BigDecimal.ZERO) > 0) {
                  exitQty = bp.quantity();
                  log.info("PositionMonitorService: Aligned CLOSE quantity with broker held position: {}", exitQty);
                }
                break;
              }
            }
          } catch (Exception bpEx) {
            log.warn("Failed to fetch broker positions for exit quantity alignment: {}", bpEx.getMessage());
          }
        }

        BigDecimal existingExposure = currentHeldQty.multiply(currentPrice);
        RiskDecisionRequest exitReq = new RiskDecisionRequest(
            exitClientOrderId,
            botId.toString(),
            bot.strategyVersionId().toString(),
            p.symbol(),
            RiskDecisionRequest.Side.SELL,
            exitQty,
            currentPrice,
            context.portfolio() != null ? context.portfolio().portfolioEquity() : BigDecimal.ZERO,
            existingExposure,
            context.risk() != null ? context.risk().portfolioExposure() : BigDecimal.ZERO,
            context.risk() != null ? context.risk().dailyLoss() : BigDecimal.ZERO,
            context.risk() != null ? context.risk().currentDrawdown() : BigDecimal.ZERO,
            BigDecimal.ZERO,
            BigDecimal.ZERO,
            now,
            bot.status() == io.algopilot.bot.BotStatus.PAUSED,
            false,
            false,
            context.positions().size(),
            0,
            0
        );

        try {
          OrderRecord exitOrder = orderService.create(exitReq);
          OrderSubmissionResult subResult = executionGateway.dispatch(exitOrder.id());

          PositionExitEvent exitEvent = new PositionExitEvent(
              UUID.randomUUID(), p.id(), botId, "POSITION_EXIT_EXECUTED", eval.reason(),
              exitQty, currentPrice, unrealizedPnl, exitOrder.id(), now
          );
          lifecycleStore.saveExitEvent(exitEvent);
          exitEvents.add(exitEvent);

          audit.record("AGENT", botId.toString(), "POSITION_EXIT_EXECUTED", "POSITION", p.id().toString(),
              Map.of("reason", eval.reason().name(), "orderId", exitOrder.id().toString(), "exchangeOrderId", subResult.exchangeOrderId() != null ? subResult.exchangeOrderId() : ""));

          boolean isFilled = false;
          BigDecimal filledQty = null;
          BigDecimal filledPrice = null;

          // If real broker adapter and fill ingestion service are present, poll for actual broker execution
          if (fillIngestionService != null && alpacaAdapter != null && subResult.exchangeOrderId() != null) {
            try {
              for (int i = 0; i < 5; i++) {
                var brokerOrder = alpacaAdapter.getOrderStatus(exitOrder.clientOrderId(), subResult.exchangeOrderId());
                if (brokerOrder.isPresent() && (brokerOrder.get().status() == io.algopilot.order.OrderStatus.FILLED || brokerOrder.get().status() == io.algopilot.order.OrderStatus.PARTIALLY_FILLED)) {
                  isFilled = true;
                  filledQty = brokerOrder.get().filledQuantity() != null && brokerOrder.get().filledQuantity().compareTo(BigDecimal.ZERO) > 0
                      ? brokerOrder.get().filledQuantity() : exitOrder.quantity();
                  filledPrice = brokerOrder.get().price() != null && brokerOrder.get().price().compareTo(BigDecimal.ZERO) > 0
                      ? brokerOrder.get().price() : currentPrice;

                  String exchangeFillId = null;
                  BigDecimal fee = BigDecimal.ZERO;

                  for (int f = 0; f < 5; f++) {
                    try {
                      var fills = alpacaAdapter.fetchFills(bot.broker(), bot.executionMode(), botId.toString(), clock.instant().minusSeconds(120));
                      if (fills != null) {
                        for (var bf : fills) {
                          if (subResult.exchangeOrderId().equals(bf.brokerOrderId())) {
                            exchangeFillId = bf.exchangeFillId();
                            if (bf.price() != null && bf.price().compareTo(BigDecimal.ZERO) > 0) filledPrice = bf.price();
                            if (bf.quantity() != null && bf.quantity().compareTo(BigDecimal.ZERO) > 0) filledQty = bf.quantity();
                            if (bf.fee() != null) fee = bf.fee();
                            break;
                          }
                        }
                      }
                      if (exchangeFillId != null) break;
                    } catch (Exception ignored) {}
                    try { Thread.sleep(300); } catch (InterruptedException ie) { Thread.currentThread().interrupt(); break; }
                  }

                  if (exchangeFillId == null) {
                    exchangeFillId = "alpaca-sell-fill-" + subResult.exchangeOrderId();
                  }

                  // 1. Authoritative Fill Ingestion -> triggers PositionAccountingService.apply(...)
                  io.algopilot.fill.FillReport report = new io.algopilot.fill.FillReport(exitOrder.id(), exchangeFillId, filledQty, filledPrice, fee);
                  fillIngestionService.ingest(report);
                  log.info("POSITION_EXIT_FILL_INGESTED botId={} orderId={} exchangeFillId={} qty={} price={}",
                      botId, exitOrder.id(), exchangeFillId, filledQty, filledPrice);

                  // 2. Synchronize local position directly with broker reality
                  if (positionStore != null) {
                    try {
                      var brokerPositions = alpacaAdapter.fetchPositions(bot.broker(), bot.executionMode(), botId.toString());
                      boolean brokerStillHasPosition = false;
                      for (var bp : brokerPositions) {
                        if (bp.symbol().equalsIgnoreCase(p.symbol()) || bp.symbol().replace("/", "").equalsIgnoreCase(p.symbol().replace("/", ""))) {
                          brokerStillHasPosition = true;
                          var localPosOpt = positionStore.find(botId.toString(), p.symbol());
                          if (localPosOpt.isPresent()) {
                            var lp = localPosOpt.get();
                            positionStore.save(new io.algopilot.portfolio.Position(
                                lp.id(), lp.botId(), lp.symbol(),
                                bp.quantity(), filledPrice, lp.realizedPnl(), clock.instant()
                            ));
                            log.info("LOCAL_POSITION_RECONCILED_WITH_BROKER_DELIVERY botId={} symbol={} remainingQty={}",
                                botId, p.symbol(), bp.quantity());
                          }
                          break;
                        }
                      }
                      // If broker has zero position for this symbol, ensure local position is 0
                      if (!brokerStillHasPosition) {
                        var localPosOpt = positionStore.find(botId.toString(), p.symbol());
                        if (localPosOpt.isPresent()) {
                          var lp = localPosOpt.get();
                          positionStore.save(new io.algopilot.portfolio.Position(
                              lp.id(), lp.botId(), lp.symbol(),
                              BigDecimal.ZERO, BigDecimal.ZERO, lp.realizedPnl(), clock.instant()
                          ));
                          log.info("LOCAL_POSITION_CONFIRMED_FLAT botId={} symbol={}", botId, p.symbol());
                        }
                      }
                    } catch (Exception syncEx) {
                      log.warn("Post-exit delivery sync notice for order {}: {}", exitOrder.id(), syncEx.getMessage());
                    }
                  }
                  break;
                }
                try { Thread.sleep(250); } catch (InterruptedException ie) { Thread.currentThread().interrupt(); break; }
              }
            } catch (Exception fillEx) {
              log.warn("Exit fill polling/ingestion notice for bot {}: {}", botId, fillEx.getMessage());
            }
          }

          // Update position lifecycle ONLY if confirmed filled (or in mock environment where adapter is null)
          if (isFilled || alpacaAdapter == null) {
            BigDecimal actualReduction = (isFilled && filledQty != null) ? filledQty : exitQty;
            BigDecimal remainingQty = lifecycle.currentQuantity().subtract(actualReduction).max(BigDecimal.ZERO);
            PositionLifecycleState nextState = remainingQty.compareTo(BigDecimal.ZERO) == 0 ? PositionLifecycleState.CLOSED : PositionLifecycleState.MONITORING;

            PositionLifecycleRecord updatedLifecycle = new PositionLifecycleRecord(
                lifecycle.positionId(), lifecycle.botId(), lifecycle.strategyVersionId(),
                lifecycle.symbol(), lifecycle.side(), lifecycle.initialQuantity(), remainingQty,
                lifecycle.entryPrice(), lifecycle.initialStopLoss(), lifecycle.currentStopLoss(),
                lifecycle.takeProfit(), lifecycle.trailingStopPct(), lifecycle.highWaterMark(),
                nextState, lifecycle.openedAt(), nextState == PositionLifecycleState.CLOSED ? now : null, now
            );
            lifecycleStore.saveLifecycle(updatedLifecycle);
          } else {
            log.info("Exit order {} dispatched but fill not yet confirmed by broker. Local position and lifecycle preserved in MONITORING.", exitOrder.id());
          }

          // Post-execution reconciliation
          try {
            if (reconciliationService != null) {
              reconciliationService.reconcile(botId.toString());
            }
          } catch (Exception recEx) {
            log.warn("Reconciliation warning post position exit for bot {}: {}", botId, recEx.getMessage());
          }

        } catch (Exception e) {
          log.error("Failed to execute position exit for bot {} position {}: {}", botId, p.id(), e.getMessage(), e);
          PositionExitEvent failEvent = new PositionExitEvent(
              UUID.randomUUID(), p.id(), botId, "POSITION_EXIT_FAILED", eval.reason(),
              eval.exitQuantity(), currentPrice, BigDecimal.ZERO, null, now
          );
          lifecycleStore.saveExitEvent(failEvent);
          exitEvents.add(failEvent);
        }
      }
    }

    return exitEvents;
  }
}
