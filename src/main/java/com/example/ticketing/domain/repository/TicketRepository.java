package com.example.ticketing.domain.repository;

import com.example.ticketing.domain.model.Ticket;
import com.example.ticketing.domain.model.TicketState;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.time.LocalDateTime;
import java.util.List;

public interface TicketRepository {
    Flux<Ticket> saveAll(List<Ticket> tickets);
    Mono<Ticket> save(Ticket ticket);
    Flux<Ticket> findByEventId(String eventId);
    
    // Finds up to 'limit' tickets that are AVAILABLE
    Flux<Ticket> findAvailableByEventId(String eventId, int limit);
    
    // Attempt to reserve tickets with optimistic locking
    // Returns the ones successfully updated
    Mono<Boolean> updateTicketsState(String eventId, List<String> ticketIds, TicketState newState, String orderId, LocalDateTime expiration);
    
    // Finds all expired reservations
    Flux<Ticket> findExpiredReservations(LocalDateTime cutoffTime);
}
