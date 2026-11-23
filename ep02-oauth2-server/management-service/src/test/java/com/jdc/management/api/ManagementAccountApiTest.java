package com.jdc.management.api;

import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import com.jdc.management.exceptions.handler.ExceptionHandlers;
import com.jdc.management.model.entity.Account;
import com.jdc.management.model.repo.AccountRepo;
import com.jdc.management.service.AccountManagementService;
import com.jdc.management.model.query.AccountQuery;
import com.jdc.management.service.AuthEmailCheckService;
import com.jdc.management.service.AccountCreationService;

class ManagementAccountApiTest {
    private AccountRepo accounts;
    private AuthEmailCheckService emailCheck;
    private AccountCreationService accountCreation;
    private MockMvc mvc;
    private static final String FORM = """
            {"name":"Alice","phone":"09123456789","email":"alice@example.com"}
            """;

    @BeforeEach
    void setup() {
        accounts = mock(AccountRepo.class);
        emailCheck = mock(AuthEmailCheckService.class);
        accountCreation = mock(AccountCreationService.class);
        var service = new AccountManagementService(accounts, emailCheck, new AccountQuery(), accountCreation);
        var builder = MockMvcBuilders.standaloneSetup(new ManagementAccountApi(service));
        builder.setControllerAdvice(new ExceptionHandlers());
        mvc = builder.build();
    }

    @Test
    void startsAccountCreationAfterEmailCheck() throws Exception {
        mvc.perform(post("/api/accounts").contentType(MediaType.APPLICATION_JSON).content(FORM))
                .andExpect(status().isOk()).andExpect(content().string(""));
        var order = inOrder(emailCheck, accountCreation);
        order.verify(emailCheck).checkAvailable("alice@example.com");
        order.verify(accountCreation).create(argThat(form -> form.name().equals("Alice")
                && form.phone().equals("09123456789") && form.email().equals("alice@example.com")));
    }

    @Test
    void rejectsDuplicatesAndInvalidBodies() throws Exception {
        when(accounts.existsByEmail("alice@example.com")).thenReturn(true);
        mvc.perform(post("/api/accounts").contentType(MediaType.APPLICATION_JSON).content(FORM))
                .andExpect(status().isBadRequest());
        mvc.perform(post("/api/accounts").contentType(MediaType.APPLICATION_JSON).content("{}"))
                .andExpect(status().isBadRequest());
        verifyNoInteractions(emailCheck);
        verifyNoInteractions(accountCreation);
        verify(accounts, never()).save(any());
    }

    @Test
    void updatePreservesIdentityAndLifecycle() throws Exception {
        var id = UUID.randomUUID();
        var registered = Instant.parse("2026-11-01T00:00:00Z");
        var account = new Account();
        account.setId(id);
        account.setEmail("alice@example.com");
        account.setStatus(Account.Status.Activated);
        account.setRegisteredAt(registered);
        account.setActivatedAt(registered);
        when(accounts.findById(id)).thenReturn(Optional.of(account));
        mvc.perform(put("/api/accounts/" + id).contentType(MediaType.APPLICATION_JSON).content(FORM))
                .andExpect(status().isOk());
        verify(accounts).save(argThat(saved -> saved.getName().equals("Alice")
                && saved.getStatus() == Account.Status.Activated && registered.equals(saved.getActivatedAt())
                && registered.equals(saved.getRegisteredAt())));
        mvc.perform(put("/api/accounts/" + id).contentType(MediaType.APPLICATION_JSON)
                .content(FORM.replace("alice@example.com", "other@example.com")))
                .andExpect(status().isBadRequest());
    }

    @Test
    void rejectsMissingAccountAndInvalidSearch() throws Exception {
        mvc.perform(put("/api/accounts/" + UUID.randomUUID()).contentType(MediaType.APPLICATION_JSON).content(FORM))
                .andExpect(status().isNotFound());
        mvc.perform(get("/api/accounts?page=-1")).andExpect(status().isBadRequest());
        mvc.perform(get("/api/accounts?createdFrom=2026-11-02&createdTo=2026-11-01"))
                .andExpect(status().isBadRequest());
        mvc.perform(get("/api/accounts?status=Unknown")).andExpect(status().isBadRequest());
    }
}
