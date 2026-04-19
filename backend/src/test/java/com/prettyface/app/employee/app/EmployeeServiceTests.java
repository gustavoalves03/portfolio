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
    // ── Lot3 Sec70: Privilege escalation on EmployeePermissionService.updatePermissions ──
    // ══════════════════════════════════════════════════════════════
    // NOTE-SEC: The permission-update entrypoint
    //   EmployeePermissionService.updatePermissions(Long employeeId, Map<PermissionDomain, AccessLevel>)
    // takes NO caller/principal argument. It identifies the *target* of the update
    // but cannot (and does not) verify that the *caller* has authority to grant
    // permissions. In the current routing (EmployeePermissionController.putMapping
    // "/api/pro/employees/{employeeId}/permissions") the only safeguard is the
    // URL prefix "/api/pro/..." enforced by Spring Security — which is coarse
    // role-level, not "is this the pro/owner and not the target employee himself".
    //
    // If an employee reaches the service (via a future endpoint, an admin bypass,
    // a mis-mapped route, or a developer mistake in another controller) and
    // passes their own employeeId, the service will happily raise their own
    // permissions to WRITE on every domain. This is the classic vertical
    // privilege-escalation gap.

    @Test
    @DisplayName("Lot3#70: updatePermissions_WARN_serviceHasNoCallerCheck — self-escalation possible at service layer")
    void updatePermissions_WARN_serviceHasNoCallerCheck_selfEscalationPossible() {
        // TODO-SEC: Add an explicit caller-authority argument or @PreAuthorize
        // on EmployeePermissionService.updatePermissions so that an employee
        // cannot grant themselves WRITE access to PROFILE/VISITS/PHOTOS/REMINDERS
        // even if they reach the service directly. Today the service signature
        // has no notion of "who is asking".
        Long attackerEmployeeId = 42L;

        // Construct the permission service inline (reuses EmployeeRepository mock
        // and adds the permission-repo mock). This is the same construction
        // pattern used by TrackingAccessLevelSecurityTests (Sec2).
        EmployeePermissionService permissionService =
                new EmployeePermissionService(employeePermissionRepository, employeeRepository);

        // Target (self) employee exists
        when(employeeRepository.findById(attackerEmployeeId))
                .thenReturn(Optional.of(employee));
        // No stored permissions yet — new rows will be created by the service
        when(employeePermissionRepository.findByEmployeeIdAndDomain(eq(attackerEmployeeId), any()))
                .thenReturn(Optional.empty());
        // Capture the saved permissions to prove the escalation went through
        when(employeePermissionRepository.save(any(EmployeePermission.class)))
                .thenAnswer(inv -> inv.getArgument(0));
        // getPermissions(...) called at the end — return an empty row set and
        // let the service materialise the defaults.
        when(employeePermissionRepository.findByEmployeeId(attackerEmployeeId))
                .thenReturn(List.of());

        // The attacker asks to raise their *own* permissions to WRITE on every domain
        Map<PermissionDomain, AccessLevel> selfEscalation = Map.of(
                PermissionDomain.PROFILE, AccessLevel.WRITE,
                PermissionDomain.VISITS, AccessLevel.WRITE,
                PermissionDomain.PHOTOS, AccessLevel.WRITE,
                PermissionDomain.REMINDERS, AccessLevel.WRITE
        );

        // Service proceeds — no "caller == target?" guard, no pro/owner role check
        Map<PermissionDomain, AccessLevel> result =
                permissionService.updatePermissions(attackerEmployeeId, selfEscalation);

        assertThat(result).isNotNull();
        // Four rows saved — one per domain — all at WRITE, for the attacker herself.
        ArgumentCaptor<EmployeePermission> permCaptor = ArgumentCaptor.forClass(EmployeePermission.class);
        verify(employeePermissionRepository, times(4)).save(permCaptor.capture());
        assertThat(permCaptor.getAllValues())
                .allSatisfy(p -> {
                    assertThat(p.getEmployeeId()).isEqualTo(attackerEmployeeId);
                    assertThat(p.getAccessLevel()).isEqualTo(AccessLevel.WRITE);
                });
        // Documents the gap: no collaborator consulted to verify the caller
        // was distinct from the target employee (the pro/owner).
    }
}
