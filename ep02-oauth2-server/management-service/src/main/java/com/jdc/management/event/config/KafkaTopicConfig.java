package com.jdc.management.event.config;

import org.apache.kafka.common.config.TopicConfig;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.config.TopicBuilder;
import org.springframework.kafka.core.KafkaAdmin.NewTopics;

import com.jdc.management.props.KafkaProps;

@Configuration
public class KafkaTopicConfig {

    @Bean
    NewTopics pinLockSettingTopics(KafkaProps kafkaProps) {
        var properties = kafkaProps.getPinLock();
        var pinLockSettings = TopicBuilder.name(properties.getTopic())
                .partitions(properties.getPartitions())
                .replicas(properties.getReplicas())
                .config(TopicConfig.CLEANUP_POLICY_CONFIG, TopicConfig.CLEANUP_POLICY_COMPACT)
                .build();
        var managementAccount = kafkaProps.getManagementAccount();
        var invitations = TopicBuilder.name(managementAccount.getInvitationTopic())
                .partitions(properties.getPartitions())
                .replicas(properties.getReplicas())
                .build();
        return new NewTopics(pinLockSettings, invitations);
    }
}
