package com.jdc.management;

import org.springframework.context.annotation.Configuration;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;

import com.jdc.management.model.BaseRepositoryImpl;
import com.jdc.management.model.ValidatedRepositoryFactoryBean;

/** Custom JPA repositories; datasource, entity manager, and transactions are configured by Spring Boot. */
@Configuration
@EnableJpaRepositories(repositoryBaseClass = BaseRepositoryImpl.class, repositoryFactoryBeanClass = ValidatedRepositoryFactoryBean.class)
public class RepositoryConfig {

}
