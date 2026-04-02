package com.prettyface.app.employee.web.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import java.util.List;

public record CreateEmployeeRequest(
    @NotBlank String name,
    @NotBlank @Email String email,
    String phone,
    @NotBlank String password,
    List<Long> careIds
) {}
