package com.luxpretty.app.post.app;

import com.luxpretty.app.post.domain.Post;
import com.luxpretty.app.post.domain.PostType;
import com.luxpretty.app.post.repo.PostImageRepository;
import com.luxpretty.app.post.repo.PostRepository;
import com.luxpretty.app.post.web.dto.PostResponse;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;

import java.time.LocalDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class PostServiceTests {

    @Mock PostRepository postRepo;
    @Mock PostImageRepository postImageRepo;
    @InjectMocks PostService service;

    @Test
    void listPublicPaged_delegatesToRepoAndMapsToResponse() {
        Post p = new Post();
        p.setId(1L);
        p.setType(PostType.PHOTO);
        p.setCaption("hello");
        p.setAfterImagePath("/uploads/posts/abc.jpg");
        p.setCreatedAt(LocalDateTime.now());

        Pageable pageable = PageRequest.of(0, 20);
        when(postRepo.findAllByOrderByCreatedAtDesc(any(Pageable.class)))
                .thenReturn(new PageImpl<>(List.of(p), pageable, 1));

        Page<PostResponse> result = service.listPublicPaged(pageable);

        assertThat(result.getTotalElements()).isEqualTo(1);
        assertThat(result.getContent()).hasSize(1);
        assertThat(result.getContent().get(0).id()).isEqualTo(1L);
        verify(postRepo).findAllByOrderByCreatedAtDesc(pageable);
    }
}
