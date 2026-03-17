package com.example.ticketing.domain.messaging;

import com.example.ticketing.domain.model.Order;
import reactor.core.publisher.Mono;

public interface OrderPublisher {
    Mono<Void> publishOrderRequest(Order order);
}
