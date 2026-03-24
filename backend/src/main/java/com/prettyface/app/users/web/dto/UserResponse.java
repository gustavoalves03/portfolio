package com.prettyface.app.users.web.dto;

public record UserResponse(
        Long id,
        String name,
        String email
) {}

