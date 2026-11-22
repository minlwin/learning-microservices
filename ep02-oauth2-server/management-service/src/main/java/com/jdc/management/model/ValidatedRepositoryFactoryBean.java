package com.jdc.management.model;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.jpa.repository.support.JpaRepositoryFactoryBean;
import org.springframework.data.repository.Repository;
import org.springframework.data.repository.core.support.RepositoryFactorySupport;
import org.springframework.validation.beanvalidation.MethodValidationInterceptor;

import jakarta.persistence.EntityManager;
import jakarta.validation.Validator;

/** Installs Bean Validation inside the Spring Data repository proxy. */
public class ValidatedRepositoryFactoryBean<R extends Repository<T, ID>, T, ID>
		extends JpaRepositoryFactoryBean<R, T, ID> {

	private Validator validator;

	public ValidatedRepositoryFactoryBean(Class<? extends R> repositoryInterface) {
		super(repositoryInterface);
	}

	@Autowired
	public void setValidator(Validator validator) {
		this.validator = validator;
	}

	@Override
	protected RepositoryFactorySupport createRepositoryFactory(EntityManager entityManager) {
		var factory = super.createRepositoryFactory(entityManager);
		factory.addRepositoryProxyPostProcessor((proxy, information) -> {
			if (BaseRepository.class.isAssignableFrom(information.getRepositoryInterface())) {
				proxy.addAdvice(0, new MethodValidationInterceptor(validator));
			}
		});
		return factory;
	}
}
