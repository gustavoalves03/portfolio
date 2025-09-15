package com.fleurdecoquillage.app.category.web.dto;

import jakarta.validation.constraints.NotBlank;

public record CategoryRequest(
        @NotBlank String name,
        String description
) {}
