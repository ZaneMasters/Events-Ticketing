package com.example.ticketing.application.usecase;

import com.example.ticketing.domain.model.Order;
import com.example.ticketing.domain.model.Ticket;
import com.example.ticketing.domain.model.TicketState;
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
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class ProcessPurchaseUseCaseTest {

    @Mock
    private OrderRepository orderRepository;
    @Mock
    private TicketRepository ticketRepository;

    @InjectMocks
    private ProcessPurchaseUseCase useCase;

    @Test
    void shouldConfirmPurchaseWhenTicketsMatchOrder() {
        Order order = Order.builder().id("order1").eventId("e1").quantity(1).status(TicketState.PENDING_CONFIRMATION).build();
        Ticket t1 = Ticket.builder().id("t1").eventId("e1").lockedByOrderId("order1").state(TicketState.RESERVED).build();

        when(ticketRepository.findByEventId("e1")).thenReturn(Flux.just(t1));
        when(ticketRepository.updateTicketsState(anyString(), eq(List.of("t1")), eq(TicketState.SOLD), eq("order1"), isNull()))
                .thenReturn(Mono.just(true));
        when(orderRepository.save(any(Order.class))).thenAnswer(i -> Mono.just(i.getArgument(0)));

        StepVerifier.create(useCase.execute(order))
                .verifyComplete();

        verify(orderRepository).save(argThat(o -> o.getStatus() == TicketState.SOLD));
    }

    @Test
    void shouldFailPurchaseWhenTicketsCountMismatch() {
        Order order = Order.builder().id("order2").eventId("e1").quantity(2).status(TicketState.PENDING_CONFIRMATION).build();
        Ticket t1 = Ticket.builder().id("t1").eventId("e1").lockedByOrderId("order2").state(TicketState.RESERVED).build();
        // Missing the second ticket

        when(ticketRepository.findByEventId("e1")).thenReturn(Flux.just(t1));
        when(orderRepository.save(any(Order.class))).thenAnswer(i -> Mono.just(i.getArgument(0)));

        StepVerifier.create(useCase.execute(order))
                .verifyComplete();

        verify(ticketRepository, never()).updateTicketsState(anyString(), anyList(), any(), anyString(), any());
        verify(orderRepository).save(argThat(o -> o.getStatus() == TicketState.AVAILABLE && "Order quantity mismatch with reserved tickets.".equals(o.getErrorMessage())));
    }

    @Test
    void shouldFailPurchaseWhenUpdateTicketsStateFails() {
        Order order = Order.builder().id("order3").eventId("e1").quantity(1).status(TicketState.PENDING_CONFIRMATION).build();
        Ticket t1 = Ticket.builder().id("t1").eventId("e1").lockedByOrderId("order3").state(TicketState.RESERVED).build();

        when(ticketRepository.findByEventId("e1")).thenReturn(Flux.just(t1));
        when(ticketRepository.updateTicketsState(anyString(), eq(List.of("t1")), eq(TicketState.SOLD), eq("order3"), isNull()))
                .thenReturn(Mono.just(false)); // Simulating locking failure
        when(orderRepository.save(any(Order.class))).thenAnswer(i -> Mono.just(i.getArgument(0)));

        StepVerifier.create(useCase.execute(order))
                .verifyComplete();

        verify(orderRepository).save(argThat(o -> o.getStatus() == TicketState.AVAILABLE && "Failed to update tickets to SOLD status.".equals(o.getErrorMessage())));
    }
}
