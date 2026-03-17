package com.example.ticketing.application.usecase;

import com.example.ticketing.domain.model.Event;
import com.example.ticketing.domain.model.Order;
import com.example.ticketing.domain.repository.EventRepository;
import com.example.ticketing.domain.repository.OrderRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

@Service
@RequiredArgsConstructor
public class QueriesUseCase {

    private final EventRepository eventRepository;
    private final OrderRepository orderRepository;

    public Mono<Event> getEventAvailability(String eventId) {
        return eventRepository.findById(eventId);
    }
    
    public Flux<Event> getAllEvents() {
        return eventRepository.findAll();
    }

    public Mono<Order> getOrderState(String orderId) {
        return orderRepository.findById(orderId);
    }
}
