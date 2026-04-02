package com.prettyface.app.employee.app;

import com.prettyface.app.care.domain.Care;
import com.prettyface.app.care.repo.CareRepository;
import com.prettyface.app.employee.domain.Employee;
import com.prettyface.app.employee.repo.EmployeeRepository;
import com.prettyface.app.employee.web.dto.CreateEmployeeRequest;
import com.prettyface.app.employee.web.dto.EmployeeResponse;
import com.prettyface.app.employee.web.dto.EmployeeSlimResponse;
import com.prettyface.app.employee.web.dto.UpdateEmployeeRequest;
import com.prettyface.app.users.domain.AuthProvider;
import com.prettyface.app.users.domain.Role;
import com.prettyface.app.users.domain.User;
import com.prettyface.app.users.repo.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.time.LocalDateTime;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;

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

    @InjectMocks
    private EmployeeService employeeService;

    private Employee employee;
    private User user;
    private Care care;

    @BeforeEach
    void setUp() {
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

        when(userRepository.existsByEmail("alice@example.com")).thenReturn(false);
        when(passwordEncoder.encode("secret")).thenReturn("hashed");
        when(userRepository.save(any(User.class))).thenReturn(user);
        when(careRepository.findAllById(List.of(1L))).thenReturn(List.of(care));
        when(employeeRepository.save(any(Employee.class))).thenReturn(employee);

        EmployeeResponse response = employeeService.create(req);

        // Verify User created with EMPLOYEE role and LOCAL provider
        ArgumentCaptor<User> userCaptor = ArgumentCaptor.forClass(User.class);
        verify(userRepository).save(userCaptor.capture());
        User savedUser = userCaptor.getValue();
        assertThat(savedUser.getRole()).isEqualTo(Role.EMPLOYEE);
        assertThat(savedUser.getProvider()).isEqualTo(AuthProvider.LOCAL);
        assertThat(savedUser.getEmail()).isEqualTo("alice@example.com");
        assertThat(savedUser.getPassword()).isEqualTo("hashed");

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
    }

    @Test
    void create_existingEmail_throws() {
        CreateEmployeeRequest req = new CreateEmployeeRequest(
                "Alice Dupont", "alice@example.com", null, "secret", null);

        when(userRepository.existsByEmail("alice@example.com")).thenReturn(true);

        assertThatThrownBy(() -> employeeService.create(req))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("alice@example.com");

        verify(userRepository, never()).save(any());
        verify(employeeRepository, never()).save(any());
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
}
