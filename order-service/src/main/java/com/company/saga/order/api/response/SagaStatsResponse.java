package com.company.saga.order.api.response;

import java.time.Instant;
import java.util.Map;
import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class SagaStatsResponse {
    private long inProgress;
    private long completed;
    private long failed;
    private long compensating;
    private long avgDurationMs;
    private Map<String, Long> failureByService;
    private Instant generatedAt;
}
