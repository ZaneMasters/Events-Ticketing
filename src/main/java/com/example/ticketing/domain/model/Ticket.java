package com.example.ticketing.domain.model;

import lombok.Builder;
import lombok.Data;
import lombok.With;

import java.time.LocalDateTime;

@Data
@Builder
@With
public class Ticket {
    private String id;
    private String eventId;
    private String sequence; // A human-readable ticket number (e.g., A-1)
    private TicketState state;
    private String lockedByOrderId;
    private LocalDateTime reservationExpiresAt;
    private Long version; // Optimistic locking
}
