package io.algopilot.bot;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import io.algopilot.audit.AuditEventWriter;
import java.util.UUID;
import java.util.Optional;
import org.junit.jupiter.api.Test;

class BotServiceTest {
  private final BotService service = new BotService(new Store(), mock(AuditEventWriter.class));
  @Test void deploys_an_alpaca_paper_bot() {
    Bot bot = service.deploy(new DeployBotRequest("BTC Momentum", UUID.randomUUID(), Broker.ALPACA_PAPER, ExecutionMode.PAPER));
    assertThat(bot.status()).isEqualTo(BotStatus.RUNNING); assertThat(bot.executionMode()).isEqualTo(ExecutionMode.PAPER);
  }
  @Test void categorically_rejects_live_trading() {
    assertThatThrownBy(() -> service.deploy(new DeployBotRequest("Unsafe", UUID.randomUUID(), Broker.ALPACA_PAPER, ExecutionMode.LIVE))).isInstanceOf(BotDeploymentException.class).hasMessage("LIVE_TRADING_DISABLED");
  }
  @Test void rejects_provider_mode_mismatch() {
    assertThatThrownBy(() -> service.deploy(new DeployBotRequest("Wrong", UUID.randomUUID(), Broker.BYBIT_DEMO, ExecutionMode.PAPER))).isInstanceOf(BotDeploymentException.class).hasMessage("BROKER_MODE_MISMATCH");
  }
  private static final class Store implements BotStore { public Bot save(Bot bot) { return bot; } public Optional<Bot> findById(UUID id) { return Optional.empty(); } public Bot updateStatus(UUID id, BotStatus status) { throw new UnsupportedOperationException(); } }
}
