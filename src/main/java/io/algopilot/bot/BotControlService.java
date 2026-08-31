package io.algopilot.bot;

import io.algopilot.audit.AuditEventWriter;
import java.util.Map;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class BotControlService {
  private final BotStore store; private final AuditEventWriter audit;
  public BotControlService(BotStore store, AuditEventWriter audit) { this.store = store; this.audit = audit; }
  @Transactional public Bot pause(UUID id) { return change(id, BotStatus.PAUSED, "BOT_PAUSED"); }
  @Transactional public Bot stop(UUID id) { return change(id, BotStatus.STOPPED, "BOT_STOPPED"); }
  @Transactional public Bot emergencyStop(UUID id) { return change(id, BotStatus.EMERGENCY_STOPPED, "BOT_EMERGENCY_STOPPED"); }
  @Transactional public Bot resume(UUID id) {
    Bot bot = require(id);
    if (bot.status() == BotStatus.EMERGENCY_STOPPED) throw new BotControlException("EMERGENCY_STOP_REQUIRES_RECONCILIATION");
    if (bot.status() != BotStatus.PAUSED) throw new BotControlException("ONLY_PAUSED_BOT_CAN_RESUME");
    return write(bot, BotStatus.RUNNING, "BOT_RESUMED");
  }
  private Bot change(UUID id, BotStatus target, String event) { Bot bot = require(id); if (bot.status() == BotStatus.EMERGENCY_STOPPED && target != BotStatus.EMERGENCY_STOPPED) throw new BotControlException("EMERGENCY_STOP_REQUIRES_RECONCILIATION"); return write(bot, target, event); }
  private Bot require(UUID id) { return store.findById(id).orElseThrow(() -> new BotNotFoundException(id)); }
  private Bot write(Bot before, BotStatus target, String event) { Bot after = store.updateStatus(before.id(), target); audit.record("USER", "single-user", event, "BOT", before.id().toString(), Map.of("from", before.status().name(), "to", target.name())); return after; }
}
