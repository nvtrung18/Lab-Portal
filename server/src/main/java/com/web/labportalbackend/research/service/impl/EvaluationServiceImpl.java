package com.web.labportalbackend.research.service.impl;

import com.web.labportalbackend.auth.repository.UserRepository;
import com.web.labportalbackend.common.exception.InvalidEvaluationScoreException;
import com.web.labportalbackend.common.exception.ResourceNotFoundException;
import com.web.labportalbackend.research.dto.request.EvaluationRequest;
import com.web.labportalbackend.research.dto.response.EvaluationResponse;
import com.web.labportalbackend.research.entity.EvaluationEntity;
import com.web.labportalbackend.research.mapper.EvaluationMapper;
import com.web.labportalbackend.research.repository.EvaluationRepository;
import com.web.labportalbackend.research.repository.ProjectRepository;
import com.web.labportalbackend.research.service.EvaluationService;
import com.web.labportalbackend.research.service.LogService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.List;

@Service
@RequiredArgsConstructor
public class EvaluationServiceImpl implements EvaluationService {

    private static final BigDecimal MIN_SCORE = BigDecimal.ZERO;
    private static final BigDecimal MAX_SCORE = BigDecimal.valueOf(100);

    private final EvaluationRepository evaluationRepository;
    private final ProjectRepository projectRepository;
    private final UserRepository userRepository;
    private final LogService logService;

    @Override
    @Transactional
    public EvaluationResponse evaluateProject(Long projectId, EvaluationRequest request) {
        validateScore(request.getScore());
        if (!projectRepository.existsById(projectId)) {
            throw new ResourceNotFoundException("Project", projectId);
        }
        if (!userRepository.existsById(request.getReviewerId())) {
            throw new ResourceNotFoundException("User", request.getReviewerId());
        }

        EvaluationEntity evaluation = EvaluationEntity.builder()
                .projectId(projectId)
                .reviewerId(request.getReviewerId())
                .score(request.getScore())
                .comments(request.getComments())
                .build();

        EvaluationEntity saved = evaluationRepository.save(evaluation);
        logService.logAction(projectId, request.getReviewerId(), "EVALUATE", "Evaluated project with score: " + request.getScore());
        return EvaluationMapper.toResponse(saved);
    }

    @Override
    @Transactional(readOnly = true)
    public List<EvaluationResponse> getByProject(Long projectId) {
        if (!projectRepository.existsById(projectId)) {
            throw new ResourceNotFoundException("Project", projectId);
        }
        return evaluationRepository.findByProjectIdOrderByCreatedAtDesc(projectId)
                .stream()
                .map(EvaluationMapper::toResponse)
                .toList();
    }

    private void validateScore(BigDecimal score) {
        if (score == null || score.compareTo(MIN_SCORE) < 0 || score.compareTo(MAX_SCORE) > 0) {
            throw new InvalidEvaluationScoreException(score);
        }
    }
}
