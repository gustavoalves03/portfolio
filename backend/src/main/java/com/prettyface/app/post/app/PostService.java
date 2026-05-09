package com.prettyface.app.post.app;

import com.prettyface.app.common.storage.StorageBackend;
import com.prettyface.app.post.domain.*;
import com.prettyface.app.post.repo.*;
import com.prettyface.app.post.web.dto.PostResponse;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.server.ResponseStatusException;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

import java.io.IOException;
import java.nio.file.Paths;
import java.util.*;

@Service
public class PostService {

    private static final Set<String> ALLOWED_IMAGE_TYPES = Set.of(
            "image/jpeg", "image/png", "image/webp", "image/gif"
    );

    private final PostRepository postRepo;
    private final PostImageRepository postImageRepo;
    private final FileStorage fileStorage;
    private final StorageBackend backend;

    public PostService(PostRepository postRepo, PostImageRepository postImageRepo,
                       FileStorage fileStorage, StorageBackend backend) {
        this.postRepo = postRepo;
        this.postImageRepo = postImageRepo;
        this.fileStorage = fileStorage;
        this.backend = backend;
    }

    @Transactional(readOnly = true)
    public List<PostResponse> listAll() {
        return postRepo.findAllByOrderByCreatedAtDesc().stream()
                .map(this::toResponse).toList();
    }

    @Transactional(readOnly = true)
    public Page<PostResponse> listPublicPaged(Pageable pageable) {
        return postRepo.findAllByOrderByCreatedAtDesc(pageable).map(this::toResponse);
    }

    @Transactional
    public PostResponse createBeforeAfter(String caption, Long careId, String careName,
                                           MultipartFile beforeImage, MultipartFile afterImage) {
        Post post = new Post();
        post.setType(PostType.BEFORE_AFTER);
        post.setCaption(caption);
        post.setCareId(careId);
        post.setCareName(careName);
        post.setBeforeImagePath(saveFile(beforeImage, "before"));
        post.setAfterImagePath(saveFile(afterImage, "after"));
        return toResponse(postRepo.save(post));
    }

    @Transactional
    public PostResponse createPhoto(String caption, Long careId, String careName, MultipartFile image) {
        Post post = new Post();
        post.setType(PostType.PHOTO);
        post.setCaption(caption);
        post.setCareId(careId);
        post.setCareName(careName);
        post.setAfterImagePath(saveFile(image, "photo"));
        return toResponse(postRepo.save(post));
    }

    @Transactional
    public PostResponse createCarousel(String caption, Long careId, String careName, List<MultipartFile> images) {
        Post post = new Post();
        post.setType(PostType.CAROUSEL);
        post.setCaption(caption);
        post.setCareId(careId);
        post.setCareName(careName);
        post = postRepo.save(post);

        for (int i = 0; i < images.size(); i++) {
            PostImage pi = new PostImage();
            pi.setPost(post);
            pi.setImagePath(saveFile(images.get(i), "carousel-" + i));
            pi.setImageOrder(i);
            postImageRepo.save(pi);
        }
        return toResponse(post);
    }

    @Transactional
    public PostResponse update(Long id, String caption, Long careId, String careName) {
        Post post = postRepo.findById(id)
                .orElseThrow(() -> new RuntimeException("Post not found"));
        post.setCaption(caption);
        post.setCareId(careId);
        post.setCareName(careName);
        return toResponse(postRepo.save(post));
    }

    @Transactional
    public void delete(Long id) {
        Post post = postRepo.findById(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Post not found"));

        // Collect every image path before removing DB rows so we can clean up the files
        // afterwards without leaving orphans on disk.
        List<String> imagePaths = new ArrayList<>();
        if (post.getBeforeImagePath() != null) imagePaths.add(post.getBeforeImagePath());
        if (post.getAfterImagePath() != null) imagePaths.add(post.getAfterImagePath());
        postImageRepo.findByPostIdOrderByImageOrderAsc(id)
                .forEach(pi -> imagePaths.add(pi.getImagePath()));

        postImageRepo.deleteByPostId(id);
        postRepo.deleteById(id);

        for (String path : imagePaths) {
            fileStorage.deleteIfExists(path);
        }
    }

    private PostResponse toResponse(Post p) {
        List<String> carouselUrls = List.of();
        if (p.getType() == PostType.CAROUSEL) {
            carouselUrls = postImageRepo.findByPostIdOrderByImageOrderAsc(p.getId())
                    .stream().map(pi -> toImageUrl(pi.getImagePath())).toList();
        }
        return new PostResponse(
                p.getId(), p.getType(), p.getCaption(),
                p.getBeforeImagePath() != null ? toImageUrl(p.getBeforeImagePath()) : null,
                p.getAfterImagePath() != null ? toImageUrl(p.getAfterImagePath()) : null,
                carouselUrls, p.getCareId(), p.getCareName(), p.getCreatedAt()
        );
    }

    private String toImageUrl(String path) {
        if (path.startsWith("http")) return path;
        return "/api/images/posts/" + extractFilename(path);
    }

    private String saveFile(MultipartFile file, String prefix) {
        String contentType = file.getContentType();
        if (contentType == null || !ALLOWED_IMAGE_TYPES.contains(contentType.toLowerCase())) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "Invalid image type. Allowed: JPEG, PNG, WebP, GIF");
        }
        try {
            String ext = "";
            String orig = file.getOriginalFilename();
            if (orig != null && orig.contains(".")) ext = orig.substring(orig.lastIndexOf('.'));
            String filename = prefix + "-" + UUID.randomUUID() + ext;
            String key = "posts/" + filename;
            backend.save(key, file.getBytes(), contentType);
            // DB stores the legacy "uploads/posts/<filename>" path; toImageUrl
            // strips it down to /api/images/posts/<filename>.
            return "uploads/" + key;
        } catch (IOException e) {
            throw new RuntimeException("Failed to save file", e);
        }
    }

    private String extractFilename(String path) {
        return Paths.get(path).getFileName().toString();
    }
}
