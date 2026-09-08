package np.gov.digital.platformidcard.repository;

import np.gov.digital.platformidcard.entity.OfficialDocument;
import np.gov.digital.platformidcard.enums.DocumentType;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface OfficialDocumentRepository extends JpaRepository<OfficialDocument, UUID> {

    /**
     * Verification lookup — QrCodeService's token carries (citizenId,
     * documentType, issuedDate), not this row's own id (see
     * OfficialDocumentService's Javadoc for why: the token format is
     * unchanged from what IdCardController already used for ID cards).
     * Ordered newest-first so a re-issued document's token — if an older
     * one somehow verifies against ambiguous matches — resolves to the
     * current, not a superseded, document.
     */
    List<OfficialDocument> findByCitizenIdAndDocumentTypeAndIssuedAtBetweenOrderByIssuedAtDesc(
            UUID citizenId, DocumentType documentType, Instant from, Instant to);

    Optional<OfficialDocument> findByVitalEventIdAndCitizen_Id(UUID vitalEventId, UUID citizenId);
}
