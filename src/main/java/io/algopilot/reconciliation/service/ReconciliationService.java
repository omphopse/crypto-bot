package io.algopilot.reconciliation.service;

import io.algopilot.audit.AuditEventWriter;
import io.algopilot.bot.Bot;
import io.algopilot.bot.BotNotFoundException;
import io.algopilot.bot.BotStatus;
import io.algopilot.bot.BotStore;
import io.algopilot.bot.ExecutionMode;
import io.algopilot.fill.Fill;
import io.algopilot.fill.FillStore;
import io.algopilot.order.OrderRecord;
import io.algopilot.order.OrderStore;
import io.algopilot.portfolio.Position;
import io.algopilot.portfolio.PositionStore;
import io.algopilot.reconciliation.broker.BrokerStateProvider;
import io.algopilot.reconciliation.broker.BrokerStateSnapshot;
import io.algopilot.reconciliation.engine.LocalStateSnapshot;
import io.algopilot.reconciliation.engine.ReconciliationEngine;
import io.algopilot.reconciliation.model.BotReconciliationStatus;
import io.algopilot.reconciliation.model.MismatchSeverity;
import io.algopilot.reconciliation.model.ReconciliationMismatch;
import io.algopilot.reconciliation.model.ReconciliationResult;
import io.algopilot.reconciliation.model.ReconciliationRun;
import io.algopilot.reconciliation.model.ReconciliationStatus;
import io.algopilot.reconciliation.model.ResolutionState;
import io.algopilot.reconciliation.persistence.ReconciliationStore;
import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class ReconciliationService {
  private static final Logger log = LoggerFactory.getLogger(ReconciliationService.class);

  private final ReconciliationEngine engine;
  private final ReconciliationStore store;
  private final BrokerStateProvider brokerStateProvider;
  private final BotStore botStore;
  private final OrderStore orderStore;
  private final FillStore fillStore;
  private final PositionStore positionStore;
  private final AuditEventWriter audit;
  private final Clock clock;

  public ReconciliationService(
      ReconciliationEngine engine,
      ReconciliationStore store,
      BrokerStateProvider brokerStateProvider,
      BotStore botStore,
      OrderStore orderStore,
      FillStore fillStore,
      PositionStore positionStore,
      AuditEventWriter audit) {
    this(engine, store, brokerStateProvider, botStore, orderStore, fillStore, positionStore, audit, Clock.systemUTC());
  }

  public ReconciliationService(
      ReconciliationEngine engine,
      ReconciliationStore store,
      BrokerStateProvider brokerStateProvider,
      BotStore botStore,
      OrderStore orderStore,
      FillStore fillStore,
      PositionStore positionStore,
      AuditEventWriter audit,
      Clock clock) {
    this.engine = engine;
    this.store = store;
    this.brokerStateProvider = brokerStateProvider;
    this.botStore = botStore;
    this.orderStore = orderStore;
    this.fillStore = fillStore;
    this.positionStore = positionStore;
    this.audit = audit;
    this.clock = clock;
  }

  @Transactional
  public ReconciliationResult reconcile(String botIdStr) {
    UUID botUuid;
    try {
      botUuid = UUID.fromString(botIdStr);
    } catch (IllegalArgumentException e) {
      throw new ReconciliationException("INVALID_BOT_ID:" + botIdStr);
    }

    Bot bot = botStore.findById(botUuid).orElseThrow(() -> new BotNotFoundException(botUuid));
    if (bot.executionMode() == ExecutionMode.LIVE) {
      throw new ReconciliationException("LIVE_TRADING_DISABLED");
    }

    Instant startTime = clock.instant();
    UUID runId = UUID.randomUUID();
    ReconciliationRun run = new ReconciliationRun(
        runId,
        bot.id().toString(),
        bot.broker(),
        bot.executionMode(),
        ReconciliationStatus.STARTED,
        0,
        null,
        startTime,
        null,
        startTime
    );
    store.saveRun(run);
    log.info("RECONCILIATION_STARTED for botId={} runId={}", bot.id(), runId);

    try {
      // 1. Collect local state
      List<OrderRecord> openOrders = orderStore.findOpenOrdersByBotId(bot.id().toString());
      List<Fill> fills = fillStore.findByBotId(bot.id().toString());
      List<Position> positions = positionStore.findByBotId(bot.id().toString());

      BigDecimal calculatedEquity = BigDecimal.ZERO;
      for (Position p : positions) {
        calculatedEquity = calculatedEquity.add(p.quantity().multiply(p.averageEntryPrice()));
      }

      LocalStateSnapshot localSnapshot = new LocalStateSnapshot(
          bot.id().toString(),
          null, // cash not separately tracked per-bot in local store
          null,
          calculatedEquity.signum() > 0 ? calculatedEquity : null,
          openOrders,
          fills,
          positions,
          startTime
      );

      // 2. Fetch broker state via abstraction
      BrokerStateSnapshot brokerSnapshot = brokerStateProvider.fetchSnapshot(
          bot.broker(),
          bot.executionMode(),
          bot.id().toString()
      );

      // 3. Reconcile deterministically
      List<ReconciliationMismatch> mismatches = engine.reconcile(
          runId,
          bot.id().toString(),
          localSnapshot,
          brokerSnapshot
      );

      Instant completeTime = clock.instant();

      if (mismatches.isEmpty()) {
        ReconciliationRun completedRun = new ReconciliationRun(
            runId,
            bot.id().toString(),
            bot.broker(),
            bot.executionMode(),
            ReconciliationStatus.MATCHED,
            0,
            null,
            startTime,
            completeTime,
            startTime
        );
        store.updateRun(completedRun);
        audit.record(
            "SYSTEM",
            bot.id().toString(),
            "RECONCILIATION_MATCHED",
            "BOT",
            bot.id().toString(),
            Map.of("runId", runId.toString(), "status", "MATCHED")
        );
        log.info("RECONCILIATION_MATCHED for botId={} runId={}", bot.id(), runId);
        return new ReconciliationResult(completedRun, Collections.emptyList());
      } else {
        // Save mismatches
        store.saveMismatches(mismatches);

        ReconciliationRun mismatchedRun = new ReconciliationRun(
            runId,
            bot.id().toString(),
            bot.broker(),
            bot.executionMode(),
            ReconciliationStatus.MISMATCHED,
            mismatches.size(),
            "Mismatches detected: " + mismatches.size(),
            startTime,
            completeTime,
            startTime
        );
        store.updateRun(mismatchedRun);

        boolean hasCritical = mismatches.stream().anyMatch(m -> m.severity() == MismatchSeverity.CRITICAL);
        if (hasCritical) {
          // Pause the affected bot if it is RUNNING
          if (bot.status() == BotStatus.RUNNING) {
            botStore.updateStatus(bot.id(), BotStatus.PAUSED);
            audit.record(
                "SYSTEM",
                bot.id().toString(),
                "BOT_PAUSED_RECONCILIATION",
                "BOT",
                bot.id().toString(),
                Map.of("runId", runId.toString(), "mismatchCount", mismatches.size(), "previousStatus", bot.status().name())
            );
            log.warn("BOT_PAUSED_RECONCILIATION botId={} due to {} critical mismatches", bot.id(), mismatches.size());
          }

          audit.record(
              "SYSTEM",
              bot.id().toString(),
              "RECONCILIATION_MISMATCH_CRITICAL",
              "BOT",
              bot.id().toString(),
              Map.of("runId", runId.toString(), "mismatchCount", mismatches.size())
          );
          log.error("RECONCILIATION_MISMATCH botId={} runId={} count={}", bot.id(), runId, mismatches.size());
        } else {
          log.warn("RECONCILIATION_MISMATCH botId={} runId={} count={} (non-critical)", bot.id(), runId, mismatches.size());
        }

        return new ReconciliationResult(mismatchedRun, mismatches);
      }
    } catch (Exception ex) {
      Instant failTime = clock.instant();
      ReconciliationRun failedRun = new ReconciliationRun(
          runId,
          bot.id().toString(),
          bot.broker(),
          bot.executionMode(),
          ReconciliationStatus.FAILED,
          0,
          ex.getMessage() != null ? ex.getMessage() : ex.getClass().getSimpleName(),
          startTime,
          failTime,
          startTime
      );
      store.updateRun(failedRun);

      // Fail-safe: pause running bot on reconciliation failure
      if (bot.status() == BotStatus.RUNNING) {
        botStore.updateStatus(bot.id(), BotStatus.PAUSED);
        audit.record(
            "SYSTEM",
            bot.id().toString(),
            "BOT_PAUSED_RECONCILIATION",
            "BOT",
            bot.id().toString(),
            Map.of("runId", runId.toString(), "reason", "RECONCILIATION_EXECUTION_FAILURE")
        );
      }

      audit.record(
          "SYSTEM",
          bot.id().toString(),
          "RECONCILIATION_FAILED",
          "BOT",
          bot.id().toString(),
          Map.of("runId", runId.toString(), "error", ex.getMessage() != null ? ex.getMessage() : "UNKNOWN_ERROR")
      );
      log.error("RECONCILIATION_FAILED botId={} runId={} error={}", bot.id(), runId, ex.getMessage(), ex);

      throw new ReconciliationException("RECONCILIATION_FAILED: " + ex.getMessage(), ex);
    }
  }

  public BotReconciliationStatus getBotReconciliationStatus(String botId) {
    Optional<ReconciliationRun> latestRun = store.findLatestRunByBotId(botId);
    List<ReconciliationMismatch> activeMismatches = store.findMismatchesByBotId(botId, ResolutionState.UNRESOLVED);

    int unresolvedCritical = (int) activeMismatches.stream()
        .filter(m -> m.severity() == MismatchSeverity.CRITICAL)
        .count();

    boolean recoveryRequired = unresolvedCritical > 0 || (latestRun.isPresent() && latestRun.get().status() == ReconciliationStatus.MISMATCHED);

    Instant lastMatched = null;
    Instant lastMismatched = null;

    List<ReconciliationRun> recentRuns = store.findRunsByBotId(botId, 10);
    for (ReconciliationRun r : recentRuns) {
      if (r.status() == ReconciliationStatus.MATCHED && lastMatched == null) {
        lastMatched = r.completedAt();
      }
      if (r.status() == ReconciliationStatus.MISMATCHED && lastMismatched == null) {
        lastMismatched = r.completedAt();
      }
    }

    return new BotReconciliationStatus(
        botId,
        latestRun.map(ReconciliationRun::status).orElse(ReconciliationStatus.STARTED),
        unresolvedCritical,
        activeMismatches.size(),
        recoveryRequired,
        lastMatched,
        lastMismatched,
        activeMismatches
    );
  }

  public List<ReconciliationRun> getRecentRuns(int limit) {
    return store.findRecentRuns(Math.min(limit, 100));
  }

  public Optional<ReconciliationRun> getRunById(UUID id) {
    return store.findRunById(id);
  }

  public List<ReconciliationMismatch> getMismatchesForRun(UUID runId) {
    return store.findMismatchesByRunId(runId);
  }

  public List<ReconciliationMismatch> getUnresolvedMismatches(int limit) {
    return store.findUnresolvedMismatches(Math.min(limit, 100));
  }
}
