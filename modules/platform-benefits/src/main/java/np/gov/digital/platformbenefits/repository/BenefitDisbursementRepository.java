package np.gov.digital.platformbenefits.repository;

import np.gov.digital.platformbenefits.entity.BenefitDisbursement;
import np.gov.digital.platformbenefits.enums.BenefitType;
import np.gov.digital.platformbenefits.enums.PaymentStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface BenefitDisbursementRepository extends JpaRepository<BenefitDisbursement, UUID> {

    /**
     * ERR_DUPLICATE_DISBURSEMENT check — mirrors
     * uq_benefit_disbursement_active_period (V39): is there already a
     * non-failed/cancelled disbursement for this citizen/benefit/period?
     * Checked in the service before insert, same as the DB constraint,
     * so a duplicate surfaces as a clean 409 rather than a raw
     * constraint-violation 500.
     */
    List<BenefitDisbursement> findByCitizen_IdAndBenefitTypeAndPeriodAndPaymentStatusNotIn(
            UUID citizenId, BenefitType benefitType, String period, List<PaymentStatus> excludedStatuses);

    Page<BenefitDisbursement> findByCitizen_Id(UUID citizenId, Pageable pageable);

    Optional<BenefitDisbursement> findByExternalReference(String externalReference);
}
