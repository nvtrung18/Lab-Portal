package com.web.labportalbackend.research.dto.response;

import org.springframework.core.io.Resource;

public record ReportFileDownload(Resource resource, String fileName, String fileType) {
}
