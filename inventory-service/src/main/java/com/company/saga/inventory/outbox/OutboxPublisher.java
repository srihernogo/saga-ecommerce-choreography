package com.company.saga.inventory.outbox;

import java.time.Instant;
import java.util.List;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import net.javacrumbs.shedlock.spring.annotation.SchedulerLock;
import org.springframework.cloud.stream.function.StreamBridge;
import org.springframework.messaging.Message;
import org.springframework.messaging.support.MessageBuilder;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Component
@RequiredArgsConstructor
@Slf4j
public class OutboxPublisher {

    private final OutboxRepository outboxRepository;
    private final StreamBridge streamBridge;

    @Scheduled(fixedDelayString = "${outbox.scheduler.fixed-delay-ms:1000}")
    @SchedulerLock(name = "outbox_publish_task", lockAtLeastFor = "500ms", lockAtMostFor = "2m")
    @Transactional
    public void publishPendingMessages() {
        List<OutboxMessage> pending = outboxRepository.findPendingToPublish(Instant.now(), 100);

        for (OutboxMessage msg : pending) {
            try {
                Message<String> kafkaMessage = MessageBuilder.withPayload(msg.getPayload())
                    .setHeader("partitionKey", msg.getAggregateId())
                    .build();

                boolean sent = streamBridge.send(msg.getTopic(), kafkaMessage);

                if (sent) {
                    msg.markPublished();
                    log.debug("Published outbox msg: {}", msg.getId());
                } else {
                    msg.markFailed("streamBridge.send returned false");
                }
            } catch (Exception e) {
                log.error("Failed to publish outbox msg: {}", msg.getId(), e);
                msg.markFailed(e.getMessage());
            }
            outboxRepository.save(msg);
        }
    }
}
