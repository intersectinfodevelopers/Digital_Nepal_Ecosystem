package np.gov.digital.platformsync.test.builder;



import np.gov.digital.citizen.entity.Citizen;

import java.util.UUID;

public class CitizenTestDataBuilder {

    private UUID id = UUID.randomUUID();
    private UUID localRecordId = UUID.randomUUID();
    private Integer versionNumber = 1;

    private CitizenTestDataBuilder() {
    }

    public static CitizenTestDataBuilder aCitizen() {
        return new CitizenTestDataBuilder();
    }

    public CitizenTestDataBuilder withId(UUID id) {
        this.id = id;
        return this;
    }

    public CitizenTestDataBuilder withLocalRecordId(UUID localRecordId) {
        this.localRecordId = localRecordId;
        return this;
    }

    public CitizenTestDataBuilder withVersionNumber(Integer versionNumber) {
        this.versionNumber = versionNumber;
        return this;
    }

    public Citizen build() {

        Citizen citizen = new Citizen();

        citizen.setId(id);
        citizen.setLocalRecordId(localRecordId);
        citizen.setVersionNumber(versionNumber);

        return citizen;
    }
}
