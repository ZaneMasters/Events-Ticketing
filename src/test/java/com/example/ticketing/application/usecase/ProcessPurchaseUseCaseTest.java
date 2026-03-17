package com.example.ticketing.application.usecase;

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
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class ProcessPurchaseUseCaseTest {

    @Mock
    private OrderRepository orderRepository;
    @Mock
    private TicketRepository ticketRepository;
    @Mock(name = "eventRepository")
    private EventRepository ignoredRepositoryForTestMocksRule;

    @InjectMocks
    private ProcessPurchaseUseCase useCase;

    @Test
    void shouldConfirmPurchaseWhenTicketsMatchOrder() {
        Order order = Order.builder().id("order1").eventId("e1").quantity(1).status(TicketState.PENDING_CONFIRMATION).build();
        Ticket t1 = Ticket.builder().id("t1").eventId("e1").lockedByOrderId("order1").state(TicketState.RESERVED).build();

        when(ticketRepository.findByEventId("e1")).thenReturn(Flux.just(t1));
        when(ticketRepository.updateTicketsState(List.of("t1"), TicketState.SOLD, "order1", null))
                .thenReturn(Mono.just(true));
        when(orderRepository.save(any(Order.class))).thenAnswer(i -> Mono.just(i.getArgument(0)));

        StepVerifier.create(useCase.execute(order))
                .verifyComplete();

        verify(orderRepository).save(argThat(o -> o.getStatus() == TicketState.SOLD));
    }
}
