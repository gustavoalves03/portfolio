package com.luxpretty.app.me.web.dto;

import jakarta.validation.constraints.NotNull;

public record SwitchTenantRequest(@NotNull Long tenantId) {}
