package com.web.labportalbackend.admin.website.scholar;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@Component
@ConfigurationProperties(prefix = "website.scholar")
@Getter
@Setter
public class ScholarSyncProperties {
    private boolean enabled = false;
    private String apiKey = "";
    private int maxPages = 100;
    private int requestTimeoutSeconds = 30;
    private int maxSnapshotBytes = 8_000_000;

    public boolean configured() { return apiKey != null && !apiKey.isBlank(); }
}
