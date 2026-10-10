package com.web.labportalbackend.admin.website.service;

import com.web.labportalbackend.admin.website.dto.*;

import java.util.List;

public interface WebsiteContentService {
    WebsiteState getState();
    List<WebsiteMemberSource> getMemberSources();
    WebsiteState saveDraft(WebsiteSaveRequest request);
    WebsiteState publish(WebsiteSaveRequest request);
    PublishedWebsiteContent getPublished();
}
