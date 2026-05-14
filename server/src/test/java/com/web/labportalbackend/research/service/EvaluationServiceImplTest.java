package com.web.labportalbackend.research.service;

import com.web.labportalbackend.auth.repository.UserRepository;
import com.web.labportalbackend.common.exception.InvalidEvaluationScoreException;
import com.web.labportalbackend.research.dto.request.EvaluationRequest;
import com.web.labportalbackend.research.dto.response.EvaluationResponse;
import com.web.labportalbackend.research.entity.EvaluationEntity;
import com.web.labportalbackend.research.repository.EvaluationRepository;
import com.web.labportalbackend.research.repository.ProjectRepository;
import com.web.labportalbackend.research.service.impl.EvaluationServiceImpl;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.Instant;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class EvaluationServiceImplTest {

    @Mock
    private EvaluationRepository evaluationRepository;

    @Mock
    private ProjectRepository projectRepository;

    @Mock
    private UserRepository userRepository;

    @Mock
    private LogService logService;

    @InjectMocks
    private EvaluationServiceImpl evaluationService;

    @Test
    void evaluateProject_acceptsValidScore() {
        EvaluationRequest request = request(new BigDecimal("85.5"));

        when(projectRepository.existsById(10L)).thenReturn(true);
        when(userRepository.existsById(7L)).thenReturn(true);
        when(evaluationRepository.save(any(EvaluationEntity.class))).thenAnswer(invocation -> {
            EvaluationEntity evaluation = invocation.getArgument(0);
            evaluation.setId(100L);
            evaluation.setCreatedAt(Instant.parse("2026-05-14T03:30:00Z"));
            return evaluation;
        });

        EvaluationResponse response = evaluationService.evaluateProject(10L, request);

        assertEquals(100L, response.getId());
        assertEquals(10L, response.getProjectId());
        assertEquals(7L, response.getReviewerId());
        assertEquals(new BigDecimal("85.5"), response.getScore());

        ArgumentCaptor<EvaluationEntity> captor = ArgumentCaptor.forClass(EvaluationEntity.class);
        verify(evaluationRepository).save(captor.capture());
        assertEquals(new BigDecimal("85.5"), captor.getValue().getScore());
        verify(logService).logAction(10L, 7L, "EVALUATE", "Evaluated project with score: 85.5");
    }

    @Test
    void evaluateProject_rejectsScoreAboveMaximum() {
        EvaluationRequest request = request(new BigDecimal("150.0"));

        assertThrows(InvalidEvaluationScoreException.class, () -> evaluationService.evaluateProject(10L, request));
        verify(evaluationRepository, never()).save(any(EvaluationEntity.class));
    }

    @Test
    void evaluateProject_rejectsScoreBelowMinimum() {
        EvaluationRequest request = request(new BigDecimal("-5.0"));

        assertThrows(InvalidEvaluationScoreException.class, () -> evaluationService.evaluateProject(10L, request));
        verify(evaluationRepository, never()).save(any(EvaluationEntity.class));
    }

    private EvaluationRequest request(BigDecimal score) {
        EvaluationRequest request = new EvaluationRequest();
        request.setProjectId(10L);
        request.setReviewerId(7L);
        request.setScore(score);
        request.setComments("Solid final result");
        return request;
    }
}
