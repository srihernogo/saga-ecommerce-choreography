package com.company.saga.order.api;

import com.company.saga.order.api.response.SagaStateResponse;
import com.company.saga.order.api.response.SagaStatsResponse;
import com.company.saga.order.repository.SagaStateRepository;
import com.company.saga.order.saga.SagaState;
import com.company.saga.order.saga.SagaStatus;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/internal/saga")
@RequiredArgsConstructor
@Slf4j
public class SagaMonitorController {

    private final SagaStateRepository sagaStateRepository;

    @GetMapping("/{orderId}")
    public ResponseEntity<SagaStateResponse> getSagaState(@PathVariable String orderId) {
        return sagaStateRepository.findById(orderId)
            .map(SagaStateResponse::from)
            .map(ResponseEntity::ok)
            .orElse(ResponseEntity.notFound().build());
    }

    @GetMapping("/stuck")
    public List<SagaStateResponse> getStuckSagas(
            @RequestParam(defaultValue = "15") int olderThanMinutes) {
        Instant cutoff = Instant.now().minus(olderThanMinutes, ChronoUnit.MINUTES);
        List<SagaState> stuck = sagaStateRepository.findStuckSagas(cutoff);

        if (!stuck.isEmpty()) {
            log.warn("[SAGA-MONITOR] Found {} stuck sagas older than {} minutes",
                stuck.size(), olderThanMinutes);
        }

        return stuck.stream().map(SagaStateResponse::from).toList();
    }

    @GetMapping("/stats")
    public SagaStatsResponse getStats() {
        Instant last24h = Instant.now().minus(24, ChronoUnit.HOURS);

        long inProgress   = sagaStateRepository.countByStatus(SagaStatus.IN_PROGRESS);
        long completed    = sagaStateRepository.countByStatus(SagaStatus.COMPLETED);
        long failed       = sagaStateRepository.countByStatus(SagaStatus.FAILED);
        long compensating = sagaStateRepository.countByStatus(SagaStatus.COMPENSATING);
        Double avgDuration= sagaStateRepository.avgDurationMsSince(last24h);

        List<Object[]> failureDist = sagaStateRepository.countFailuresByService(last24h);
        Map<String, Long> failureByService = failureDist.stream()
            .collect(Collectors.toMap(
                row -> (String)  row[0],
                row -> (Long)    row[1]
            ));

        return SagaStatsResponse.builder()
            .inProgress(inProgress)
            .completed(completed)
            .failed(failed)
            .compensating(compensating)
            .avgDurationMs(avgDuration != null ? avgDuration.longValue() : 0)
            .failureByService(failureByService)
            .generatedAt(Instant.now())
            .build();
    }

    @GetMapping
    public List<SagaStateResponse> listByStatus(
            @RequestParam(defaultValue = "IN_PROGRESS") SagaStatus status,
            @RequestParam(defaultValue = "50") int limit) {
        return sagaStateRepository.findByStatus(status).stream()
            .limit(limit)
            .map(SagaStateResponse::from)
            .toList();
    }
}
