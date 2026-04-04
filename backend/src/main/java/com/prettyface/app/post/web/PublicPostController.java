package com.prettyface.app.post.web;

import com.prettyface.app.post.domain.Post;
import com.prettyface.app.post.repo.PostRepository;
import com.prettyface.app.post.web.dto.RecentPostResponse;
import org.springframework.web.bind.annotation.*;

import java.nio.file.Paths;
import java.util.List;

@RestController
@RequestMapping("/api/public/posts")
public class PublicPostController {

    private final PostRepository postRepository;

    public PublicPostController(PostRepository postRepository) {
        this.postRepository = postRepository;
    }

    @GetMapping("/recent")
    public List<RecentPostResponse> recent() {
        return postRepository.findTop6ByOrderByCreatedAtDesc().stream()
                .map(this::toResponse)
                .toList();
    }

    private RecentPostResponse toResponse(Post p) {
        String thumbnailPath = p.getAfterImagePath() != null
                ? p.getAfterImagePath()
                : p.getBeforeImagePath();
        String thumbnailUrl = thumbnailPath != null
                ? "/api/images/posts/" + Paths.get(thumbnailPath).getFileName().toString()
                : null;

        return new RecentPostResponse(
                p.getId(), p.getType(), p.getCaption(),
                thumbnailUrl, null, null, p.getCreatedAt()
        );
    }
}
