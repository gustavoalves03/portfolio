package com.prettyface.app.care.repo;

import com.prettyface.app.care.domain.CareImage;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface CareImageRepository extends JpaRepository<CareImage, Long> {
    List<CareImage> findByCareIdOrderByImageOrderAsc(Long careId);
    void deleteByCareId(Long careId);
}
