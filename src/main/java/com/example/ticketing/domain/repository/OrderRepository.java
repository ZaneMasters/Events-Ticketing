package com.example.ticketing.domain.repository;

import com.example.ticketing.domain.model.Order;
import reactor.core.publisher.Mono;

public interface OrderRepository {
    Mono<Order> save(Order order);
    Mono<Order> findById(String id);
}
