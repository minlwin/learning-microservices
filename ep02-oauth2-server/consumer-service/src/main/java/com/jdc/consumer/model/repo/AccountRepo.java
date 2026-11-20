package com.jdc.consumer.model.repo;

import org.springframework.data.jpa.repository.JpaRepository;

import com.jdc.consumer.model.entity.Account;

public interface AccountRepo extends JpaRepository<Account, String>{

}
