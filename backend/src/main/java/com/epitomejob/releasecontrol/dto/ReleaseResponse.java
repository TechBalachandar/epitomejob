package com.epitomejob.releasecontrol.dto;

import com.epitomejob.releasecontrol.model.ReleaseCandidate;
import java.time.Instant;
import java.util.UUID;

public record ReleaseResponse(UUID id, String repository, long pullRequestNumber,
                              String sourceSha, String state,
                              Instant createdAt, Instant updatedAt) {
  public static ReleaseResponse from(ReleaseCandidate c) {
    return new ReleaseResponse(c.getId(), c.getRepository(), c.getPullRequestNumber(),
        c.getSourceSha(), c.getState().name(), c.getCreatedAt(), c.getUpdatedAt());
  }
}
