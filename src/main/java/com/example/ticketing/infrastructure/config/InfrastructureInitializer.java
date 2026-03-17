package com.example.ticketing.infrastructure.config;

import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Configuration;
import software.amazon.awssdk.services.dynamodb.DynamoDbAsyncClient;
import software.amazon.awssdk.services.dynamodb.model.*;
import software.amazon.awssdk.services.sqs.SqsAsyncClient;
import software.amazon.awssdk.services.sqs.model.CreateQueueRequest;

import java.util.List;

@Configuration
@RequiredArgsConstructor
@Slf4j
public class InfrastructureInitializer {

    private final DynamoDbAsyncClient dynamoDbClient;
    private final SqsAsyncClient sqsClient;

    public static final String EVENTS_TABLE = "Events";
    public static final String TICKETS_TABLE = "Tickets";
    public static final String ORDERS_TABLE = "Orders";
    public static final String ORDERS_QUEUE = "orders-queue";

    @PostConstruct
    public void init() {
        createTableIfNotExists(EVENTS_TABLE, "id", ScalarAttributeType.S, null, null);
        createTableIfNotExists(TICKETS_TABLE, "id", ScalarAttributeType.S, "eventId", ScalarAttributeType.S);
        createTableIfNotExists(ORDERS_TABLE, "id", ScalarAttributeType.S, null, null);
        createQueueIfNotExists(ORDERS_QUEUE);
    }

    private void createTableIfNotExists(String tableName, String hashKey, ScalarAttributeType hashType, String sortKey, ScalarAttributeType sortType) {
        dynamoDbClient.listTables().thenAccept(response -> {
            if (!response.tableNames().contains(tableName)) {
                log.info("Creating table {}", tableName);
                
                CreateTableRequest.Builder builder = CreateTableRequest.builder()
                        .tableName(tableName)
                        .billingMode(BillingMode.PAY_PER_REQUEST);
                
                if (sortKey != null) {
                    builder.keySchema(
                            KeySchemaElement.builder().attributeName(hashKey).keyType(KeyType.HASH).build(),
                            KeySchemaElement.builder().attributeName(sortKey).keyType(KeyType.RANGE).build()
                    );
                    builder.attributeDefinitions(
                            AttributeDefinition.builder().attributeName(hashKey).attributeType(hashType).build(),
                            AttributeDefinition.builder().attributeName(sortKey).attributeType(sortType).build()
                    );
                    
                    // Add index for querying tickets by eventId alone if we use a composed key
                    if(tableName.equals(TICKETS_TABLE)) {
                       builder.globalSecondaryIndexes(GlobalSecondaryIndex.builder()
                               .indexName("EventIdIndex")
                               .keySchema(KeySchemaElement.builder().attributeName("eventId").keyType(KeyType.HASH).build())
                               .projection(Projection.builder().projectionType(ProjectionType.ALL).build())
                               .build());
                    }
                } else {
                    builder.keySchema(KeySchemaElement.builder().attributeName(hashKey).keyType(KeyType.HASH).build());
                    builder.attributeDefinitions(AttributeDefinition.builder().attributeName(hashKey).attributeType(hashType).build());
                }

                dynamoDbClient.createTable(builder.build()).join();
                log.info("Table {} created", tableName);
            }
        }).exceptionally(e -> {
            log.error("Failed to initialize table {}", tableName, e);
            return null;
        });
    }

    private void createQueueIfNotExists(String queueName) {
        sqsClient.listQueues().thenAccept(response -> {
            if (response.queueUrls().stream().noneMatch(url -> url.contains(queueName))) {
                log.info("Creating queue {}", queueName);
                sqsClient.createQueue(CreateQueueRequest.builder().queueName(queueName).build()).join();
                log.info("Queue {} created", queueName);
            }
        });
    }
}
