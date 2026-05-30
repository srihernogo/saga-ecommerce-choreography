package com.company.saga.order.saga;

import com.company.saga.order.repository.SagaStateRepository;
import io.micrometer.core.instrument.MeterRegistry;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
@Slf4j
public class StuckSagaDetector {

    private final SagaStateRepository sagaStateRepository;
    private final MeterRegistry meterRegistry;

    private static final int STUCK_THRESHOLD_MINUTES = 15;

    @Scheduled(fixedDelay = 300000) // 5 minutes
    public void detectStuckSagas() {
        Instant cutoff = Instant.now().minus(STUCK_THRESHOLD_MINUTES, ChronoUnit.MINUTES);
        List<SagaState> stuck = sagaStateRepository.findStuckSagas(cutoff);

        if (stuck.isEmpty()) return;

        log.warn("[STUCK-DETECTOR] Found {} stuck sagas", stuck.size());
        meterRegistry.gauge("saga.stuck.count", stuck.size());

        String stuckList = stuck.stream()
            .map(s -> String.format("orderId=%s step=%s age=%dm",
                s.getOrderId(),
                s.getCurrentStep(),
                s.getAgeMs() / 60000))
            .collect(Collectors.joining("\n"));
            
        log.error("Sagas stuck > {} minutes:\n{}", STUCK_THRESHOLD_MINUTES, stuckList);
    }
}
