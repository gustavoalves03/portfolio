package com.prettyface.app.employee.web.dto;

import com.prettyface.app.employee.domain.DocumentType;
import java.time.LocalDateTime;

public record DocumentResponse(
    Long id,
    Long employeeId,
    DocumentType type,
    String title,
    String filename,
    LocalDateTime createdAt
) {}
