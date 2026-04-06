package com.prettyface.app.care.domain;


import com.prettyface.app.category.domain.Category;
import jakarta.persistence.*;
import jakarta.validation.constraints.Min;
import lombok.Getter;
import lombok.Setter;

import java.util.ArrayList;
import java.util.List;

@Getter
@Setter
@Entity
@Table(name = "SERVICES")
public class Care {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Setter
    @Column(name = "name", nullable = false)
    private String name;

    @Setter
    @Min(0)
    @Column(name = "price", nullable = false)
    private Integer price;

    @Setter
    @Column(name = "description", nullable = false)
    private String description;

    @Setter
    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false)
    private CareStatus status;

    @Setter
    @Min(1)
    @Column(name = "duration", nullable = false)
    private Integer duration;

    @Column(name = "display_order")
    private Integer displayOrder;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "category_id", nullable = false,
                foreignKey = @ForeignKey(name = "FK_CARE_CATEGORY"))
    private Category category;

    @OneToMany(mappedBy = "care", cascade = CascadeType.ALL, orphanRemoval = true)
    @OrderBy("imageOrder ASC")
    private List<CareImage> images = new ArrayList<>();

    public Care() {}
}
