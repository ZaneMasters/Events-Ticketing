package com.example.ticketing.infrastructure.persistence;

import com.example.ticketing.domain.model.Order;
import com.example.ticketing.domain.model.TicketState;
import com.example.ticketing.domain.repository.OrderRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;
import reactor.core.publisher.Mono;
import software.amazon.awssdk.services.dynamodb.DynamoDbAsyncClient;
import software.amazon.awssdk.services.dynamodb.model.AttributeValue;
import software.amazon.awssdk.services.dynamodb.model.GetItemRequest;
import software.amazon.awssdk.services.dynamodb.model.PutItemRequest;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.Map;

@Repository
@RequiredArgsConstructor
public class DynamoDbOrderRepository implements OrderRepository {

    private final DynamoDbAsyncClient dynamoDbClient;
    private static final String TABLE_NAME = "Orders";

    @Override
    public Mono<Order> save(Order order) {
        Map<String, AttributeValue> item = new HashMap<>();
        item.put("id", AttributeValue.builder().s(order.getId()).build());
        item.put("userId", AttributeValue.builder().s(order.getUserId()).build());
        item.put("eventId", AttributeValue.builder().s(order.getEventId()).build());
        item.put("quantity", AttributeValue.builder().n(order.getQuantity().toString()).build());
        item.put("status", AttributeValue.builder().s(order.getStatus().name()).build());
        if (order.getCreatedAt() != null) {
            item.put("createdAt", AttributeValue.builder().s(order.getCreatedAt().toString()).build());
        }
        if (order.getErrorMessage() != null) {
            item.put("errorMessage", AttributeValue.builder().s(order.getErrorMessage()).build());
        }

        PutItemRequest request = PutItemRequest.builder()
                .tableName(TABLE_NAME)
                .item(item)
                .build();

        return Mono.fromFuture(() -> dynamoDbClient.putItem(request))
                .map(response -> order);
    }

    @Override
    public Mono<Order> findById(String id) {
        Map<String, AttributeValue> key = new HashMap<>();
        key.put("id", AttributeValue.builder().s(id).build());

        GetItemRequest request = GetItemRequest.builder()
                .tableName(TABLE_NAME)
                .key(key)
                .build();

        return Mono.fromFuture(() -> dynamoDbClient.getItem(request))
                .mapNotNull(response -> {
                    if (!response.hasItem()) return null;
                    return mapToOrder(response.item());
                });
    }

    private Order mapToOrder(Map<String, AttributeValue> item) {
        Order.OrderBuilder builder = Order.builder()
                .id(item.get("id").s());

        if (item.containsKey("userId")) builder.userId(item.get("userId").s());
        if (item.containsKey("eventId")) builder.eventId(item.get("eventId").s());
        if (item.containsKey("quantity")) builder.quantity(Integer.parseInt(item.get("quantity").n()));
        if (item.containsKey("status")) builder.status(TicketState.valueOf(item.get("status").s()));
        if (item.containsKey("createdAt")) builder.createdAt(LocalDateTime.parse(item.get("createdAt").s()));
        if (item.containsKey("errorMessage")) builder.errorMessage(item.get("errorMessage").s());

        return builder.build();
    }
}
