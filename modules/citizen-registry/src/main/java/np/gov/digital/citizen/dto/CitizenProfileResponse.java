package np.gov.digital.citizen.dto;

import lombok.*;
import np.gov.digital.citizen.enums.DigitalLiteracy;

import java.time.Instant;
import java.util.UUID;

/**
 * Output DTO for GET /v1/citizens/{id} (single-profile view).
 *
 * Decrypts DOB/phone/email for an authorized viewer, but still never
 * returns the raw NID, and returns the citizenship number masked (last 4
 * characters only) rather than in full — a profile screen needs enough to
 * confirm identity at a glance, not the whole identifier. Fetch the full
 * citizenship number only through a purpose-built, separately-audited
 * verification flow if one is ever needed, not through this endpoint.
 */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class CitizenProfileResponse {
    private UUID citizenId;
    private UUID wardId;

    private String nameNp;
    private String nameEn;
    private String dob;
    private String sex;
    private String bloodGroup;
    private String religion;
    private String ethnicity;
    private String motherTongue;
    private String tole;

    /** Last 4 characters only — see class javadoc. */
    private String citizenshipNoMasked;

    private String phone;
    private String phoneAlt;
    private String email;

    private DigitalLiteracy digitalLiteracy;
    private Boolean hasSmartphone;
    private String photoUrl;

    private Boolean nidVerified;
    private Boolean isActive;
    private String syncStatus;
    private String registrationChannel;
    private Instant registeredAt;
}
