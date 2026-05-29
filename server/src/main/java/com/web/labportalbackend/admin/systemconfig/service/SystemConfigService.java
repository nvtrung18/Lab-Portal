package com.web.labportalbackend.admin.systemconfig.service;

import com.web.labportalbackend.admin.systemconfig.dto.SystemConfigRequest;
import com.web.labportalbackend.admin.systemconfig.dto.SystemConfigResponse;

public interface SystemConfigService {

    SystemConfigResponse getConfig();

    SystemConfigResponse updateConfig(SystemConfigRequest request);
}
