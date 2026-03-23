package com.fleurdecoquillage.app.care.web.dto;

import com.fleurdecoquillage.app.care.domain.CareStatus;
import jakarta.validation.constraints.NotNull;

public record StatusRequest(@NotNull CareStatus status) {}
