package com.company.saga.shipping.outbox;

import java.time.Instant;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

@Repository
public interface OutboxRepository extends JpaRepository<OutboxMessage, String> {

    @Query(value = """
        SELECT m FROM OutboxMessage m
        WHERE m.status = 'PENDING'
          AND m.nextRetryAt <= :now
        ORDER BY m.createdAt ASC
        LIMIT :limit
        """)
    List<OutboxMessage> findPendingToPublish(
        @Param("now") Instant now, 
        @Param("limit") int limit
    );
}
