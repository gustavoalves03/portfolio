package com.luxpretty.app.users.app;

import com.luxpretty.app.users.domain.Role;
import com.luxpretty.app.users.domain.ScopeType;
import com.luxpretty.app.users.domain.UserRoleAssignment;
import com.luxpretty.app.multitenancy.ApplicationSchemaExecutor;
import com.luxpretty.app.multitenancy.TenantContext;
import com.luxpretty.app.tenant.domain.Tenant;
import com.luxpretty.app.tenant.repo.TenantRepository;
import com.luxpretty.app.users.repo.UserRoleAssignmentRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.LinkedHashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

@Service
public class UserRoleService {

    private final UserRoleAssignmentRepository repo;
    private final TenantRepository tenantRepository;
    private final ApplicationSchemaExecutor applicationSchemaExecutor;

    public UserRoleService(UserRoleAssignmentRepository repo,
                           TenantRepository tenantRepository,
                           ApplicationSchemaExecutor applicationSchemaExecutor) {
        this.repo = repo;
        this.tenantRepository = tenantRepository;
        this.applicationSchemaExecutor = applicationSchemaExecutor;
    }

    @Transactional
    public UserRoleAssignment assign(Long userId, Role role, ScopeType scopeType, Long scopeId) {
        validateScope(role, scopeType, scopeId);
        return repo.findByUserIdAndRoleAndScopeTypeAndScopeId(userId, role, scopeType, scopeId)
                .orElseGet(() -> repo.save(UserRoleAssignment.builder()
                        .userId(userId)
                        .role(role)
                        .scopeType(scopeType)
                        .scopeId(scopeId)
                        .build()));
    }

    public UserRoleAssignment assignGlobal(Long userId, Role role) {
        return assign(userId, role, ScopeType.GLOBAL, null);
    }

    public UserRoleAssignment assignOnTenant(Long userId, Role role, Long tenantId) {
        return assign(userId, role, ScopeType.TENANT, tenantId);
    }

    @Transactional
    public void revoke(Long userId, Role role, ScopeType scopeType, Long scopeId) {
        repo.deleteByUserIdAndRoleAndScopeTypeAndScopeId(userId, role, scopeType, scopeId);
    }

    @Transactional(readOnly = true)
    public Set<Role> resolveRoles(Long userId, Long activeTenantId) {
        return repo.findByUserId(userId).stream()
                .filter(a -> a.getScopeType() == ScopeType.GLOBAL
                        || (a.getScopeType() == ScopeType.TENANT
                                && Objects.equals(a.getScopeId(), activeTenantId)))
                .map(UserRoleAssignment::getRole)
                .collect(Collectors.toCollection(LinkedHashSet::new));
    }

    /**
     * Returns true if the user holds any of the given roles **resolved against
     * a specific tenant**: GLOBAL roles always count; TENANT roles count only
     * when scopeId matches activeTenantId.
     */
    @Transactional(readOnly = true)
    public boolean hasAnyRole(Long userId, Long activeTenantId, Role... roles) {
        Set<Role> wanted = Set.of(roles);
        return resolveRoles(userId, activeTenantId).stream().anyMatch(wanted::contains);
    }

    /**
     * Returns true if the user holds any of the given roles resolved against
     * the tenant currently set in {@link TenantContext}. GLOBAL roles always
     * count; TENANT roles count only when scopeId matches the active tenant.
     *
     * <p>This is the safe check for tenant-scoped permission gates inside a
     * request that already established TenantContext (via TenantFilter,
     * JwtAuthenticationFilter, or an explicit set in a tenant-schema endpoint).
     * If no tenant is set, only GLOBAL roles are considered — i.e. an ADMIN
     * still passes, but a PRO on tenant X does not become PRO "everywhere".
     */
    @Transactional(readOnly = true)
    public boolean hasAnyRoleOnCurrentTenant(Long userId, Role... roles) {
        String slug = TenantContext.getCurrentTenant();
        Long activeTenantId = slug == null
                ? null
                : applicationSchemaExecutor.call(() -> tenantRepository.findBySlug(slug)
                        .map(Tenant::getId)
                        .orElse(null));
        return hasAnyRole(userId, activeTenantId, roles);
    }

    @Transactional(readOnly = true)
    public List<Long> findUserTenantIds(Long userId) {
        return repo.findByUserIdAndScopeType(userId, ScopeType.TENANT).stream()
                .map(UserRoleAssignment::getScopeId)
                .distinct()
                .toList();
    }

    private void validateScope(Role role, ScopeType scopeType, Long scopeId) {
        if (role.expectedScopeType() != scopeType) {
            throw new IllegalArgumentException(
                    "Role " + role + " expects scope " + role.expectedScopeType()
                            + ", got " + scopeType);
        }
        if (scopeType == ScopeType.TENANT && scopeId == null) {
            throw new IllegalArgumentException("scopeId required for TENANT scope");
        }
        if (scopeType == ScopeType.GLOBAL && scopeId != null) {
            throw new IllegalArgumentException("scopeId must be null for GLOBAL scope");
        }
    }
}
