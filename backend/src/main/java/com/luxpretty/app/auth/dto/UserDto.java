package com.luxpretty.app.auth.dto;

import com.luxpretty.app.me.web.dto.TenantSummary;
import com.luxpretty.app.users.domain.AuthProvider;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;

import java.util.List;

@Data
@Builder
@AllArgsConstructor
public class UserDto {
    private Long id;
    private String name;
    private String email;
    private String imageUrl;
    private AuthProvider provider;
    private List<String> roles;
    private Long activeTenantId;
    private List<TenantSummary> availableTenants;
}
