package np.gov.digital.platformsync.test.builder;


import np.gov.digital.citizen.entity.Citizen;
import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

class CitizenTestDataBuilderTest {

    @Test
    void shouldBuildCitizenWithDefaultValues() {

        Citizen citizen =
                CitizenTestDataBuilder
                        .aCitizen()
                        .build();

        assertNotNull(citizen);
        assertNotNull(citizen.getId());
        assertNotNull(citizen.getLocalRecordId());
        assertEquals(1, citizen.getVersionNumber());
    }

    @Test
    void shouldBuildCitizenWithCustomValues() {

        UUID id = UUID.randomUUID();
        UUID localRecordId = UUID.randomUUID();

        Citizen citizen =
                CitizenTestDataBuilder
                        .aCitizen()
                        .withId(id)
                        .withLocalRecordId(localRecordId)
                        .withVersionNumber(5)
                        .build();

        assertEquals(id, citizen.getId());
        assertEquals(localRecordId, citizen.getLocalRecordId());
        assertEquals(5, citizen.getVersionNumber());
    }
}
