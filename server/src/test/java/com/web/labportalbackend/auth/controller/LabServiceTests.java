package com.web.labportalbackend.auth.controller;

import com.web.labportalbackend.auth.entity.Role;
import com.web.labportalbackend.auth.entity.User;
import com.web.labportalbackend.auth.repository.RoleRepository;
import com.web.labportalbackend.auth.repository.UserRepository;
import com.web.labportalbackend.auth.service.LabService;
import com.web.labportalbackend.common.dto.CreateLabRequest;
import com.web.labportalbackend.common.dto.LabDTO;
import com.web.labportalbackend.common.enums.UserStatus;
import com.web.labportalbackend.lab.entity.Laboratory;
import com.web.labportalbackend.lab.repository.LaboratoryRepository;
import jakarta.persistence.EntityNotFoundException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.transaction.annotation.Transactional;

import java.util.HashSet;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit and integration tests for LabService.
 * Tests include:
 * - Lab creation with validation
 * - Manager assignment with role verification
 * - Foreign key validation (user_id)
 * - Lab domain independence
 */
@SpringBootTest
@Transactional
@DisplayName("Laboratory Service Tests - Day 5")
class LabServiceTests {

    @Autowired
    private LabService labService;

    @Autowired
    private LaboratoryRepository laboratoryRepository;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private RoleRepository roleRepository;

    private User adminUser;
    private User studentUser;
    private Role adminRole;

    @BeforeEach
    void setup() {
        // Get existing admin role from database
        adminRole = roleRepository.findByName("ADMIN")
                .orElseThrow(() -> new RuntimeException("ADMIN role not found"));

        // Create or get admin user
        adminUser = userRepository.findByUsername("admin")
                .orElse(null);

        if (adminUser == null) {
            adminUser = new User();
            adminUser.setUsername("admin_lab_test");
            adminUser.setEmail("admin_lab_test@test.com");
            adminUser.setPassword("encoded_password");
            adminUser.setStatus(UserStatus.ACTIVE);
            Set<Role> roles = new HashSet<>();
            roles.add(adminRole);
            adminUser.setRoles(roles);
            adminUser = userRepository.save(adminUser);
        }

        // Create student user for negative tests
        studentUser = new User();
        studentUser.setUsername("student_lab_test");
        studentUser.setEmail("student_lab_test@test.com");
        studentUser.setPassword("encoded_password");
        studentUser.setStatus(UserStatus.ACTIVE);
        Role studentRole = roleRepository.findByName("STUDENT")
                .orElseThrow(() -> new RuntimeException("STUDENT role not found"));
        Set<Role> studentRoles = new HashSet<>();
        studentRoles.add(studentRole);
        studentUser.setRoles(studentRoles);
        studentUser = userRepository.save(studentUser);
    }

    @Test
    @DisplayName("Create Lab: Should successfully create lab with valid request")
    void testCreateLabSuccess() {
        CreateLabRequest request = new CreateLabRequest();
        request.setLabName("Physics Lab Test");
        request.setDescription("Advanced physics experiments");
        request.setLocation("Building A, Floor 3");
        request.setCapacity(30);
        request.setDepartment("Physics");

        LabDTO result = labService.createLab(request);

        assertNotNull(result);
        assertNotNull(result.getId());
        assertEquals("Physics Lab Test", result.getLabName());
        assertEquals("Advanced physics experiments", result.getDescription());
        assertEquals("Building A, Floor 3", result.getLocation());
        assertEquals(30, result.getCapacity());
        assertEquals("Physics", result.getDepartment());
        assertNull(result.getManager());

        // Verify persisted in database
        assertTrue(laboratoryRepository.existsByLabName("Physics Lab Test"));
    }

    @Test
    @DisplayName("Create Lab: Should reject duplicate lab name")
    void testCreateLabDuplicateName() {
        CreateLabRequest request = new CreateLabRequest();
        request.setLabName("Duplicate Lab");
        request.setDescription("Description");
        request.setLocation("Location");
        request.setCapacity(20);
        request.setDepartment("Dept");

        // Create first lab
        labService.createLab(request);

        // Try to create with same name
        CreateLabRequest duplicate = new CreateLabRequest();
        duplicate.setLabName("Duplicate Lab");
        duplicate.setDescription("Different description");
        duplicate.setLocation("Different location");
        duplicate.setCapacity(25);
        duplicate.setDepartment("Different dept");

        assertThrows(IllegalArgumentException.class, () -> labService.createLab(duplicate),
                "Should reject duplicate lab name");
    }

    @Test
    @DisplayName("Assign Manager: Should successfully assign admin user as manager")
    void testAssignManagerSuccess() {
        // Create lab
        CreateLabRequest createRequest = new CreateLabRequest();
        createRequest.setLabName("Manager Test Lab");
        createRequest.setDescription("Description");
        createRequest.setLocation("Location");
        createRequest.setCapacity(20);

        LabDTO createdLab = labService.createLab(createRequest);
        Long labId = createdLab.getId();

        // Assign manager (admin user)
        LabDTO result = labService.assignManager(labId, adminUser.getId());

        assertNotNull(result);
        assertNotNull(result.getManager());
        assertEquals(adminUser.getId(), result.getManager().getId());
        assertEquals(adminUser.getUsername(), result.getManager().getUsername());

        // Verify in database
        Laboratory persistedLab = laboratoryRepository.findById(labId)
                .orElseThrow();
        assertNotNull(persistedLab.getManager());
        assertEquals(adminUser.getId(), persistedLab.getManager().getId());
    }

    @Test
    @DisplayName("Assign Manager: Should reject non-admin user as manager")
    void testAssignManagerNonAdminRoleFailure() {
        // Create lab
        CreateLabRequest createRequest = new CreateLabRequest();
        createRequest.setLabName("Non-Admin Manager Lab");
        createRequest.setDescription("Description");
        createRequest.setLocation("Location");
        createRequest.setCapacity(20);

        LabDTO createdLab = labService.createLab(createRequest);
        Long labId = createdLab.getId();

        // Try to assign student (non-admin) as manager
        assertThrows(IllegalArgumentException.class,
                () -> labService.assignManager(labId, studentUser.getId()),
                "Should reject non-admin user as manager");
    }

    @Test
    @DisplayName("Assign Manager: Should handle non-existent lab")
    void testAssignManagerNonExistentLab() {
        assertThrows(EntityNotFoundException.class,
                () -> labService.assignManager(99999L, adminUser.getId()),
                "Should throw EntityNotFoundException for non-existent lab");
    }

    @Test
    @DisplayName("Assign Manager: Should handle non-existent user")
    void testAssignManagerNonExistentUser() {
        // Create lab
        CreateLabRequest createRequest = new CreateLabRequest();
        createRequest.setLabName("Non-Existent User Lab");
        createRequest.setDescription("Description");
        createRequest.setLocation("Location");
        createRequest.setCapacity(20);

        LabDTO createdLab = labService.createLab(createRequest);

        assertThrows(EntityNotFoundException.class,
                () -> labService.assignManager(createdLab.getId(), 99999L),
                "Should throw EntityNotFoundException for non-existent user");
    }

    @Test
    @DisplayName("Assign Manager: Should verify FK relationship to user table")
    void testAssignManagerForeignKeyValidation() {
        // Create lab
        CreateLabRequest createRequest = new CreateLabRequest();
        createRequest.setLabName("FK Validation Lab");
        createRequest.setDescription("Description");
        createRequest.setLocation("Location");
        createRequest.setCapacity(20);

        LabDTO createdLab = labService.createLab(createRequest);
        Long labId = createdLab.getId();

        // Assign valid manager
        LabDTO result = labService.assignManager(labId, adminUser.getId());

        // Verify FK constraint is maintained
        assertNotNull(result.getManager());
        assertEquals(adminUser.getId(), result.getManager().getId());

        // Verify database integrity - manager_id should reference valid user_id
        Laboratory lab = laboratoryRepository.findById(labId).orElseThrow();
        assertNotNull(lab.getManager());
        assertTrue(userRepository.existsById(lab.getManager().getId()));
    }

    @Test
    @DisplayName("Lab Domain: Independence - Create and manage lab without other modules")
    void testLabDomainIndependence() {
        // Scenario 1: Create lab independently
        CreateLabRequest request1 = new CreateLabRequest();
        request1.setLabName("Independent Lab 1");
        request1.setDescription("Independent description");
        request1.setLocation("Independent location");
        request1.setCapacity(40);
        request1.setDepartment("Independent dept");

        LabDTO lab1 = labService.createLab(request1);
        assertNotNull(lab1);
        assertNotNull(lab1.getId());

        // Scenario 2: Create another lab independently
        CreateLabRequest request2 = new CreateLabRequest();
        request2.setLabName("Independent Lab 2");
        request2.setDescription("Another independent lab");
        request2.setLocation("Another location");
        request2.setCapacity(25);
        request2.setDepartment("Another dept");

        LabDTO lab2 = labService.createLab(request2);
        assertNotNull(lab2);
        assertNotNull(lab2.getId());

        // Scenario 3: Assign managers independently
        LabDTO updatedLab1 = labService.assignManager(lab1.getId(), adminUser.getId());
        assertEquals(adminUser.getId(), updatedLab1.getManager().getId());

        // Verify lab2 is unaffected
        LabDTO lab2Check = labService.getLabById(lab2.getId());
        assertNull(lab2Check.getManager());

        // Verify independence - no other module dependencies needed
        assertTrue(laboratoryRepository.existsByLabName("Independent Lab 1"));
        assertTrue(laboratoryRepository.existsByLabName("Independent Lab 2"));
    }
}
