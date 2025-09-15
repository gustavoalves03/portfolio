package com.fleurdecoquillage.app.care.repo;

import org.springframework.data.jpa.repository.JpaRepository;
import com.fleurdecoquillage.app.care.domain.Care;

public interface CareRepository extends JpaRepository<Care, Long> {
}
