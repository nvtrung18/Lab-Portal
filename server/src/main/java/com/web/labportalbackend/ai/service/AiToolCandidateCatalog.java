package com.web.labportalbackend.ai.service;

import java.util.List;

public interface AiToolCandidateCatalog {
    List<AiToolCandidate> candidates();

    default List<AiToolCandidate> candidates(String input) {
        return candidates();
    }
}
