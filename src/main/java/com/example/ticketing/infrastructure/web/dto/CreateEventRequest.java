package com.example.ticketing.infrastructure.web.dto;

import lombok.Data;

@Data
public class CreateEventRequest {
    private String name;
    private String date;
    private String location;
    private Integer totalCapacity;
}
