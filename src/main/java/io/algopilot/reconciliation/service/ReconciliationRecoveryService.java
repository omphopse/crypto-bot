package io.algopilot.reconciliation.service;

import io.algopilot.audit.AuditEventWriter;
import io.algopilot.bot.Bot;
import io.algopilot.bot.BotNotFoundException;
import io.algopilot.bot.BotStatus;
import io.algopilot.bot.BotStore;
import io.algopilot.reconciliation.model.RecoveryRequest;
import io.algopilot.reconciliation.model.RecoveryResult;
import io.algopilot.reconciliation.model.ReconciliationRun;
import io.algopilot.reconciliation.model.ReconciliationStatus;
import io.algopilot.reconciliation.persistence.ReconciliationStore;
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
public class ReconciliationRecoveryService {
  private static final Logger log = LoggerFactory.getLogger(ReconciliationRecoveryService.class);

  private final ReconciliationStore store;
  private final BotStore botStore;
  private final AuditEventWriter audit;
  private final Clock clock;

  @org.springframework.beans.factory.annotation.Autowired
  public ReconciliationRecoveryService(
      ReconciliationStore store,
      BotStore botStore,
      AuditEventWriter audit) {
    this(store, botStore, audit, Clock.systemUTC());
  }

  public ReconciliationRecoveryService(
      ReconciliationStore store,
      BotStore botStore,
      AuditEventWriter audit,
      Clock clock) {
    this.store = store;
    this.botStore = botStore;
    this.audit = audit;
    this.clock = clock;
  }

  @Transactional
  public RecoveryResult recover(RecoveryRequest request) {
    UUID botUuid;
    try {
      botUuid = UUID.fromString(request.botId());
    } catch (IllegalArgumentException e) {
      throw new ReconciliationException("INVALID_BOT_ID:" + request.botId());
    }

    Bot bot = botStore.findById(botUuid).orElseThrow(() -> new BotNotFoundException(botUuid));

    // Invariant: Emergency-stopped bot CANNOT resume or recover via standard reconciliation recovery
    if (bot.status() == BotStatus.EMERGENCY_STOPPED) {
      throw new ReconciliationException("EMERGENCY_STOP_CANNOT_RESUME_VIA_RECONCILIATION");
    }

    if (bot.status() != BotStatus.PAUSED) {
      throw new ReconciliationException("BOT_NOT_PAUSED: Bot must be PAUSED to perform recovery, current status is " + bot.status());
    }

    // Check latest reconciliation run
    Optional<ReconciliationRun> latestRunOpt = store.findLatestRunByBotId(bot.id().toString());
    if (latestRunOpt.isEmpty()) {
      throw new ReconciliationException("NO_RECONCILIATION_RUN_FOUND: Must execute reconciliation before recovery");
    }

    ReconciliationRun latestRun = latestRunOpt.get();
    Instant now = clock.instant();
    UUID recoveryId = UUID.randomUUID();
    String operator = request.operatorId() != null && !request.operatorId().isBlank() ? request.operatorId() : "operator";
    String reason = request.reason() != null && !request.reason().isBlank() ? request.reason() : "Explicit operator recovery and state synchronization";

    // Mark historical unresolved mismatches as resolved
    store.resolveAllUnresolvedMismatchesForBot(bot.id().toString(), now);

    // Save synchronized post-recovery MATCHED run
    UUID postRunId = UUID.randomUUID();
    ReconciliationRun recoveredRun = new ReconciliationRun(
        postRunId,
        bot.id().toString(),
        bot.broker(),
        bot.executionMode(),
        ReconciliationStatus.MATCHED,
        0,
        "State synchronized via operator recovery " + recoveryId,
        now,
        now,
        now
    );
    store.saveRun(recoveredRun);

    // Record recovery
    store.saveRecovery(
        recoveryId,
        bot.id().toString(),
        latestRun.id(),
        operator,
        "COMPLETED",
        reason,
        now
    );

    audit.record(
        "USER",
        operator,
        "RECOVERY_COMPLETED",
        "BOT",
        bot.id().toString(),
        Map.of("runId", latestRun.id().toString(), "recoveryId", recoveryId.toString(), "reason", reason)
    );

    log.info("RECOVERY_COMPLETED for botId={} by operator={} recoveryId={}", bot.id(), operator, recoveryId);

    return new RecoveryResult(
        recoveryId,
        bot.id().toString(),
        "COMPLETED",
        "Bot state successfully recovered and synchronized. Discrepancies resolved.",
        now
    );
  }
}
