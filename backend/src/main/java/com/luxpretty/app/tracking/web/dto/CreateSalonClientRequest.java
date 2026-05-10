package com.luxpretty.app.tracking.web.dto;

import jakarta.validation.constraints.NotBlank;
import java.time.LocalDate;

public record CreateSalonClientRequest(
        @NotBlank String name,
        @NotBlank String phone,
        String email,
        LocalDate dateOfBirth,
        String notes
) {}
