package io.algopilot.ops.recovery;

import io.algopilot.audit.AuditEventWriter;
import io.algopilot.bot.Bot;
import io.algopilot.bot.BotStatus;
import io.algopilot.bot.BotStore;
import io.algopilot.reconciliation.model.ReconciliationResult;
import io.algopilot.reconciliation.model.ReconciliationStatus;
import io.algopilot.reconciliation.service.ReconciliationService;
import java.time.Clock;
import java.time.Instant;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class RecoveryService {
  private static final Logger log = LoggerFactory.getLogger(RecoveryService.class);

  private final RecoveryStore recoveryStore;
  private final BotStore botStore;
  private final ReconciliationService reconciliationService;
  private final AuditEventWriter audit;
  private final Clock clock;

  public RecoveryService(
      RecoveryStore recoveryStore,
      BotStore botStore,
      ReconciliationService reconciliationService,
      AuditEventWriter audit,
      Clock clock
  ) {
    this.recoveryStore = recoveryStore;
    this.botStore = botStore;
    this.reconciliationService = reconciliationService;
    this.audit = audit;
    this.clock = clock;
  }

  @Transactional
  public RecoveryRun runRecovery(UUID botId, String instanceId, String triggerReason) {
    Instant now = clock.instant();
    UUID runId = UUID.randomUUID();

    log.warn("STARTING_AUTOMATED_RECOVERY for botId={} instanceId={} reason={}", botId, instanceId, triggerReason);
    audit.record("SYSTEM", botId.toString(), "RECOVERY_STARTED", "RECOVERY_RUN", runId.toString(),
        Map.of("instanceId", instanceId, "reason", triggerReason));

    // 1. Initial State: Always ensure bot is safe/paused during recovery
    botStore.updateStatus(botId, BotStatus.PAUSED);

    RecoveryRun run = new RecoveryRun(
        runId, botId, instanceId, RecoveryStatus.STARTED, triggerReason,
        "Step 1: Bot paused for safety; executing broker state reconciliation.", now, null
    );
    recoveryStore.saveRun(run);

    // 2. Perform Reconciliation
    try {
      ReconciliationResult reconResult = reconciliationService.reconcile(botId.toString());

      if (reconResult.run().status() == ReconciliationStatus.MATCHED && reconResult.mismatches().isEmpty()) {
        Instant completedAt = clock.instant();
        RecoveryRun completedRun = new RecoveryRun(
            runId, botId, instanceId, RecoveryStatus.COMPLETED, triggerReason,
            "Recovery clean: Reconciliation matched, position state aligned. Bot is safely paused and ready for resume.",
            now, completedAt
        );
        recoveryStore.saveRun(completedRun);

        audit.record("SYSTEM", botId.toString(), "RECOVERY_COMPLETED", "RECOVERY_RUN", runId.toString(), Map.of());
        log.info("AUTOMATED_RECOVERY_SUCCEEDED for botId={}", botId);
        return completedRun;
      } else {
        Instant failedAt = clock.instant();
        RecoveryRun failedRun = new RecoveryRun(
            runId, botId, instanceId, RecoveryStatus.FAILED, triggerReason,
            "Recovery blocked: " + reconResult.mismatches().size() + " reconciliation mismatches remain. Manual operator inspection required.",
            now, failedAt
        );
        recoveryStore.saveRun(failedRun);

        audit.record("SYSTEM", botId.toString(), "RECOVERY_FAILED", "RECOVERY_RUN", runId.toString(),
            Map.of("mismatchCount", reconResult.mismatches().size()));
        log.error("AUTOMATED_RECOVERY_FAILED for botId={} due to active reconciliation mismatches", botId);
        return failedRun;
      }
    } catch (Exception e) {
      Instant failedAt = clock.instant();
      RecoveryRun failedRun = new RecoveryRun(
          runId, botId, instanceId, RecoveryStatus.FAILED, triggerReason,
          "Recovery error during reconciliation: " + e.getMessage(), now, failedAt
      );
      recoveryStore.saveRun(failedRun);

      audit.record("SYSTEM", botId.toString(), "RECOVERY_FAILED", "RECOVERY_RUN", runId.toString(),
          Map.of("error", e.getMessage() != null ? e.getMessage() : "UNKNOWN_ERROR"));
      log.error("AUTOMATED_RECOVERY_EXCEPTION for botId={}: {}", botId, e.getMessage(), e);
      return failedRun;
    }
  }

  public void handleApplicationStartupRecovery() {
    log.info("DISCOVERING_BOTS_FOR_RESTART_RECOVERY...");
    var allBots = botStore.findAll();
    for (Bot b : allBots) {
      if (b.status() == BotStatus.RUNNING) {
        log.warn("RESTART_RECOVERY: Bot {} was running before process restart. Defaulting to PAUSED/SAFE.", b.id());
        botStore.updateStatus(b.id(), BotStatus.PAUSED);
        audit.record("SYSTEM", b.id().toString(), "BOT_SAFETY_PAUSED", "BOT", b.id().toString(),
            Map.of("reason", "PROCESS_RESTART_SAFE_PAUSE"));
      }
    }
  }
}
