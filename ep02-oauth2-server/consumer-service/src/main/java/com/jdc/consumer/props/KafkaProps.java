package com.jdc.consumer.props;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;
import org.springframework.validation.annotation.Validated;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import lombok.Data;

@Data
@Validated
@Component
@ConfigurationProperties(prefix = "app.kafka")
public class KafkaProps {

    @Valid
    private Outbox outbox = new Outbox();

    @Valid
    private PinLock pinLock = new PinLock();

    @Valid
    private Account account = new Account();

    @Data
    public static class PinLock {
        @NotBlank
        private String activityTopic = "pin-lock-activity-changed.v1";

        @NotBlank
        private String activityConsumerGroupId = "consumer-service-pin-lock-history";
    }

    @Data
    public static class Outbox {
        @Min(1)
        private long fixedDelay = 1000;
    }

    @Data
    public static class Account {
        @NotBlank
        private String approvedTopic = "consumer-account-approved.v1";

        @NotBlank
        private String rejectedTopic = "consumer-account-rejected.v1";
    }
}
