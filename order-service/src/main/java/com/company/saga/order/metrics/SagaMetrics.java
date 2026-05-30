package com.company.saga.order.metrics;

import com.company.saga.order.repository.SagaStateRepository;
import com.company.saga.order.saga.SagaStatus;
import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.MeterRegistry;
import jakarta.annotation.PostConstruct;
import java.util.concurrent.TimeUnit;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class SagaMetrics {

    private final MeterRegistry meterRegistry;
    private final SagaStateRepository sagaStateRepository;

    @PostConstruct
    public void registerGauges() {
        Gauge.builder("saga.in_progress.count",
                sagaStateRepository,
                repo -> repo.countByStatus(SagaStatus.IN_PROGRESS))
            .description("Number of sagas currently in progress")
            .register(meterRegistry);
    }

    public void recordStarted() {
        meterRegistry.counter("saga.started.total").increment();
    }

    public void recordCompleted(long durationMs) {
        meterRegistry.counter("saga.completed.total").increment();
        meterRegistry.timer("saga.duration", "result", "success")
            .record(durationMs, TimeUnit.MILLISECONDS);
    }

    public void recordFailed(String failedBy, long durationMs) {
        meterRegistry.counter("saga.failed.total", "failed_by", failedBy).increment();
        meterRegistry.timer("saga.duration", "result", "failed")
            .record(durationMs, TimeUnit.MILLISECONDS);
    }

    public void recordCompensating(String reason) {
        meterRegistry.counter("saga.compensating.total", "reason", reason).increment();
    }
}
