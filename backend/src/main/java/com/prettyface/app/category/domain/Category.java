package com.prettyface.app.category.domain;

import com.prettyface.app.care.domain.Care;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;
import java.util.HashSet;
import java.util.Set;

@Getter
@Setter
@Entity
@Table(name = "CATEGORIES", uniqueConstraints = {
    @UniqueConstraint(name = "UK_CATEGORY_NAME", columnNames = "name")
})
public class Category {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "name", nullable = false)
    private String name;

    @Column(name = "description")
    private String description;

    @OneToMany(mappedBy = "category")
    private Set<Care> cares = new HashSet<>();
}
