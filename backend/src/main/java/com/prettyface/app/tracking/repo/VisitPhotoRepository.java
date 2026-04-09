package com.prettyface.app.tracking.repo;

import com.prettyface.app.tracking.domain.VisitPhoto;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface VisitPhotoRepository extends JpaRepository<VisitPhoto, Long> {

    List<VisitPhoto> findByVisitRecordIdOrderByImageOrderAsc(Long visitRecordId);

    void deleteByVisitRecordId(Long visitRecordId);
}
