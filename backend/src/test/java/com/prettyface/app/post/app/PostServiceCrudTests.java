package com.prettyface.app.post.app;

import com.prettyface.app.post.domain.Post;
import com.prettyface.app.post.domain.PostImage;
import com.prettyface.app.post.domain.PostType;
import com.prettyface.app.post.repo.PostImageRepository;
import com.prettyface.app.post.repo.PostRepository;
import com.prettyface.app.post.web.dto.PostResponse;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Lot 8 — Post CRUD coverage at the service layer.
 *
 * Exercises PostService directly with mocked repositories and
 * {@link MockMultipartFile} payloads. saveFile() actually writes under
 * uploads/posts/ so we use small byte arrays and rely on repo mocks
 * returning the input Post unchanged to assert persistence shape.
 */
@ExtendWith(MockitoExtension.class)
class PostServiceCrudTests {

    @Mock PostRepository postRepo;
    @Mock PostImageRepository postImageRepo;
    @InjectMocks PostService service;

    private static MockMultipartFile jpeg(String field, String filename) {
        return new MockMultipartFile(field, filename, MediaType.IMAGE_JPEG_VALUE,
                new byte[] {(byte) 0xFF, (byte) 0xD8, (byte) 0xFF, 0});
    }

    @Test
    @DisplayName("Lot8 #1: createPhoto persists a PHOTO post with afterImagePath saved via saveFile()")
    void createPhoto_persistsPhotoPost() {
        MockMultipartFile image = jpeg("image", "cover.jpg");
        when(postRepo.save(any(Post.class))).thenAnswer(inv -> {
            Post p = inv.getArgument(0);
            p.setId(42L);
            return p;
        });

        PostResponse response = service.createPhoto("hello", 7L, "Facial", image);

        ArgumentCaptor<Post> captor = ArgumentCaptor.forClass(Post.class);
        verify(postRepo).save(captor.capture());
        Post saved = captor.getValue();
        assertThat(saved.getType()).isEqualTo(PostType.PHOTO);
        assertThat(saved.getCaption()).isEqualTo("hello");
        assertThat(saved.getCareId()).isEqualTo(7L);
        assertThat(saved.getCareName()).isEqualTo("Facial");
        assertThat(saved.getAfterImagePath()).isNotBlank();
        assertThat(saved.getBeforeImagePath()).isNull();

        assertThat(response.id()).isEqualTo(42L);
        assertThat(response.type()).isEqualTo(PostType.PHOTO);
        assertThat(response.afterImageUrl()).startsWith("/api/images/posts/");
    }

    @Test
    @DisplayName("Lot8 #2: createBeforeAfter persists 2 image paths (before + after)")
    void createBeforeAfter_persistsBothImagePaths() {
        MockMultipartFile before = jpeg("beforeImage", "before.jpg");
        MockMultipartFile after = jpeg("afterImage", "after.jpg");
        when(postRepo.save(any(Post.class))).thenAnswer(inv -> {
            Post p = inv.getArgument(0);
            p.setId(100L);
            return p;
        });

        PostResponse response = service.createBeforeAfter("transformation", null, "Peeling", before, after);

        ArgumentCaptor<Post> captor = ArgumentCaptor.forClass(Post.class);
        verify(postRepo).save(captor.capture());
        Post saved = captor.getValue();
        assertThat(saved.getType()).isEqualTo(PostType.BEFORE_AFTER);
        assertThat(saved.getBeforeImagePath()).isNotBlank();
        assertThat(saved.getAfterImagePath()).isNotBlank();
        // saveFile generates unique UUID filenames so the two paths must differ
        assertThat(saved.getBeforeImagePath()).isNotEqualTo(saved.getAfterImagePath());

        assertThat(response.type()).isEqualTo(PostType.BEFORE_AFTER);
        assertThat(response.beforeImageUrl()).startsWith("/api/images/posts/");
        assertThat(response.afterImageUrl()).startsWith("/api/images/posts/");
    }

    @Test
    @DisplayName("Lot8 #3: createCarousel saves one PostImage per file, preserving order")
    void createCarousel_savesAllImagesInOrder() {
        List<MultipartFile> images = List.of(
                jpeg("images", "c0.jpg"),
                jpeg("images", "c1.jpg"),
                jpeg("images", "c2.jpg")
        );
        when(postRepo.save(any(Post.class))).thenAnswer(inv -> {
            Post p = inv.getArgument(0);
            if (p.getId() == null) p.setId(9L);
            return p;
        });
        when(postImageRepo.findByPostIdOrderByImageOrderAsc(9L)).thenReturn(List.of());

        PostResponse response = service.createCarousel("album", null, null, images);

        ArgumentCaptor<PostImage> imageCaptor = ArgumentCaptor.forClass(PostImage.class);
        verify(postImageRepo, org.mockito.Mockito.times(3)).save(imageCaptor.capture());
        List<PostImage> savedImages = imageCaptor.getAllValues();
        assertThat(savedImages).hasSize(3);
        assertThat(savedImages).extracting(PostImage::getImageOrder).containsExactly(0, 1, 2);
        assertThat(savedImages).allSatisfy(pi -> {
            assertThat(pi.getImagePath()).isNotBlank();
            assertThat(pi.getPost()).isNotNull();
        });

        assertThat(response.type()).isEqualTo(PostType.CAROUSEL);
    }
}
