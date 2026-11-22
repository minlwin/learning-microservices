package com.jdc.consumer;

import org.springframework.context.annotation.Configuration;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;

import com.jdc.consumer.model.BaseRepositoryImpl;
import com.jdc.consumer.model.ValidatedRepositoryFactoryBean;

/** Custom JPA repositories; datasource, entity manager, and transactions are configured by Spring Boot. */
@Configuration
@EnableJpaRepositories(repositoryBaseClass = BaseRepositoryImpl.class, repositoryFactoryBeanClass = ValidatedRepositoryFactoryBean.class)
public class RepositoryConfig {

}
