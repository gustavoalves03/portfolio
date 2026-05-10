package com.luxpretty.app.tenant.web.dto;

import java.util.List;

public record PublishErrorResponse(
    String message,
    List<String> missing
) {}
