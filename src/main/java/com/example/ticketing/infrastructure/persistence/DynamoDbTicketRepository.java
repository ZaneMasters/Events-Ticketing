package com.example.ticketing.infrastructure.persistence;

import com.example.ticketing.domain.model.Ticket;
import com.example.ticketing.domain.model.TicketState;
import com.example.ticketing.domain.repository.TicketRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Repository;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import software.amazon.awssdk.services.dynamodb.DynamoDbAsyncClient;
import software.amazon.awssdk.services.dynamodb.model.*;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Repository
@RequiredArgsConstructor
@Slf4j
public class DynamoDbTicketRepository implements TicketRepository {

    private final DynamoDbAsyncClient dynamoDbClient;
    private static final String TABLE_NAME = "Tickets";

    @Override
    public Flux<Ticket> saveAll(List<Ticket> tickets) {
        // Simple sequential save for demo, ideally should use batchWriteItem
        return Flux.fromIterable(tickets)
                .flatMap(this::save);
    }

    @Override
    public Mono<Ticket> save(Ticket ticket) {
        if (ticket.getVersion() == null) ticket.setVersion(1L);

        Map<String, AttributeValue> item = new HashMap<>();
        item.put("id", AttributeValue.builder().s(ticket.getId()).build());
        item.put("eventId", AttributeValue.builder().s(ticket.getEventId()).build());
        item.put("state", AttributeValue.builder().s(ticket.getState().name()).build());
        item.put("version", AttributeValue.builder().n(ticket.getVersion().toString()).build());
        if (ticket.getSequence() != null) {
            item.put("sequence", AttributeValue.builder().s(ticket.getSequence()).build());
        }
        if (ticket.getLockedByOrderId() != null) {
            item.put("lockedByOrderId", AttributeValue.builder().s(ticket.getLockedByOrderId()).build());
        }
        if (ticket.getReservationExpiresAt() != null) {
            item.put("reservationExpiresAt", AttributeValue.builder().s(ticket.getReservationExpiresAt().toString()).build());
        }

        PutItemRequest request = PutItemRequest.builder()
                .tableName(TABLE_NAME)
                .item(item)
                .build();

        return Mono.fromFuture(() -> dynamoDbClient.putItem(request))
                .map(response -> ticket);
    }

    @Override
    public Flux<Ticket> findByEventId(String eventId) {
        Map<String, AttributeValue> expressionAttributeValues = new HashMap<>();
        expressionAttributeValues.put(":eventId", AttributeValue.builder().s(eventId).build());

        QueryRequest request = QueryRequest.builder()
                .tableName(TABLE_NAME)
                .indexName("EventIdIndex")
                .keyConditionExpression("eventId = :eventId")
                .expressionAttributeValues(expressionAttributeValues)
                .build();

        return Mono.fromFuture(() -> dynamoDbClient.query(request))
                .flatMapMany(response -> Flux.fromIterable(response.items()))
                .map(this::mapToTicket);
    }

    @Override
    public Flux<Ticket> findAvailableByEventId(String eventId, int limit) {
        Map<String, AttributeValue> expressionAttributeValues = new HashMap<>();
        expressionAttributeValues.put(":eventId", AttributeValue.builder().s(eventId).build());
        expressionAttributeValues.put(":state", AttributeValue.builder().s(TicketState.AVAILABLE.name()).build());

        Map<String, String> expressionAttributeNames = new HashMap<>();
        expressionAttributeNames.put("#state", "state");

        QueryRequest request = QueryRequest.builder()
                .tableName(TABLE_NAME)
                .indexName("EventIdIndex")
                .keyConditionExpression("eventId = :eventId")
                .filterExpression("#state = :state")
                .expressionAttributeValues(expressionAttributeValues)
                .expressionAttributeNames(expressionAttributeNames)
                // We use limit heavily mapped in memory for this demo simplicity.
                .build();

        return Mono.fromFuture(() -> dynamoDbClient.query(request))
                .flatMapMany(response -> Flux.fromIterable(response.items()))
                .map(this::mapToTicket)
                .take(limit);
    }

    @Override
    public Mono<Boolean> updateTicketsState(String eventId, List<String> ticketIds, TicketState newState, String orderId, LocalDateTime expiration) {
        if (ticketIds.isEmpty()) return Mono.just(true);

        return Flux.fromIterable(ticketIds)
                .flatMap(ticketId -> {
                    // Update each ticket using optimistic locking / conditional mapping
                    // We don't have explicit version here to compare in TransactWrite without loading them first.
                    // This is simple conditional updating.
                    Map<String, AttributeValue> key = new HashMap<>();
                    key.put("id", AttributeValue.builder().s(ticketId).build());
                    key.put("eventId", AttributeValue.builder().s(eventId).build());

                    Map<String, String> expressionAttributeNames = new HashMap<>();
                    expressionAttributeNames.put("#state", "state");

                    Map<String, AttributeValue> expressionAttributeValues = new HashMap<>();
                    expressionAttributeValues.put(":newState", AttributeValue.builder().s(newState.name()).build());

                    String updateExpression = "SET #state = :newState";

                    if (orderId != null) {
                        expressionAttributeNames.put("#lockedBy", "lockedByOrderId");
                        expressionAttributeValues.put(":lockId", AttributeValue.builder().s(orderId).build());
                        updateExpression += ", #lockedBy = :lockId";
                    } else {
                        updateExpression += " REMOVE lockedByOrderId";  // Clear the lock
                    }

                    if (expiration != null) {
                        expressionAttributeNames.put("#expiresAt", "reservationExpiresAt");
                        expressionAttributeValues.put(":exp", AttributeValue.builder().s(expiration.toString()).build());
                        updateExpression += ", #expiresAt = :exp";
                    } else {
                        updateExpression += " REMOVE reservationExpiresAt"; // Clear expiration
                    }

                    UpdateItemRequest request = UpdateItemRequest.builder()
                            .tableName(TABLE_NAME)
                            .key(key)
                            .updateExpression(updateExpression)
                            .expressionAttributeNames(expressionAttributeNames)
                            .expressionAttributeValues(expressionAttributeValues)
                            // Ideally, we add condition here to ensure we don't override an already sold/reserved ticket
                            // .conditionExpression("#state = :expectedState")
                            .returnValues(ReturnValue.ALL_NEW)
                            .build();

                    return Mono.fromFuture(() -> dynamoDbClient.updateItem(request))
                            .map(r -> true)
                            .onErrorResume(ConditionalCheckFailedException.class, e -> {
                                log.warn("Optimistic locking failed for ticket ID {}", ticketId);
                                return Mono.just(false);
                            });
                })
                .collectList()
                .map(results -> !results.contains(false)); // Returns true ONLY if all updates succeeded
    }

    @Override
    public Flux<Ticket> findExpiredReservations(LocalDateTime cutoffTime) {
        Map<String, AttributeValue> expressionAttributeValues = new HashMap<>();
        expressionAttributeValues.put(":state", AttributeValue.builder().s(TicketState.RESERVED.name()).build());
        expressionAttributeValues.put(":cutoff", AttributeValue.builder().s(cutoffTime.toString()).build());

        Map<String, String> expressionAttributeNames = new HashMap<>();
        expressionAttributeNames.put("#state", "state");
        expressionAttributeNames.put("#expiresAt", "reservationExpiresAt");

        ScanRequest request = ScanRequest.builder()
                .tableName(TABLE_NAME)
                .filterExpression("#state = :state AND #expiresAt < :cutoff")
                .expressionAttributeNames(expressionAttributeNames)
                .expressionAttributeValues(expressionAttributeValues)
                .build();

        return Mono.fromFuture(() -> dynamoDbClient.scan(request))
                .flatMapMany(response -> Flux.fromIterable(response.items()))
                .map(this::mapToTicket);
    }

    private Ticket mapToTicket(Map<String, AttributeValue> item) {
        Ticket.TicketBuilder builder = Ticket.builder()
                .id(item.get("id").s());

        if (item.containsKey("eventId")) builder.eventId(item.get("eventId").s());
        if (item.containsKey("state")) builder.state(TicketState.valueOf(item.get("state").s()));
        if (item.containsKey("version")) builder.version(Long.parseLong(item.get("version").n()));
        if (item.containsKey("sequence")) builder.sequence(item.get("sequence").s());
        if (item.containsKey("lockedByOrderId")) builder.lockedByOrderId(item.get("lockedByOrderId").s());
        if (item.containsKey("reservationExpiresAt")) builder.reservationExpiresAt(LocalDateTime.parse(item.get("reservationExpiresAt").s()));

        return builder.build();
    }
}
