package com.example.ticketing.infrastructure.web;

import com.example.ticketing.application.usecase.QueriesUseCase;
import com.example.ticketing.domain.model.Order;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import reactor.core.publisher.Mono;

@RestController
@RequestMapping("/api/orders")
@RequiredArgsConstructor
public class OrderController {

    private final QueriesUseCase queriesUseCase;

    @GetMapping("/{id}")
    public Mono<Order> getOrder(@PathVariable String id) {
        return queriesUseCase.getOrderState(id);
    }
}
