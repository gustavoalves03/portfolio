package com.prettyface.app.employee.web.dto;

import java.util.List;

public record UpdateEmployeeRequest(
    String name,
    String phone,
    Boolean active,
    List<Long> careIds
) {}
