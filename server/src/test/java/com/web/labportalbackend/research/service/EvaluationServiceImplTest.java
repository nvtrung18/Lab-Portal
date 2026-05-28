package com.web.labportalbackend.research.service;

import com.web.labportalbackend.auth.entity.Role;
import com.web.labportalbackend.auth.entity.User;
import com.web.labportalbackend.auth.repository.UserRepository;
import com.web.labportalbackend.common.exception.InvalidEvaluationScoreException;
import com.web.labportalbackend.lab.entity.Laboratory;
import com.web.labportalbackend.lab.repository.LaboratoryRepository;
import com.web.labportalbackend.research.dto.request.EvaluationRequest;
import com.web.labportalbackend.research.dto.response.EvaluationResponse;
import com.web.labportalbackend.research.entity.EvaluationEntity;
import com.web.labportalbackend.research.entity.ProjectEntity;
import com.web.labportalbackend.research.repository.EvaluationRepository;
import com.web.labportalbackend.research.repository.GroupMemberRepository;
import com.web.labportalbackend.research.repository.GroupRepository;
import com.web.labportalbackend.research.repository.ProjectRepository;
import com.web.labportalbackend.research.service.impl.EvaluationServiceImpl;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Optional;

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
    private GroupRepository groupRepository;

    @Mock
    private GroupMemberRepository groupMemberRepository;

    @Mock
    private LaboratoryRepository laboratoryRepository;

    @Mock
    private UserRepository userRepository;

    @Mock
    private LogService logService;

    @InjectMocks
    private EvaluationServiceImpl evaluationService;

    @AfterEach
    void tearDown() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void evaluateProject_upsertsStudentEvaluationAndCalculatesAverageScore() {
        EvaluationRequest request = request(new BigDecimal("8.5"));
        User manager = user(7L, "manager", "LAB_MANAGER");
        User student = user(12L, "student", "STUDENT");
        Laboratory lab = lab(3L);
        ProjectEntity project = ProjectEntity.builder().lab(lab).title("Project").build();
        project.setId(10L);
        mockManagerContext(manager, lab, project);

        when(userRepository.findById(12L)).thenReturn(Optional.of(student));
        when(groupMemberRepository.findActiveGroupIdsByProjectIdAndUserId(10L, 12L)).thenReturn(List.of(20L));
        when(evaluationRepository.findFirstByProjectIdAndStudentIdAndDeletedFalseAndActiveTrueOrderByCreatedAtDesc(10L, 12L))
                .thenReturn(Optional.empty());
        when(evaluationRepository.save(any(EvaluationEntity.class))).thenAnswer(invocation -> {
            EvaluationEntity evaluation = invocation.getArgument(0);
            evaluation.setId(100L);
            evaluation.setCreatedAt(Instant.parse("2026-05-14T03:30:00Z"));
            return evaluation;
        });

        EvaluationResponse response = evaluationService.evaluateProject(10L, request);

        assertEquals(100L, response.getId());
        assertEquals(10L, response.getProjectId());
        assertEquals(12L, response.getStudentId());
        assertEquals(7L, response.getEvaluatorId());
        assertEquals(new BigDecimal("8.70"), response.getTotalScore());

        ArgumentCaptor<EvaluationEntity> captor = ArgumentCaptor.forClass(EvaluationEntity.class);
        verify(evaluationRepository).save(captor.capture());
        assertEquals(20L, captor.getValue().getGroupId());
        assertEquals(new BigDecimal("8.50"), captor.getValue().getAttendanceScore());
        assertEquals(new BigDecimal("8.70"), captor.getValue().getTotalScore());
        verify(logService).logAction(10L, 7L, "EVALUATE_STUDENT", "Evaluated student 12 with total score: 8.70");
    }

    @Test
    void evaluateProject_rejectsScoreAboveMaximum() {
        EvaluationRequest request = request(new BigDecimal("10.5"));
        User manager = user(7L, "manager", "LAB_MANAGER");
        Laboratory lab = lab(3L);
        ProjectEntity project = ProjectEntity.builder().lab(lab).title("Project").build();
        project.setId(10L);
        mockManagerContext(manager, lab, project);

        assertThrows(InvalidEvaluationScoreException.class, () -> evaluationService.evaluateProject(10L, request));
        verify(evaluationRepository, never()).save(any(EvaluationEntity.class));
    }

    @Test
    void evaluateProject_rejectsScoreBelowMinimum() {
        EvaluationRequest request = request(new BigDecimal("-0.5"));
        User manager = user(7L, "manager", "LAB_MANAGER");
        Laboratory lab = lab(3L);
        ProjectEntity project = ProjectEntity.builder().lab(lab).title("Project").build();
        project.setId(10L);
        mockManagerContext(manager, lab, project);

        assertThrows(InvalidEvaluationScoreException.class, () -> evaluationService.evaluateProject(10L, request));
        verify(evaluationRepository, never()).save(any(EvaluationEntity.class));
    }

    @Test
    void evaluateProject_rejectsNonManagerEvaluator() {
        EvaluationRequest request = request(new BigDecimal("8.5"));
        User leader = user(12L, "leader", "STUDENT");
        ProjectEntity project = ProjectEntity.builder().lab(lab(3L)).title("Project").build();
        project.setId(10L);
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken("leader", null, List.of())
        );
        when(userRepository.findByUsername("leader")).thenReturn(Optional.of(leader));
        when(projectRepository.findByIdAndDeletedFalseAndActiveTrue(10L)).thenReturn(Optional.of(project));

        assertThrows(AccessDeniedException.class, () -> evaluationService.evaluateProject(10L, request));
        verify(evaluationRepository, never()).save(any(EvaluationEntity.class));
    }

    @Test
    void getByProject_studentReceivesOnlyOwnEvaluation() {
        User student = user(12L, "student", "STUDENT");
        ProjectEntity project = ProjectEntity.builder().lab(lab(3L)).title("Project").build();
        project.setId(10L);
        EvaluationEntity evaluation = new EvaluationEntity();
        evaluation.setId(100L);
        evaluation.setProjectId(10L);
        evaluation.setStudentId(12L);
        evaluation.setEvaluatorId(7L);
        evaluation.setAttendanceScore(new BigDecimal("8.50"));
        evaluation.setTaskScore(new BigDecimal("9.00"));
        evaluation.setReportScore(new BigDecimal("8.00"));
        evaluation.setProductScore(new BigDecimal("8.50"));
        evaluation.setAttitudeScore(new BigDecimal("9.50"));
        evaluation.setTotalScore(new BigDecimal("8.70"));

        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken("student", null, List.of())
        );
        when(userRepository.findByUsername("student")).thenReturn(Optional.of(student));
        when(projectRepository.findByIdAndDeletedFalseAndActiveTrue(10L)).thenReturn(Optional.of(project));
        when(groupMemberRepository.existsActiveMemberByProjectIdAndUserId(10L, 12L)).thenReturn(true);
        when(evaluationRepository.findByProjectIdAndStudentIdAndDeletedFalseAndActiveTrueOrderByCreatedAtDesc(10L, 12L))
                .thenReturn(List.of(evaluation));
        when(userRepository.findById(12L)).thenReturn(Optional.of(student));

        List<EvaluationResponse> responses = evaluationService.getByProject(10L);

        assertEquals(1, responses.size());
        assertEquals(12L, responses.get(0).getStudentId());
        verify(evaluationRepository, never()).findByProjectIdAndDeletedFalseAndActiveTrueOrderByCreatedAtDesc(10L);
    }

    private void mockManagerContext(User manager, Laboratory lab, ProjectEntity project) {
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(manager.getUsername(), null, List.of())
        );
        when(userRepository.findByUsername(manager.getUsername())).thenReturn(Optional.of(manager));
        when(projectRepository.findByIdAndDeletedFalseAndActiveTrue(10L)).thenReturn(Optional.of(project));
        when(laboratoryRepository.findFirstByManagerIdAndDeletedFalse(manager.getId())).thenReturn(Optional.of(lab));
    }

    private EvaluationRequest request(BigDecimal attendanceScore) {
        EvaluationRequest request = new EvaluationRequest();
        request.setProjectId(10L);
        request.setStudentId(12L);
        request.setAttendanceScore(attendanceScore);
        request.setTaskScore(new BigDecimal("9.0"));
        request.setReportScore(new BigDecimal("8.0"));
        request.setProductScore(new BigDecimal("8.5"));
        request.setAttitudeScore(new BigDecimal("9.5"));
        request.setLecturerComment("Solid final result");
        return request;
    }

    private User user(Long id, String username, String roleName) {
        User user = new User();
        user.setId(id);
        user.setUsername(username);
        user.setFullName(username);
        user.addRole(new Role(roleName, roleName));
        return user;
    }

    private Laboratory lab(Long id) {
        Laboratory lab = new Laboratory();
        lab.setId(id);
        lab.setLabName("Lab");
        lab.setLocation("Room");
        lab.setCapacity(10);
        return lab;
    }
}
