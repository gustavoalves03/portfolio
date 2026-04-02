package com.prettyface.app.post.web.dto;

import com.prettyface.app.post.domain.PostType;
import java.time.LocalDateTime;
import java.util.List;

public record PostResponse(
    Long id,
    PostType type,
    String caption,
    String beforeImageUrl,
    String afterImageUrl,
    List<String> carouselImageUrls,
    Long careId,
    String careName,
    LocalDateTime createdAt
) {}
