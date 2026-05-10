package com.luxpretty.app.auth.dto;

import com.luxpretty.app.users.domain.AuthProvider;
import com.luxpretty.app.users.domain.Role;
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
