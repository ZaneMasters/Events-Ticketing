package com.example.ticketing.application.usecase;

import com.example.ticketing.domain.model.Event;
import com.example.ticketing.domain.model.Ticket;
import com.example.ticketing.domain.model.TicketState;
import com.example.ticketing.domain.repository.EventRepository;
import com.example.ticketing.domain.repository.TicketRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;

import java.util.List;
import java.util.UUID;
import java.util.stream.IntStream;

@Service
@RequiredArgsConstructor
public class CreateEventUseCase {

    private final EventRepository eventRepository;
    private final TicketRepository ticketRepository;

    public Mono<Event> execute(Event event) {
        String eventId = UUID.randomUUID().toString();
        event.setId(eventId);
        event.setAvailableTickets(event.getTotalCapacity()); // Initialize availability

        List<Ticket> tickets = IntStream.rangeClosed(1, event.getTotalCapacity())
                .mapToObj(i -> Ticket.builder()
                        .id(UUID.randomUUID().toString())
                        .eventId(eventId)
                        .sequence("T-" + i)
                        .state(TicketState.AVAILABLE)
                        .version(1L)
                        .build())
                .toList();

        return eventRepository.save(event)
                .then(ticketRepository.saveAll(tickets).then())
                .thenReturn(event);
    }
}
