package com.prettyface.app.category.repo;

import com.prettyface.app.category.domain.Category;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.util.List;

public interface CategoryRepository extends JpaRepository<Category, Long> {

    @Query("SELECT DISTINCT c FROM Category c LEFT JOIN FETCH c.cares WHERE c.cares IS NOT EMPTY")
    List<Category> findAllWithCares();
}
