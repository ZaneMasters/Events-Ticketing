package com.example.ticketing.domain.model;

import lombok.Builder;
import lombok.Data;
import lombok.With;

import java.time.LocalDateTime;

@Data
@Builder
@With
@lombok.NoArgsConstructor
@lombok.AllArgsConstructor
public class Event {
    private String id;
    private String name;
    private LocalDateTime date;
    private String location;
    private Integer totalCapacity;
    private Integer availableTickets;
}
