package com.company.saga.order.idempotency;

import java.time.Instant;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
@Slf4j
public class ProcessedEventTracker {

    private final ProcessedEventRepository repository;

    public boolean isAlreadyProcessed(String eventId) {
        return repository.existsById(eventId);
    }

    public void markProcessed(String eventId, String eventType) {
        try {
            repository.saveAndFlush(new ProcessedEvent(eventId, eventType, Instant.now()));
        } catch (DataIntegrityViolationException e) {
            log.warn("Event {} already marked as processed (concurrent race).", eventId);
        }
    }
}
