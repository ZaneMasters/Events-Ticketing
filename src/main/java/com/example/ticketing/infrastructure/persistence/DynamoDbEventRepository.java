package com.example.ticketing.infrastructure.persistence;

import com.example.ticketing.domain.model.Event;
import com.example.ticketing.domain.repository.EventRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import software.amazon.awssdk.services.dynamodb.DynamoDbAsyncClient;
import software.amazon.awssdk.services.dynamodb.model.*;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.Map;

@Repository
@RequiredArgsConstructor
public class DynamoDbEventRepository implements EventRepository {

    private final DynamoDbAsyncClient dynamoDbClient;
    private static final String TABLE_NAME = "Events";

    @Override
    public Mono<Event> save(Event event) {
        Map<String, AttributeValue> item = new HashMap<>();
        item.put("id", AttributeValue.builder().s(event.getId()).build());
        item.put("name", AttributeValue.builder().s(event.getName()).build());
        if (event.getDate() != null) {
            item.put("date", AttributeValue.builder().s(event.getDate().toString()).build());
        }
        if (event.getLocation() != null) {
            item.put("location", AttributeValue.builder().s(event.getLocation()).build());
        }
        if (event.getTotalCapacity() != null) {
            item.put("totalCapacity", AttributeValue.builder().n(event.getTotalCapacity().toString()).build());
        }
        if (event.getAvailableTickets() != null) {
            item.put("availableTickets", AttributeValue.builder().n(event.getAvailableTickets().toString()).build());
        }

        PutItemRequest putItemRequest = PutItemRequest.builder()
                .tableName(TABLE_NAME)
                .item(item)
                .build();

        return Mono.fromFuture(() -> dynamoDbClient.putItem(putItemRequest))
                .map(response -> event);
    }

    @Override
    public Mono<Event> findById(String id) {
        Map<String, AttributeValue> key = new HashMap<>();
        key.put("id", AttributeValue.builder().s(id).build());

        GetItemRequest request = GetItemRequest.builder()
                .tableName(TABLE_NAME)
                .key(key)
                .build();

        return Mono.fromFuture(() -> dynamoDbClient.getItem(request))
                .mapNotNull(response -> {
                    if (!response.hasItem()) return null;
                    return mapToEvent(response.item());
                });
    }

    @Override
    public Flux<Event> findAll() {
        ScanRequest request = ScanRequest.builder()
                .tableName(TABLE_NAME)
                .build();

        return Mono.fromFuture(() -> dynamoDbClient.scan(request))
                .flatMapMany(response -> Flux.fromIterable(response.items()))
                .map(this::mapToEvent);
    }

    @Override
    public Mono<Event> decrementAvailableTickets(String eventId, int quantity) {
        return updateAvailableTickets(eventId, -quantity);
    }

    @Override
    public Mono<Event> incrementAvailableTickets(String eventId, int quantity) {
        return updateAvailableTickets(eventId, quantity);
    }

    private Mono<Event> updateAvailableTickets(String eventId, int delta) {
        Map<String, AttributeValue> key = new HashMap<>();
        key.put("id", AttributeValue.builder().s(eventId).build());

        Map<String, String> expressionAttributeNames = new HashMap<>();
        expressionAttributeNames.put("#AT", "availableTickets");

        Map<String, AttributeValue> expressionAttributeValues = new HashMap<>();
        expressionAttributeValues.put(":delta", AttributeValue.builder().n(String.valueOf(delta)).build());
        
        // Ensure available tickets does not drop below zero if we are decrementing
        String conditionExpression = delta < 0 ? "#AT >= :minQty" : null;
        if (delta < 0) {
            expressionAttributeValues.put(":minQty", AttributeValue.builder().n(String.valueOf(Math.abs(delta))).build());
        }

        UpdateItemRequest.Builder requestBuilder = UpdateItemRequest.builder()
                .tableName(TABLE_NAME)
                .key(key)
                .updateExpression("SET #AT = #AT + :delta")
                .expressionAttributeNames(expressionAttributeNames)
                .expressionAttributeValues(expressionAttributeValues)
                .returnValues(ReturnValue.ALL_NEW);

        if (conditionExpression != null) {
            requestBuilder.conditionExpression(conditionExpression);
        }

        return Mono.fromFuture(() -> dynamoDbClient.updateItem(requestBuilder.build()))
                .map(response -> mapToEvent(response.attributes()))
                .onErrorMap(ConditionalCheckFailedException.class, e -> new RuntimeException("Not enough tickets available in inventory"));
    }

    private Event mapToEvent(Map<String, AttributeValue> item) {
        Event.EventBuilder builder = Event.builder()
                .id(item.get("id").s());
        
        if (item.containsKey("name")) builder.name(item.get("name").s());
        if (item.containsKey("date")) builder.date(LocalDateTime.parse(item.get("date").s()));
        if (item.containsKey("location")) builder.location(item.get("location").s());
        if (item.containsKey("totalCapacity")) builder.totalCapacity(Integer.parseInt(item.get("totalCapacity").n()));
        if (item.containsKey("availableTickets")) builder.availableTickets(Integer.parseInt(item.get("availableTickets").n()));
        
        return builder.build();
    }
}
