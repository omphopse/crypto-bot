package io.algopilot.agent.position;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface PositionLifecycleStore {
  PositionLifecycleRecord saveLifecycle(PositionLifecycleRecord record);
  Optional<PositionLifecycleRecord> findLifecycleByPositionId(UUID positionId);
  List<PositionLifecycleRecord> findOpenLifecyclesByBotId(UUID botId);

  PositionSnapshot saveSnapshot(PositionSnapshot snapshot);
  List<PositionSnapshot> findSnapshotsByPositionId(UUID positionId, int limit);

  PositionStopRecord saveStopRecord(PositionStopRecord record);
  List<PositionStopRecord> findStopHistoryByPositionId(UUID positionId);

  PositionExitEvent saveExitEvent(PositionExitEvent event);
  List<PositionExitEvent> findExitEventsByPositionId(UUID positionId);
}
