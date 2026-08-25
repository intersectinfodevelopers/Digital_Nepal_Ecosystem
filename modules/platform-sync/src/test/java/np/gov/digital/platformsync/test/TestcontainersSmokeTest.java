package np.gov.digital.platformsync.test;



import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertTrue;

class TestcontainersSmokeTest extends TestcontainersConfiguration {

    @Test
    void containersShouldBeRunning() {

        assertTrue(
                POSTGRES.isRunning(),
                "PostgreSQL container is not running"
        );

        assertTrue(
                REDIS.isRunning(),
                "Redis container is not running"
        );
    }
}
