package com.example.ticketing.infrastructure.web;

import com.example.ticketing.application.usecase.CreateEventUseCase;
import com.example.ticketing.application.usecase.QueriesUseCase;
import com.example.ticketing.application.usecase.ReserveTicketsUseCase;
import com.example.ticketing.domain.model.Event;
import com.example.ticketing.domain.model.Order;
import com.example.ticketing.infrastructure.web.dto.CreateEventRequest;
import com.example.ticketing.infrastructure.web.dto.PurchaseRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.time.LocalDateTime;

@RestController
@RequestMapping("/api/events")
@RequiredArgsConstructor
public class EventController {

    private final CreateEventUseCase createEventUseCase;
    private final QueriesUseCase queriesUseCase;
    private final ReserveTicketsUseCase reserveTicketsUseCase;

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public Mono<Event> createEvent(@RequestBody CreateEventRequest request) {
        Event event = Event.builder()
                .name(request.getName())
                .date(LocalDateTime.parse(request.getDate()))
                .location(request.getLocation())
                .totalCapacity(request.getTotalCapacity())
                .build();
        return createEventUseCase.execute(event);
    }

    @GetMapping
    public Flux<Event> getAllEvents() {
        return queriesUseCase.getAllEvents();
    }

    @GetMapping("/{id}/availability")
    public Mono<Event> getAvailability(@PathVariable String id) {
        return queriesUseCase.getEventAvailability(id);
    }

    @PostMapping("/{id}/purchase")
    @ResponseStatus(HttpStatus.ACCEPTED) // 202 Accepted because it's processed async
    public Mono<Order> purchaseTickets(@PathVariable String id, @RequestBody PurchaseRequest request) {
        // Validation could be added
        return reserveTicketsUseCase.execute(request.getUserId(), id, request.getQuantity());
    }
}
