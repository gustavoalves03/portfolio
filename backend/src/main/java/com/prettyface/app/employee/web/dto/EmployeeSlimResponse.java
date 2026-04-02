package com.prettyface.app.employee.web.dto;

public record EmployeeSlimResponse(
    Long id,
    String name,
    String imageUrl
) {}
