package com.jdc.management.service;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.same;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import com.jdc.management.api.input.AccountRegistrationForm;
import com.jdc.management.event.service.ManagementAccountInvitationOutboxService;
import com.jdc.management.model.entity.Account;
import com.jdc.management.model.repo.AccountRepo;

class AccountCreationServiceTest {

    private final AccountRepo accounts = mock(AccountRepo.class);
    private final ManagementAccountInvitationOutboxService invitations =
            mock(ManagementAccountInvitationOutboxService.class);
    private final AccountCreationService service = new AccountCreationService(accounts, invitations);

    @Test
    void savesPendingAccountAndQueuesItsInvitation() {
        when(accounts.save(any(Account.class))).thenAnswer(invocation -> invocation.getArgument(0));
        var order = inOrder(accounts, invitations);

        service.create(new AccountRegistrationForm("Alice", "09123456789", "alice@example.com"));

        var accountCaptor = ArgumentCaptor.forClass(Account.class);
        order.verify(accounts).save(accountCaptor.capture());
        var account = accountCaptor.getValue();
        assertEquals(Account.Status.Pending, account.getStatus());
        assertEquals("Alice", account.getName());
        assertEquals("alice@example.com", account.getEmail());
        assertNotNull(account.getRegisteredAt());
        order.verify(invitations).enqueue(same(account));
    }
}
