package com.prettyface.app.post.web.dto;

import com.prettyface.app.post.domain.PostType;
import java.time.LocalDateTime;

public record RecentPostResponse(
    Long id,
    PostType type,
    String caption,
    String thumbnailUrl,
    String salonName,
    String salonSlug,
    LocalDateTime createdAt
) {}
