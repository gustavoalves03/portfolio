package com.luxpretty.app.users.repo;

import com.luxpretty.app.users.domain.Role;
import com.luxpretty.app.users.domain.ScopeType;
import com.luxpretty.app.users.domain.UserRoleAssignment;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface UserRoleAssignmentRepository extends JpaRepository<UserRoleAssignment, Long> {

    List<UserRoleAssignment> findByUserId(Long userId);

    Optional<UserRoleAssignment> findByUserIdAndRoleAndScopeTypeAndScopeId(
            Long userId, Role role, ScopeType scopeType, Long scopeId);

    List<UserRoleAssignment> findByUserIdAndScopeType(Long userId, ScopeType scopeType);

    void deleteByUserIdAndRoleAndScopeTypeAndScopeId(
            Long userId, Role role, ScopeType scopeType, Long scopeId);
}
