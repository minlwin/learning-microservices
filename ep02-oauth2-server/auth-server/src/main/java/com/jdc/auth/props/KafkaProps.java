package com.jdc.auth.props;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;
import org.springframework.validation.annotation.Validated;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Min;
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

    @Data
    public static class Outbox {
        @Min(1)
        private long fixedDelay = 1000;
    }

    @Data
    public static class PinLock {
        @NotBlank
        private String topic = "pin-lock-setting-changed.v1";

        @NotBlank
        private String activityTopic = "pin-lock-activity-changed.v1";

        @NotBlank
        private String consumerGroupId = "auth-server-pin-lock-settings";
    }
}
