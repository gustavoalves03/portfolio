package com.prettyface.app.care.repo;

import com.prettyface.app.care.domain.Care;
import com.prettyface.app.category.domain.Category;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface CareRepository extends JpaRepository<Care, Long> {

    @Query("SELECT c FROM Care c ORDER BY COALESCE(c.displayOrder, 999999) ASC, c.id ASC")
    java.util.List<Care> findAllOrdered();

    long countByCategoryId(Long categoryId);

    long countByStatus(com.prettyface.app.care.domain.CareStatus status);

    @Modifying
    @Query("UPDATE Care c SET c.category = :target WHERE c.category.id = :sourceId")
    int reassignCategory(@Param("sourceId") Long sourceId, @Param("target") Category target);
}
