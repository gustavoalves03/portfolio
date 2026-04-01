package com.prettyface.app.auth.dto;

import jakarta.validation.constraints.*;

public record ProRegisterRequest(
    @NotBlank String name,
    @NotBlank @Email String email,
    @NotBlank @Size(min = 8) String password,
    @NotNull @AssertTrue Boolean consent,
    @NotBlank String salonName,
    String phone,
    String addressStreet,
    String addressPostalCode,
    String addressCity,
    String siret,
    String plan
) {}
