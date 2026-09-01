package io.algopilot.agent.state;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyMap;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.algopilot.audit.AuditEventWriter;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class AgentStateMachineTest {
  private MemoryAgentStateStore store;
  private AuditEventWriter audit;
  private Clock clock;
  private ObjectMapper json;
  private AgentStateMachine stateMachine;

  @BeforeEach
  void setUp() {
    store = new MemoryAgentStateStore();
    audit = mock(AuditEventWriter.class);
    clock = Clock.fixed(Instant.parse("2026-09-01T12:00:00Z"), ZoneOffset.UTC);
    json = new ObjectMapper();
    stateMachine = new AgentStateMachine(store, audit, clock, json);
  }

  @Test
  void testFullAutonomousLifecycle_passesAllLegalTransitions() {
    UUID botId = UUID.randomUUID();
    AgentSession session = stateMachine.startSession(botId, "Canary AI Agent", AutonomousMode.OBSERVE_ONLY, json.createObjectNode());
    assertThat(session.currentState()).isEqualTo(AgentState.IDLE);
    verify(audit, times(1)).record(eq("AGENT"), eq(botId.toString()), eq("AGENT_STARTED"), eq("SESSION"), eq(session.id().toString()), anyMap());

    // IDLE -> OBSERVING
    session = stateMachine.transition(session.id(), AgentState.OBSERVING, "Observing orderbook", null);
    assertThat(session.currentState()).isEqualTo(AgentState.OBSERVING);

    // OBSERVING -> SCANNING
    session = stateMachine.transition(session.id(), AgentState.SCANNING, "Scanning multi-asset signals", null);
    assertThat(session.currentState()).isEqualTo(AgentState.SCANNING);

    // SCANNING -> RESEARCHING
    session = stateMachine.transition(session.id(), AgentState.RESEARCHING, "Gathering asset news", null);
    assertThat(session.currentState()).isEqualTo(AgentState.RESEARCHING);

    // RESEARCHING -> ANALYZING
    session = stateMachine.transition(session.id(), AgentState.ANALYZING, "Building context thesis", null);
    assertThat(session.currentState()).isEqualTo(AgentState.ANALYZING);

    // ANALYZING -> DECIDING
    session = stateMachine.transition(session.id(), AgentState.DECIDING, "AI generating decision", null);
    assertThat(session.currentState()).isEqualTo(AgentState.DECIDING);

    // DECIDING -> RISK_CHECK
    session = stateMachine.transition(session.id(), AgentState.RISK_CHECK, "Validating risk engine", null);
    assertThat(session.currentState()).isEqualTo(AgentState.RISK_CHECK);

    // RISK_CHECK -> EXECUTING
    session = stateMachine.transition(session.id(), AgentState.EXECUTING, "Dispatching paper order", null);
    assertThat(session.currentState()).isEqualTo(AgentState.EXECUTING);

    // EXECUTING -> MONITORING
    session = stateMachine.transition(session.id(), AgentState.MONITORING, "Monitoring active position", null);
    assertThat(session.currentState()).isEqualTo(AgentState.MONITORING);

    // MONITORING -> EXIT_EVALUATION
    session = stateMachine.transition(session.id(), AgentState.EXIT_EVALUATION, "Evaluating exit target", null);
    assertThat(session.currentState()).isEqualTo(AgentState.EXIT_EVALUATION);

    // EXIT_EVALUATION -> RISK_CHECK
    session = stateMachine.transition(session.id(), AgentState.RISK_CHECK, "Closing position risk check", null);
    assertThat(session.currentState()).isEqualTo(AgentState.RISK_CHECK);

    // RISK_CHECK -> EXECUTING
    session = stateMachine.transition(session.id(), AgentState.EXECUTING, "Executing exit order", null);
    assertThat(session.currentState()).isEqualTo(AgentState.EXECUTING);

    // EXECUTING -> MONITORING
    session = stateMachine.transition(session.id(), AgentState.MONITORING, "Position closed", null);
    assertThat(session.currentState()).isEqualTo(AgentState.MONITORING);

    // MONITORING -> OBSERVING
    session = stateMachine.transition(session.id(), AgentState.OBSERVING, "Back to market watch", null);
    assertThat(session.currentState()).isEqualTo(AgentState.OBSERVING);

    // OBSERVING -> IDLE
    session = stateMachine.transition(session.id(), AgentState.IDLE, "Loop iteration complete", null);
    assertThat(session.currentState()).isEqualTo(AgentState.IDLE);

    List<AgentStateEvent> events = store.findStateEventsBySessionId(session.id(), 50);
    assertThat(events).hasSize(14);
  }

  @Test
  void testIllegalTransition_throwsExceptionAndPreservesState() {
    UUID botId = UUID.randomUUID();
    AgentSession session = stateMachine.startSession(botId, "Safety Agent", AutonomousMode.OBSERVE_ONLY, null);
    assertThat(session.currentState()).isEqualTo(AgentState.IDLE);

    // IDLE -> EXECUTING is strictly ILLEGAL
    assertThatThrownBy(() -> stateMachine.transition(session.id(), AgentState.EXECUTING, "Direct jump", null))
        .isInstanceOf(InvalidStateTransitionException.class)
        .hasMessageContaining("Illegal agent state transition from IDLE to EXECUTING");

    // Verify state was not modified
    AgentSession reloaded = store.findSessionById(session.id()).orElseThrow();
    assertThat(reloaded.currentState()).isEqualTo(AgentState.IDLE);
  }

  @Test
  void testPauseAndResumeLifecycle() {
    UUID botId = UUID.randomUUID();
    AgentSession session = stateMachine.startSession(botId, "Pause Agent", AutonomousMode.PAPER_AUTONOMOUS, null);

    session = stateMachine.transition(session.id(), AgentState.OBSERVING, "Observing", null);
    session = stateMachine.pauseSession(session.id(), "Emergency operator intervention");
    assertThat(session.currentState()).isEqualTo(AgentState.PAUSED);

    session = stateMachine.resumeSession(session.id(), "Operator resumed bot");
    assertThat(session.currentState()).isEqualTo(AgentState.IDLE);
  }

  @Test
  void testErrorHandlingLifecycle() {
    UUID botId = UUID.randomUUID();
    AgentSession session = stateMachine.startSession(botId, "Error Agent", AutonomousMode.DEMO_AUTONOMOUS, null);

    session = stateMachine.transition(session.id(), AgentState.OBSERVING, "Observing", null);
    session = stateMachine.transition(session.id(), AgentState.SCANNING, "Scanning", null);
    session = stateMachine.recordError(session.id(), "Broker timeout during scan");
    assertThat(session.currentState()).isEqualTo(AgentState.ERROR);

    // ERROR -> PAUSED -> IDLE
    session = stateMachine.pauseSession(session.id(), "Moving from error to paused");
    assertThat(session.currentState()).isEqualTo(AgentState.PAUSED);
    session = stateMachine.resumeSession(session.id(), "Resuming");
    assertThat(session.currentState()).isEqualTo(AgentState.IDLE);
  }

  @Test
  void testLiveLockedMode_rejectedAtStart() {
    UUID botId = UUID.randomUUID();
    assertThatThrownBy(() -> stateMachine.startSession(botId, "Live Attempt", AutonomousMode.LIVE_LOCKED, null))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("Live trading is strictly disabled");
  }

  @Test
  void testStopSession_setsTerminalState() {
    UUID botId = UUID.randomUUID();
    AgentSession session = stateMachine.startSession(botId, "Stopping Agent", AutonomousMode.OBSERVE_ONLY, null);

    UUID sessionId = session.id();
    AgentSession stopped = stateMachine.stopSession(sessionId, "Shutting down bot");
    assertThat(stopped.currentState()).isEqualTo(AgentState.STOPPED);
    assertThat(stopped.stoppedAt()).isNotNull();

    // From STOPPED, no further transitions are legal
    assertThatThrownBy(() -> stateMachine.transition(sessionId, AgentState.IDLE, "Cannot resume stopped", null))
        .isInstanceOf(InvalidStateTransitionException.class);
  }

  private static final class MemoryAgentStateStore implements AgentStateStore {
    private final Map<UUID, AgentSession> sessions = Collections.synchronizedMap(new HashMap<>());
    private final List<AgentStateEvent> events = Collections.synchronizedList(new ArrayList<>());

    @Override
    public AgentSession saveSession(AgentSession session) {
      sessions.put(session.id(), session);
      return session;
    }

    @Override
    public Optional<AgentSession> findSessionById(UUID id) {
      return Optional.ofNullable(sessions.get(id));
    }

    @Override
    public Optional<AgentSession> findActiveSessionByBotId(UUID botId) {
      return sessions.values().stream()
          .filter(s -> s.botId().equals(botId) && s.currentState() != AgentState.STOPPED)
          .findFirst();
    }

    @Override
    public List<AgentSession> findSessionsByBotId(UUID botId) {
      return sessions.values().stream().filter(s -> s.botId().equals(botId)).toList();
    }

    @Override
    public List<AgentSession> findAllSessions() {
      return new ArrayList<>(sessions.values());
    }

    @Override
    public AgentStateEvent saveStateEvent(AgentStateEvent event) {
      events.add(event);
      return event;
    }

    @Override
    public List<AgentStateEvent> findStateEventsBySessionId(UUID sessionId, int limit) {
      return events.stream().filter(e -> e.sessionId().equals(sessionId)).toList();
    }

    @Override
    public List<AgentStateEvent> findStateEventsByBotId(UUID botId, int limit) {
      return events.stream().filter(e -> e.botId().equals(botId)).toList();
    }
  }
}
