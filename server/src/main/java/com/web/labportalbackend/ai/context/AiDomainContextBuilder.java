package com.web.labportalbackend.ai.context;

import com.web.labportalbackend.ai.enums.AiAssistantDomain;

public interface AiDomainContextBuilder {
    AiAssistantDomain domain();
    AiDomainContext build(TrustedContextInput input);
}
