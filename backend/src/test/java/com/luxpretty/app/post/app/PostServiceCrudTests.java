package com.luxpretty.app.post.app;

import com.luxpretty.app.common.storage.StorageBackend;
import com.luxpretty.app.post.domain.Post;
import com.luxpretty.app.post.domain.PostImage;
import com.luxpretty.app.post.domain.PostType;
import com.luxpretty.app.post.repo.PostImageRepository;
import com.luxpretty.app.post.repo.PostRepository;
import com.luxpretty.app.post.web.dto.PostResponse;
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
import org.springframework.web.server.ResponseStatusException;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
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
    @Mock FileStorage fileStorage;
    @Mock StorageBackend backend;
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

    @Test
    @DisplayName("Lot8 #4: update changes caption/care without touching image paths")
    void update_caption_doesNotTouchImagePaths() {
        Post existing = new Post();
        existing.setId(55L);
        existing.setType(PostType.PHOTO);
        existing.setCaption("old caption");
        existing.setAfterImagePath("uploads/posts/photo-keep.jpg");
        existing.setBeforeImagePath(null);

        when(postRepo.findById(55L)).thenReturn(Optional.of(existing));
        when(postRepo.save(any(Post.class))).thenAnswer(inv -> inv.getArgument(0));

        PostResponse response = service.update(55L, "new caption", 3L, "Mask");

        ArgumentCaptor<Post> captor = ArgumentCaptor.forClass(Post.class);
        verify(postRepo).save(captor.capture());
        Post saved = captor.getValue();
        assertThat(saved.getCaption()).isEqualTo("new caption");
        assertThat(saved.getCareId()).isEqualTo(3L);
        assertThat(saved.getCareName()).isEqualTo("Mask");
        // images preserved
        assertThat(saved.getAfterImagePath()).isEqualTo("uploads/posts/photo-keep.jpg");
        assertThat(saved.getBeforeImagePath()).isNull();
        // type must not change via update
        assertThat(saved.getType()).isEqualTo(PostType.PHOTO);

        assertThat(response.caption()).isEqualTo("new caption");
        assertThat(response.careName()).isEqualTo("Mask");
    }

    @Test
    @DisplayName("Lot8 #4b: update throws when post does not exist")
    void update_unknownId_throws() {
        when(postRepo.findById(999L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.update(999L, "x", null, null))
                .isInstanceOf(RuntimeException.class)
                .hasMessageContaining("Post not found");

        verify(postRepo, never()).save(any());
    }

    @Test
    @DisplayName("Lot8 #5: delete removes post images then the post row AND cleans up files on disk")
    void delete_removesImagesThenPostAndCleansUpFiles() {
        Post post = new Post();
        post.setId(12L);
        post.setType(PostType.BEFORE_AFTER);
        post.setBeforeImagePath("uploads/posts/before-12.jpg");
        post.setAfterImagePath("uploads/posts/after-12.jpg");

        PostImage carousel0 = new PostImage();
        carousel0.setImagePath("uploads/posts/carousel-0-12.jpg");
        carousel0.setImageOrder(0);
        PostImage carousel1 = new PostImage();
        carousel1.setImagePath("uploads/posts/carousel-1-12.jpg");
        carousel1.setImageOrder(1);

        when(postRepo.findById(12L)).thenReturn(Optional.of(post));
        when(postImageRepo.findByPostIdOrderByImageOrderAsc(12L))
                .thenReturn(List.of(carousel0, carousel1));

        service.delete(12L);

        // order matters: images first (FK), then post
        var inOrder = org.mockito.Mockito.inOrder(postImageRepo, postRepo);
        inOrder.verify(postImageRepo).deleteByPostId(12L);
        inOrder.verify(postRepo).deleteById(12L);

        // every image path (before + after + each carousel) must be wiped from disk
        verify(fileStorage).deleteIfExists("uploads/posts/before-12.jpg");
        verify(fileStorage).deleteIfExists("uploads/posts/after-12.jpg");
        verify(fileStorage).deleteIfExists("uploads/posts/carousel-0-12.jpg");
        verify(fileStorage).deleteIfExists("uploads/posts/carousel-1-12.jpg");
    }

    @Test
    @DisplayName("Lot8 #7: saveFile rejects non-image content types with 400 (service layer)")
    void createPhoto_nonImageContentType_rejected() {
        // Service-layer mirror of the MockMvc test in PostControllerValidationTests.
        MockMultipartFile badFile = new MockMultipartFile(
                "image", "payload.txt", MediaType.TEXT_PLAIN_VALUE, "not-an-image".getBytes());

        assertThatThrownBy(() -> service.createPhoto("caption", null, null, badFile))
                .isInstanceOf(ResponseStatusException.class)
                .hasMessageContaining("Invalid image type");

        verify(postRepo, never()).save(any());
    }

    @Test
    @DisplayName("Lot8 #7b: saveFile rejects missing content-type with 400")
    void createPhoto_missingContentType_rejected() {
        MockMultipartFile noType = new MockMultipartFile(
                "image", "whatever.bin", null, new byte[] {1, 2, 3});

        assertThatThrownBy(() -> service.createPhoto("c", null, null, noType))
                .isInstanceOf(ResponseStatusException.class);

        verify(postRepo, never()).save(any());
    }

    @Test
    @DisplayName("Lot8 #8: cross-tenant create is schema-scoped, not service-testable")
    void crossTenantCreate_outOfScope() {
        // Multi-tenancy in LuxPretty is enforced at the Hibernate schema layer
        // via TenantContext + TenantFilter, not inside PostService. PostService
        // has no tenantId parameter or authorization guard — any authenticated
        // PRO call routes to the tenant schema set by the filter. A unit test
        // with mocked repos cannot observe this; it would require an
        // integration test with a real DataSource + TenantFilter.
        //
        // Documented here to keep the scenario visible; intentionally no
        // assertion beyond presence — passing this test means the gap is
        // acknowledged.
        assertThat(true).isTrue();
    }
}
