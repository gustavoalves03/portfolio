package com.prettyface.app.post.app;

import com.prettyface.app.post.domain.*;
import com.prettyface.app.post.repo.*;
import com.prettyface.app.post.web.dto.PostResponse;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.nio.file.*;
import java.util.*;

@Service
public class PostService {

    private static final String UPLOAD_BASE = "uploads/posts";

    private final PostRepository postRepo;
    private final PostImageRepository postImageRepo;

    public PostService(PostRepository postRepo, PostImageRepository postImageRepo) {
        this.postRepo = postRepo;
        this.postImageRepo = postImageRepo;
    }

    @Transactional(readOnly = true)
    public List<PostResponse> listAll() {
        return postRepo.findAllByOrderByCreatedAtDesc().stream()
                .map(this::toResponse).toList();
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
        postImageRepo.deleteByPostId(id);
        postRepo.deleteById(id);
    }

    private PostResponse toResponse(Post p) {
        List<String> carouselUrls = List.of();
        if (p.getType() == PostType.CAROUSEL) {
            carouselUrls = postImageRepo.findByPostIdOrderByImageOrderAsc(p.getId())
                    .stream().map(pi -> "/api/images/posts/" + extractFilename(pi.getImagePath())).toList();
        }
        return new PostResponse(
                p.getId(), p.getType(), p.getCaption(),
                p.getBeforeImagePath() != null ? "/api/images/posts/" + extractFilename(p.getBeforeImagePath()) : null,
                p.getAfterImagePath() != null ? "/api/images/posts/" + extractFilename(p.getAfterImagePath()) : null,
                carouselUrls, p.getCareId(), p.getCareName(), p.getCreatedAt()
        );
    }

    private String saveFile(MultipartFile file, String prefix) {
        try {
            String ext = "";
            String orig = file.getOriginalFilename();
            if (orig != null && orig.contains(".")) ext = orig.substring(orig.lastIndexOf('.'));
            String filename = prefix + "-" + UUID.randomUUID() + ext;
            Path dir = Paths.get(UPLOAD_BASE);
            Files.createDirectories(dir);
            Files.copy(file.getInputStream(), dir.resolve(filename));
            return dir.resolve(filename).toString();
        } catch (IOException e) {
            throw new RuntimeException("Failed to save file", e);
        }
    }

    private String extractFilename(String path) {
        return Paths.get(path).getFileName().toString();
    }
}
