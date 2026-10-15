package com.web.labportalbackend.admin.website.scholar;

import com.web.labportalbackend.admin.website.dto.WebsiteContent;
import java.time.Instant;
import java.util.List;

public record ScholarSnapshot(WebsiteContent.ScholarMetrics metrics,
                              List<WebsiteContent.ScholarPublication> publications,
                              Instant fetchedAt, Instant sourceUpdatedAt) {}
