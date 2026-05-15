package com.luxpretty.app.me.web;

import com.luxpretty.app.auth.TokenService;
import com.luxpretty.app.auth.UserPrincipal;
import com.luxpretty.app.auth.dto.AuthResponse;
import com.luxpretty.app.auth.dto.UserDto;
import com.luxpretty.app.me.web.dto.SwitchTenantRequest;
import com.luxpretty.app.me.web.dto.TenantSummary;
import com.luxpretty.app.tenant.repo.TenantRepository;
import com.luxpretty.app.users.app.UserRoleService;
import com.luxpretty.app.users.domain.Role;
import com.luxpretty.app.users.domain.User;
import com.luxpretty.app.users.repo.UserRepository;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Optional;
import java.util.Set;

@RestController
@RequestMapping("/api/me")
public class MeController {

    private final UserRoleService userRoleService;
    private final TenantRepository tenantRepository;
    private final UserRepository userRepository;
    private final TokenService tokenService;

    public MeController(UserRoleService userRoleService,
                        TenantRepository tenantRepository,
                        UserRepository userRepository,
                        TokenService tokenService) {
        this.userRoleService = userRoleService;
        this.tenantRepository = tenantRepository;
        this.userRepository = userRepository;
        this.tokenService = tokenService;
    }

    @GetMapping("/tenants")
    public List<TenantSummary> myTenants(@AuthenticationPrincipal UserPrincipal principal) {
        return userRoleService.findUserTenantIds(principal.getId()).stream()
                .map(tenantRepository::findById)
                .flatMap(Optional::stream)
                .map(t -> new TenantSummary(t.getId(), t.getSlug(), t.getName()))
                .toList();
    }

    @PostMapping("/switch-tenant")
    public ResponseEntity<AuthResponse> switchTenant(
            @AuthenticationPrincipal UserPrincipal principal,
            @Valid @RequestBody SwitchTenantRequest req) {

        List<Long> allowed = userRoleService.findUserTenantIds(principal.getId());
        if (!allowed.contains(req.tenantId())) {
            throw new AccessDeniedException("User has no role on tenant " + req.tenantId());
        }

        User user = userRepository.findById(principal.getId())
                .orElseThrow(() -> new IllegalStateException("User missing"));
        String newToken = tokenService.generateToken(user, req.tenantId());

        Set<Role> resolved = userRoleService.resolveRoles(user.getId(), req.tenantId());
        List<String> roleNames = resolved.stream().map(Enum::name).toList();

        UserDto dto = UserDto.builder()
                .id(user.getId())
                .name(user.getName())
                .email(user.getEmail())
                .imageUrl(user.getImageUrl())
                .provider(user.getProvider())
                .role(pickLegacyRole(resolved))
                .roles(roleNames)
                .build();

        return ResponseEntity.ok(new AuthResponse(newToken, dto));
    }

    private static String pickLegacyRole(Set<Role> roles) {
        if (roles.contains(Role.ADMIN)) return "ADMIN";
        if (roles.contains(Role.COMMERCIAL)) return "COMMERCIAL";
        if (roles.contains(Role.PRO)) return "PRO";
        if (roles.contains(Role.EMPLOYEE)) return "EMPLOYEE";
        return null;
    }
}
