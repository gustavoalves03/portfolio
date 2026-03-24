package com.prettyface.app.care.web.dto;

import jakarta.validation.constraints.NotEmpty;
import java.util.List;

public record ReorderRequest(@NotEmpty List<Long> orderedIds) {}
