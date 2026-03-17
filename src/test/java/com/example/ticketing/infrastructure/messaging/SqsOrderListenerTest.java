package com.example.ticketing.infrastructure.messaging;

import com.example.ticketing.application.usecase.ProcessPurchaseUseCase;
import com.example.ticketing.domain.model.Order;
import com.example.ticketing.domain.model.TicketState;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import reactor.core.publisher.Mono;
import software.amazon.awssdk.services.sqs.SqsAsyncClient;
import software.amazon.awssdk.services.sqs.model.*;

import java.util.concurrent.CompletableFuture;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class SqsOrderListenerTest {

    @Mock
    private SqsAsyncClient sqsClient;
    @Mock
    private ObjectMapper objectMapper;
    @Mock
    private ProcessPurchaseUseCase processPurchaseUseCase;

    @InjectMocks
    private SqsOrderListener listener;

    @BeforeEach
    void setUp() throws Exception {
        // Manually trigger init so queueUrlRef is populated
        when(sqsClient.getQueueUrl(any(GetQueueUrlRequest.class)))
                .thenReturn(CompletableFuture.completedFuture(GetQueueUrlResponse.builder().queueUrl("http://localhost:4566/dummy").build()));
        listener.init();
    }

    @Test
    void shouldProcessMessageSuccessfully() throws Exception {
        String receiptHandle = "rh-1";
        Message message = Message.builder()
                .body("{\"id\":\"order1\"}")
                .receiptHandle(receiptHandle)
                .build();

        Order order = Order.builder()
                .id("order1")
                .status(TicketState.PENDING_CONFIRMATION)
                .build();

        when(sqsClient.receiveMessage(any(ReceiveMessageRequest.class)))
                .thenReturn(CompletableFuture.completedFuture(ReceiveMessageResponse.builder().messages(message).build()));

        when(objectMapper.readValue("{\"id\":\"order1\"}", Order.class)).thenReturn(order);
        when(processPurchaseUseCase.execute(order)).thenReturn(Mono.empty());
        when(sqsClient.deleteMessage(any(DeleteMessageRequest.class)))
                .thenReturn(CompletableFuture.completedFuture(DeleteMessageResponse.builder().build()));

        listener.pollQueue();

        // Allow async hooks to run
        Thread.sleep(200);

        verify(processPurchaseUseCase).execute(order);
        verify(sqsClient).deleteMessage(any(DeleteMessageRequest.class));
    }

    @Test
    void shouldNotDeleteMessageWhenProcessingFails() throws Exception {
        Message message = Message.builder()
                .body("{\"id\":\"order1\"}")
                .receiptHandle("rh-1")
                .build();

        Order order = Order.builder().id("order1").build();

        when(sqsClient.receiveMessage(any(ReceiveMessageRequest.class)))
                .thenReturn(CompletableFuture.completedFuture(ReceiveMessageResponse.builder().messages(message).build()));

        when(objectMapper.readValue("{\"id\":\"order1\"}", Order.class)).thenReturn(order);
        when(processPurchaseUseCase.execute(order)).thenReturn(Mono.error(new RuntimeException("Simulated failure")));

        listener.pollQueue();
        Thread.sleep(200);

        verify(processPurchaseUseCase).execute(order);
        verify(sqsClient, never()).deleteMessage(any(DeleteMessageRequest.class));
    }

    @Test
    void shouldHandleJsonProcessingError() throws Exception {
        Message message = Message.builder()
                .body("invalid")
                .receiptHandle("rh-1")
                .build();

        when(sqsClient.receiveMessage(any(ReceiveMessageRequest.class)))
                .thenReturn(CompletableFuture.completedFuture(ReceiveMessageResponse.builder().messages(message).build()));

        when(objectMapper.readValue("invalid", Order.class)).thenThrow(mock(JsonProcessingException.class));

        listener.pollQueue();
        Thread.sleep(200);

        verify(processPurchaseUseCase, never()).execute(any());
        verify(sqsClient, never()).deleteMessage(any(DeleteMessageRequest.class));
    }
}
