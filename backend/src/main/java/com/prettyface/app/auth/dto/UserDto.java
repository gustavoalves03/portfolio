package com.prettyface.app.auth.dto;

import com.prettyface.app.users.domain.AuthProvider;
import com.prettyface.app.users.domain.Role;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;

@Data
@Builder
@AllArgsConstructor
public class UserDto {
    private Long id;
    private String name;
    private String email;
    private String imageUrl;
    private AuthProvider provider;
    private Role role;
}
