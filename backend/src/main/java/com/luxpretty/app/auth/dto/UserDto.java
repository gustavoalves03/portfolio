package com.luxpretty.app.auth.dto;

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
    /**
     * Legacy single-role field for backwards compat with the current frontend.
     * Populated as the highest-priority role from {@link #roles}
     * (ADMIN &gt; COMMERCIAL &gt; PRO &gt; EMPLOYEE). Frontend (PR2) migrates to {@link #roles}.
     */
    private String role;
    private List<String> roles;
}
