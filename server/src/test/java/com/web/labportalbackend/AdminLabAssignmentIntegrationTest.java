package com.web.labportalbackend;

import com.web.labportalbackend.auth.dto.AssignableManagerResponse;
import com.web.labportalbackend.auth.entity.Role;
import com.web.labportalbackend.auth.entity.User;
import com.web.labportalbackend.auth.repository.RoleRepository;
import com.web.labportalbackend.auth.repository.UserRepository;
import com.web.labportalbackend.auth.service.AuthService;
import com.web.labportalbackend.common.enums.LabStatus;
import com.web.labportalbackend.common.enums.UserStatus;
import com.web.labportalbackend.lab.dto.response.LabResponse;
import com.web.labportalbackend.lab.entity.Laboratory;
import com.web.labportalbackend.lab.repository.LaboratoryRepository;
import com.web.labportalbackend.lab.service.LabService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

import java.util.Collections;
import java.util.HashSet;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest
@ActiveProfiles("test")
@Transactional
@WithMockUser(username = "admin_user", roles = {"ADMIN"})
class AdminLabAssignmentIntegrationTest {

    @Autowired
    private LabService labService;

    @Autowired
    private AuthService authService;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private RoleRepository roleRepository;

    @Autowired
    private LaboratoryRepository laboratoryRepository;

    private Role studentRole;
    private Role managerRole;
    private Role adminRole;
    private User testStudent;
    private User testManager;
    private Laboratory testLab;

    @BeforeEach
    void setUp() {
        studentRole = roleRepository.findByName("STUDENT")
                .orElseGet(() -> roleRepository.save(new Role("STUDENT", "Student")));
        managerRole = roleRepository.findByName("LAB_MANAGER")
                .orElseGet(() -> roleRepository.save(new Role("LAB_MANAGER", "Lab Manager")));
        adminRole = roleRepository.findByName("ADMIN")
                .orElseGet(() -> roleRepository.save(new Role("ADMIN", "Admin")));

        // Create admin user
        if (userRepository.findByUsername("admin_user").isEmpty()) {
            User adminUser = new User();
            adminUser.setUsername("admin_user");
            adminUser.setEmail("admin@test.com");
            adminUser.setPassword("password123");
            adminUser.setStatus(UserStatus.ACTIVE);
            adminUser.setRoles(new HashSet<>(Collections.singleton(adminRole)));
            userRepository.saveAndFlush(adminUser);
        }

        // Create student user
        testStudent = new User();
        testStudent.setUsername("student1");
        testStudent.setEmail("student1@test.com");
        testStudent.setPassword("password123");
        testStudent.setStatus(UserStatus.ACTIVE);
        testStudent.setRoles(new HashSet<>(Collections.singleton(studentRole)));
        testStudent = userRepository.saveAndFlush(testStudent);

        // Create manager user (unassigned initially)
        testManager = new User();
        testManager.setUsername("manager1");
        testManager.setEmail("manager1@test.com");
        testManager.setPassword("password123");
        testManager.setStatus(UserStatus.ACTIVE);
        testManager.setRoles(new HashSet<>(Collections.singleton(managerRole)));
        testManager = userRepository.saveAndFlush(testManager);

        // Create laboratory without manager
        testLab = new Laboratory();
        testLab.setLabName("Network Lab");
        testLab.setLocation("Building C");
        testLab.setCapacity(25);
        testLab.setStatus(LabStatus.AVAILABLE);
        testLab = laboratoryRepository.saveAndFlush(testLab);
    }

    @Test
    void getAssignableManagers_returnsUnassignedManagersOnly() {
        List<AssignableManagerResponse> managers = authService.getAssignableManagers();
        assertNotNull(managers);
        
        // At least our testManager should be present since it is unassigned
        boolean foundManager = managers.stream()
                .anyMatch(m -> m.getId().equals(testManager.getId()));
        assertTrue(foundManager);

        // Assign manager to testLab and check again
        testLab.setManager(testManager);
        laboratoryRepository.saveAndFlush(testLab);

        managers = authService.getAssignableManagers();
        boolean foundManagerAfterAssign = managers.stream()
                .anyMatch(m -> m.getId().equals(testManager.getId()));
        assertFalse(foundManagerAfterAssign);
    }

    @Test
    void assignManagerPatch_succeeds() {
        LabResponse response = labService.assignManagerPatch(testLab.getId(), testManager.getId());
        assertNotNull(response);
        assertNotNull(response.getManager());
        assertEquals(testManager.getId(), response.getManager().getId());

        // Verify in database
        Laboratory updatedLab = laboratoryRepository.findById(testLab.getId()).orElseThrow();
        assertNotNull(updatedLab.getManager());
        assertEquals(testManager.getId(), updatedLab.getManager().getId());
    }

    @Test
    void assignManagerPatch_failsWhenLabAlreadyHasManager() {
        // Assign manager first
        testLab.setManager(testManager);
        laboratoryRepository.saveAndFlush(testLab);

        // Create another manager
        User anotherManager = new User();
        anotherManager.setUsername("manager2");
        anotherManager.setEmail("manager2@test.com");
        anotherManager.setPassword("password123");
        anotherManager.setStatus(UserStatus.ACTIVE);
        anotherManager.setRoles(new HashSet<>(Collections.singleton(managerRole)));
        anotherManager = userRepository.saveAndFlush(anotherManager);

        User finalAnotherManager = anotherManager;
        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class, () -> {
            labService.assignManagerPatch(testLab.getId(), finalAnotherManager.getId());
        });
        assertEquals("PTN này đã có quản lý.", ex.getMessage());
    }

    @Test
    void assignManagerPatch_failsWhenUserIsNotManager() {
        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class, () -> {
            labService.assignManagerPatch(testLab.getId(), testStudent.getId());
        });
        assertEquals("Người dùng phải có vai trò quản lý PTN.", ex.getMessage());
    }
}
