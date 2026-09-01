package io.algopilot.ops.health;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface HeartbeatStore {
  HeartbeatRecord recordHeartbeat(HeartbeatRecord record);
  Optional<HeartbeatRecord> findLatestHeartbeat(ComponentType component, String instanceId, UUID botId);
  List<HeartbeatRecord> findAllActiveHeartbeats();
  List<HeartbeatRecord> findHeartbeatsByBotId(UUID botId);

  HealthEvent recordHealthEvent(HealthEvent event);
  List<HealthEvent> findRecentHealthEvents(int limit);
}
