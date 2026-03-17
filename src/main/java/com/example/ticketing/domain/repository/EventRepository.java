package com.example.ticketing.domain.repository;

import com.example.ticketing.domain.model.Event;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

public interface EventRepository {
    Mono<Event> save(Event event);
    Mono<Event> findById(String id);
    Flux<Event> findAll();
    // Use for optimistic locking update of available tickets
    Mono<Event> decrementAvailableTickets(String eventId, int quantity);
    Mono<Event> incrementAvailableTickets(String eventId, int quantity);
}
