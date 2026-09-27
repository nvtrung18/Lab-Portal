package com.web.labportalbackend.research.dto.response;

import org.springframework.core.io.Resource;

public record ProductFileDownload(Resource resource, String fileName) {
}
