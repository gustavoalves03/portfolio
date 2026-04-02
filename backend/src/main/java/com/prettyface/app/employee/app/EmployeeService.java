package com.prettyface.app.employee.app;

import com.prettyface.app.care.domain.Care;
import com.prettyface.app.care.repo.CareRepository;
import com.prettyface.app.employee.domain.Employee;
import com.prettyface.app.employee.repo.EmployeeRepository;
import com.prettyface.app.employee.web.dto.CreateEmployeeRequest;
import com.prettyface.app.employee.web.dto.EmployeeResponse;
import com.prettyface.app.employee.web.dto.EmployeeSlimResponse;
import com.prettyface.app.employee.web.dto.UpdateEmployeeRequest;
import com.prettyface.app.multitenancy.TenantContext;
import com.prettyface.app.users.domain.AuthProvider;
import com.prettyface.app.users.domain.Role;
import com.prettyface.app.users.domain.User;
import com.prettyface.app.users.repo.UserRepository;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.HashSet;
import java.util.List;

@Service
public class EmployeeService {

    private final EmployeeRepository employeeRepository;
    private final UserRepository userRepository;
    private final CareRepository careRepository;
    private final PasswordEncoder passwordEncoder;

    public EmployeeService(EmployeeRepository employeeRepository,
                           UserRepository userRepository,
                           CareRepository careRepository,
                           PasswordEncoder passwordEncoder) {
        this.employeeRepository = employeeRepository;
        this.userRepository = userRepository;
        this.careRepository = careRepository;
        this.passwordEncoder = passwordEncoder;
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
                .orElseThrow(() -> new IllegalArgumentException("Employee not found: " + id));
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
        if (userRepository.existsByEmail(req.email())) {
            throw new IllegalArgumentException("Email already in use: " + req.email());
        }

        User user = User.builder()
                .name(req.name())
                .email(req.email())
                .password(passwordEncoder.encode(req.password()))
                .provider(AuthProvider.LOCAL)
                .role(Role.EMPLOYEE)
                .emailVerified(false)
                .build();
        User savedUser = userRepository.save(user);
        savedUser.setTenantSlug(TenantContext.getCurrentTenant());
        savedUser = userRepository.save(savedUser);

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

        Employee saved = employeeRepository.save(employee);
        return toResponse(saved);
    }

    @Transactional
    public EmployeeResponse update(Long id, UpdateEmployeeRequest req) {
        Employee employee = employeeRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("Employee not found: " + id));

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
                .orElseThrow(() -> new IllegalArgumentException("Employee not found: " + id));
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
}
