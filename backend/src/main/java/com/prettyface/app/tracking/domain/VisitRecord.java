package com.prettyface.app.tracking.domain;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDate;
import java.time.LocalDateTime;

@Entity
@Table(name = "VISIT_RECORDS")
@Getter
@Setter
@NoArgsConstructor
public class VisitRecord {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "client_profile_id", nullable = false)
    private Long clientProfileId;

    @Column(name = "booking_id")
    private Long bookingId;

    @Column(name = "care_id")
    private Long careId;

    @Column(name = "care_name", length = 255)
    private String careName;

    @Column(name = "visit_date", nullable = false)
    private LocalDate visitDate;

    @Column(name = "practitioner_notes", length = 2000)
    private String practitionerNotes;

    @Column(name = "products_used", length = 1000)
    private String productsUsed;

    @Column(name = "satisfaction_score")
    private Integer satisfactionScore;

    @Column(name = "satisfaction_comment", length = 500)
    private String satisfactionComment;

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @Column(name = "updated_at")
    private LocalDateTime updatedAt;

    @Column(name = "updated_by")
    private Long updatedBy;

    @PrePersist
    protected void onCreate() {
        createdAt = LocalDateTime.now();
    }
}
