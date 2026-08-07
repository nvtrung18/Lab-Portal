package com.web.labportalbackend.ai.security.impl;

import com.web.labportalbackend.admin.systemconfig.dto.SystemConfigResponse;
import com.web.labportalbackend.admin.systemconfig.service.SystemConfigService;
import com.web.labportalbackend.ai.enums.AiAssistantDomain;
import com.web.labportalbackend.ai.enums.AiCapabilityDenialReason;
import com.web.labportalbackend.ai.enums.AiCapabilityEvidence;
import com.web.labportalbackend.ai.enums.AiResourceScope;
import com.web.labportalbackend.ai.enums.AiResourceType;
import com.web.labportalbackend.ai.security.AiCapabilityPermissionAdapter;
import com.web.labportalbackend.ai.service.AiCapabilityDecision;
import com.web.labportalbackend.ai.service.AiCapabilityRequest;
import com.web.labportalbackend.auth.entity.User;
import com.web.labportalbackend.booking.entity.Booking;
import com.web.labportalbackend.booking.entity.TimeSlot;
import com.web.labportalbackend.booking.repository.BookingRepository;
import com.web.labportalbackend.booking.repository.TimeSlotRepository;
import com.web.labportalbackend.common.enums.BookingStatus;
import com.web.labportalbackend.common.enums.LabStatus;
import com.web.labportalbackend.common.enums.TimeSlotStatus;
import com.web.labportalbackend.lab.entity.Laboratory;
import com.web.labportalbackend.lab.repository.LaboratoryRepository;
import com.web.labportalbackend.lab.repository.MembershipRepository;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Set;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

@Component
public class AiLabCapabilityPermissionAdapter implements AiCapabilityPermissionAdapter {

    private final LaboratoryRepository laboratoryRepository;
    private final MembershipRepository membershipRepository;
    private final TimeSlotRepository timeSlotRepository;
    private final BookingRepository bookingRepository;
    private final SystemConfigService systemConfigService;
    private final Clock clock;

    @Autowired
    public AiLabCapabilityPermissionAdapter(LaboratoryRepository laboratoryRepository,
                                            MembershipRepository membershipRepository,
                                            TimeSlotRepository timeSlotRepository,
                                            BookingRepository bookingRepository,
                                            SystemConfigService systemConfigService) {
        this(laboratoryRepository, membershipRepository, timeSlotRepository, bookingRepository,
                systemConfigService, Clock.systemUTC());
    }

    AiLabCapabilityPermissionAdapter(LaboratoryRepository laboratoryRepository,
                                     MembershipRepository membershipRepository,
                                     TimeSlotRepository timeSlotRepository,
                                     BookingRepository bookingRepository,
                                     SystemConfigService systemConfigService,
                                     Clock clock) {
        this.laboratoryRepository = laboratoryRepository;
        this.membershipRepository = membershipRepository;
        this.timeSlotRepository = timeSlotRepository;
        this.bookingRepository = bookingRepository;
        this.systemConfigService = systemConfigService;
        this.clock = clock;
    }

    @Override
    public AiAssistantDomain domain() {
        return AiAssistantDomain.LAB;
    }

    @Override
    public Evaluation evaluate(User actor, AiCapabilityRequest request) {
        try {
            return switch (request.capability()) {
                case LAB_POLICY_READ -> policyRead(request);
                case LAB_SLOT_READ -> slotRead(actor, request);
                case LAB_OWN_BOOKING_READ -> ownBookingRead(actor, request);
                case LAB_MANAGED_SUMMARY -> managedSummary(actor, request);
                case LAB_BOOKING_DRAFT -> bookingDraft(actor, request);
                case LAB_CHECKIN_GUIDANCE -> checkinGuidance(actor, request);
                default -> Evaluation.denied(AiCapabilityDenialReason.DOMAIN_MISMATCH);
            };
        } catch (RuntimeException ex) {
            return Evaluation.denied(AiCapabilityDenialReason.RESOURCE_UNAVAILABLE);
        }
    }

    private Evaluation policyRead(AiCapabilityRequest request) {
        Laboratory lab = activeLab(request.resource().id());
        if (lab == null) {
            return unavailable();
        }
        return allow(AiResourceType.LABORATORY, lab.getId(), lab.getId(), AiResourceScope.EXISTING_BUSINESS_PERMISSION,
                AiCapabilityEvidence.EXISTING_PERMISSION);
    }

    private Evaluation slotRead(User actor, AiCapabilityRequest request) {
        TimeSlot slot = activeSlot(request.resource().id());
        Laboratory lab = slot == null ? null : usableLab(slot.getLab());
        if (slot == null || lab == null) {
            return unavailable();
        }
        if (hasRole(actor, "STUDENT")) {
            if (membershipRepository.existsByUserIdAndLaboratoryIdAndActiveTrueAndDeletedFalse(
                    actor.getId(), lab.getId())) {
                return allow(AiResourceType.TIME_SLOT, slot.getId(), lab.getId(), AiResourceScope.LAB_MEMBER,
                        AiCapabilityEvidence.LAB_MEMBERSHIP);
            }
        }
        if (hasRole(actor, "LAB_MANAGER")) {
            if (laboratoryRepository.existsByIdAndManagerIdAndActiveTrueAndDeletedFalse(
                    lab.getId(), actor.getId())) {
                return allow(AiResourceType.TIME_SLOT, slot.getId(), lab.getId(), AiResourceScope.MANAGED_LAB,
                        AiCapabilityEvidence.MANAGED_LAB);
            }
        }
        if (hasRole(actor, "STUDENT")) {
            return Evaluation.denied(AiCapabilityDenialReason.NOT_LAB_MEMBER);
        }
        if (hasRole(actor, "LAB_MANAGER")) {
            return Evaluation.denied(AiCapabilityDenialReason.NOT_MANAGED_LAB);
        }
        return Evaluation.denied(AiCapabilityDenialReason.ROLE_NOT_ALLOWED);
    }

    private Evaluation ownBookingRead(User actor, AiCapabilityRequest request) {
        if (!hasRole(actor, "STUDENT")) {
            return Evaluation.denied(AiCapabilityDenialReason.ROLE_NOT_ALLOWED);
        }
        Booking booking = bookingRepository.findById(request.resource().id())
                .filter(value -> !Boolean.TRUE.equals(value.getDeleted()))
                .orElse(null);
        if (booking == null || booking.getUser() == null || booking.getUser().getId() == null
                || booking.getLab() == null || booking.getLab().getId() == null) {
            return unavailable();
        }
        if (!actor.getId().equals(booking.getUser().getId())) {
            return Evaluation.denied(AiCapabilityDenialReason.NOT_OWNER);
        }
        return allow(AiResourceType.BOOKING, booking.getId(), booking.getLab().getId(), AiResourceScope.SELF,
                AiCapabilityEvidence.OWNERSHIP);
    }

    private Evaluation managedSummary(User actor, AiCapabilityRequest request) {
        if (!hasRole(actor, "LAB_MANAGER")) {
            return Evaluation.denied(AiCapabilityDenialReason.ROLE_NOT_ALLOWED);
        }
        Laboratory lab = activeLab(request.resource().id());
        if (lab == null) {
            return unavailable();
        }
        if (!laboratoryRepository.existsByIdAndManagerIdAndActiveTrueAndDeletedFalse(lab.getId(), actor.getId())) {
            return Evaluation.denied(AiCapabilityDenialReason.NOT_MANAGED_LAB);
        }
        return allow(AiResourceType.LABORATORY, lab.getId(), lab.getId(), AiResourceScope.MANAGED_LAB,
                AiCapabilityEvidence.MANAGED_LAB);
    }

    private Evaluation bookingDraft(User actor, AiCapabilityRequest request) {
        if (!hasRole(actor, "STUDENT")) {
            return Evaluation.denied(AiCapabilityDenialReason.ROLE_NOT_ALLOWED);
        }
        TimeSlot slot = activeSlot(request.resource().id());
        Laboratory lab = slot == null ? null : usableLab(slot.getLab());
        if (slot == null || lab == null || slot.getStartTime() == null) {
            return unavailable();
        }
        if (!membershipRepository.existsByUserIdAndLaboratoryIdAndActiveTrueAndDeletedFalse(
                actor.getId(), lab.getId())) {
            return Evaluation.denied(AiCapabilityDenialReason.NOT_LAB_MEMBER);
        }
        if (slot.getStatus() != TimeSlotStatus.AVAILABLE) {
            return Evaluation.denied(AiCapabilityDenialReason.RESOURCE_OUT_OF_SCOPE);
        }
        SystemConfigResponse config = systemConfigService.getConfig();
        if (config == null || config.lab() == null) {
            return unavailable();
        }
        if (config.lab().disableBookingForInactiveLab() && lab.getStatus() != LabStatus.AVAILABLE) {
            return Evaluation.denied(AiCapabilityDenialReason.RESOURCE_OUT_OF_SCOPE);
        }
        Instant evaluatedAt = clock.instant();
        if (!slot.getStartTime().isAfter(evaluatedAt)) {
            return Evaluation.denied(AiCapabilityDenialReason.RESOURCE_OUT_OF_SCOPE);
        }
        if (bookingRepository.existsActiveBookingByUserAndSlot(actor.getId(), slot.getId())) {
            return Evaluation.denied(AiCapabilityDenialReason.RESOURCE_OUT_OF_SCOPE);
        }
        return Evaluation.allowed(new AiCapabilityDecision.ResolvedResource(
                        AiResourceType.TIME_SLOT, slot.getId(), lab.getId(), null, null, null,
                        AiResourceScope.LAB_MEMBER),
                Set.of(AiCapabilityEvidence.DERIVED_RESOURCE, AiCapabilityEvidence.LAB_MEMBERSHIP,
                        AiCapabilityEvidence.EXISTING_PERMISSION));
    }

    private Evaluation checkinGuidance(User actor, AiCapabilityRequest request) {
        if (!hasRole(actor, "STUDENT")) {
            return Evaluation.denied(AiCapabilityDenialReason.ROLE_NOT_ALLOWED);
        }
        Booking booking = bookingRepository.findById(request.resource().id())
                .filter(value -> Boolean.TRUE.equals(value.getActive()))
                .filter(value -> !Boolean.TRUE.equals(value.getDeleted()))
                .orElse(null);
        if (booking == null || booking.getUser() == null || booking.getUser().getId() == null) {
            return unavailable();
        }
        if (!actor.getId().equals(booking.getUser().getId())) {
            return Evaluation.denied(AiCapabilityDenialReason.NOT_OWNER);
        }
        Laboratory lab = usableLab(booking.getLab());
        TimeSlot slot = booking.getTimeSlot();
        if (lab == null || slot == null || !Boolean.TRUE.equals(slot.getActive())
                || Boolean.TRUE.equals(slot.getDeleted()) || slot.getLab() == null
                || !lab.getId().equals(slot.getLab().getId()) || booking.getStartTime() == null) {
            return unavailable();
        }
        if (booking.getStatus() != BookingStatus.APPROVED || slot.getStatus() == TimeSlotStatus.CANCELLED) {
            return Evaluation.denied(AiCapabilityDenialReason.RESOURCE_OUT_OF_SCOPE);
        }
        Instant evaluatedAt = clock.instant();
        if (evaluatedAt.isBefore(booking.getStartTime())) {
            return Evaluation.denied(AiCapabilityDenialReason.RESOURCE_OUT_OF_SCOPE);
        }
        SystemConfigResponse config = systemConfigService.getConfig();
        if (config == null || config.booking() == null || config.booking().checkinWindowMinutes() <= 0) {
            return unavailable();
        }
        Instant effectiveEnd = booking.getStartTime()
                .plus(Duration.ofMinutes(config.booking().checkinWindowMinutes()));
        if (!evaluatedAt.isBefore(effectiveEnd)) {
            return Evaluation.denied(AiCapabilityDenialReason.RESOURCE_OUT_OF_SCOPE);
        }
        return Evaluation.allowed(new AiCapabilityDecision.ResolvedResource(
                        AiResourceType.BOOKING, booking.getId(), lab.getId(), null, null, null,
                        AiResourceScope.SELF),
                Set.of(AiCapabilityEvidence.DERIVED_RESOURCE, AiCapabilityEvidence.OWNERSHIP,
                        AiCapabilityEvidence.EXISTING_PERMISSION));
    }

    private Laboratory activeLab(Long labId) {
        return laboratoryRepository.findById(labId).map(this::usableLab).orElse(null);
    }

    private TimeSlot activeSlot(Long slotId) {
        return timeSlotRepository.findActiveById(slotId)
                .filter(slot -> Boolean.TRUE.equals(slot.getActive()))
                .filter(slot -> !Boolean.TRUE.equals(slot.getDeleted()))
                .orElse(null);
    }

    private Laboratory usableLab(Laboratory lab) {
        return lab != null && lab.getId() != null && Boolean.TRUE.equals(lab.getActive())
                && !Boolean.TRUE.equals(lab.getDeleted()) ? lab : null;
    }

    private static boolean hasRole(User user, String role) {
        return user != null && user.getId() != null && user.hasRole(role);
    }

    private static Evaluation unavailable() {
        return Evaluation.denied(AiCapabilityDenialReason.RESOURCE_UNAVAILABLE);
    }

    private static Evaluation allow(AiResourceType type, Long id, Long labId, AiResourceScope scope,
                                    AiCapabilityEvidence policyEvidence) {
        return Evaluation.allowed(new AiCapabilityDecision.ResolvedResource(
                        type, id, labId, null, null, null, scope),
                Set.of(AiCapabilityEvidence.DERIVED_RESOURCE, policyEvidence));
    }
}
