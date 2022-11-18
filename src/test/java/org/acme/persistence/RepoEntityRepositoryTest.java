package org.acme.persistence;

import io.quarkus.test.TestTransaction;
import io.quarkus.test.junit.QuarkusTest;
import org.acme.model.*;
import org.acme.notification.NotificationScope;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import javax.inject.Inject;
import javax.persistence.EntityManager;
import java.util.List;

@QuarkusTest
class RepoEntityRepositoryTest {

    @Inject
    EntityManager entityManager;

    @Inject
    RepoEntityRepository repository;

    @Test
    @TestTransaction
    public void configProperty() {

        entityManager.createNativeQuery("""
                INSERT INTO "REPOSITORY" ("ID", "ENABLED", "IDENTIFIER", "INTERNAL", "PASSWORD", "RESOLUTION_ORDER", "TYPE", "URL") VALUES
                                    (1, 'true', 'central', 'false', 'null', 2, 'MAVEN', 'https://repo1.maven.org/maven2/');
                """).executeUpdate();
        entityManager.createNativeQuery("""
                INSERT INTO "REPOSITORY" ("ID", "ENABLED", "IDENTIFIER", "INTERNAL", "PASSWORD", "RESOLUTION_ORDER", "TYPE", "URL") VALUES
                                    (2, 'true', 'central2', 'false', 'null', 1, 'MAVEN', 'https://repo1.maven.org/maven2/123');
                """).executeUpdate();
        final List<Repository> config= repository
                .findRepositoryByRepositoryType(RepositoryType.MAVEN);
        Assertions.assertEquals(2, config.size());
        Assertions.assertEquals(1, config.get(0).getResolutionOrder());
        Assertions.assertEquals(2, config.get(1).getResolutionOrder());
    }



}