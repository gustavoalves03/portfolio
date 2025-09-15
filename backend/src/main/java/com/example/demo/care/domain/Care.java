package com.example.demo.care.domain;


import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;
import com.example.demo.care.domain.CareStatus;

@Getter
@Setter
@Entity
@Table(name = "SERVICES")
public class Care {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Setter
    @Column(nullable = false)
    private String name;

    @Setter
    @Column(nullable = false)
    private Integer price;

    @Setter
    @Column(nullable = false)
    private String description;

    @Setter
    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private CareStatus status;

    @Setter
    @Column(nullable = false)
    private Integer duration;

    public Care() {}
}
