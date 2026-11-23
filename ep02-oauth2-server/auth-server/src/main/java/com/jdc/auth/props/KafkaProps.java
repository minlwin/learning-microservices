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

    @Valid
    private ConsumerAccount consumerAccount = new ConsumerAccount();

    @Valid
    private ManagementAccount managementAccount = new ManagementAccount();

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

    @Data
    public static class ConsumerAccount {
        @NotBlank
        private String approvedTopic = "consumer-account-approved.v1";

        @NotBlank
        private String approvedGroupId = "auth-server-consumer-account-approvals";

        @NotBlank
        private String rejectedTopic = "consumer-account-rejected.v1";

        @NotBlank
        private String rejectedGroupId = "auth-server-consumer-account-rejections";
    }

    @Data
    public static class ManagementAccount {
        @NotBlank
        private String invitationTopic = "management-account-invitation.v1";

        @NotBlank
        private String invitationGroupId = "auth-server-management-account-invitations";

        @NotBlank
        private String activatedTopic = "management-account-activated.v1";
    }
}
