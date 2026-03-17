package com.example.ticketing.domain.model;

import lombok.Builder;
import lombok.Data;
import lombok.With;

import java.time.LocalDateTime;
import java.util.List;

@Data
@Builder
@With
@lombok.NoArgsConstructor
@lombok.AllArgsConstructor
public class Order {
    private String id;
    private String userId;
    private String eventId;
    private Integer quantity;
    private TicketState status; // Represents the status of the overall order (AVAILABLE implies not processed, PENDING_CONFIRMATION is in progress, etc)
    private LocalDateTime createdAt;
    private String errorMessage;
}
