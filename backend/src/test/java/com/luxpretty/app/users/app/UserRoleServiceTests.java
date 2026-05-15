package com.luxpretty.app.users.app;

import com.luxpretty.app.users.domain.Role;
import com.luxpretty.app.users.domain.ScopeType;
import com.luxpretty.app.users.domain.UserRoleAssignment;
import com.luxpretty.app.users.repo.UserRoleAssignmentRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Optional;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class UserRoleServiceTests {

    private UserRoleAssignmentRepository repo;
    private UserRoleService service;

    @BeforeEach
    void setUp() {
        repo = mock(UserRoleAssignmentRepository.class);
        service = new UserRoleService(repo);
    }

    @Test
    void assign_createsAssignment_whenAbsent() {
        when(repo.findByUserIdAndRoleAndScopeTypeAndScopeId(1L, Role.PRO, ScopeType.TENANT, 42L))
                .thenReturn(Optional.empty());
        when(repo.save(any(UserRoleAssignment.class))).thenAnswer(inv -> inv.getArgument(0));

        UserRoleAssignment result = service.assign(1L, Role.PRO, ScopeType.TENANT, 42L);

        assertThat(result.getUserId()).isEqualTo(1L);
        assertThat(result.getRole()).isEqualTo(Role.PRO);
        assertThat(result.getScopeType()).isEqualTo(ScopeType.TENANT);
        assertThat(result.getScopeId()).isEqualTo(42L);
        verify(repo).save(any(UserRoleAssignment.class));
    }

    @Test
    void assign_isIdempotent_returnsExisting_whenPresent() {
        UserRoleAssignment existing = UserRoleAssignment.builder()
                .id(7L).userId(1L).role(Role.PRO).scopeType(ScopeType.TENANT).scopeId(42L).build();
        when(repo.findByUserIdAndRoleAndScopeTypeAndScopeId(1L, Role.PRO, ScopeType.TENANT, 42L))
                .thenReturn(Optional.of(existing));

        UserRoleAssignment result = service.assign(1L, Role.PRO, ScopeType.TENANT, 42L);

        assertThat(result).isSameAs(existing);
        verify(repo, never()).save(any());
    }

    @Test
    void assign_rejectsProWithGlobalScope() {
        assertThatThrownBy(() -> service.assign(1L, Role.PRO, ScopeType.GLOBAL, null))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("PRO");
    }

    @Test
    void assign_rejectsAdminWithTenantScope() {
        assertThatThrownBy(() -> service.assign(1L, Role.ADMIN, ScopeType.TENANT, 42L))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("ADMIN");
    }

    @Test
    void assign_rejectsTenantScopeWithoutScopeId() {
        assertThatThrownBy(() -> service.assign(1L, Role.PRO, ScopeType.TENANT, null))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("scopeId required");
    }

    @Test
    void assign_rejectsGlobalScopeWithScopeId() {
        assertThatThrownBy(() -> service.assign(1L, Role.ADMIN, ScopeType.GLOBAL, 42L))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("must be null");
    }

    @Test
    void resolveRoles_returnsGlobalRoles_whenActiveTenantNull() {
        when(repo.findByUserId(1L)).thenReturn(List.of(
                UserRoleAssignment.builder().userId(1L).role(Role.ADMIN).scopeType(ScopeType.GLOBAL).build(),
                UserRoleAssignment.builder().userId(1L).role(Role.PRO).scopeType(ScopeType.TENANT).scopeId(42L).build()
        ));

        Set<Role> roles = service.resolveRoles(1L, null);

        assertThat(roles).containsExactlyInAnyOrder(Role.ADMIN);
    }

    @Test
    void resolveRoles_returnsGlobalRolesPlusActiveTenantRoles() {
        when(repo.findByUserId(1L)).thenReturn(List.of(
                UserRoleAssignment.builder().userId(1L).role(Role.COMMERCIAL).scopeType(ScopeType.GLOBAL).build(),
                UserRoleAssignment.builder().userId(1L).role(Role.PRO).scopeType(ScopeType.TENANT).scopeId(42L).build(),
                UserRoleAssignment.builder().userId(1L).role(Role.EMPLOYEE).scopeType(ScopeType.TENANT).scopeId(42L).build()
        ));

        Set<Role> roles = service.resolveRoles(1L, 42L);

        assertThat(roles).containsExactlyInAnyOrder(Role.COMMERCIAL, Role.PRO, Role.EMPLOYEE);
    }

    @Test
    void resolveRoles_excludesOtherTenantRoles() {
        when(repo.findByUserId(1L)).thenReturn(List.of(
                UserRoleAssignment.builder().userId(1L).role(Role.PRO).scopeType(ScopeType.TENANT).scopeId(42L).build(),
                UserRoleAssignment.builder().userId(1L).role(Role.PRO).scopeType(ScopeType.TENANT).scopeId(43L).build()
        ));

        Set<Role> roles = service.resolveRoles(1L, 42L);

        assertThat(roles).containsExactly(Role.PRO);
        assertThat(roles).doesNotContain(Role.EMPLOYEE);
    }

    @Test
    void findUserTenantIds_returnsDistinctTenants() {
        when(repo.findByUserIdAndScopeType(1L, ScopeType.TENANT)).thenReturn(List.of(
                UserRoleAssignment.builder().userId(1L).role(Role.PRO).scopeType(ScopeType.TENANT).scopeId(42L).build(),
                UserRoleAssignment.builder().userId(1L).role(Role.EMPLOYEE).scopeType(ScopeType.TENANT).scopeId(42L).build(),
                UserRoleAssignment.builder().userId(1L).role(Role.PRO).scopeType(ScopeType.TENANT).scopeId(43L).build()
        ));

        List<Long> ids = service.findUserTenantIds(1L);

        assertThat(ids).containsExactlyInAnyOrder(42L, 43L);
    }
}
