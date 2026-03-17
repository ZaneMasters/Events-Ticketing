package com.example.ticketing.infrastructure.messaging;

import com.example.ticketing.domain.messaging.OrderPublisher;
import com.example.ticketing.domain.model.Order;
import com.example.ticketing.infrastructure.config.InfrastructureInitializer;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Mono;
import software.amazon.awssdk.services.sqs.SqsAsyncClient;
import software.amazon.awssdk.services.sqs.model.GetQueueUrlRequest;
import software.amazon.awssdk.services.sqs.model.SendMessageRequest;

@Component
@RequiredArgsConstructor
@Slf4j
public class SqsOrderPublisher implements OrderPublisher {

    private final SqsAsyncClient sqsClient;
    private final ObjectMapper objectMapper;
    private String queueUrlCache;

    @Override
    public Mono<Void> publishOrderRequest(Order order) {
        return getQueueUrl()
                .flatMap(url -> {
                    try {
                        String messageBody = objectMapper.writeValueAsString(order);
                        SendMessageRequest request = SendMessageRequest.builder()
                                .queueUrl(url)
                                .messageBody(messageBody)
                                .build();
                        
                        return Mono.fromFuture(() -> sqsClient.sendMessage(request))
                                .doOnSuccess(r -> log.info("Published order {} to queue", order.getId()))
                                .then();
                    } catch (JsonProcessingException e) {
                        return Mono.error(new RuntimeException("Failed to serialize order", e));
                    }
                });
    }

    private Mono<String> getQueueUrl() {
        if (queueUrlCache != null) {
            return Mono.just(queueUrlCache);
        }
        return Mono.fromFuture(() -> sqsClient.getQueueUrl(GetQueueUrlRequest.builder()
                        .queueName(InfrastructureInitializer.ORDERS_QUEUE)
                        .build()))
                .map(response -> {
                    queueUrlCache = response.queueUrl();
                    return queueUrlCache;
                });
    }
}
