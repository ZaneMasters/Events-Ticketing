package com.example.ticketing.application.usecase;

import com.example.ticketing.domain.model.Event;
import com.example.ticketing.domain.repository.EventRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;

import java.util.UUID;

@Service
@RequiredArgsConstructor
public class CreateEventUseCase {

    private final EventRepository eventRepository;

    public Mono<Event> execute(Event event) {
        event.setId(UUID.randomUUID().toString());
        event.setAvailableTickets(event.getTotalCapacity()); // Initialize availability
        return eventRepository.save(event);
    }
}
