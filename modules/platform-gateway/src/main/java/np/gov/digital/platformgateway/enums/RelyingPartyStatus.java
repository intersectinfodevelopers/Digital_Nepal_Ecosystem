package np.gov.digital.platformgateway.enums;

/**
 * ACTIVE -&gt; {SUSPENDED | REVOKED}; SUSPENDED -&gt; {ACTIVE | REVOKED} (a
 * suspension can be lifted, e.g. after a lapsed certificate is renewed;
 * a revocation cannot). REVOKED is terminal — the graduated penalty
 * ladder's final rung (Extended Modules §6.5).
 */
public enum RelyingPartyStatus {
    ACTIVE,
    SUSPENDED,
    REVOKED
}
