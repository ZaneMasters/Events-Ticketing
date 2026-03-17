package com.example.ticketing.infrastructure.web.dto;

import lombok.Data;

@Data
public class PurchaseRequest {
    private String userId;
    private Integer quantity;
}
