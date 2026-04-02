package com.prettyface.app.employee.web.dto;

import java.time.LocalDateTime;
import java.util.List;

public record EmployeeResponse(
    Long id,
    Long userId,
    String name,
    String email,
    String phone,
    boolean active,
    List<CareRef> assignedCares,
    LocalDateTime createdAt
) {
    public record CareRef(Long id, String name) {}
}
