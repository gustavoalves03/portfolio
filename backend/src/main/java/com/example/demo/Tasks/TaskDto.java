package com.example.demo.Tasks;
import jakarta.validation.constraints.NotBlank;

public record TaskDto(
        Long id,
        @NotBlank String title
) {}
