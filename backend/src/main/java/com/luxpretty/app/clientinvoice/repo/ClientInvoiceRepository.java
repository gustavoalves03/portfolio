package com.luxpretty.app.clientinvoice.repo;

import com.luxpretty.app.clientinvoice.domain.ClientInvoice;
import com.luxpretty.app.clientinvoice.domain.ClientInvoiceStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;

public interface ClientInvoiceRepository extends JpaRepository<ClientInvoice, Long> {

    @Query("""
        SELECT i FROM ClientInvoice i
        WHERE (:status IS NULL OR i.status = :status)
          AND (:year IS NULL OR FUNCTION('EXTRACT', YEAR FROM i.issuedAt) = :year)
          AND (:q IS NULL OR LOWER(i.numberLabel) LIKE LOWER(CONCAT('%', :q, '%')))
        ORDER BY i.issuedAt DESC
        """)
    Page<ClientInvoice> searchForPro(@Param("status") ClientInvoiceStatus status,
                                     @Param("year") Integer year,
                                     @Param("q") String q,
                                     Pageable pageable);

    @Query("""
        SELECT i FROM ClientInvoice i
        WHERE i.clientUserId = :userId
          AND (:status IS NULL OR i.status = :status)
          AND (:year IS NULL OR FUNCTION('EXTRACT', YEAR FROM i.issuedAt) = :year)
        ORDER BY i.issuedAt DESC
        """)
    Page<ClientInvoice> searchForClient(@Param("userId") Long userId,
                                        @Param("status") ClientInvoiceStatus status,
                                        @Param("year") Integer year,
                                        Pageable pageable);

    Optional<ClientInvoice> findByIdAndClientUserId(Long id, Long clientUserId);
}
