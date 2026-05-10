package com.luxpretty.app.care.web.dto;

import com.luxpretty.app.care.domain.CareStatus;
import jakarta.validation.constraints.NotNull;

public record StatusRequest(@NotNull CareStatus status) {}
