package io.algopilot.reset;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import io.algopilot.reset.model.PreflightStatusResponse;
import io.algopilot.reset.service.CleanResetService;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;

class CleanResetServiceTest {
  private JdbcTemplate jdbc;
  private CleanResetService resetService;

  @BeforeEach
  void setUp() {
    jdbc = mock(JdbcTemplate.class);
    resetService = new CleanResetService(jdbc);
  }

  @Test
  void testConfirmReset_withValidConfirmation_executesDeletesAndLogsAudit() {
    Map<String, Object> res = resetService.confirmReset("RESET_ALGOPILOT_EXPERIMENT_STATE", "ADMIN_USER");

    assertThat(res).containsEntry("status", "SUCCESS");
    assertThat(res).containsKey("resetOperationId");
    assertThat(res).containsKey("tablesClearedCount");

    verify(jdbc).update(anyString(), org.mockito.ArgumentMatchers.any(), eq("ADMIN_USER"), eq("RESET_ALGOPILOT_EXPERIMENT_STATE"), anyString(), org.mockito.ArgumentMatchers.any());
  }

  @Test
  void testConfirmReset_withInvalidConfirmation_throwsIllegalArgumentException() {
    assertThatThrownBy(() -> resetService.confirmReset("WRONG_STRING", "ADMIN"))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("Invalid confirmation string");
  }

  @Test
  void testGetPreflightStatus_returnsCleanStateAndConfigurationRequired() {
    when(jdbc.queryForObject(anyString(), eq(Integer.class))).thenReturn(0);

    PreflightStatusResponse preflight = resetService.getPreflightStatus();

    assertThat(preflight.localDatabaseClean()).isTrue();
    assertThat(preflight.activeBots()).isEqualTo(0);
    assertThat(preflight.localOrders()).isEqualTo(0);
    assertThat(preflight.localFills()).isEqualTo(0);
    assertThat(preflight.localPositions()).isEqualTo(0);
    assertThat(preflight.localTrades()).isEqualTo(0);
    assertThat(preflight.agentSessions()).isEqualTo(0);
    assertThat(preflight.systemMode()).isEqualTo("CONFIGURATION_REQUIRED");
    assertThat(preflight.liveTradingDisabled()).isTrue();
  }
}
