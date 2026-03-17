package com.example.ticketing.infrastructure.messaging;

import com.example.ticketing.application.usecase.ProcessPurchaseUseCase;
import com.example.ticketing.domain.model.Order;
import com.example.ticketing.infrastructure.config.InfrastructureInitializer;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import software.amazon.awssdk.services.sqs.SqsAsyncClient;
import software.amazon.awssdk.services.sqs.model.DeleteMessageRequest;
import software.amazon.awssdk.services.sqs.model.GetQueueUrlRequest;
import software.amazon.awssdk.services.sqs.model.ReceiveMessageRequest;

import java.util.concurrent.atomic.AtomicReference;

@Component
@RequiredArgsConstructor
@Slf4j
public class SqsOrderListener {

    private final SqsAsyncClient sqsClient;
    private final ObjectMapper objectMapper;
    private final ProcessPurchaseUseCase processPurchaseUseCase;
    
    private final AtomicReference<String> queueUrlRef = new AtomicReference<>();

    @PostConstruct
    public void init() {
         sqsClient.getQueueUrl(GetQueueUrlRequest.builder().queueName(InfrastructureInitializer.ORDERS_QUEUE).build())
                 .thenAccept(response -> queueUrlRef.set(response.queueUrl()))
                 .exceptionally(ex -> {
                     log.error("Failed to get queue URL on startup", ex);
                     return null;
                 });
    }

    @Scheduled(fixedDelay = 1000)
    public void pollQueue() {
        String url = queueUrlRef.get();
        if (url == null) return;

        ReceiveMessageRequest receiveRequest = ReceiveMessageRequest.builder()
                .queueUrl(url)
                .maxNumberOfMessages(10)
                .waitTimeSeconds(2)
                .build();

        sqsClient.receiveMessage(receiveRequest).thenAccept(response -> {
            response.messages().forEach(message -> {
                try {
                    Order order = objectMapper.readValue(message.body(), Order.class);
                    log.info("Received order message from SQS: {}", order.getId());

                    processPurchaseUseCase.execute(order)
                            .doOnSuccess(v -> deleteMessage(url, message.receiptHandle()))
                            .doOnError(e -> log.error("Error processing order {}: {}", order.getId(), e.getMessage()))
                            .subscribe();

                } catch (Exception e) {
                    log.error("Error deserializing message body", e);
                }
            });
        });
    }

    private void deleteMessage(String url, String receiptHandle) {
        DeleteMessageRequest deleteRequest = DeleteMessageRequest.builder()
                .queueUrl(url)
                .receiptHandle(receiptHandle)
                .build();
        sqsClient.deleteMessage(deleteRequest).thenAccept(r -> log.debug("Message deleted from queue"));
    }
}
