package io.algopilot.research.service;

import io.algopilot.research.model.ResearchDocument;
import io.algopilot.research.model.ResearchEvidence;
import io.algopilot.research.model.ResearchRequest;
import io.algopilot.research.model.ResearchSource;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface ResearchStore {
  ResearchRequest saveRequest(ResearchRequest req);
  Optional<ResearchRequest> findRequestById(UUID id);
  List<ResearchRequest> findRecentRequests(int limit);

  ResearchSource saveSource(ResearchSource source);
  List<ResearchSource> findAllSources();
  Optional<ResearchSource> findSourceByDomain(String domain);

  ResearchDocument saveDocument(ResearchDocument doc);
  List<ResearchDocument> findDocumentsByRequestId(UUID requestId);
  Optional<ResearchDocument> findDocumentByHash(String contentHash);

  ResearchEvidence saveEvidence(ResearchEvidence ev);
  List<ResearchEvidence> findEvidenceByRequestId(UUID requestId);
  List<ResearchEvidence> findEvidenceByAsset(String asset, int limit);
}
