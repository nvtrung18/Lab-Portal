package com.web.labportalbackend.ai.service.impl;

import com.web.labportalbackend.ai.enums.AiAssistantSystemRole;
import com.web.labportalbackend.ai.service.AiAssistantAvailabilityException;
import com.web.labportalbackend.ai.service.AiAssistantAvailabilityFailure;
import com.web.labportalbackend.ai.service.AiCurrentActor;
import com.web.labportalbackend.ai.service.AiCurrentActorProvider;
import com.web.labportalbackend.auth.entity.User;
import com.web.labportalbackend.auth.repository.UserRepository;
import com.web.labportalbackend.common.enums.UserStatus;
import java.util.Arrays;
import org.springframework.security.authentication.AnonymousAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class AiCurrentActorProviderImpl implements AiCurrentActorProvider {

    private final UserRepository userRepository;

    public AiCurrentActorProviderImpl(UserRepository userRepository) {
        this.userRepository = userRepository;
    }

    @Override
    @Transactional(readOnly = true)
    public AiCurrentActor requireCurrentActor() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null || !authentication.isAuthenticated()
                || authentication instanceof AnonymousAuthenticationToken
                || authentication.getName() == null || authentication.getName().isBlank()
                || "anonymousUser".equals(authentication.getName())) {
            throw new AiAssistantAvailabilityException(AiAssistantAvailabilityFailure.UNAUTHENTICATED);
        }
        User actor = userRepository.findByUsername(authentication.getName())
                .filter(AiCurrentActorProviderImpl::usable)
                .orElseThrow(() -> new AiAssistantAvailabilityException(
                        AiAssistantAvailabilityFailure.ACTOR_UNAVAILABLE));
        AiAssistantSystemRole role = Arrays.stream(AiAssistantSystemRole.values())
                .filter(candidate -> actor.hasRole(candidate.name()))
                .findFirst()
                .orElseThrow(() -> new AiAssistantAvailabilityException(
                        AiAssistantAvailabilityFailure.ROLE_NOT_ALLOWED));
        return new AiCurrentActor(actor.getId(), role);
    }

    private static boolean usable(User actor) {
        return actor.getId() != null && Boolean.TRUE.equals(actor.getActive())
                && !Boolean.TRUE.equals(actor.getDeleted()) && actor.getStatus() == UserStatus.ACTIVE
                && actor.getRoles() != null;
    }
}
