package com.example.ticketing.application.usecase;

import com.example.ticketing.domain.model.Order;
import com.example.ticketing.domain.model.TicketState;
import com.example.ticketing.domain.repository.EventRepository;
import com.example.ticketing.domain.repository.OrderRepository;
import com.example.ticketing.domain.repository.TicketRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;

import java.time.LocalDateTime;

@Service
@RequiredArgsConstructor
@Slf4j
public class ProcessPurchaseUseCase {

    private final OrderRepository orderRepository;
    private final TicketRepository ticketRepository;
    private final EventRepository eventRepository;

    public Mono<Void> execute(Order order) {
        log.info("Processing order {} asynchronously", order.getId());

        // We assume tickets were reserved with TicketState.RESERVED before the order was placed in the queue.
        // We now need to confirm this order by marking those tickets SOLD.
        
        // This is a simplified async purchase processor:
        return ticketRepository.findByEventId(order.getEventId())
                .filter(ticket -> order.getId().equals(ticket.getLockedByOrderId()) && ticket.getState() == TicketState.RESERVED)
                .collectList()
                .flatMap(reservedTickets -> {
                    if (reservedTickets.size() == order.getQuantity()) {
                        // Confirm tickets as SOLD
                        return ticketRepository.updateTicketsState(
                                reservedTickets.stream().map(t -> t.getId()).toList(),
                                TicketState.SOLD,
                                order.getId(),
                                null // No expiration for sold tickets
                        ).flatMap(success -> {
                            if (Boolean.TRUE.equals(success)) {
                                order.setStatus(TicketState.SOLD);
                                return orderRepository.save(order).then();
                            } else {
                                return failOrder(order, "Failed to update tickets to SOLD status.");
                            }
                        });
                    } else {
                        return failOrder(order, "Order quantity mismatch with reserved tickets.");
                    }
                });
    }

    private Mono<Void> failOrder(Order order, String reason) {
        log.error("Order {} failed: {}", order.getId(), reason);
        order.setStatus(TicketState.AVAILABLE); // In this simplified logic, AVAILABLE means order failed
        order.setErrorMessage(reason);
        return orderRepository.save(order).then();
    }
}
