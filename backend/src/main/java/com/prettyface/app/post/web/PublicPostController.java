package com.prettyface.app.post.web;

import com.prettyface.app.multitenancy.TenantContext;
import com.prettyface.app.post.domain.Post;
import com.prettyface.app.post.domain.PostType;
import com.prettyface.app.post.repo.PostImageRepository;
import com.prettyface.app.post.repo.PostRepository;
import com.prettyface.app.post.web.dto.RecentPostResponse;
import com.prettyface.app.tenant.domain.Tenant;
import com.prettyface.app.tenant.domain.TenantStatus;
import com.prettyface.app.tenant.repo.TenantRepository;
import org.springframework.web.bind.annotation.*;

import java.nio.file.Paths;
import java.util.*;

@RestController
@RequestMapping("/api/public/posts")
public class PublicPostController {

    private final PostRepository postRepository;
    private final PostImageRepository postImageRepository;
    private final TenantRepository tenantRepository;

    public PublicPostController(PostRepository postRepository,
                                PostImageRepository postImageRepository,
                                TenantRepository tenantRepository) {
        this.postRepository = postRepository;
        this.postImageRepository = postImageRepository;
        this.tenantRepository = tenantRepository;
    }

    @GetMapping("/recent")
    public List<RecentPostResponse> recent() {
        List<Tenant> tenants = tenantRepository.findByStatus(TenantStatus.ACTIVE);
        List<RecentPostResponse> allPosts = new ArrayList<>();

        String previousTenant = TenantContext.getCurrentTenant();
        try {
            for (Tenant tenant : tenants) {
                TenantContext.setCurrentTenant(tenant.getSlug());
                List<Post> posts = postRepository.findTop6ByOrderByCreatedAtDesc();

                String logoUrl = tenant.getLogoPath() != null
                        ? "/api/images/tenant/" + tenant.getId() + "/" + extractFilename(tenant.getLogoPath())
                        : null;

                for (Post p : posts) {
                    allPosts.add(toResponse(p, tenant.getName(), tenant.getSlug(), logoUrl, tenant.getAddressCity()));
                }
            }
        } finally {
            if (previousTenant != null) {
                TenantContext.setCurrentTenant(previousTenant);
            } else {
                TenantContext.clear();
            }
        }

        allPosts.sort(Comparator.comparing(RecentPostResponse::createdAt).reversed());
        return allPosts.size() > 6 ? allPosts.subList(0, 6) : allPosts;
    }

    private RecentPostResponse toResponse(Post p, String salonName, String salonSlug,
                                           String salonLogoUrl, String salonCity) {
        String beforeUrl = toImageUrl(p.getBeforeImagePath());
        String afterUrl = toImageUrl(p.getAfterImagePath());

        List<String> carouselUrls = List.of();
        if (p.getType() == PostType.CAROUSEL) {
            carouselUrls = postImageRepository.findByPostIdOrderByImageOrderAsc(p.getId())
                    .stream().map(pi -> toImageUrl(pi.getImagePath())).toList();
        }

        return new RecentPostResponse(
                p.getId(), p.getType(), p.getCaption(),
                beforeUrl, afterUrl, carouselUrls, p.getCareName(),
                salonName, salonSlug, salonLogoUrl, salonCity,
                p.getCreatedAt()
        );
    }

    private String toImageUrl(String path) {
        if (path == null) return null;
        if (path.startsWith("http")) return path;
        return "/api/images/posts/" + extractFilename(path);
    }

    private String extractFilename(String path) {
        return Paths.get(path).getFileName().toString();
    }
}
