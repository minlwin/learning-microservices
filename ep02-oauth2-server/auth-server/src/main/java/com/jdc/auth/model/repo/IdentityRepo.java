package com.jdc.auth.model.repo;

import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

import com.jdc.auth.model.entity.Identity;

public interface IdentityRepo extends JpaRepository<Identity, UUID>{

	int countByUsername(String email);

	Optional<Identity> findOneByUsername(String email);

}
