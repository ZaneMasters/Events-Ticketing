package com.example.ticketing.infrastructure.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import software.amazon.awssdk.auth.credentials.AwsBasicCredentials;
import software.amazon.awssdk.auth.credentials.StaticCredentialsProvider;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.dynamodb.DynamoDbAsyncClient;
import software.amazon.awssdk.services.sqs.SqsAsyncClient;

import java.net.URI;

import org.springframework.core.env.Environment;
import java.util.Arrays;

@Configuration
public class AwsConfig {

    @Value("${aws.region:us-east-1}")
    private String region;

    @Value("${aws.endpoint:http://localhost:4566}")
    private String endpoint;

    @Bean
    public DynamoDbAsyncClient dynamoDbAsyncClient(Environment env) {
        var builder = DynamoDbAsyncClient.builder()
                .region(Region.of(region));

        if (isLocalProfileActive(env)) {
            builder.endpointOverride(URI.create(endpoint))
                   .credentialsProvider(StaticCredentialsProvider.create(
                           AwsBasicCredentials.create("test", "test")
                   ));
        }

        return builder.build();
    }

    @Bean
    public SqsAsyncClient sqsAsyncClient(Environment env) {
        var builder = SqsAsyncClient.builder()
                .region(Region.of(region));

        if (isLocalProfileActive(env)) {
            builder.endpointOverride(URI.create(endpoint))
                   .credentialsProvider(StaticCredentialsProvider.create(
                           AwsBasicCredentials.create("test", "test")
                   ));
        }

        return builder.build();
    }

    private boolean isLocalProfileActive(Environment env) {
        String[] profiles = env.getActiveProfiles();
        if (profiles == null || profiles.length == 0) return true;
        return Arrays.asList(profiles).contains("local");
    }
}
