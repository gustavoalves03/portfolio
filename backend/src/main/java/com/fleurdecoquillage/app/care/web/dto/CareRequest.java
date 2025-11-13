package com.fleurdecoquillage.app.care.web.dto;

import jakarta.validation.constraints.*;
import com.fleurdecoquillage.app.care.domain.CareStatus;

import java.util.List;

public record CareRequest(
        @NotBlank String name,
        @NotNull @Positive Integer price,
        @NotBlank String description,
        @NotNull @Positive Integer duration,
        @NotNull CareStatus status,
        @NotNull Long categoryId,
        @Size(max = 5, message = "Maximum 5 images allowed") List<CareImageDto> images
) {}
