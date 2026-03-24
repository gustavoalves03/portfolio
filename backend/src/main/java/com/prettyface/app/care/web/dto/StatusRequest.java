package com.prettyface.app.care.web.dto;

import com.prettyface.app.care.domain.CareStatus;
import jakarta.validation.constraints.NotNull;

public record StatusRequest(@NotNull CareStatus status) {}
