package io.algopilot.audit;

import java.util.List;
import java.util.Map;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/audit")
public class AuditController {
  private final JdbcTemplate jdbc;

  public AuditController(JdbcTemplate jdbc) {
    this.jdbc = jdbc;
  }

  @GetMapping("/events")
  public List<Map<String, Object>> listEvents(@RequestParam(defaultValue = "50") int limit) {
    String sql = "select id, occurred_at, actor_type, actor_id, event_type, aggregate_type, aggregate_id, payload from audit_events order by occurred_at desc limit ?";
    return jdbc.queryForList(sql, Math.max(1, limit));
  }
}
