package com.example.demo.care.repo;

import org.springframework.data.jpa.repository.JpaRepository;
import com.example.demo.care.domain.Care;

public interface CareRepository extends JpaRepository<Care, Long> {
}
