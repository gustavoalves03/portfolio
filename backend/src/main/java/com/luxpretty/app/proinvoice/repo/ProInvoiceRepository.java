package com.luxpretty.app.proinvoice.repo;

import com.luxpretty.app.proinvoice.domain.ProInvoice;
import com.luxpretty.app.proinvoice.domain.ProInvoiceStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;

public interface ProInvoiceRepository extends JpaRepository<ProInvoice, Long> {

    @Query("""
        SELECT i FROM ProInvoice i
        WHERE (:status IS NULL OR i.status = :status)
          AND (:year IS NULL OR YEAR(i.issuedAt) = :year)
          AND (:q IS NULL OR LOWER(i.numberLabel) LIKE LOWER(CONCAT('%', :q, '%')))
        ORDER BY i.issuedAt DESC
        """)
    Page<ProInvoice> search(@Param("status") ProInvoiceStatus status,
                            @Param("year") Integer year,
                            @Param("q") String q,
                            Pageable pageable);

    Optional<ProInvoice> findByStripeInvoiceId(String stripeInvoiceId);
}
