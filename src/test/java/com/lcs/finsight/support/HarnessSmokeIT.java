package com.lcs.finsight.support;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.lcs.finsight.models.User;
import com.lcs.finsight.utils.ApiRoutes;
import javax.sql.DataSource;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;

/**
 * Proves T1-T5 end to end: no .env/tunnel, a real ephemeral Postgres, the full
 * Flyway chain, ddl-auto=validate, and an authenticated MockMvc round-trip.
 */
class HarnessSmokeIT extends AbstractIntegrationTest {

    @Autowired
    private DataSource dataSource;

    @Test
    void bootsAgainstTestcontainersAndAppliesFlywayChain() {
        JdbcTemplate jdbcTemplate = new JdbcTemplate(dataSource);
        Integer failedMigrations = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM flyway_schema_history WHERE success = false", Integer.class);
        Integer appliedMigrations = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM flyway_schema_history WHERE success = true", Integer.class);

        assertThat(failedMigrations).isZero();
        assertThat(appliedMigrations).isGreaterThan(0);
    }

    @Test
    void authenticatedRequestSucceedsAndUnauthenticatedIsRejected() throws Exception {
        User user = fixtures.aUser();

        mockMvc.perform(get(ApiRoutes.PLAN).with(testAuthHelper.asUser(user)))
                .andExpect(status().is2xxSuccessful());

        mockMvc.perform(get(ApiRoutes.PLAN))
                .andExpect(status().is4xxClientError());
    }

    @Test
    void truncateAllIsolatesStateBetweenTests() {
        long userCountBeforeAnyFixtureInThisTest = new JdbcTemplate(dataSource)
                .queryForObject("SELECT COUNT(*) FROM users", Long.class);

        assertThat(userCountBeforeAnyFixtureInThisTest)
                .as("truncateAll must have cleared users left over from the previous test method")
                .isZero();
    }
}
