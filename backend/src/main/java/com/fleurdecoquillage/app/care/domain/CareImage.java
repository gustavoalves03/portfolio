package com.fleurdecoquillage.app.care.domain;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
@Entity
@Table(name = "CARE_IMAGES")
public class CareImage {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private String name;

    @Column(name = "image_order", nullable = false)
    private Integer imageOrder;

    @Column(nullable = false, length = 100)
    private String filename;

    @Column(name = "file_path", nullable = false, length = 500)
    private String filePath;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "care_id", nullable = false)
    private Care care;

    public CareImage() {}
}
