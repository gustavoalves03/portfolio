package com.prettyface.app.post.repo;

import com.prettyface.app.post.domain.PostImage;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;

public interface PostImageRepository extends JpaRepository<PostImage, Long> {
    List<PostImage> findByPostIdOrderByImageOrderAsc(Long postId);
    void deleteByPostId(Long postId);
}
