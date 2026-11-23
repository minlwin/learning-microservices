package com.jdc.management.props;

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
    private PinLock pinLock = new PinLock();

    @Valid
    private ManagementAccount managementAccount = new ManagementAccount();

    @Valid
    private Outbox outbox = new Outbox();

    @Data
    public static class PinLock {
        @NotBlank
        private String topic = "pin-lock-setting-changed.v1";

        @NotBlank
        private String activityTopic = "pin-lock-activity-changed.v1";

        @NotBlank
        private String activityConsumerGroupId = "management-service-pin-lock-history";

        @Min(1)
        private int partitions = 3;

        @Min(1)
        private int replicas = 1;
    }

    @Data
    public static class ManagementAccount {
        @NotBlank
        private String invitationTopic = "management-account-invitation.v1";

        @NotBlank
        private String activatedTopic = "management-account-activated.v1";

        @NotBlank
        private String activatedConsumerGroupId = "management-service-account-activation";
    }

    @Data
    public static class Outbox {
        @Min(1)
        private long fixedDelay = 1000;
    }
}
