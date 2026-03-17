package com.example.ticketing.application.usecase;

import com.example.ticketing.domain.model.Ticket;
import com.example.ticketing.domain.model.TicketState;
import com.example.ticketing.domain.repository.EventRepository;
import com.example.ticketing.domain.repository.TicketRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.time.LocalDateTime;
import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class ReleaseExpiredReservationsUseCaseTest {

    @Mock
    private TicketRepository ticketRepository;
    
    @Mock
    private EventRepository eventRepository;

    @InjectMocks
    private ReleaseExpiredReservationsUseCase useCase;

    @Test
    void shouldReleaseExpiredReservations() throws Exception {
        Ticket ticket = Ticket.builder()
                .id("t1")
                .eventId("e1")
                .state(TicketState.RESERVED)
                .reservationExpiresAt(LocalDateTime.now().minusMinutes(1))
                .build();

        when(ticketRepository.findExpiredReservations(any(LocalDateTime.class)))
                .thenReturn(Flux.just(ticket));

        when(ticketRepository.updateTicketsState(eq(List.of("t1")), eq(TicketState.AVAILABLE), isNull(), isNull()))
                .thenReturn(Mono.just(true));

        when(eventRepository.incrementAvailableTickets("e1", 1))
                .thenReturn(Mono.empty());

        useCase.execute();

        // Since it's void and async without returning a publisher, we verify interactions.
        // We use an arbitrary small sleep to allow the async subscribe() to execute.
        Thread.sleep(500);

        verify(ticketRepository).findExpiredReservations(any(LocalDateTime.class));
        verify(ticketRepository).updateTicketsState(eq(List.of("t1")), eq(TicketState.AVAILABLE), isNull(), isNull());
        verify(eventRepository).incrementAvailableTickets("e1", 1);
    }
    
    @Test
    void shouldDoNothingWhenUpdateFails() throws Exception {
        Ticket ticket = Ticket.builder()
                .id("t1")
                .eventId("e1")
                .state(TicketState.RESERVED)
                .build();

        when(ticketRepository.findExpiredReservations(any(LocalDateTime.class)))
                .thenReturn(Flux.just(ticket));

        when(ticketRepository.updateTicketsState(eq(List.of("t1")), eq(TicketState.AVAILABLE), isNull(), isNull()))
                .thenReturn(Mono.just(false));

        useCase.execute();

        Thread.sleep(500);

        verify(ticketRepository).findExpiredReservations(any(LocalDateTime.class));
        verify(ticketRepository).updateTicketsState(eq(List.of("t1")), eq(TicketState.AVAILABLE), isNull(), isNull());
        verify(eventRepository, never()).incrementAvailableTickets(anyString(), anyInt());
    }
}
