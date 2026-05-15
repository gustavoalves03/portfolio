package com.luxpretty.app.employee.app;


import com.luxpretty.app.common.error.ResourceNotFoundException;
import com.luxpretty.app.care.domain.Care;
import com.luxpretty.app.care.repo.CareRepository;
import com.luxpretty.app.employee.domain.Employee;
import com.luxpretty.app.employee.repo.EmployeeRepository;
import com.luxpretty.app.employee.web.dto.CreateEmployeeRequest;
import com.luxpretty.app.employee.web.dto.EmployeeResponse;
import com.luxpretty.app.employee.web.dto.EmployeeSlimResponse;
import com.luxpretty.app.employee.web.dto.UpdateEmployeeRequest;
import com.luxpretty.app.multitenancy.ApplicationSchemaExecutor;
import com.luxpretty.app.multitenancy.TenantContext;
import com.luxpretty.app.users.domain.AuthProvider;
import com.luxpretty.app.users.domain.Role;
import com.luxpretty.app.users.domain.User;
import com.luxpretty.app.users.repo.UserRepository;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.util.HashSet;
import java.util.List;

@Service
public class EmployeeService {

    private static final Logger logger = LoggerFactory.getLogger(EmployeeService.class);

    private final EmployeeRepository employeeRepository;
    private final UserRepository userRepository;
    private final CareRepository careRepository;
    private final PasswordEncoder passwordEncoder;
    private final ApplicationSchemaExecutor applicationSchemaExecutor;
    private final com.luxpretty.app.users.app.UserRoleService userRoleService;
    private final com.luxpretty.app.tenant.repo.TenantRepository tenantRepository;

    public EmployeeService(EmployeeRepository employeeRepository,
                           UserRepository userRepository,
                           CareRepository careRepository,
                           PasswordEncoder passwordEncoder,
                           ApplicationSchemaExecutor applicationSchemaExecutor,
                           com.luxpretty.app.users.app.UserRoleService userRoleService,
                           com.luxpretty.app.tenant.repo.TenantRepository tenantRepository) {
        this.employeeRepository = employeeRepository;
        this.userRepository = userRepository;
        this.careRepository = careRepository;
        this.passwordEncoder = passwordEncoder;
        this.applicationSchemaExecutor = applicationSchemaExecutor;
        this.userRoleService = userRoleService;
        this.tenantRepository = tenantRepository;
    }

    // -----------------------------------------------------------------------
    // Self-employee provisioning (pro signup)
    // -----------------------------------------------------------------------

    /**
     * Create the "pro-self" employee linked to the tenant owner (User).
     * Idempotent: if an employee with userId == owner.id already exists in
     * the current tenant schema, returns that one unchanged.
     *
     * Requires an active TenantContext.
     */
    @Transactional
    public Employee createSelfEmployee(User owner) {
        TenantContext.requireActive();

        return employeeRepository.findByUserId(owner.getId())
                .orElseGet(() -> {
                    Employee e = new Employee();
                    e.setUserId(owner.getId());
                    e.setName(owner.getName());
                    e.setEmail(owner.getEmail());
                    e.setPhone(null);
                    e.setActive(true);
                    return employeeRepository.save(e);
                });
    }

    // -----------------------------------------------------------------------
    // Queries
    // -----------------------------------------------------------------------

    @Transactional(readOnly = true)
    public List<EmployeeResponse> listAll() {
        return employeeRepository.findAll().stream()
                .map(this::toResponse)
                .toList();
    }

    @Transactional(readOnly = true)
    public EmployeeResponse get(Long id) {
        return employeeRepository.findById(id)
                .map(this::toResponse)
                .orElseThrow(() -> new ResourceNotFoundException("Employee not found: " + id));
    }

    @Transactional(readOnly = true)
    public List<EmployeeSlimResponse> listForCare(Long careId) {
        return employeeRepository.findActiveByAssignedCareId(careId).stream()
                .map(e -> new EmployeeSlimResponse(e.getId(), e.getName(), null))
                .toList();
    }

    // -----------------------------------------------------------------------
    // Commands
    // -----------------------------------------------------------------------

    @Transactional
    public EmployeeResponse create(CreateEmployeeRequest req) {
        String tenantSlug = TenantContext.requireActive();

        if (Boolean.TRUE.equals(applicationSchemaExecutor.call(() -> userRepository.existsByEmail(req.email())))) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Email already in use");
        }

        User savedUser;
        try {
            savedUser = applicationSchemaExecutor.call(() -> userRepository.save(
                    User.builder()
                            .name(req.name())
                            .email(req.email())
                            .password(passwordEncoder.encode(req.password()))
                            .provider(AuthProvider.LOCAL)
                            .emailVerified(false)
                            .consentGivenAt(java.time.LocalDateTime.now())
                            .tenantSlug(tenantSlug)
                            .build()
            ));
        } catch (DataIntegrityViolationException ex) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Email already in use", ex);
        }

        Employee employee = new Employee();
        employee.setUserId(savedUser.getId());
        employee.setName(req.name());
        employee.setEmail(req.email());
        employee.setPhone(req.phone());
        employee.setActive(true);

        if (req.careIds() != null && !req.careIds().isEmpty()) {
            List<Care> cares = careRepository.findAllById(req.careIds());
            employee.setAssignedCares(new HashSet<>(cares));
        }

        try {
            Employee saved = employeeRepository.save(employee);
            // Grant the EMPLOYEE scoped role on this tenant. Resolves tenantSlug
            // → tenantId via the application schema (TenantRepository lives in
            // the shared schema).
            final Long savedUserId = savedUser.getId();
            applicationSchemaExecutor.run(() -> {
                Long tenantId = tenantRepository.findBySlug(tenantSlug)
                        .map(com.luxpretty.app.tenant.domain.Tenant::getId)
                        .orElseThrow(() -> new IllegalStateException(
                                "Tenant not found for slug " + tenantSlug));
                userRoleService.assignOnTenant(savedUserId,
                        com.luxpretty.app.users.domain.Role.EMPLOYEE, tenantId);
            });
            return toResponse(saved);
        } catch (RuntimeException ex) {
            rollbackSharedUser(savedUser.getId(), ex);
            throw ex;
        }
    }

    @Transactional
    public EmployeeResponse update(Long id, UpdateEmployeeRequest req) {
        Employee employee = employeeRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Employee not found: " + id));

        if (req.name() != null) {
            employee.setName(req.name());
        }
        if (req.phone() != null) {
            employee.setPhone(req.phone());
        }
        if (req.active() != null) {
            employee.setActive(req.active());
        }
        if (req.careIds() != null) {
            List<Care> cares = careRepository.findAllById(req.careIds());
            employee.setAssignedCares(new HashSet<>(cares));
        }

        Employee saved = employeeRepository.save(employee);
        return toResponse(saved);
    }

    @Transactional
    public void delete(Long id) {
        Employee employee = employeeRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Employee not found: " + id));
        employeeRepository.delete(employee);
    }

    // -----------------------------------------------------------------------
    // Mapping
    // -----------------------------------------------------------------------

    private EmployeeResponse toResponse(Employee e) {
        List<EmployeeResponse.CareRef> careRefs = e.getAssignedCares().stream()
                .map(c -> new EmployeeResponse.CareRef(c.getId(), c.getName()))
                .toList();
        return new EmployeeResponse(
                e.getId(),
                e.getUserId(),
                e.getName(),
                e.getEmail(),
                e.getPhone(),
                e.isActive(),
                careRefs,
                e.getCreatedAt()
        );
    }

    private void rollbackSharedUser(Long userId, RuntimeException originalException) {
        try {
            applicationSchemaExecutor.run(() -> userRepository.deleteById(userId));
        } catch (RuntimeException cleanupException) {
            logger.error("Failed to clean up shared user {} after employee creation failure", userId, cleanupException);
            originalException.addSuppressed(cleanupException);
        }
    }
}
