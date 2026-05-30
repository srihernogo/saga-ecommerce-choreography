package com.company.saga.order.repository;

import com.company.saga.order.saga.SagaState;
import com.company.saga.order.saga.SagaStatus;
import java.time.Instant;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

@Repository
public interface SagaStateRepository extends JpaRepository<SagaState, String> {

    @Query("""
        SELECT s FROM SagaState s
        WHERE s.status = 'IN_PROGRESS'
          AND s.lastUpdatedAt < :cutoff
        ORDER BY s.lastUpdatedAt ASC
        """)
    List<SagaState> findStuckSagas(@Param("cutoff") Instant cutoff);

    long countByStatus(SagaStatus status);

    @Query("""
        SELECT AVG(EXTRACT(EPOCH FROM (s.completedAt - s.startedAt)) * 1000)
        FROM SagaState s
        WHERE s.status = 'COMPLETED'
          AND s.completedAt > :since
        """)
    Double avgDurationMsSince(@Param("since") Instant since);

    @Query("""
        SELECT s.failedBy, COUNT(s)
        FROM SagaState s
        WHERE s.status IN ('FAILED', 'COMPENSATING')
          AND s.completedAt > :since
        GROUP BY s.failedBy
        """)
    List<Object[]> countFailuresByService(@Param("since") Instant since);

    List<SagaState> findByStatus(SagaStatus status);
}
