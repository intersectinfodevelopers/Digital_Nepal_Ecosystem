package np.gov.digital.citizen.enums;

/**
 * How far a citizen's identity has progressed — the "progressive digital
 * identity" model (Concept Document §4): a registry entry exists at birth,
 * long before any physical document, and identity accumulates over a
 * lifetime rather than being all-or-nothing.
 */
public enum RegistrationStage {
    /** Registered at birth; no NID/citizenship document exists yet. */
    BIRTH_REGISTERED,
    /** Turned 16 with no document on file — citizenship application reminder fired. */
    CITIZENSHIP_PENDING,
    /** Citizenship certificate and/or National ID recorded and hash-verified. */
    DOCUMENT_REGISTERED
}
