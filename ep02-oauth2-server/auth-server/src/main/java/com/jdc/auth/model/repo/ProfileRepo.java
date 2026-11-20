package com.jdc.auth.model.repo;

import java.util.UUID;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.jdc.auth.model.entity.Profile;
import com.jdc.auth.model.entity.Identity;

public interface ProfileRepo extends JpaRepository<Profile, UUID>{

	Optional<Profile> findByIdentityUsernameAndSystem(String username, String system);

	boolean existsByIdentityAndSystem(Identity identity, String system);

	@Modifying(clearAutomatically = true, flushAutomatically = true)
	@Query("update Profile p set p.tokenVersion = p.tokenVersion + 1 where p.id = :id and p.tokenVersion = :expected")
	int revokeTokens(@Param("id") UUID id, @Param("expected") long expectedVersion);

}
