package com.example.ticketing.application.usecase;

import com.example.ticketing.domain.model.Event;
import com.example.ticketing.domain.repository.EventRepository;
import com.example.ticketing.domain.repository.TicketRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

import java.time.LocalDateTime;
import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class CreateEventUseCaseTest {

    @Mock
    private EventRepository eventRepository;

    @Mock
    private TicketRepository ticketRepository;

    @InjectMocks
    private CreateEventUseCase createEventUseCase;

    @Test
    void shouldCreateEventAndInitializeAvailability() {
        Event event = Event.builder()
                .name("Concert")
                .date(LocalDateTime.now())
                .location("Arena")
                .totalCapacity(100)
                .build();

        when(eventRepository.save(any(Event.class))).thenAnswer(invocation -> Mono.just(invocation.getArgument(0)));
        when(ticketRepository.saveAll(any(List.class))).thenReturn(reactor.core.publisher.Flux.empty());

        StepVerifier.create(createEventUseCase.execute(event))
                .expectNextMatches(savedEvent -> savedEvent.getId() != null &&
                        savedEvent.getAvailableTickets() == 100)
                .verifyComplete();
    }
}
