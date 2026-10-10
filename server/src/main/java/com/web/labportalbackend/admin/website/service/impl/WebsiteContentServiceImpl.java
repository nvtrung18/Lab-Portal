package com.web.labportalbackend.admin.website.service.impl;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.web.labportalbackend.admin.audit.enums.AuditAction;
import com.web.labportalbackend.admin.audit.enums.AuditModule;
import com.web.labportalbackend.admin.audit.service.AuditLogService;
import com.web.labportalbackend.admin.systemconfig.entity.SystemConfigEntity;
import com.web.labportalbackend.admin.systemconfig.repository.SystemConfigRepository;
import com.web.labportalbackend.admin.website.dto.*;
import com.web.labportalbackend.admin.website.service.WebsiteContentService;
import com.web.labportalbackend.auth.entity.User;
import com.web.labportalbackend.auth.repository.UserRepository;
import com.web.labportalbackend.lab.repository.LaboratoryRepository;
import com.web.labportalbackend.common.exception.WebsiteRevisionConflictException;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.time.LocalDate;
import java.time.format.DateTimeParseException;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class WebsiteContentServiceImpl implements WebsiteContentService {
    private static final String CONFIG_KEY = "PUBLIC_WEBSITE_CONTENT";
    private final SystemConfigRepository repository;
    private final ObjectMapper objectMapper;
    private final UserRepository userRepository;
    private final LaboratoryRepository laboratoryRepository;
    private final AuditLogService auditLogService;

    @Override
    @Transactional(readOnly = true)
    public WebsiteState getState() {
        return repository.findByConfigKeyAndDeletedFalse(CONFIG_KEY)
                .map(this::read).orElse(new WebsiteState(0, null, null, null));
    }

    @Override
    @Transactional(readOnly = true)
    public List<WebsiteMemberSource> getMemberSources() {
        currentAdmin();
        return userRepository.findWebsiteMemberSources();
    }

    @Override
    @Transactional
    public WebsiteState saveDraft(WebsiteSaveRequest request) {
        return update(request, false);
    }

    @Override
    @Transactional
    public WebsiteState publish(WebsiteSaveRequest request) {
        return update(request, true);
    }

    private WebsiteState update(WebsiteSaveRequest request, boolean publish) {
        User actor = currentAdmin();
        validateContent(request.content());
        SystemConfigEntity entity = repository.findByConfigKeyForUpdate(CONFIG_KEY)
                .orElseThrow(() -> new IllegalStateException("Website content storage is not initialized. Apply migration V72."));
        WebsiteState previous = read(entity);
        if (previous.revision() != request.revision()) throw new WebsiteRevisionConflictException();
        WebsiteState next = new WebsiteState(previous.revision() + 1, request.content(),
                publish ? request.content() : previous.published(),
                publish ? Instant.now() : previous.publishedAt());
        String json = write(next);
        if (json.getBytes(StandardCharsets.UTF_8).length > 4_000_000) {
            throw new IllegalArgumentException("Website content is too large.");
        }
        entity.setConfigValueJson(json);
        entity.setUpdatedBy(actor);
        repository.save(entity);
        auditLogService.log(actor, publish ? AuditAction.PUBLISH_WEBSITE : AuditAction.SAVE_WEBSITE_DRAFT,
                AuditModule.SYSTEM_CONFIG, "WEBSITE_CONTENT", entity.getId(),
                publish ? "Admin đã xuất bản nội dung website giới thiệu." : "Admin đã lưu bản nháp website giới thiệu.");
        return next;
    }

    @Override
    @Transactional(readOnly = true)
    public PublishedWebsiteContent getPublished() {
        WebsiteState state = getState();
        WebsiteContent content = state.published();
        if (content == null) return new PublishedWebsiteContent(null, null);
        WebsiteContent.LabData data = content.data();
        WebsiteContent.LabData visible = new WebsiteContent.LabData(
                data.research().stream().filter(item -> !Boolean.FALSE.equals(item.visible()))
                        .map(item -> new WebsiteContent.ResearchArea(item.id(), item.title(), item.description(),
                                item.visible(), item.image(), item.icon(), item.topics(), null)).toList(),
                data.members().stream().filter(item -> !Boolean.FALSE.equals(item.visible()))
                        .map(item -> new WebsiteContent.Member(item.id(), item.title(), item.description(),
                                item.visible(), item.role(), item.initials(), item.area(), item.email(),
                                item.color(), item.image(), null)).toList(),
                data.publications().stream().filter(item -> !Boolean.FALSE.equals(item.visible())).toList(),
                data.projects().stream().filter(item -> !Boolean.FALSE.equals(item.visible())).toList(),
                data.news().stream().filter(item -> !Boolean.FALSE.equals(item.visible())).toList());
        Map<String, WebsiteContent.WebsiteSection> library = content.contentLibrary() == null ? Map.of()
                : content.contentLibrary().stream().collect(Collectors.toMap(WebsiteContent.WebsiteSection::id, section -> section));
        List<WebsiteContent.WebsitePage> pages = content.pages() == null ? null : content.pages().stream()
                .filter(page -> !Boolean.FALSE.equals(page.visible()))
                .map(page -> new WebsiteContent.WebsitePage(page.id(), page.slug(), page.title(), page.description(),
                        page.navigationTitle(), page.template(), page.visible(), page.showInNavigation(),
                        publishedSections(page, library), null))
                .toList();
        return new PublishedWebsiteContent(new WebsiteContent(content.profile(), visible, content.copy(), content.media(), pages, null), state.publishedAt());
    }

    private List<WebsiteContent.WebsiteSection> publishedSections(WebsiteContent.WebsitePage page,
                                                                 Map<String, WebsiteContent.WebsiteSection> library) {
        List<WebsiteContent.WebsiteSection> sections = page.sectionIds() == null ? page.sections()
                : page.sectionIds().stream().map(id -> {
                    WebsiteContent.WebsiteSection section = library.get(id);
                    if (section == null) throw new IllegalStateException("Published website references missing content.");
                    return section;
                }).toList();
        return sections.stream().filter(section -> !Boolean.FALSE.equals(section.visible())).toList();
    }

    private void validateContent(WebsiteContent content) {
        WebsiteContent.LabData data = content.data();
        uniqueIds(data.research().stream().map(WebsiteContent.ResearchArea::id).toList());
        List<Long> researchLabIds = data.research().stream().map(WebsiteContent.ResearchArea::labId)
                .filter(java.util.Objects::nonNull).toList();
        if (new HashSet<>(researchLabIds).size() != researchLabIds.size()) {
            throw new IllegalArgumentException("Each research group can have only one website research direction.");
        }
        if (!researchLabIds.isEmpty()
                && laboratoryRepository.findExistingWebsiteResearchLabIds(researchLabIds).size() != researchLabIds.size()) {
            throw new IllegalArgumentException("A linked research group no longer exists. Change its linked group or remove the website direction.");
        }
        uniqueIds(data.members().stream().map(WebsiteContent.Member::id).toList());
        List<Long> memberUserIds = data.members().stream().map(WebsiteContent.Member::userId)
                .filter(java.util.Objects::nonNull).toList();
        if (new HashSet<>(memberUserIds).size() != memberUserIds.size()) {
            throw new IllegalArgumentException("Each account can have only one website member profile.");
        }
        if (!memberUserIds.isEmpty()
                && userRepository.findExistingWebsiteMemberIds(memberUserIds).size() != memberUserIds.size()) {
            throw new IllegalArgumentException("A linked member account no longer exists. Change its linked account or remove the website profile.");
        }
        uniqueIds(data.publications().stream().map(WebsiteContent.Publication::id).toList());
        uniqueIds(data.projects().stream().map(WebsiteContent.Project::id).toList());
        uniqueIds(data.news().stream().map(WebsiteContent.NewsItem::id).toList());
        HashSet<String> libraryIds = new HashSet<>();
        if (content.contentLibrary() != null) {
            List<String> ids = content.contentLibrary().stream().map(WebsiteContent.WebsiteSection::id).toList();
            uniqueIds(ids);
            libraryIds.addAll(ids);
        }
        if (content.pages() != null) {
            uniqueIds(content.pages().stream().map(WebsiteContent.WebsitePage::id).toList());
            List<String> slugs = content.pages().stream().map(WebsiteContent.WebsitePage::slug).toList();
            if (new HashSet<>(slugs).size() != slugs.size()) throw new IllegalArgumentException("Website page URL segments must be unique.");
            if (slugs.contains("admin")) throw new IllegalArgumentException("The admin URL segment is reserved.");
            for (WebsiteContent.WebsitePage page : content.pages()) {
                uniqueIds(page.sections().stream().map(WebsiteContent.WebsiteSection::id).toList());
                if (page.sectionIds() != null) {
                    uniqueIds(page.sectionIds());
                    if (content.contentLibrary() == null || !libraryIds.containsAll(page.sectionIds())) {
                        throw new IllegalArgumentException("Selected website content must exist in the library.");
                    }
                }
            }
        }
        for (WebsiteContent.NewsItem item : data.news()) {
            if (!item.date().isEmpty()) {
                try { LocalDate.parse(item.date()); }
                catch (DateTimeParseException ex) { throw new IllegalArgumentException("Invalid news or event date."); }
            }
        }
    }

    private void uniqueIds(List<String> ids) {
        if (new HashSet<>(ids).size() != ids.size()) throw new IllegalArgumentException("Duplicate website record identifier.");
    }

    private WebsiteState read(SystemConfigEntity entity) {
        try {
            return objectMapper.readValue(entity.getConfigValueJson(), WebsiteState.class);
        } catch (JsonProcessingException ex) {
            throw new IllegalStateException("Stored website content cannot be read.", ex);
        }
    }

    private String write(WebsiteState state) {
        try {
            return objectMapper.writeValueAsString(state);
        } catch (JsonProcessingException ex) {
            throw new IllegalStateException("Website content cannot be saved.", ex);
        }
    }

    private User currentAdmin() {
        var authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null) throw new AccessDeniedException("Authentication is required");
        User actor = userRepository.findByUsername(authentication.getName())
                .orElseThrow(() -> new AccessDeniedException("Current user not found"));
        if (!actor.hasRole("ADMIN")) throw new AccessDeniedException("Only ADMIN can edit website content");
        return actor;
    }
}
