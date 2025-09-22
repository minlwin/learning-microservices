package com.jdc.ledger.domain.jpa;

import java.io.Serializable;

import jakarta.persistence.MappedSuperclass;

@MappedSuperclass
public abstract class AbstractEntity implements Serializable{

	private static final long serialVersionUID = 1L;

}
