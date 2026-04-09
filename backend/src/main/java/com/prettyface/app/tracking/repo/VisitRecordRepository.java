package com.prettyface.app.tracking.repo;

import com.prettyface.app.tracking.domain.VisitRecord;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface VisitRecordRepository extends JpaRepository<VisitRecord, Long> {

    List<VisitRecord> findByClientProfileIdOrderByVisitDateDesc(Long clientProfileId);
}
