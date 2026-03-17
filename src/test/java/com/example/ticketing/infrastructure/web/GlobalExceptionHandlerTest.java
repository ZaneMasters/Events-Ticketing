package com.example.ticketing.infrastructure.web;

import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

import static org.assertj.core.api.Assertions.assertThat;

class GlobalExceptionHandlerTest {

    private final GlobalExceptionHandler handler = new GlobalExceptionHandler();

    @Test
    void shouldHandleIllegalArgumentException() {
        IllegalArgumentException ex = new IllegalArgumentException("Invalid argument data");
        
        Mono<ResponseEntity<GlobalExceptionHandler.ErrorResponse>> responseMono = handler.handleIllegalArgumentException(ex);
        
        StepVerifier.create(responseMono)
                .assertNext(response -> {
                    assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
                    assertThat(response.getBody()).isNotNull();
                    assertThat(response.getBody().getStatus()).isEqualTo(400);
                    assertThat(response.getBody().getMessage()).isEqualTo("Invalid argument data");
                })
                .verifyComplete();
    }

    @Test
    void shouldHandleRuntimeException() {
        RuntimeException ex = new RuntimeException("Business rule violation");
        
        Mono<ResponseEntity<GlobalExceptionHandler.ErrorResponse>> responseMono = handler.handleRuntimeException(ex);
        
        StepVerifier.create(responseMono)
                .assertNext(response -> {
                    assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
                    assertThat(response.getBody()).isNotNull();
                    assertThat(response.getBody().getStatus()).isEqualTo(400);
                    assertThat(response.getBody().getMessage()).isEqualTo("Business rule violation");
                })
                .verifyComplete();
    }

    @Test
    void shouldHandleGenericException() {
        Exception ex = new Exception("Unknown critical failure");
        
        Mono<ResponseEntity<GlobalExceptionHandler.ErrorResponse>> responseMono = handler.handleGenericException(ex);
        
        StepVerifier.create(responseMono)
                .assertNext(response -> {
                    assertThat(response.getStatusCode()).isEqualTo(HttpStatus.INTERNAL_SERVER_ERROR);
                    assertThat(response.getBody()).isNotNull();
                    assertThat(response.getBody().getStatus()).isEqualTo(500);
                    assertThat(response.getBody().getMessage()).isEqualTo("An unexpected error occurred");
                })
                .verifyComplete();
    }
}
