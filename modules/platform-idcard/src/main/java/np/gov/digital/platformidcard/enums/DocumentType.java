package np.gov.digital.platformidcard.enums;

/**
 * Every kind of official_document (V38, SDD Extended Modules §4.7) — ID
 * cards and vital-event certificates share one table and one PDF+QR
 * issuance pipeline. Card types match citizen-registry's existing
 * IdCardType (DISABILITY/UNEMPLOYMENT only — SENIOR/SINGLE_WOMAN/FARMER
 * are that enum's own Phase 2 placeholders, not issuable yet).
 */
public enum DocumentType {
    DISABILITY_CARD,
    UNEMPLOYMENT_CARD,
    BIRTH_CERTIFICATE,
    DEATH_CERTIFICATE,
    MARRIAGE_CERTIFICATE,
    DIVORCE_CERTIFICATE;

    public boolean isCertificate() {
        return this != DISABILITY_CARD && this != UNEMPLOYMENT_CARD;
    }
}
