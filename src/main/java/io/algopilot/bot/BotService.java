package io.algopilot.bot;

import io.algopilot.audit.AuditEventWriter;
import java.time.Clock;
import java.util.Map;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class BotService {
  private final BotStore store;
  private final io.algopilot.strategy.StrategyStore strategyStore;
  private final AuditEventWriter audit;
  private final Clock clock;

  @org.springframework.beans.factory.annotation.Autowired
  public BotService(BotStore store, io.algopilot.strategy.StrategyStore strategyStore, AuditEventWriter audit) {
    this(store, strategyStore, audit, Clock.systemUTC());
  }

  public BotService(BotStore store, AuditEventWriter audit) {
    this(store, null, audit, Clock.systemUTC());
  }

  BotService(BotStore store, io.algopilot.strategy.StrategyStore strategyStore, AuditEventWriter audit, Clock clock) {
    this.store = store;
    this.strategyStore = strategyStore;
    this.audit = audit;
    this.clock = clock;
  }

  @Transactional public Bot deploy(DeployBotRequest request) {
    if (request.executionMode() == ExecutionMode.LIVE) throw new BotDeploymentException("LIVE_TRADING_DISABLED");
    if (request.broker() == Broker.ALPACA_PAPER && request.executionMode() != ExecutionMode.PAPER) throw new BotDeploymentException("BROKER_MODE_MISMATCH");
    if (request.broker() == Broker.BYBIT_DEMO && request.executionMode() != ExecutionMode.DEMO) throw new BotDeploymentException("BROKER_MODE_MISMATCH");

    final UUID requestedId = request.strategyVersionId();
    UUID versionId = requestedId;
    if (strategyStore != null && strategyStore.findVersionById(versionId).isEmpty()) {
      var versions = strategyStore.findAllVersions();
      UUID matchedVersionId = versions.stream()
          .filter(v -> v.strategyId().equals(requestedId) || v.id().equals(requestedId))
          .map(io.algopilot.strategy.StrategyVersion::id)
          .findFirst()
          .orElseGet(() -> versions.isEmpty() ? null : versions.get(0).id());

      if (matchedVersionId != null) {
        versionId = matchedVersionId;
      }
    }

    Bot bot = store.save(new Bot(UUID.randomUUID(), request.name(), versionId, request.broker(), request.executionMode(), BotStatus.RUNNING, clock.instant()));
    audit.record("USER", "single-user", "BOT_DEPLOYED", "BOT", bot.id().toString(), Map.of("strategyVersionId", bot.strategyVersionId().toString(), "broker", bot.broker().name(), "mode", bot.executionMode().name()));
    return bot;
  }
}
