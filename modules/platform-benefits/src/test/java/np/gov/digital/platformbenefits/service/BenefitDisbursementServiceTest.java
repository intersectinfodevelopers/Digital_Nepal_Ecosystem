package np.gov.digital.platformbenefits.service;

import np.gov.digital.citizen.dto.EligibilityResponse;
import np.gov.digital.citizen.entity.Citizen;
import np.gov.digital.citizen.entity.Ward;
import np.gov.digital.citizen.enums.IdCardType;
import np.gov.digital.citizen.repository.CitizenRepository;
import np.gov.digital.citizen.service.EligibilityService;
import np.gov.digital.platformaudit.audit.AuditLogService;
import np.gov.digital.platformbenefits.dto.DisburseRequest;
import np.gov.digital.platformbenefits.dto.DisbursementCallbackRequest;
import np.gov.digital.platformbenefits.entity.BenefitDisbursement;
import np.gov.digital.platformbenefits.enums.BenefitType;
import np.gov.digital.platformbenefits.enums.PaymentRail;
import np.gov.digital.platformbenefits.enums.PaymentStatus;
import np.gov.digital.platformbenefits.exception.DuplicateDisbursementException;
import np.gov.digital.platformbenefits.exception.IneligibleCitizenException;
import np.gov.digital.platformbenefits.exception.InvalidPaymentTransitionException;
import np.gov.digital.platformbenefits.repository.BenefitDisbursementRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class BenefitDisbursementServiceTest {

    @Mock private BenefitDisbursementRepository benefitDisbursementRepository;
    @Mock private CitizenRepository citizenRepository;
    @Mock private EligibilityService eligibilityService;
    @Mock private AuditLogService auditLogService;

    private BenefitDisbursementService service;

    private UUID citizenId;
    private Citizen citizen;

    @BeforeEach
    void setUp() {
        service = new BenefitDisbursementService(
                benefitDisbursementRepository, citizenRepository, eligibilityService, auditLogService);

        citizenId = UUID.randomUUID();
        Ward ward = new Ward();
        ward.setWardNo(3);
        citizen = new Citizen();
        citizen.setId(citizenId);
        citizen.setWard(ward);
        citizen.setNameEn("Test Citizen");

        lenient().when(benefitDisbursementRepository.save(any(BenefitDisbursement.class)))
                .thenAnswer(inv -> inv.getArgument(0));
    }

    private DisburseRequest request() {
        return DisburseRequest.builder()
                .benefitType(BenefitType.DISABILITY)
                .period("2026-09")
                .amountNpr(new BigDecimal("3000.00"))
                .paymentRail(PaymentRail.BANK_TRANSFER)
                .build();
    }

    private EligibilityResponse eligibleResponse(boolean eligible) {
        return EligibilityResponse.builder()
                .citizenId(citizenId)
                .eligibleCards(List.of(EligibilityResponse.EligibilityResult.builder()
                        .cardType(IdCardType.DISABILITY)
                        .eligible(eligible)
                        .reason(eligible ? "Severity 3 with certificate" : null)
                        .ineligibilityReason(eligible ? null : "Not eligible")
                        .build()))
                .build();
    }

    @Test
    void disburse_eligibleCitizen_createsInitiatedDisbursement() {
        when(citizenRepository.findById(citizenId)).thenReturn(Optional.of(citizen));
        when(eligibilityService.evaluate(citizenId)).thenReturn(eligibleResponse(true));
        when(benefitDisbursementRepository.findByCitizen_IdAndBenefitTypeAndPeriodAndPaymentStatusNotIn(
                eq(citizenId), eq(BenefitType.DISABILITY), eq("2026-09"), anyList()))
                .thenReturn(List.of());

        var result = service.disburse(citizenId, request());

        assertThat(result.getPaymentStatus()).isEqualTo(PaymentStatus.INITIATED);
        assertThat(result.getExternalReference()).startsWith("DISB-");
        verify(auditLogService).log(any(), eq(citizenId), anyString());
    }

    @Test
    void disburse_ineligibleCitizen_throws() {
        when(citizenRepository.findById(citizenId)).thenReturn(Optional.of(citizen));
        when(eligibilityService.evaluate(citizenId)).thenReturn(eligibleResponse(false));

        assertThatThrownBy(() -> service.disburse(citizenId, request()))
                .isInstanceOf(IneligibleCitizenException.class);

        verify(benefitDisbursementRepository, never()).save(any());
    }

    @Test
    void disburse_alreadyHasActiveDisbursementForPeriod_throwsDuplicate() {
        when(citizenRepository.findById(citizenId)).thenReturn(Optional.of(citizen));
        when(eligibilityService.evaluate(citizenId)).thenReturn(eligibleResponse(true));
        BenefitDisbursement existing = BenefitDisbursement.builder()
                .id(UUID.randomUUID()).citizen(citizen).benefitType(BenefitType.DISABILITY)
                .period("2026-09").paymentStatus(PaymentStatus.INITIATED).build();
        when(benefitDisbursementRepository.findByCitizen_IdAndBenefitTypeAndPeriodAndPaymentStatusNotIn(
                eq(citizenId), eq(BenefitType.DISABILITY), eq("2026-09"), anyList()))
                .thenReturn(List.of(existing));

        assertThatThrownBy(() -> service.disburse(citizenId, request()))
                .isInstanceOf(DuplicateDisbursementException.class);
    }

    @Test
    void applyCallback_success_marksSettled() {
        UUID disbursementId = UUID.randomUUID();
        BenefitDisbursement disbursement = BenefitDisbursement.builder()
                .id(disbursementId).citizen(citizen).benefitType(BenefitType.DISABILITY)
                .period("2026-09").paymentStatus(PaymentStatus.INITIATED).build();
        when(benefitDisbursementRepository.findById(disbursementId)).thenReturn(Optional.of(disbursement));

        var result = service.applyCallback(disbursementId,
                DisbursementCallbackRequest.builder().success(true).build());

        assertThat(result.getPaymentStatus()).isEqualTo(PaymentStatus.SETTLED);
    }

    @Test
    void applyCallback_failure_marksFailedWithReason() {
        UUID disbursementId = UUID.randomUUID();
        BenefitDisbursement disbursement = BenefitDisbursement.builder()
                .id(disbursementId).citizen(citizen).benefitType(BenefitType.DISABILITY)
                .period("2026-09").paymentStatus(PaymentStatus.INITIATED).build();
        when(benefitDisbursementRepository.findById(disbursementId)).thenReturn(Optional.of(disbursement));

        var result = service.applyCallback(disbursementId,
                DisbursementCallbackRequest.builder().success(false).failureReason("Insufficient funds").build());

        assertThat(result.getPaymentStatus()).isEqualTo(PaymentStatus.FAILED);
        assertThat(result.getFailureReason()).isEqualTo("Insufficient funds");
    }

    @Test
    void applyCallback_onAlreadySettled_throwsInvalidTransition() {
        UUID disbursementId = UUID.randomUUID();
        BenefitDisbursement disbursement = BenefitDisbursement.builder()
                .id(disbursementId).citizen(citizen).benefitType(BenefitType.DISABILITY)
                .period("2026-09").paymentStatus(PaymentStatus.SETTLED).build();
        when(benefitDisbursementRepository.findById(disbursementId)).thenReturn(Optional.of(disbursement));

        assertThatThrownBy(() -> service.applyCallback(disbursementId,
                DisbursementCallbackRequest.builder().success(true).build()))
                .isInstanceOf(InvalidPaymentTransitionException.class);
    }
}
