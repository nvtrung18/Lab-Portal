package com.web.labportalbackend.ai.repository;

import static org.junit.jupiter.api.Assertions.assertEquals;

import com.web.labportalbackend.ai.entity.AiUsageLogEntity;
import com.web.labportalbackend.ai.enums.AiAssistantKey;
import java.time.Instant;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;

@DataJpaTest
class AiUsageLogRepositoryTest {

    private static final Long USER_ID = 7L;
    private static final Instant START = Instant.parse("2026-08-05T00:00:00Z");
    private static final Instant END = Instant.parse("2026-08-06T00:00:00Z");

    @Autowired AiUsageLogRepository usageLogRepository;

    @Test
    void assistantWideCountIncludesAllActiveStatusesAndRoleModuleBucketsInsideUtcWindow() {
        save(USER_ID, AiAssistantKey.LAB_ASSISTANT, "STUDENT", "research", "SUCCESS", START, true, false);
        save(USER_ID, AiAssistantKey.LAB_ASSISTANT, "STUDENT", "research", "ERROR", START.plusSeconds(1), true, false);
        save(USER_ID, AiAssistantKey.LAB_ASSISTANT, "TEACHER", "other", "PENDING", START.plusSeconds(2), true, false);
        save(USER_ID, AiAssistantKey.LAB_ASSISTANT, "MANAGER", "research", "CANCELLED", START.plusSeconds(3), true, false);
        save(USER_ID + 1, AiAssistantKey.LAB_ASSISTANT, "STUDENT", "research", "SUCCESS", START.plusSeconds(4), true, false);
        save(USER_ID, AiAssistantKey.RESEARCH_ASSISTANT, "STUDENT", "research", "SUCCESS", START.plusSeconds(4), true, false);
        save(USER_ID, AiAssistantKey.LAB_ASSISTANT, "STUDENT", "research", "SUCCESS", START.minusSeconds(1), true, false);
        save(USER_ID, AiAssistantKey.LAB_ASSISTANT, "STUDENT", "research", "SUCCESS", END, true, false);
        save(USER_ID, AiAssistantKey.LAB_ASSISTANT, "STUDENT", "research", "SUCCESS", START.plusSeconds(5), false, false);
        save(USER_ID, AiAssistantKey.LAB_ASSISTANT, "STUDENT", "research", "SUCCESS", START.plusSeconds(6), true, true);

        assertEquals(4, usageLogRepository
                .countByUserIdAndAssistantKeyAndCreatedAtGreaterThanEqualAndCreatedAtLessThanAndActiveTrueAndDeletedFalse(
                        USER_ID, AiAssistantKey.LAB_ASSISTANT, START, END));
    }

    @Test
    void narrowCountUsesExactRoleModuleAndIncludesStartButExcludesNextUtcDay() {
        save(USER_ID, AiAssistantKey.LAB_ASSISTANT, "STUDENT", "research", "SUCCESS", START, true, false);
        save(USER_ID, AiAssistantKey.LAB_ASSISTANT, "STUDENT", "research", "ERROR", START.plusSeconds(1), true, false);
        save(USER_ID, AiAssistantKey.LAB_ASSISTANT, "STUDENT", "other", "SUCCESS", START.plusSeconds(2), true, false);
        save(USER_ID, AiAssistantKey.LAB_ASSISTANT, "TEACHER", "research", "SUCCESS", START.plusSeconds(3), true, false);
        save(USER_ID, AiAssistantKey.LAB_ASSISTANT, "STUDENT", "research", "SUCCESS", END, true, false);
        save(USER_ID, AiAssistantKey.LAB_ASSISTANT, "STUDENT", "research", "SUCCESS", START.plusSeconds(4), false, false);
        save(USER_ID, AiAssistantKey.LAB_ASSISTANT, "STUDENT", "research", "SUCCESS", START.plusSeconds(5), true, true);

        assertEquals(2, usageLogRepository
                .countByUserIdAndAssistantKeyAndRoleAndModuleAndCreatedAtGreaterThanEqualAndCreatedAtLessThanAndActiveTrueAndDeletedFalse(
                        USER_ID, AiAssistantKey.LAB_ASSISTANT, "STUDENT", "research", START, END));
    }

    private void save(Long userId, AiAssistantKey assistantKey, String role, String module, String status,
                      Instant createdAt, boolean active, boolean deleted) {
        AiUsageLogEntity usageLog = AiUsageLogEntity.builder().userId(userId).assistantKey(assistantKey).role(role)
                .module(module).status(status).createdAt(createdAt).build();
        usageLog.setActive(active);
        usageLog.setDeleted(deleted);
        usageLogRepository.saveAndFlush(usageLog);
    }
}
