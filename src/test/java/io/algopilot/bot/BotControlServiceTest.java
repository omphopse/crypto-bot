package io.algopilot.bot;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import io.algopilot.audit.AuditEventWriter;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class BotControlServiceTest {
  @Test void paused_bot_can_resume_but_emergency_stop_cannot() {
    MemoryStore store = new MemoryStore(bot(BotStatus.RUNNING)); BotControlService service = new BotControlService(store, mock(AuditEventWriter.class));
    assertThat(service.pause(store.value.id()).status()).isEqualTo(BotStatus.PAUSED); assertThat(service.resume(store.value.id()).status()).isEqualTo(BotStatus.RUNNING);
    service.emergencyStop(store.value.id()); assertThatThrownBy(() -> service.resume(store.value.id())).isInstanceOf(BotControlException.class).hasMessage("EMERGENCY_STOP_REQUIRES_RECONCILIATION");
  }
  private static Bot bot(BotStatus status) { return new Bot(UUID.randomUUID(), "bot", UUID.randomUUID(), Broker.ALPACA_PAPER, ExecutionMode.PAPER, status, Instant.now()); }
  private static final class MemoryStore implements BotStore { Bot value; MemoryStore(Bot value) { this.value = value; } public Bot save(Bot bot) { return value = bot; } public Optional<Bot> findById(UUID id) { return Optional.ofNullable(value.id().equals(id) ? value : null); } public Bot updateStatus(UUID id, BotStatus status) { return value = new Bot(value.id(), value.name(), value.strategyVersionId(), value.broker(), value.executionMode(), status, value.createdAt()); } }
}
