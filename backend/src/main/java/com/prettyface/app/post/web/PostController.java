package com.prettyface.app.post.web;

import com.prettyface.app.post.app.PostService;
import com.prettyface.app.post.web.dto.PostResponse;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;

@RestController
@RequestMapping("/api/pro/posts")
public class PostController {

    private final PostService service;

    public PostController(PostService service) { this.service = service; }

    @GetMapping
    public List<PostResponse> list() { return service.listAll(); }

    @PostMapping("/before-after")
    @ResponseStatus(HttpStatus.CREATED)
    public PostResponse createBeforeAfter(
            @RequestParam("caption") String caption,
            @RequestParam(value = "careId", required = false) Long careId,
            @RequestParam(value = "careName", required = false) String careName,
            @RequestParam("beforeImage") MultipartFile beforeImage,
            @RequestParam("afterImage") MultipartFile afterImage) {
        return service.createBeforeAfter(caption, careId, careName, beforeImage, afterImage);
    }

    @PostMapping("/photo")
    @ResponseStatus(HttpStatus.CREATED)
    public PostResponse createPhoto(
            @RequestParam("caption") String caption,
            @RequestParam(value = "careId", required = false) Long careId,
            @RequestParam(value = "careName", required = false) String careName,
            @RequestParam("image") MultipartFile image) {
        return service.createPhoto(caption, careId, careName, image);
    }

    @PostMapping("/carousel")
    @ResponseStatus(HttpStatus.CREATED)
    public PostResponse createCarousel(
            @RequestParam("caption") String caption,
            @RequestParam(value = "careId", required = false) Long careId,
            @RequestParam(value = "careName", required = false) String careName,
            @RequestParam("images") List<MultipartFile> images) {
        return service.createCarousel(caption, careId, careName, images);
    }

    @PutMapping("/{id}")
    public PostResponse update(
            @PathVariable Long id,
            @RequestParam(value = "caption", required = false) String caption,
            @RequestParam(value = "careId", required = false) Long careId,
            @RequestParam(value = "careName", required = false) String careName) {
        return service.update(id, caption, careId, careName);
    }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void delete(@PathVariable Long id) { service.delete(id); }
}
