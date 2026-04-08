package com.prettyface.app.employee.web.dto;

import java.util.Map;

public record UpdatePermissionsRequest(
        Map<String, String> permissions
) {}
