package com.web.labportalbackend.research.repository;

import com.web.labportalbackend.research.entity.TaskCommentEntity;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

@DataJpaTest
class TaskCommentRepositoryTest {

    @Autowired TaskCommentRepository taskCommentRepository;
    @Autowired EntityManager entityManager;

    @Test
    void savesAndReloadsTaskCommentBaseEntityFields() {
        TaskCommentEntity saved = taskCommentRepository.saveAndFlush(TaskCommentEntity.builder()
                .taskId(10L).authorId(20L).content("Persisted task comment").build());
        entityManager.clear();

        TaskCommentEntity reloaded = taskCommentRepository.findById(saved.getId()).orElseThrow();
        assertEquals(10L, reloaded.getTaskId());
        assertEquals(20L, reloaded.getAuthorId());
        assertEquals("Persisted task comment", reloaded.getContent());
        assertNotNull(reloaded.getCreatedAt());
        assertNotNull(reloaded.getUpdatedAt());
        assertEquals(Boolean.TRUE, reloaded.getActive());
        assertEquals(Boolean.FALSE, reloaded.getDeleted());
    }
}
