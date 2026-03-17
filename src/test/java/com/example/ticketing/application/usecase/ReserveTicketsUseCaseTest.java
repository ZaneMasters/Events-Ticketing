package com.example.ticketing.application.usecase;

import com.example.ticketing.domain.messaging.OrderPublisher;
import com.example.ticketing.domain.model.Order;
import com.example.ticketing.domain.model.Ticket;
import com.example.ticketing.domain.model.TicketState;
import com.example.ticketing.domain.repository.EventRepository;
import com.example.ticketing.domain.repository.OrderRepository;
import com.example.ticketing.domain.repository.TicketRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ReserveTicketsUseCaseTest {

    @Mock
    private TicketRepository ticketRepository;
    @Mock
    private EventRepository eventRepository;
    @Mock
    private OrderRepository orderRepository;
    @Mock
    private OrderPublisher orderPublisher;

    @InjectMocks
    private ReserveTicketsUseCase useCase;

    @Test
    void shouldReserveTicketsAndPublishOrderIfAvailable() {
        Ticket t1 = Ticket.builder().id("t1").eventId("e1").state(TicketState.AVAILABLE).build();
        Ticket t2 = Ticket.builder().id("t2").eventId("e1").state(TicketState.AVAILABLE).build();

        when(ticketRepository.findAvailableByEventId("e1", 2)).thenReturn(Flux.just(t1, t2));
        when(ticketRepository.updateTicketsState(anyList(), eq(TicketState.RESERVED), anyString(), any()))
                .thenReturn(Mono.just(true));
        
        when(eventRepository.decrementAvailableTickets("e1", 2)).thenReturn(Mono.empty());
        when(orderRepository.save(any(Order.class))).thenAnswer(i -> Mono.just(i.getArgument(0)));
        when(orderPublisher.publishOrderRequest(any(Order.class))).thenReturn(Mono.empty());

        StepVerifier.create(useCase.execute("user1", "e1", 2))
                .expectNextMatches(order ->
                        order.getUserId().equals("user1") &&
                        order.getEventId().equals("e1") &&
                        order.getQuantity() == 2 &&
                        order.getStatus() == TicketState.PENDING_CONFIRMATION)
                .verifyComplete();
    }

    @Test
    void shouldFailIfConcurrencyCollisionDuringReservation() {
        Ticket t1 = Ticket.builder().id("t1").eventId("e1").state(TicketState.AVAILABLE).build();

        when(ticketRepository.findAvailableByEventId("e1", 1)).thenReturn(Flux.just(t1));
        when(ticketRepository.updateTicketsState(anyList(), eq(TicketState.RESERVED), anyString(), any()))
                .thenReturn(Mono.just(false)); // Locking failed

        StepVerifier.create(useCase.execute("user1", "e1", 1))
                .expectErrorMessage("Concurrent reservation collision. Please retry.")
                .verify();
    }
}
