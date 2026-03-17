package com.example.ticketing.application.usecase;

import com.example.ticketing.domain.model.Ticket;
import com.example.ticketing.domain.model.TicketState;
import com.example.ticketing.domain.repository.EventRepository;
import com.example.ticketing.domain.repository.TicketRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;

import java.time.LocalDateTime;

@Service
@RequiredArgsConstructor
@Slf4j
public class ReleaseExpiredReservationsUseCase {

    private final TicketRepository ticketRepository;
    private final EventRepository eventRepository;

    // Run every minute
    @Scheduled(fixedRate = 60000)
    public void execute() {
        log.info("Scanning for expired reservations...");
        ticketRepository.findExpiredReservations(LocalDateTime.now())
                .flatMap(ticket -> {
                    log.info("Releasing expired ticket: {}", ticket.getId());
                    return ticketRepository.updateTicketsState(
                            java.util.List.of(ticket.getId()),
                            TicketState.AVAILABLE,
                            null,
                            null
                    ).flatMap(success -> {
                        if (Boolean.TRUE.equals(success)) {
                            return eventRepository.incrementAvailableTickets(ticket.getEventId(), 1);
                        }
                        return Mono.empty();
                    });
                })
                .subscribe(); // We want to execute this purely asynchronously via Spring scheduling
    }
}
