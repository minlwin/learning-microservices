package com.jdc.management;

import static org.junit.jupiter.api.Assertions.*;

import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;

import javax.sql.DataSource;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.ApplicationContext;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.transaction.PlatformTransactionManager;

import com.jdc.management.api.input.AccountRegistrationForm;
import com.jdc.management.model.entity.Account;
import com.jdc.management.model.query.AccountQuery;
import com.jdc.management.model.repo.AccountRepo;
import com.jdc.management.model.repo.PinSettingRepo;
import com.jdc.management.props.AuthenticationProps;
import com.jdc.management.props.ClientSystemProps;
import com.jdc.management.service.AccountManagementService;
import com.jdc.management.service.AuthEmailCheckService;

@SpringBootTest
@Import(ApplicationConfigurationTest.DatabaseConfig.class)
class ApplicationConfigurationTest {
    @Autowired
    private ApplicationContext context;
    @Autowired
    private AccountRepo accounts;
    @Autowired
    private AccountManagementService service;

    @TestConfiguration(proxyBeanMethods = false)
    static class DatabaseConfig {
        @Bean
        DataSource dataSource() throws Exception {
            var source = new DriverManagerDataSource(
                    "jdbc:h2:mem:management_application;MODE=PostgreSQL;DB_CLOSE_DELAY=-1", "sa", "");
            var jdbc = new JdbcTemplate(source);
            jdbc.execute("CREATE SCHEMA management");
            var script = Path.of("postgres/04-management-schema.sql");
            if (!Files.exists(script)) {
                script = Path.of("../postgres/04-management-schema.sql");
            }
            var sql = Files.readString(script);
            jdbc.execute(sql.replace("SET LOCAL ROLE mnguser;", ""));
            return source;
        }
    }

    @Test
    void wiresApplicationPropertiesSecurityRepositoriesAndKafka() {
        assertNotNull(context.getBean(SecurityFilterChain.class));
        assertNotNull(context.getBean(PlatformTransactionManager.class));
        assertNotNull(context.getBean(KafkaTemplate.class));
        assertNotNull(context.getBean(PinSettingRepo.class));
        assertNotNull(context.getBean(AccountQuery.class));
        assertNotNull(context.getBean(AuthEmailCheckService.class));
        var auth = context.getBean(AuthenticationProps.class);
        assertFalse(auth.getAudiences().isEmpty());
        assertFalse(auth.getEmailCheckUri().isBlank());
        assertFalse(context.getBean(ClientSystemProps.class).getSystems().isEmpty());
    }

    @Test
    void persistsServiceUpdatesUsingConfiguredTransactions() {
        var account = new Account();
        account.setName("Before");
        account.setPhone("09111111111");
        account.setEmail("configuration-test@example.com");
        account.setStatus(Account.Status.Pending);
        account.setRegisteredAt(Instant.now());
        var saved = accounts.saveAndFlush(account);
        service.update(saved.getId(), new AccountRegistrationForm(
                "After", "09222222222", saved.getEmail()));
        assertEquals("After", accounts.findById(saved.getId()).orElseThrow().getName());
        accounts.deleteById(saved.getId());
    }
}
