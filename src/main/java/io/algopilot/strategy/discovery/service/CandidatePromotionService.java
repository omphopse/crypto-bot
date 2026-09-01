package io.algopilot.strategy.discovery.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import io.algopilot.audit.AuditEventWriter;
import io.algopilot.bot.Bot;
import io.algopilot.bot.BotStatus;
import io.algopilot.bot.BotStore;
import io.algopilot.bot.Broker;
import io.algopilot.bot.ExecutionMode;
import io.algopilot.strategy.CreateStrategyRequest;
import io.algopilot.strategy.StrategyService;
import io.algopilot.strategy.StrategyVersion;
import io.algopilot.strategy.discovery.model.CandidateStatus;
import io.algopilot.strategy.discovery.model.RobustnessTag;
import io.algopilot.strategy.discovery.model.StrategyCandidate;
import io.algopilot.strategy.discovery.persistence.CandidateStore;
import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class CandidatePromotionService {
  private static final Logger log = LoggerFactory.getLogger(CandidatePromotionService.class);

  private final CandidateStore candidateStore;
  private final StrategyService strategyService;
  private final BotStore botStore;
  private final AuditEventWriter audit;
  private final ObjectMapper objectMapper;
  private final Clock clock;

  public record QualificationCheckResult(
      boolean qualified,
      StrategyCandidate candidate,
      String failureReason
  ) {}

  public record CanaryDeploymentResult(
      UUID candidateId,
      UUID strategyVersionId,
      UUID botId,
      String broker,
      String executionMode,
      String candidateName,
      Instant deployedAt,
      String status
  ) {}

  public CandidatePromotionService(
      CandidateStore candidateStore,
      StrategyService strategyService,
      BotStore botStore,
      AuditEventWriter audit,
      ObjectMapper objectMapper,
      Clock clock
  ) {
    this.candidateStore = candidateStore;
    this.strategyService = strategyService;
    this.botStore = botStore;
    this.audit = audit;
    this.objectMapper = objectMapper;
    this.clock = clock;
  }

  public QualificationCheckResult selectQualifiedCandidate() {
    List<StrategyCandidate> allCandidates = candidateStore.findAllCandidates();
    for (StrategyCandidate c : allCandidates) {
      if (c.robustnessClassification() == RobustnessTag.ROBUST &&
          (c.status() == CandidateStatus.PAPER_PENDING || c.status() == CandidateStatus.QUALIFIED || c.status() == CandidateStatus.GENERATED)) {

        if (c.netExpectancy() == null || c.netExpectancy().signum() <= 0) {
          return new QualificationCheckResult(false, c, "NEGATIVE_NET_EXPECTANCY");
        }
        if (c.maxDrawdownPct() != null && c.maxDrawdownPct().compareTo(new BigDecimal("15.0")) > 0) {
          return new QualificationCheckResult(false, c, "EXCESSIVE_MAX_DRAWDOWN");
        }
        if (c.profitFactor() != null && c.profitFactor().compareTo(new BigDecimal("1.20")) < 0) {
          return new QualificationCheckResult(false, c, "PROFIT_FACTOR_BELOW_THRESHOLD");
        }
        if (c.robustnessScore() != null && c.robustnessScore().compareTo(new BigDecimal("70.0")) < 0) {
          return new QualificationCheckResult(false, c, "ROBUSTNESS_SCORE_BELOW_THRESHOLD");
        }
        return new QualificationCheckResult(true, c, "QUALIFIED");
      }
    }
    return new QualificationCheckResult(false, null, "NO_QUALIFIED_CANDIDATE: No candidate satisfied all robustness and stress criteria");
  }

  @Transactional
  public CanaryDeploymentResult promoteToPaper(UUID candidateId) {
    StrategyCandidate cand = candidateStore.findCandidateById(candidateId)
        .orElseThrow(() -> new IllegalArgumentException("Candidate not found: " + candidateId));

    if (cand.robustnessClassification() != RobustnessTag.ROBUST) {
      throw new IllegalStateException("Cannot promote non-ROBUST candidate: classification=" + cand.robustnessClassification());
    }

    Instant now = clock.instant();

    // 1. Create Immutable StrategyVersion
    ObjectNode def = objectMapper.createObjectNode();
    if (cand.parameters() != null) {
      cand.parameters().forEach(def::put);
    }
    CreateStrategyRequest stratReq = new CreateStrategyRequest(
        cand.name(), def, "Promoted from candidate " + candidateId
    );
    StrategyVersion version = strategyService.create(stratReq);

    // 2. Create / Register Alpaca Paper Canary Bot
    UUID botId = UUID.randomUUID();
    Bot canaryBot = new Bot(
        botId,
        "Canary-Alpaca-" + cand.symbol().replace("/", "-"),
        version.id(),
        Broker.ALPACA_PAPER,
        ExecutionMode.PAPER,
        BotStatus.RUNNING,
        now
    );
    botStore.save(canaryBot);

    // 3. Transition Candidate to PAPER_RUNNING
    StrategyCandidate updated = new StrategyCandidate(
        cand.candidateId(), cand.fingerprint(), cand.baseStrategyId(), cand.name(),
        cand.family(), cand.symbol(), cand.timeframe(), cand.parameters(),
        cand.generationMethod(), CandidateStatus.PAPER_RUNNING, cand.robustnessClassification(),
        cand.robustnessScore(), cand.netExpectancy(), cand.profitFactor(), cand.maxDrawdownPct(),
        cand.createdAt()
    );
    candidateStore.saveCandidate(updated);

    // 4. Audit Trail
    audit.record("SYSTEM", "canary-promotion", "CANDIDATE_QUALIFIED_FOR_PAPER", "CANDIDATE", candidateId.toString(),
        Map.of("strategyVersionId", version.id().toString(), "botId", botId.toString(), "broker", Broker.ALPACA_PAPER.name()));

    audit.record("SYSTEM", "canary-promotion", "PAPER_VALIDATION_STARTED", "BOT", botId.toString(),
        Map.of("candidateId", candidateId.toString(), "mode", "PAPER_AUTONOMOUS"));

    log.info("CANDIDATE_PROMOTED_TO_PAPER candidateId={} botId={} versionId={}", candidateId, botId, version.id());

    return new CanaryDeploymentResult(
        candidateId,
        version.id(),
        botId,
        Broker.ALPACA_PAPER.name(),
        ExecutionMode.PAPER.name(),
        cand.name(),
        now,
        "DEPLOYED_TO_PAPER"
    );
  }
}
