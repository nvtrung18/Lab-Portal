package com.web.labportalbackend.admin.systemconfig.service;

import com.web.labportalbackend.admin.systemconfig.dto.SystemConfigRequest;
import com.web.labportalbackend.admin.systemconfig.dto.SystemConfigResponse;

public interface SystemConfigService {

    SystemConfigResponse getConfig();

    /**
     * Database-current locking read reserved for the canonical task-status
     * authorization flow after its task and permission locks are held.
     */
    SystemConfigResponse getConfigForStatusAuthorization();

    SystemConfigResponse updateConfig(SystemConfigRequest request);
}
