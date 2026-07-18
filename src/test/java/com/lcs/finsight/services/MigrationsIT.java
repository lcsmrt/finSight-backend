package com.lcs.finsight.services;

import static org.assertj.core.api.Assertions.assertThat;

import com.lcs.finsight.support.AbstractIntegrationTest;
import java.io.IOException;
import java.util.Arrays;
import java.util.Comparator;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import javax.sql.DataSource;
import org.flywaydb.core.Flyway;
import org.flywaydb.core.api.MigrationInfo;
import org.flywaydb.core.api.MigrationState;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.ApplicationContext;
import org.springframework.core.io.Resource;
import org.springframework.core.io.support.PathMatchingResourcePatternResolver;

/**
 * Proves the FULL Flyway chain (V1..latest) applies successfully on the
 * Testcontainers Postgres instance and that no drift exists between the
 * Flyway-built schema and the JPA entity model under {@code ddl-auto=validate}.
 *
 * <p>The container is a JVM-wide singleton (see {@code TestContainersConfig}), so
 * Flyway only ever runs its migrations once — the first time ANY {@code *IT}
 * boots a context against it — and that first run is always against a genuinely
 * empty database. {@code HarnessSmokeIT} already smoke-checks that run (zero
 * failures, at least one success). This test is independent of run order: it
 * inspects the resulting {@code flyway_schema_history}/{@link Flyway#info()}
 * state, whichever test happened to trigger the real migration, and asserts
 * something stronger — that the *exact* set of applied versions matches the
 * *exact* set of {@code V*.sql} files on disk, one-to-one, all in
 * {@link MigrationState#SUCCESS}. That catches a missing, skipped, or silently
 * renamed migration file, which HarnessSmokeIT's "at least one success" check
 * would not.
 */
class MigrationsIT extends AbstractIntegrationTest {

    private static final Pattern MIGRATION_FILE_PATTERN = Pattern.compile("V(\\d+(?:_\\d+)*)__.*\\.sql");

    @Autowired
    private Flyway flyway;

    @Autowired
    private ApplicationContext applicationContext;

    @Autowired
    private DataSource dataSource;

    @Test
    void contextLoadsUnderDdlAutoValidateWithNoEntitySchemaDrift() {
        // application-test.properties pins spring.jpa.hibernate.ddl-auto=validate.
        // If the Flyway-built schema had drifted from the JPA entity mappings
        // (missing column, wrong type, renamed table, ...), Hibernate would throw
        // a SchemaManagementException during context refresh and this whole class
        // would never reach a @Test method. Asserting on the context here makes
        // that guarantee explicit and first-class rather than an implicit side
        // effect of every other *IT extending AbstractIntegrationTest.
        assertThat(applicationContext).isNotNull();
        assertThat(dataSource).isNotNull();
    }

    @Test
    void fullMigrationChainAppliesCleanOnGenuinelyEmptyDatabase() throws IOException {
        List<String> versionsOnDisk = migrationVersionsOnDisk();
        assertThat(versionsOnDisk).as("sanity check: migration files must be discoverable on the classpath").isNotEmpty();

        MigrationInfo[] applied = flyway.info().applied();
        List<String> appliedVersions = Arrays.stream(applied)
                .map(MigrationInfo::getVersion)
                .filter(v -> v != null)
                .map(v -> v.getVersion())
                .collect(Collectors.toList());

        assertThat(appliedVersions)
                .as("every V*.sql file on disk must be applied, in order, with none missing/renamed/extra")
                .containsExactlyElementsOf(versionsOnDisk);

        assertThat(applied)
                .as("every applied migration must report state SUCCESS")
                .extracting(MigrationInfo::getState)
                .containsOnly(MigrationState.SUCCESS);
    }

    private List<String> migrationVersionsOnDisk() throws IOException {
        PathMatchingResourcePatternResolver resolver = new PathMatchingResourcePatternResolver();
        Resource[] resources = resolver.getResources("classpath*:db/migration/V*.sql");
        return Arrays.stream(resources)
                .map(Resource::getFilename)
                .map(MIGRATION_FILE_PATTERN::matcher)
                .filter(Matcher::matches)
                .map(m -> m.group(1).replace('_', '.'))
                .sorted(Comparator.comparingDouble(Double::parseDouble))
                .collect(Collectors.toList());
    }
}
