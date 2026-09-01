package io.algopilot.ops.recovery;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface RecoveryStore {
  RecoveryRun saveRun(RecoveryRun run);
  Optional<RecoveryRun> findLatestRunByBotId(UUID botId);
  List<RecoveryRun> findRecentRuns(int limit);
}
