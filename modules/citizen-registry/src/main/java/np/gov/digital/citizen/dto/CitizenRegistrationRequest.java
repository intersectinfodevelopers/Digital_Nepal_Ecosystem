package np.gov.digital.citizen.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.*;
import np.gov.digital.citizen.enums.ConsentChannel;
import np.gov.digital.citizen.enums.DigitalLiteracy;
import java.util.List;
import java.util.UUID;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class CitizenRegistrationRequest {
    // GEOGRAPHIC SCOPE
    @NotNull(message = "Ward ID is required")
    private UUID wardId;

    // IDENTITY
    @NotBlank(message = "NID is required")
    private String nid;

    @NotBlank(message = "Citizenship number is required")
    private String citizenshipNo;

    private String passportNo;

    // NAME
    @NotBlank(message = "Nepali name is required")
    @Size(max = 300, message = "Nepali name must not exceed 300 characters")
    private String nameNp;

    @NotBlank(message = "English name is required")
    @Size(max = 300, message = "English name must not exceed 300 characters")
    private String nameEn;

    // BASIC DEMOGRAPHICS
    @NotBlank(message = "Date of birth is required")
    @Pattern(regexp = "\\d{4}-\\d{2}-\\d{2}", message = "Date of birth must be in YYYY-MM-DD format")
    private String dob;

    @NotBlank(message = "Sex is required")
    @Pattern(regexp = "MALE|FEMALE|OTHER", message = "Sex must be MALE, FEMALE, or OTHER")
    private String sex;

    private String bloodGroup;
    private String religion;
    private String ethnicity;
    private String motherTongue;
    private String tole;

    // CONTACT
    private String phone;
    private String phoneAlt;
    private String email;

    // DIGITAL PROFILE
    private DigitalLiteracy digitalLiteracy;
    private Boolean hasSmartphone;
    private String photoUrl;

    // CONSENT — required by Individual Privacy Act 2018
    @NotNull(message = "Consent channel is required")
    private ConsentChannel consentChannel;

    private String registrationChannel;

    // OFFLINE SYNC (Flutter mobile app)
    private UUID localRecordId;

    private String deviceId;

    private GpsDto gps;

    // FAMILY LINKS — normalized citizenship numbers of relatives, resolved
    // by CitizenService.buildFamilyMap() into FamilyLinkService links.
    // A relative who isn't registered yet gets a PENDING link that resolves
    // automatically once they register (see FamilyLinkService.resolvePendingLinks).
    private String fatherCitizenshipNo;
    private String motherCitizenshipNo;
    private String spouseCitizenshipNo;
    private List<String> childrenCitizenshipNos;
}
