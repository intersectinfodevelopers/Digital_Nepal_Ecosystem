package np.gov.digital.platformevidenceexchange.enums;

/**
 * PENDING -&gt; {RESPONDED | FAILED}; PENDING can also become EXPIRED (by
 * EvidenceRequestExpiryJob, never by the requester) if the target agency
 * never answers within the request's own expiresAt.
 */
public enum EvidenceRequestStatus {
    PENDING,
    RESPONDED,
    FAILED,
    EXPIRED
}
