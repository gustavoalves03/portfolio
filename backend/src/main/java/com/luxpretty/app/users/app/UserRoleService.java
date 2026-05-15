package com.luxpretty.app.users.app;

import com.luxpretty.app.users.domain.Role;
import com.luxpretty.app.users.domain.ScopeType;
import com.luxpretty.app.users.domain.UserRoleAssignment;
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

    public UserRoleService(UserRoleAssignmentRepository repo) {
        this.repo = repo;
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
     * Returns true if the user holds any of the given roles in any scope
     * (GLOBAL or any TENANT). Convenience for permission checks that don't
     * need to know which specific tenant the role applies to — e.g. "is this
     * user a PRO somewhere in the platform?".
     */
    @Transactional(readOnly = true)
    public boolean hasAnyRoleAcrossScopes(Long userId, Role... roles) {
        Set<Role> wanted = Set.of(roles);
        return repo.findByUserId(userId).stream()
                .anyMatch(a -> wanted.contains(a.getRole()));
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
