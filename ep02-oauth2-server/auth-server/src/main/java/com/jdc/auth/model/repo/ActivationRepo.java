package com.jdc.auth.model.repo;

import java.util.UUID;
import java.util.Optional;
import org.springframework.data.jpa.repository.Lock;
import jakarta.persistence.LockModeType;

import org.springframework.data.jpa.repository.JpaRepository;

import com.jdc.auth.model.entity.Activation;

public interface ActivationRepo extends JpaRepository<Activation, UUID>{

	@Override
	@Lock(LockModeType.PESSIMISTIC_WRITE)
	Optional<Activation> findById(UUID id);

}
