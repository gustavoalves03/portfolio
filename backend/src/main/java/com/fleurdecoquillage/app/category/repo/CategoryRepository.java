package com.fleurdecoquillage.app.category.repo;

import com.fleurdecoquillage.app.category.domain.Category;
import org.springframework.data.jpa.repository.JpaRepository;

public interface CategoryRepository extends JpaRepository<Category, Long> {
}
