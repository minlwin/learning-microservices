package com.jdc.auth.event.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.config.TopicBuilder;
import org.springframework.kafka.core.KafkaAdmin.NewTopics;

import com.jdc.auth.props.KafkaProps;

@Configuration
public class PinLockActivityTopicConfig {

    @Bean
    NewTopics pinLockActivityTopic(KafkaProps properties) {
        var pinLock = properties.getPinLock();
        var topic = TopicBuilder.name(pinLock.getActivityTopic())
                .partitions(3)
                .replicas(1)
                .build();
        var managementAccount = properties.getManagementAccount();
        var activated = TopicBuilder.name(managementAccount.getActivatedTopic())
                .partitions(3)
                .replicas(1)
                .build();
        return new NewTopics(topic, activated);
    }
}
