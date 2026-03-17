package com.example.ticketing;

import org.springframework.context.ApplicationContext;
import org.junit.jupiter.api.BeforeEach;

import com.example.ticketing.domain.model.Event;
import com.example.ticketing.domain.model.Order;
import com.example.ticketing.infrastructure.web.dto.CreateEventRequest;
import com.example.ticketing.infrastructure.web.dto.PurchaseRequest;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.reactive.server.WebTestClient;
import org.testcontainers.containers.localstack.LocalStackContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;

import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThat;
import static org.testcontainers.containers.localstack.LocalStackContainer.Service.DYNAMODB;
import static org.testcontainers.containers.localstack.LocalStackContainer.Service.SQS;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@Testcontainers
@ActiveProfiles("local")
class TicketingApplicationTests {

    @Container
    @SuppressWarnings("resource")
    static LocalStackContainer localStack = new LocalStackContainer(DockerImageName.parse("localstack/localstack:3.0.0"))
            .withServices(DYNAMODB, SQS);

    @DynamicPropertySource
    static void overrideConfiguration(DynamicPropertyRegistry registry) {
        registry.add("aws.endpoint", () -> localStack.getEndpointOverride(DYNAMODB).toString());
        registry.add("aws.region", localStack::getRegion);
    }

    @Autowired
    private ApplicationContext context;

    private WebTestClient webTestClient;

    @BeforeEach
    void setUp() {
        this.webTestClient = WebTestClient.bindToApplicationContext(this.context)
                .configureClient()
                .responseTimeout(java.time.Duration.ofHours(1))
                .build();
    }

    @Test
    void shouldCreateEventAndProcessPurchaseFlow() {
        // 1. Create Event
        CreateEventRequest eventRequest = new CreateEventRequest();
        eventRequest.setName("Integration Test Concert");
        eventRequest.setDate(LocalDateTime.now().plusDays(10).toString());
        eventRequest.setLocation("Test Arena");
        eventRequest.setTotalCapacity(50);

        Event createdEvent = webTestClient.post()
                .uri("/api/events")
                .bodyValue(eventRequest)
                .exchange()
                .expectStatus().isCreated()
                .expectBody(Event.class)
                .returnResult().getResponseBody();

        assertThat(createdEvent).isNotNull();
        assertThat(createdEvent.getId()).isNotBlank();
        assertThat(createdEvent.getAvailableTickets()).isEqualTo(50);

        // 2. Query Event
        webTestClient.get()
                .uri("/api/events/" + createdEvent.getId() + "/availability")
                .exchange()
                .expectStatus().isOk()
                .expectBody()
                .jsonPath("$.name").isEqualTo("Integration Test Concert");

        // 3. Purchase Tickets
        PurchaseRequest purchaseRequest = new PurchaseRequest();
        purchaseRequest.setUserId("user-123");
        purchaseRequest.setQuantity(2);

        Order createdOrder = webTestClient.post()
                .uri("/api/events/" + createdEvent.getId() + "/purchase")
                .bodyValue(purchaseRequest)
                .exchange()
                .expectStatus().isAccepted()
                .expectBody(Order.class)
                .returnResult().getResponseBody();

        assertThat(createdOrder).isNotNull();
        assertThat(createdOrder.getId()).isNotBlank();
        assertThat(createdOrder.getQuantity()).isEqualTo(2);

        // 4. Query Order
        webTestClient.get()
                .uri("/api/orders/" + createdOrder.getId())
                .exchange()
                .expectStatus().isOk()
                .expectBody()
                .jsonPath("$.userId").isEqualTo("user-123");

        // 5. Test Error Handling (Not enough tickets)
        PurchaseRequest exhaustedRequest = new PurchaseRequest();
        exhaustedRequest.setUserId("user-456");
        exhaustedRequest.setQuantity(100);

        webTestClient.post()
                .uri("/api/events/" + createdEvent.getId() + "/purchase")
                .bodyValue(exhaustedRequest)
                .exchange()
                .expectStatus().isBadRequest()
                .expectBody()
                .jsonPath("$.message").isEqualTo("Not enough available tickets.");
    }
}
