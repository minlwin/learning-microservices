package com.jdc.auth.model.repo;

import java.util.UUID;
import java.util.Optional;
import org.springframework.data.jpa.repository.Lock;
import jakarta.persistence.LockModeType;

import org.springframework.data.jpa.repository.JpaRepository;

import com.jdc.auth.model.entity.Registration;

public interface RegistrationRepo extends JpaRepository<Registration, UUID>{

	@Override
	@Lock(LockModeType.PESSIMISTIC_WRITE)
	Optional<Registration> findById(UUID id);

	int countByEmail(String email);

}
