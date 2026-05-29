package com.web.labportalbackend.auth;

import com.web.labportalbackend.auth.dto.UpdateUserRoleRequest;
import com.web.labportalbackend.auth.dto.UpdateUserRoleResponse;
import com.web.labportalbackend.auth.entity.Role;
import com.web.labportalbackend.auth.entity.User;
import com.web.labportalbackend.auth.repository.RoleRepository;
import com.web.labportalbackend.auth.repository.UserRepository;
import com.web.labportalbackend.auth.service.AuthService;
import com.web.labportalbackend.common.enums.LabStatus;
import com.web.labportalbackend.common.enums.UserStatus;
import com.web.labportalbackend.lab.entity.Laboratory;
import com.web.labportalbackend.lab.repository.LaboratoryRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

import java.util.Collections;
import java.util.HashSet;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest
@ActiveProfiles("test")
@Transactional
@WithMockUser(username = "admin_user", roles = {"ADMIN"})
class AuthServiceRoleChangeIntegrationTest {

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
    private User testUser;
    private Laboratory testLab;

    @BeforeEach
    void setUp() {
        // Ensure roles exist
        studentRole = roleRepository.findByName("STUDENT")
                .orElseGet(() -> roleRepository.save(new Role("STUDENT", "Student")));
        managerRole = roleRepository.findByName("LAB_MANAGER")
                .orElseGet(() -> roleRepository.save(new Role("LAB_MANAGER", "Lab Manager")));
        Role adminRole = roleRepository.findByName("ADMIN")
                .orElseGet(() -> roleRepository.save(new Role("ADMIN", "Admin")));

        // Create admin user in database so getCurrentUser can look it up
        if (userRepository.findByUsername("admin_user").isEmpty()) {
            User adminUser = new User();
            adminUser.setUsername("admin_user");
            adminUser.setEmail("admin@test.com");
            adminUser.setPassword("password123");
            adminUser.setStatus(UserStatus.ACTIVE);
            adminUser.setRoles(new HashSet<>(Collections.singleton(adminRole)));
            userRepository.saveAndFlush(adminUser);
        }

        // Create test user (currently STUDENT)
        testUser = new User();
        testUser.setUsername("teststudent");
        testUser.setEmail("student@test.com");
        testUser.setPassword("password123");
        testUser.setStatus(UserStatus.ACTIVE);
        testUser.setRoles(new HashSet<>(Collections.singleton(studentRole)));
        testUser = userRepository.saveAndFlush(testUser);

        // Create test laboratory
        testLab = new Laboratory();
        testLab.setLabName("AI Research Lab");
        testLab.setLocation("Building A");
        testLab.setCapacity(20);
        testLab.setStatus(LabStatus.AVAILABLE);
        testLab = laboratoryRepository.saveAndFlush(testLab);
    }

    @Test
    void promoteStudentToLabManager_succeedsAndAssignsLab() {
        UpdateUserRoleRequest request = new UpdateUserRoleRequest();
        request.setRole("LAB_MANAGER");
        request.setLabId(testLab.getId());

        UpdateUserRoleResponse response = authService.patchUserRole(testUser.getId(), request);

        assertNotNull(response);
        assertEquals("Đã cập nhật vai trò người dùng.", response.getMessage());
        assertEquals("LAB_MANAGER", response.getUser().getRole());
        assertEquals(testLab.getId(), response.getUser().getManagedLabId());

        // Verify database state
        User updatedUser = userRepository.findById(testUser.getId()).orElseThrow();
        assertTrue(updatedUser.hasRole("LAB_MANAGER"));

        Laboratory updatedLab = laboratoryRepository.findById(testLab.getId()).orElseThrow();
        assertNotNull(updatedLab.getManager());
        assertEquals(testUser.getId(), updatedLab.getManager().getId());
    }

    @Test
    void demoteLabManagerToStudent_succeedsAndUnassignsLab() {
        // 1. First promote to manager
        testUser.setRoles(new HashSet<>(Collections.singleton(managerRole)));
        testUser = userRepository.saveAndFlush(testUser);
        testLab.setManager(testUser);
        testLab = laboratoryRepository.saveAndFlush(testLab);

        // Verify initial setup
        assertTrue(testUser.hasRole("LAB_MANAGER"));
        assertEquals(testUser.getId(), testLab.getManager().getId());

        // 2. Perform demotion to STUDENT
        UpdateUserRoleRequest request = new UpdateUserRoleRequest();
        request.setRole("STUDENT");

        UpdateUserRoleResponse response = authService.patchUserRole(testUser.getId(), request);

        assertNotNull(response);
        assertEquals("Đã chuyển người dùng về vai trò sinh viên.", response.getMessage());
        assertEquals("STUDENT", response.getUser().getRole());
        assertNull(response.getUser().getManagedLabId());

        // Verify database state
        User updatedUser = userRepository.findById(testUser.getId()).orElseThrow();
        assertTrue(updatedUser.hasRole("STUDENT"));
        assertFalse(updatedUser.hasRole("LAB_MANAGER"));

        Laboratory updatedLab = laboratoryRepository.findById(testLab.getId()).orElseThrow();
        assertNull(updatedLab.getManager());
    }

    @Test
    void promoteToLabManagerWithoutLabId_throwsException() {
        UpdateUserRoleRequest request = new UpdateUserRoleRequest();
        request.setRole("LAB_MANAGER");
        request.setLabId(null);

        assertThrows(IllegalArgumentException.class, () -> {
            authService.patchUserRole(testUser.getId(), request);
        });
    }
}
