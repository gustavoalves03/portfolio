package com.prettyface.app.employee.web.dto;

import java.util.Map;

public record EmployeePermissionResponse(
        Map<String, String> permissions
) {}
