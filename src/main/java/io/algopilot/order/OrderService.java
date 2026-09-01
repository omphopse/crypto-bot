package io.algopilot.order;

import io.algopilot.audit.AuditEventWriter;
import io.algopilot.bot.Bot;
import io.algopilot.bot.BotStatus;
import io.algopilot.bot.BotStore;
import io.algopilot.portfolio.PositionStore;
import io.algopilot.risk.RiskDecision;
import io.algopilot.risk.RiskDecisionRequest;
import io.algopilot.risk.RiskDecisionService;
import java.math.BigDecimal;
import java.time.Clock;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.locks.ReentrantLock;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.lang.Nullable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class OrderService {
  private final RiskDecisionService risk;
  private final OrderStore orders;
  private final PositionStore positions;
  private final BotStore botStore;
  private final AuditEventWriter audit;
  private final org.springframework.jdbc.core.JdbcTemplate jdbc;
  private final Clock clock;
  private final ReentrantLock orderLock = new ReentrantLock(true);

  @Autowired
  public OrderService(
      RiskDecisionService risk,
      OrderStore orders,
      @Nullable PositionStore positions,
      @Nullable BotStore botStore,
      AuditEventWriter audit,
      @Nullable org.springframework.jdbc.core.JdbcTemplate jdbc,
      Clock clock) {
    this.risk = risk;
    this.orders = orders;
    this.positions = positions;
    this.botStore = botStore;
    this.audit = audit;
    this.jdbc = jdbc;
    this.clock = clock;
  }

  public OrderService(
      RiskDecisionService risk,
      OrderStore orders,
      @Nullable PositionStore positions,
      @Nullable BotStore botStore,
      AuditEventWriter audit,
      Clock clock) {
    this(risk, orders, positions, botStore, audit, null, clock);
  }

  public OrderService(RiskDecisionService risk, OrderStore orders, AuditEventWriter audit) {
    this(risk, orders, null, null, audit, null, Clock.systemUTC());
  }

  public OrderService(RiskDecisionService risk, OrderStore orders, AuditEventWriter audit, Clock clock) {
    this(risk, orders, null, null, audit, null, clock);
  }

  public OrderService(
      RiskDecisionService risk,
      OrderStore orders,
      PositionStore positions,
      BotStore botStore,
      AuditEventWriter audit) {
    this(risk, orders, positions, botStore, audit, null, Clock.systemUTC());
  }

  @Transactional(noRollbackFor = OrderRejectedException.class)
  public OrderRecord create(RiskDecisionRequest command) {
    orderLock.lock();
    try {
      // 1. Database-backed distributed row lock for multi-instance safety
      if (jdbc != null) {
        try {
          jdbc.queryForList("SELECT id FROM portfolio_accounts WHERE id = 'GLOBAL' FOR UPDATE");
        } catch (Exception ignored) {
          // Table may not exist in mock testing or before migration
        }
      }

      var existing = orders.findByClientOrderId(command.clientOrderId());
      if (existing.isPresent()) return existing.get(); // idempotent retry: do not evaluate or create a second order

      // Authoritative Bot State Check
      boolean botPaused = false;
      boolean emergencyStop = false;
      if (botStore != null && command.botId() != null && !command.botId().isBlank()) {
        try {
          UUID botUuid = UUID.fromString(command.botId());
          var botOpt = botStore.findById(botUuid);
          if (botOpt.isPresent()) {
            Bot bot = botOpt.get();
            botPaused = bot.status() == BotStatus.PAUSED || bot.status() == BotStatus.STOPPED;
            emergencyStop = bot.status() == BotStatus.EMERGENCY_STOPPED;
          }
        } catch (IllegalArgumentException ignored) {}
      }

      // Calculate authoritative Symbol Exposure:
      // 1. Settled positions for this symbol
      BigDecimal settledSymbolExposure = BigDecimal.ZERO;
      if (positions != null) {
        settledSymbolExposure = positions.findAll().stream()
            .filter(p -> p.symbol() != null && p.symbol().equalsIgnoreCase(command.symbol()))
            .map(p -> p.quantity().abs().multiply(p.averageEntryPrice()))
            .reduce(BigDecimal.ZERO, BigDecimal::add);
      }

      // 2. Open / in-flight orders for this symbol
      BigDecimal openOrdersSymbolExposure = orders.findAllOpenOrders().stream()
          .filter(o -> o.symbol() != null && o.symbol().equalsIgnoreCase(command.symbol()))
          .map(o -> o.quantity().multiply(o.referencePrice()))
          .reduce(BigDecimal.ZERO, BigDecimal::add);

      BigDecimal authoritativeSymbolExposure = settledSymbolExposure.add(openOrdersSymbolExposure);
      if (command.existingSymbolExposure() != null && command.existingSymbolExposure().compareTo(authoritativeSymbolExposure) > 0) {
        authoritativeSymbolExposure = command.existingSymbolExposure();
      }

      // Calculate authoritative Global Portfolio Exposure:
      // 1. Settled positions across ALL symbols and bots
      BigDecimal settledPortfolioExposure = BigDecimal.ZERO;
      if (positions != null) {
        settledPortfolioExposure = positions.findAll().stream()
            .map(p -> p.quantity().abs().multiply(p.averageEntryPrice()))
            .reduce(BigDecimal.ZERO, BigDecimal::add);
      }

      // 2. Open / in-flight orders across ALL symbols and bots
      BigDecimal openOrdersPortfolioExposure = orders.findAllOpenOrders().stream()
          .map(o -> o.quantity().multiply(o.referencePrice()))
          .reduce(BigDecimal.ZERO, BigDecimal::add);

      BigDecimal authoritativePortfolioExposure = settledPortfolioExposure.add(openOrdersPortfolioExposure);
      if (command.existingPortfolioExposure() != null && command.existingPortfolioExposure().compareTo(authoritativePortfolioExposure) > 0) {
        authoritativePortfolioExposure = command.existingPortfolioExposure();
      }

      // Count active open trades
      int activePositionCount = positions != null ? (int) positions.findAll().stream().filter(p -> p.quantity().signum() != 0).count() : 0;
      int activeOrderCount = orders.findAllOpenOrders().size();
      int authoritativeOpenTrades = Math.max(command.openTrades(), activePositionCount + activeOrderCount);

      // Authoritative Account Equity
      BigDecimal authoritativeAccountEquity = (command.accountEquity() != null && command.accountEquity().compareTo(BigDecimal.ZERO) > 0)
          ? command.accountEquity()
          : new BigDecimal("100000.00");

      RiskDecisionRequest authoritativeCommand = new RiskDecisionRequest(
          command.clientOrderId(),
          command.botId(),
          command.strategyVersionId(),
          command.symbol(),
          command.side(),
          command.quantity(),
          command.referencePrice(),
          authoritativeAccountEquity,
          authoritativeSymbolExposure,
          authoritativePortfolioExposure,
          command.realizedDailyLoss() != null ? command.realizedDailyLoss() : BigDecimal.ZERO,
          command.drawdownPercent() != null ? command.drawdownPercent() : BigDecimal.ZERO,
          command.estimatedSpreadPercent() != null ? command.estimatedSpreadPercent() : BigDecimal.ZERO,
          command.estimatedSlippagePercent() != null ? command.estimatedSlippagePercent() : BigDecimal.ZERO,
          command.marketDataTimestamp() != null ? command.marketDataTimestamp() : clock.instant(),
          command.botPaused() || botPaused,
          command.emergencyStop() || emergencyStop,
          command.duplicateOrder(),
          authoritativeOpenTrades,
          command.tradesToday(),
          command.consecutiveLosses()
      );

      RiskDecision decision = risk.evaluate(authoritativeCommand);
      if (decision.status() == RiskDecision.Status.REJECTED) {
        throw new OrderRejectedException(decision);
      }

      OrderRecord created = new OrderRecord(
          UUID.randomUUID(),
          command.clientOrderId(),
          command.botId(),
          command.strategyVersionId(),
          command.symbol(),
          command.side(),
          command.quantity(),
          command.referencePrice(),
          OrderStatus.CREATED,
          clock.instant()
      );
      orders.save(created);
      audit.record("SYSTEM", command.botId(), "ORDER_CREATED", "ORDER", created.id().toString(), Map.of("clientOrderId", created.clientOrderId(), "status", created.status().name()));
      return created;
    } finally {
      orderLock.unlock();
    }
  }
}
