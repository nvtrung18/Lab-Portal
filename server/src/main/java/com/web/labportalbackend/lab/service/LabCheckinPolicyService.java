package com.web.labportalbackend.lab.service;

import com.web.labportalbackend.lab.dto.request.AddLabAllowedNetworkRequest;
import com.web.labportalbackend.lab.dto.request.LabCheckinPolicyRequest;
import com.web.labportalbackend.lab.dto.response.LabAllowedNetworkResponse;
import com.web.labportalbackend.lab.dto.response.LabCheckinPolicyResponse;

public interface LabCheckinPolicyService {

    LabCheckinPolicyResponse getPolicy(Long labId);

    LabCheckinPolicyResponse updatePolicy(Long labId, LabCheckinPolicyRequest request);

    LabAllowedNetworkResponse addNetwork(Long labId, AddLabAllowedNetworkRequest request);

    LabAllowedNetworkResponse registerCurrentNetwork(Long labId, String currentIp);

    void disableNetwork(Long labId, Long networkId);
}
