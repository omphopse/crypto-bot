package io.algopilot.agent.state;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.algopilot.audit.AuditEventWriter;
import java.time.Clock;
import java.time.Instant;
import java.util.Collections;
import java.util.EnumMap;
import java.util.EnumSet;
import java.util.Map;
import java.util.NoSuchElementException;
import java.util.Set;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

@Service
public class AgentStateMachine {
  private static final Logger log = LoggerFactory.getLogger(AgentStateMachine.class);

  private static final Map<AgentState, Set<AgentState>> VALID_TRANSITIONS;

  static {
    Map<AgentState, Set<AgentState>> m = new EnumMap<>(AgentState.class);
    m.put(AgentState.IDLE, EnumSet.of(AgentState.OBSERVING, AgentState.PAUSED, AgentState.STOPPED, AgentState.ERROR));
    m.put(AgentState.OBSERVING, EnumSet.of(AgentState.SCANNING, AgentState.MONITORING, AgentState.IDLE, AgentState.PAUSED, AgentState.STOPPED, AgentState.ERROR));
    m.put(AgentState.SCANNING, EnumSet.of(AgentState.RESEARCHING, AgentState.ANALYZING, AgentState.IDLE, AgentState.PAUSED, AgentState.STOPPED, AgentState.ERROR));
    m.put(AgentState.RESEARCHING, EnumSet.of(AgentState.ANALYZING, AgentState.IDLE, AgentState.PAUSED, AgentState.STOPPED, AgentState.ERROR));
    m.put(AgentState.ANALYZING, EnumSet.of(AgentState.DECIDING, AgentState.IDLE, AgentState.PAUSED, AgentState.STOPPED, AgentState.ERROR));
    m.put(AgentState.DECIDING, EnumSet.of(AgentState.RISK_CHECK, AgentState.IDLE, AgentState.PAUSED, AgentState.STOPPED, AgentState.ERROR));
    m.put(AgentState.RISK_CHECK, EnumSet.of(AgentState.EXECUTING, AgentState.IDLE, AgentState.PAUSED, AgentState.STOPPED, AgentState.ERROR));
    m.put(AgentState.EXECUTING, EnumSet.of(AgentState.MONITORING, AgentState.IDLE, AgentState.PAUSED, AgentState.STOPPED, AgentState.ERROR));
    m.put(AgentState.MONITORING, EnumSet.of(AgentState.EXIT_EVALUATION, AgentState.OBSERVING, AgentState.IDLE, AgentState.PAUSED, AgentState.STOPPED, AgentState.ERROR));
    m.put(AgentState.EXIT_EVALUATION, EnumSet.of(AgentState.RISK_CHECK, AgentState.MONITORING, AgentState.IDLE, AgentState.PAUSED, AgentState.STOPPED, AgentState.ERROR));
    m.put(AgentState.PAUSED, EnumSet.of(AgentState.IDLE, AgentState.STOPPED, AgentState.ERROR));
    m.put(AgentState.ERROR, EnumSet.of(AgentState.PAUSED, AgentState.STOPPED));
    m.put(AgentState.STOPPED, Collections.emptySet());
    VALID_TRANSITIONS = Collections.unmodifiableMap(m);
  }

  private final AgentStateStore store;
  private final AuditEventWriter audit;
  private final Clock clock;
  private final ObjectMapper json;

  public AgentStateMachine(
      AgentStateStore store,
      AuditEventWriter audit,
      Clock clock,
      ObjectMapper json) {
    this.store = store;
    this.audit = audit;
    this.clock = clock;
    this.json = json;
  }

  public AgentStateMachine(AgentStateStore store, AuditEventWriter audit) {
    this(store, audit, Clock.systemUTC(), new ObjectMapper());
  }

  public boolean isLegalTransition(AgentState from, AgentState to) {
    if (from == null || to == null) return false;
    Set<AgentState> legal = VALID_TRANSITIONS.get(from);
    return legal != null && legal.contains(to);
  }

  public AgentSession startSession(UUID botId, String name, AutonomousMode mode, JsonNode config) {
    if (mode == AutonomousMode.LIVE_LOCKED) {
      throw new IllegalArgumentException("LIVE_LOCKED mode cannot be started. Live trading is strictly disabled.");
    }

    store.findActiveSessionByBotId(botId).ifPresent(active -> {
      log.info("Stopping previous active session {} for bot {}", active.id(), botId);
      stopSession(active.id(), "Superceded by new session");
    });

    Instant now = clock.instant();
    AgentSession session = new AgentSession(
        UUID.randomUUID(),
        botId,
        name,
        AgentState.IDLE,
        mode != null ? mode : AutonomousMode.OBSERVE_ONLY,
        config != null ? config : json.createObjectNode(),
        now,
        now,
        null
    );

    store.saveSession(session);
    audit.record("AGENT", botId.toString(), "AGENT_STARTED", "SESSION", session.id().toString(),
        Map.of("name", name, "mode", session.mode().name(), "state", AgentState.IDLE.name()));

    return session;
  }

  public AgentSession transition(UUID sessionId, AgentState targetState, String reason, JsonNode metadata) {
    AgentSession current = store.findSessionById(sessionId)
        .orElseThrow(() -> new NoSuchElementException("Agent session not found: " + sessionId));

    AgentState fromState = current.currentState();
    if (!isLegalTransition(fromState, targetState)) {
      throw new InvalidStateTransitionException(fromState, targetState, reason);
    }

    Instant now = clock.instant();
    AgentStateEvent event = new AgentStateEvent(
        UUID.randomUUID(),
        sessionId,
        current.botId(),
        fromState,
        targetState,
        reason != null ? reason : "State transition",
        metadata != null ? metadata : json.createObjectNode(),
        now
    );
    store.saveStateEvent(event);

    Instant stoppedAt = (targetState == AgentState.STOPPED) ? now : current.stoppedAt();
    AgentSession updated = new AgentSession(
        current.id(),
        current.botId(),
        current.name(),
        targetState,
        current.mode(),
        current.configuration(),
        current.startedAt(),
        now,
        stoppedAt
    );
    store.saveSession(updated);

    audit.record("AGENT", current.botId().toString(), "AGENT_STATE_TRANSITION", "SESSION", sessionId.toString(),
        Map.of("from", fromState.name(), "to", targetState.name(), "reason", reason != null ? reason : ""));

    log.info("Agent session {} (bot {}) transitioned from {} -> {}: {}", sessionId, current.botId(), fromState, targetState, reason);
    return updated;
  }

  public AgentSession pauseSession(UUID sessionId, String reason) {
    return transition(sessionId, AgentState.PAUSED, reason != null ? reason : "Operator paused", json.createObjectNode());
  }

  public AgentSession resumeSession(UUID sessionId, String reason) {
    return transition(sessionId, AgentState.IDLE, reason != null ? reason : "Resumed to idle", json.createObjectNode());
  }

  public AgentSession stopSession(UUID sessionId, String reason) {
    return transition(sessionId, AgentState.STOPPED, reason != null ? reason : "Stopped", json.createObjectNode());
  }

  public AgentSession recordError(UUID sessionId, String errorMessage) {
    return transition(sessionId, AgentState.ERROR, errorMessage, json.createObjectNode());
  }

  public void heartbeat(UUID sessionId) {
    store.findSessionById(sessionId).ifPresent(s -> {
      Instant now = clock.instant();
      store.saveSession(new AgentSession(
          s.id(), s.botId(), s.name(), s.currentState(), s.mode(),
          s.configuration(), s.startedAt(), now, s.stoppedAt()
      ));
    });
  }
}
