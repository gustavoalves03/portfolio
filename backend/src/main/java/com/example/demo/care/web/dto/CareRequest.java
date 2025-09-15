package com.example.demo.care.web.dto;

import jakarta.validation.constraints.*;
import com.example.demo.care.domain.CareStatus;

public record CareRequest(
        @NotBlank String name,
        @NotNull @Positive Integer price,
        @NotBlank String description,
        @NotNull @Positive Integer duration,
        @NotNull CareStatus status
) {}
