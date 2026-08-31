package io.algopilot.strategy;

import io.algopilot.audit.AuditEventWriter;
import java.time.Clock;
import java.util.Map;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class StrategyService {
  private final StrategyStore store; private final AuditEventWriter audit; private final Clock clock;
  public StrategyService(StrategyStore store, AuditEventWriter audit) { this(store, audit, Clock.systemUTC()); }
  StrategyService(StrategyStore store, AuditEventWriter audit, Clock clock) { this.store = store; this.audit = audit; this.clock = clock; }
  @Transactional public StrategyVersion create(CreateStrategyRequest request) {
    Strategy strategy = store.saveStrategy(new Strategy(UUID.randomUUID(), request.name(), "DRAFT", clock.instant()));
    return createVersion(strategy.id(), request);
  }
  @Transactional public StrategyVersion createVersion(UUID strategyId, CreateStrategyRequest request) {
    int number = store.latestVersionNumber(strategyId) + 1;
    StrategyVersion version = store.saveVersion(new StrategyVersion(UUID.randomUUID(), strategyId, number, request.definition().deepCopy(), request.changeReason(), clock.instant()));
    audit.record("USER", "single-user", "STRATEGY_VERSION_CREATED", "STRATEGY", strategyId.toString(), Map.of("version", number, "reason", request.changeReason()));
    return version;
  }
}
