package com.luxpretty.app.mail.repo;

import com.luxpretty.app.mail.domain.MailOutbox;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

public interface MailOutboxRepository extends JpaRepository<MailOutbox, Long> {

    /**
     * Atomically locks a batch of PENDING rows whose retry window has elapsed.
     * Uses Oracle FOR UPDATE SKIP LOCKED so multiple workers can run concurrently
     * without double-sending.
     *
     * Oracle rejects FETCH FIRST ... FOR UPDATE SKIP LOCKED with ORA-02014, so
     * the row limit is applied in an inner ROWNUM subquery while the outer query
     * still locks base-table rows.
     */
    @Query(value = """
        SELECT *
        FROM MAIL_OUTBOX
        WHERE ID IN (
            SELECT ID
            FROM (
                SELECT ID
                FROM MAIL_OUTBOX
                WHERE STATUS = 'PENDING'
                  AND NEXT_ATTEMPT_AT <= :now
                ORDER BY CREATED_AT, ID
            )
            WHERE ROWNUM <= :batchSize
        )
        ORDER BY CREATED_AT, ID
        FOR UPDATE SKIP LOCKED
        """, nativeQuery = true)
    List<MailOutbox> lockBatchForSending(@Param("now") LocalDateTime now,
                                         @Param("batchSize") int batchSize);

    Optional<MailOutbox> findByProviderMessageId(String providerMessageId);
}
