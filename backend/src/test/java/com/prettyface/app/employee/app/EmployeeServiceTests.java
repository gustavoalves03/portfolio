package com.prettyface.app.employee.app;

import com.prettyface.app.care.domain.Care;
import com.prettyface.app.care.repo.CareRepository;
import com.prettyface.app.employee.domain.AccessLevel;
import com.prettyface.app.employee.domain.Employee;
import com.prettyface.app.employee.domain.EmployeePermission;
import com.prettyface.app.employee.domain.PermissionDomain;
import com.prettyface.app.employee.repo.EmployeePermissionRepository;
import com.prettyface.app.employee.repo.EmployeeRepository;
import com.prettyface.app.employee.web.dto.CreateEmployeeRequest;
import com.prettyface.app.employee.web.dto.EmployeeResponse;
import com.prettyface.app.employee.web.dto.EmployeeSlimResponse;
import com.prettyface.app.employee.web.dto.UpdateEmployeeRequest;
import com.prettyface.app.multitenancy.ApplicationSchemaExecutor;
import com.prettyface.app.multitenancy.TenantContext;
import com.prettyface.app.users.domain.AuthProvider;
import com.prettyface.app.users.domain.Role;
import com.prettyface.app.users.domain.User;
import com.prettyface.app.users.repo.UserRepository;
import org.springframework.http.HttpStatus;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.web.server.ResponseStatusException;

import java.util.Map;

import java.time.LocalDateTime;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.function.Supplier;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class EmployeeServiceTests {

    @Mock
    private EmployeeRepository employeeRepository;

    @Mock
    private UserRepository userRepository;

    @Mock
    private CareRepository careRepository;

    @Mock
    private PasswordEncoder passwordEncoder;

    @Mock
    private ApplicationSchemaExecutor applicationSchemaExecutor;

    @Mock
    private EmployeePermissionRepository employeePermissionRepository;

    @InjectMocks
    private EmployeeService employeeService;

    private Employee employee;
    private User user;
    private Care care;

    @BeforeEach
    void setUp() {
        lenient().when(applicationSchemaExecutor.call(org.mockito.ArgumentMatchers.<Supplier<?>>any()))
                .thenAnswer(invocation -> invocation.<Supplier<?>>getArgument(0).get());
        lenient().doAnswer(invocation -> {
            invocation.<Runnable>getArgument(0).run();
            return null;
        }).when(applicationSchemaExecutor).run(any(Runnable.class));
        TenantContext.clear();

        user = User.builder()
                .id(10L)
                .name("Alice Dupont")
                .email("alice@example.com")
                .password("hashed")
                .provider(AuthProvider.LOCAL)
                .role(Role.EMPLOYEE)
                .build();

        care = new Care();
        care.setId(1L);
        care.setName("Facial");

        employee = new Employee();
        employee.setId(100L);
        employee.setUserId(10L);
        employee.setName("Alice Dupont");
        employee.setEmail("alice@example.com");
        employee.setPhone("+33600000000");
        employee.setActive(true);
        employee.setAssignedCares(new HashSet<>(Set.of(care)));
        // createdAt set via @PrePersist — simulate it
        try {
            var f = Employee.class.getDeclaredField("createdAt");
            f.setAccessible(true);
            f.set(employee, LocalDateTime.of(2024, 1, 1, 9, 0));
        } catch (Exception ignored) {}
    }

    // -----------------------------------------------------------------------
    // create
    // -----------------------------------------------------------------------

    @Test
    void create_createsUserAndEmployee() {
        CreateEmployeeRequest req = new CreateEmployeeRequest(
                "Alice Dupont", "alice@example.com", "+33600000000", "secret", List.of(1L));
        TenantContext.setCurrentTenant("camille-dubois");

        when(userRepository.existsByEmail("alice@example.com")).thenReturn(false);
        when(passwordEncoder.encode("secret")).thenReturn("hashed");
        when(userRepository.save(any(User.class))).thenReturn(user);
        when(careRepository.findAllById(List.of(1L))).thenReturn(List.of(care));
        when(employeeRepository.save(any(Employee.class))).thenReturn(employee);

        EmployeeResponse response = employeeService.create(req);

        // Verify User created with EMPLOYEE role and LOCAL provider
        ArgumentCaptor<User> userCaptor = ArgumentCaptor.forClass(User.class);
        verify(userRepository, atLeast(1)).save(userCaptor.capture());
        User savedUser = userCaptor.getValue();
        assertThat(savedUser.getRole()).isEqualTo(Role.EMPLOYEE);
        assertThat(savedUser.getProvider()).isEqualTo(AuthProvider.LOCAL);
        assertThat(savedUser.getEmail()).isEqualTo("alice@example.com");
        assertThat(savedUser.getPassword()).isEqualTo("hashed");
        assertThat(savedUser.getTenantSlug()).isEqualTo("camille-dubois");

        // Verify Employee created with correct fields
        ArgumentCaptor<Employee> empCaptor = ArgumentCaptor.forClass(Employee.class);
        verify(employeeRepository).save(empCaptor.capture());
        Employee savedEmp = empCaptor.getValue();
        assertThat(savedEmp.getName()).isEqualTo("Alice Dupont");
        assertThat(savedEmp.getEmail()).isEqualTo("alice@example.com");
        assertThat(savedEmp.getPhone()).isEqualTo("+33600000000");
        assertThat(savedEmp.getUserId()).isEqualTo(10L);
        assertThat(savedEmp.getAssignedCares()).contains(care);

        assertThat(response).isNotNull();
        assertThat(response.name()).isEqualTo("Alice Dupont");
        assertThat(TenantContext.getCurrentTenant()).isEqualTo("camille-dubois");
    }

    @Test
    void create_existingEmail_throws() {
        CreateEmployeeRequest req = new CreateEmployeeRequest(
                "Alice Dupont", "alice@example.com", null, "secret", null);
        TenantContext.setCurrentTenant("camille-dubois");

        when(userRepository.existsByEmail("alice@example.com")).thenReturn(true);

        assertThatThrownBy(() -> employeeService.create(req))
                .isInstanceOf(ResponseStatusException.class)
                .satisfies(ex -> {
                    ResponseStatusException responseStatusException = (ResponseStatusException) ex;
                    assertThat(responseStatusException.getStatusCode().value()).isEqualTo(409);
                    assertThat(responseStatusException.getReason()).isEqualTo("Email already in use");
                });

        verify(userRepository, never()).save(any());
        verify(employeeRepository, never()).save(any());
    }

    @Test
    void create_whenUserInsertHitsUniqueConstraint_returnsConflict() {
        CreateEmployeeRequest req = new CreateEmployeeRequest(
                "Alice Dupont", "alice@example.com", null, "secret", null);
        TenantContext.setCurrentTenant("camille-dubois");

        when(userRepository.existsByEmail("alice@example.com")).thenReturn(false);
        when(passwordEncoder.encode("secret")).thenReturn("hashed");
        when(userRepository.save(any(User.class))).thenThrow(new DataIntegrityViolationException("UK_USER_EMAIL"));

        assertThatThrownBy(() -> employeeService.create(req))
                .isInstanceOf(ResponseStatusException.class)
                .satisfies(ex -> {
                    ResponseStatusException responseStatusException = (ResponseStatusException) ex;
                    assertThat(responseStatusException.getStatusCode().value()).isEqualTo(409);
                    assertThat(responseStatusException.getReason()).isEqualTo("Email already in use");
                });

        verify(employeeRepository, never()).save(any());
    }

    @Test
    void create_whenEmployeeSaveFails_rollsBackSharedUser() {
        CreateEmployeeRequest req = new CreateEmployeeRequest(
                "Alice Dupont", "alice@example.com", "+33600000000", "secret", null);
        TenantContext.setCurrentTenant("camille-dubois");

        when(userRepository.existsByEmail("alice@example.com")).thenReturn(false);
        when(passwordEncoder.encode("secret")).thenReturn("hashed");
        when(userRepository.save(any(User.class))).thenReturn(user);
        when(employeeRepository.save(any(Employee.class))).thenThrow(new IllegalStateException("employee save failed"));

        assertThatThrownBy(() -> employeeService.create(req))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("employee save failed");

        verify(userRepository).deleteById(10L);
    }

    // -----------------------------------------------------------------------
    // update
    // -----------------------------------------------------------------------

    @Test
    void toggleActive_setsFlag() {
        UpdateEmployeeRequest req = new UpdateEmployeeRequest(null, null, false, null);

        when(employeeRepository.findById(100L)).thenReturn(Optional.of(employee));
        when(employeeRepository.save(any(Employee.class))).thenReturn(employee);

        EmployeeResponse response = employeeService.update(100L, req);

        ArgumentCaptor<Employee> captor = ArgumentCaptor.forClass(Employee.class);
        verify(employeeRepository).save(captor.capture());
        assertThat(captor.getValue().isActive()).isFalse();
    }

    @Test
    void update_assignsCares() {
        Care care2 = new Care();
        care2.setId(2L);
        care2.setName("Body Wrap");

        UpdateEmployeeRequest req = new UpdateEmployeeRequest(null, null, null, List.of(2L));

        when(employeeRepository.findById(100L)).thenReturn(Optional.of(employee));
        when(careRepository.findAllById(List.of(2L))).thenReturn(List.of(care2));
        when(employeeRepository.save(any(Employee.class))).thenReturn(employee);

        employeeService.update(100L, req);

        ArgumentCaptor<Employee> captor = ArgumentCaptor.forClass(Employee.class);
        verify(employeeRepository).save(captor.capture());
        assertThat(captor.getValue().getAssignedCares()).contains(care2);
    }

    // -----------------------------------------------------------------------
    // listAll
    // -----------------------------------------------------------------------

    @Test
    void listAll_returnsAllEmployees() {
        when(employeeRepository.findAll()).thenReturn(List.of(employee));

        List<EmployeeResponse> result = employeeService.listAll();

        assertThat(result).hasSize(1);
        assertThat(result.get(0).name()).isEqualTo("Alice Dupont");
        assertThat(result.get(0).email()).isEqualTo("alice@example.com");
        assertThat(result.get(0).active()).isTrue();
    }

    // -----------------------------------------------------------------------
    // get
    // -----------------------------------------------------------------------

    @Test
    void get_returnsEmployee() {
        when(employeeRepository.findById(100L)).thenReturn(Optional.of(employee));

        EmployeeResponse response = employeeService.get(100L);

        assertThat(response.id()).isEqualTo(100L);
        assertThat(response.name()).isEqualTo("Alice Dupont");
    }

    @Test
    void get_notFound_throws() {
        when(employeeRepository.findById(999L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> employeeService.get(999L))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("999");
    }

    // -----------------------------------------------------------------------
    // listForCare
    // -----------------------------------------------------------------------

    @Test
    void listForCare_returnsActiveEmployeesForCare() {
        when(employeeRepository.findActiveByAssignedCareId(1L)).thenReturn(List.of(employee));

        List<EmployeeSlimResponse> result = employeeService.listForCare(1L);

        assertThat(result).hasSize(1);
        assertThat(result.get(0).id()).isEqualTo(100L);
        assertThat(result.get(0).name()).isEqualTo("Alice Dupont");
    }

    // ══════════════════════════════════════════════════════════════
    // ── Lot2 Sec1: Cross-tenant IDOR on employee creation ──
    // ══════════════════════════════════════════════════════════════
    // NOTE-SEC: Employee entity has NO tenantSlug/tenantId column. It is stored
    // in the per-tenant schema routed by TenantContext. EmployeeService.create
    // reads TenantContext.getCurrentTenant() only to stamp tenantSlug on the
    // shared-schema User row. It does NOT verify that the caller has authority
    // over the TenantContext value. If the TenantContext is attacker-controlled
    // (bug / bypass), the attacker can add an employee to an arbitrary salon.

    @Test
    @DisplayName("Lot2#40: create_WARN_trustsTenantContextWithoutCheckingCallerOwnership (FINDING)")
    void create_WARN_trustsTenantContextWithoutCheckingCallerOwnership() {
        // TODO-SEC: EmployeeService.create trusts whatever value is in
        // TenantContext. No cross-check against the caller's owned salon.
        // If TenantContext is set to "victim-salon" (via a bypass / misrouted
        // request / malicious background job), this method will happily create
        // an employee in the victim salon's schema.
        TenantContext.setCurrentTenant("victim-salon");
        try {
            CreateEmployeeRequest req = new CreateEmployeeRequest(
                    "Mallory", "mallory@evil.example", "+33600000001", "pwd", null);

            when(userRepository.existsByEmail("mallory@evil.example")).thenReturn(false);
            when(passwordEncoder.encode("pwd")).thenReturn("hashed-pwd");
            User mallory = User.builder()
                    .id(999L)
                    .name("Mallory")
                    .email("mallory@evil.example")
                    .password("hashed-pwd")
                    .provider(AuthProvider.LOCAL)
                    .role(Role.EMPLOYEE)
                    .build();
            when(userRepository.save(any(User.class))).thenReturn(mallory);
            Employee savedEmployee = new Employee();
            savedEmployee.setId(9000L);
            savedEmployee.setUserId(999L);
            savedEmployee.setName("Mallory");
            savedEmployee.setEmail("mallory@evil.example");
            savedEmployee.setActive(true);
            savedEmployee.setAssignedCares(new HashSet<>());
            when(employeeRepository.save(any(Employee.class))).thenReturn(savedEmployee);

            // Service proceeds without any caller-authority check against "victim-salon"
            EmployeeResponse response = employeeService.create(req);

            assertThat(response).isNotNull();
            // Verify the User row was stamped with the attacker-chosen tenant slug
            ArgumentCaptor<User> userCaptor = ArgumentCaptor.forClass(User.class);
            verify(userRepository).save(userCaptor.capture());
            assertThat(userCaptor.getValue().getTenantSlug()).isEqualTo("victim-salon");
            // Employee row is saved — it will live in whatever schema the router picked.
            verify(employeeRepository).save(any(Employee.class));
        } finally {
            TenantContext.clear();
        }
    }

    // ══════════════════════════════════════════════════════════════
    // ── Lot3 Sec70 / Fix4a: Privilege escalation on EmployeePermissionService.updatePermissions ──
    // ══════════════════════════════════════════════════════════════
    // Fix4a: updatePermissions now takes callerUserId and verifies (a) the caller's
    // role is PRO or ADMIN, (b) the caller is not the target employee themselves.

    @Test
    @DisplayName("Lot3#70 / Fix4a: updatePermissions_employeeSelfGrant_throwsForbidden — service rejects self-escalation")
    void updatePermissions_employeeSelfGrant_throwsForbidden() {
        Long attackerEmployeeId = 42L;
        Long attackerUserId = 10L; // employee.userId — matches the target's userId

        EmployeePermissionService permissionService = new EmployeePermissionService(
                employeePermissionRepository, employeeRepository, userRepository, applicationSchemaExecutor);

        // Attacker is an EMPLOYEE (not PRO/ADMIN) — caller-role lookup resolves to EMPLOYEE.
        User attacker = User.builder()
                .id(attackerUserId)
                .name("Alice Dupont")
                .email("alice@example.com")
                .password("hashed")
                .provider(AuthProvider.LOCAL)
                .role(Role.EMPLOYEE)
                .build();
        when(userRepository.findById(attackerUserId)).thenReturn(Optional.of(attacker));

        Map<PermissionDomain, AccessLevel> selfEscalation = Map.of(
                PermissionDomain.PROFILE, AccessLevel.WRITE,
                PermissionDomain.VISITS, AccessLevel.WRITE
        );

        assertThatThrownBy(() ->
                permissionService.updatePermissions(attackerEmployeeId, selfEscalation, attackerUserId))
                .isInstanceOf(ResponseStatusException.class)
                .satisfies(ex -> assertThat(((ResponseStatusException) ex).getStatusCode())
                        .isEqualTo(HttpStatus.FORBIDDEN));

        // No permission row was saved.
        verify(employeePermissionRepository, never()).save(any(EmployeePermission.class));
    }

    @Test
    @DisplayName("Fix4a: updatePermissions_proCannotTargetOwnEmployeeRecord_throwsForbidden — defence-in-depth")
    void updatePermissions_proCannotTargetOwnEmployeeRecord_throwsForbidden() {
        // If a PRO somehow also has an Employee record linked to their userId,
        // the service must still refuse to modify that record.
        Long proUserId = 10L;
        Long targetEmployeeId = 42L;

        EmployeePermissionService permissionService = new EmployeePermissionService(
                employeePermissionRepository, employeeRepository, userRepository, applicationSchemaExecutor);

        User pro = User.builder()
                .id(proUserId)
                .name("Pro User")
                .email("pro@example.com")
                .provider(AuthProvider.LOCAL)
                .role(Role.PRO)
                .build();
        when(userRepository.findById(proUserId)).thenReturn(Optional.of(pro));

        // Employee record's userId == proUserId → same identity
        Employee selfEmployee = new Employee();
        selfEmployee.setId(targetEmployeeId);
        selfEmployee.setUserId(proUserId);
        when(employeeRepository.findById(targetEmployeeId)).thenReturn(Optional.of(selfEmployee));

        Map<PermissionDomain, AccessLevel> updates = Map.of(
                PermissionDomain.PROFILE, AccessLevel.WRITE);

        assertThatThrownBy(() ->
                permissionService.updatePermissions(targetEmployeeId, updates, proUserId))
                .isInstanceOf(ResponseStatusException.class)
                .satisfies(ex -> assertThat(((ResponseStatusException) ex).getStatusCode())
                        .isEqualTo(HttpStatus.FORBIDDEN));
        verify(employeePermissionRepository, never()).save(any(EmployeePermission.class));
    }

    @Test
    @DisplayName("Fix4a: updatePermissions_proCallerOnOtherEmployee_succeeds — happy path")
    void updatePermissions_proCallerOnOtherEmployee_succeeds() {
        Long proUserId = 99L;
        Long targetEmployeeId = 42L;
        Long targetEmployeeUserId = 10L; // different from proUserId

        EmployeePermissionService permissionService = new EmployeePermissionService(
                employeePermissionRepository, employeeRepository, userRepository, applicationSchemaExecutor);

        User pro = User.builder()
                .id(proUserId)
                .name("Pro")
                .email("pro@example.com")
                .provider(AuthProvider.LOCAL)
                .role(Role.PRO)
                .build();
        when(userRepository.findById(proUserId)).thenReturn(Optional.of(pro));

        Employee target = new Employee();
        target.setId(targetEmployeeId);
        target.setUserId(targetEmployeeUserId);
        when(employeeRepository.findById(targetEmployeeId)).thenReturn(Optional.of(target));

        when(employeePermissionRepository.findByEmployeeIdAndDomain(eq(targetEmployeeId), any()))
                .thenReturn(Optional.empty());
        when(employeePermissionRepository.save(any(EmployeePermission.class)))
                .thenAnswer(inv -> inv.getArgument(0));
        when(employeePermissionRepository.findByEmployeeId(targetEmployeeId)).thenReturn(List.of());

        Map<PermissionDomain, AccessLevel> updates = Map.of(
                PermissionDomain.PROFILE, AccessLevel.WRITE,
                PermissionDomain.VISITS, AccessLevel.READ);

        Map<PermissionDomain, AccessLevel> result =
                permissionService.updatePermissions(targetEmployeeId, updates, proUserId);

        assertThat(result).isNotNull();
        ArgumentCaptor<EmployeePermission> captor = ArgumentCaptor.forClass(EmployeePermission.class);
        verify(employeePermissionRepository, times(2)).save(captor.capture());
        assertThat(captor.getAllValues()).allSatisfy(p ->
                assertThat(p.getEmployeeId()).isEqualTo(targetEmployeeId));
    }
}
