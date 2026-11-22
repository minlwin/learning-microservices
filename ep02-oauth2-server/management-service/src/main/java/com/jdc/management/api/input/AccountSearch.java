package com.jdc.management.api.input;

import java.time.LocalDate;
import com.jdc.management.model.entity.Account.Status;

public record AccountSearch(Status status, LocalDate createdFrom, LocalDate createdTo, String keyword) {
}
