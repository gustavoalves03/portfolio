package com.prettyface.app.auth.dto;

import lombok.AllArgsConstructor;
import lombok.Data;

@Data
@AllArgsConstructor
public class AuthResponse {
    private String accessToken;
    private String tokenType = "Bearer";
    private UserDto user;

    public AuthResponse(String accessToken, UserDto user) {
        this.accessToken = accessToken;
        this.user = user;
    }
}
