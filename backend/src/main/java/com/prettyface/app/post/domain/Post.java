package com.prettyface.app.post.domain;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDateTime;

@Getter @Setter @NoArgsConstructor
@Entity
@Table(name = "POSTS")
public class Post {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Enumerated(EnumType.STRING)
    @Column(name = "post_type", nullable = false)
    private PostType type;

    @Column(name = "caption", length = 1000)
    private String caption;

    @Column(name = "before_image_path", length = 500)
    private String beforeImagePath;

    @Column(name = "after_image_path", length = 500)
    private String afterImagePath;

    @Column(name = "care_id")
    private Long careId;

    @Column(name = "care_name", length = 255)
    private String careName;

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @PrePersist
    protected void onCreate() { createdAt = LocalDateTime.now(); }
}
