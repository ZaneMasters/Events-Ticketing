package com.example.ticketing.application.usecase;

import com.example.ticketing.domain.messaging.OrderPublisher;
import com.example.ticketing.domain.model.Order;
import com.example.ticketing.domain.model.Ticket;
import com.example.ticketing.domain.model.TicketState;
import com.example.ticketing.domain.repository.EventRepository;
import com.example.ticketing.domain.repository.OrderRepository;
import com.example.ticketing.domain.repository.TicketRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;

import java.time.LocalDateTime;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Slf4j
public class ReserveTicketsUseCase {

    private final TicketRepository ticketRepository;
    private final EventRepository eventRepository;
    private final OrderRepository orderRepository;
    private final OrderPublisher orderPublisher;

    public Mono<Order> execute(String userId, String eventId, int quantity) {
        String orderId = UUID.randomUUID().toString();
        LocalDateTime expiresAt = LocalDateTime.now().plusMinutes(10); // 10 minutes reservation as required

        return ticketRepository.findAvailableByEventId(eventId, quantity)
                .collectList()
                .flatMap(availableTickets -> {
                    if (availableTickets.size() < quantity) {
                        return Mono.error(new RuntimeException("Not enough available tickets."));
                    }
                    
                    // Attempt to reserve them
                    return ticketRepository.updateTicketsState(
                            eventId,
                            availableTickets.stream().map(Ticket::getId).toList(),
                            TicketState.RESERVED,
                            orderId,
                            expiresAt
                    ).flatMap(success -> {
                        if (Boolean.TRUE.equals(success)) {
                            // Decrement available capacity in Event to reflect reservation
                            return eventRepository.decrementAvailableTickets(eventId, quantity)
                                    .then(createAndPublishOrder(userId, eventId, quantity, orderId));
                        } else {
                            // Concurrency collision, someone else grabbed some tickets
                            return Mono.error(new RuntimeException("Concurrent reservation collision. Please retry."));
                        }
                    });
                });
    }

    private Mono<Order> createAndPublishOrder(String userId, String eventId, int quantity, String orderId) {
        Order order = Order.builder()
                .id(orderId)
                .userId(userId)
                .eventId(eventId)
                .quantity(quantity)
                .status(TicketState.PENDING_CONFIRMATION) // Represent order is processing
                .createdAt(LocalDateTime.now())
                .build();

        return orderRepository.save(order)
                .flatMap(savedOrder -> orderPublisher.publishOrderRequest(savedOrder).thenReturn(savedOrder));
    }
}
