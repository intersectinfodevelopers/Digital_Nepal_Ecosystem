package np.gov.digital.platformvitalevents.job;

import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.time.ZoneOffset;

import static org.assertj.core.api.Assertions.*;

/**
 * Nepal's work week is Sunday-Friday with Saturday as the sole weekly
 * holiday — these tests deliberately span a Saturday to prove that day,
 * and only that day, is excluded.
 */
class BusinessDayCalculatorTest {

    // 2026-09-06 is a Sunday, 2026-09-12 is a Saturday (verified against
    // the proleptic Gregorian calendar Java uses).
    private static final LocalDate SUNDAY = LocalDate.of(2026, 9, 6);

    @Test
    void fiveConsecutiveNonSaturdayDaysCountAsFiveBusinessDays() {
        // Sun -> Fri is 5 calendar days, no Saturday in between.
        LocalDate from = SUNDAY;
        LocalDate to = from.plusDays(5); // Friday
        int businessDays = BusinessDayCalculator.businessDaysElapsed(
                from.atStartOfDay(ZoneOffset.UTC).toInstant(),
                to.atStartOfDay(ZoneOffset.UTC).toInstant());
        assertThat(businessDays).isEqualTo(5);
    }

    @Test
    void saturdayIsExcludedFromTheCount() {
        // Fri -> Sun spans exactly one Saturday: 2 calendar days, 1 business day.
        LocalDate friday = SUNDAY.plusDays(5);
        LocalDate nextSunday = friday.plusDays(2);
        int businessDays = BusinessDayCalculator.businessDaysElapsed(
                friday.atStartOfDay(ZoneOffset.UTC).toInstant(),
                nextSunday.atStartOfDay(ZoneOffset.UTC).toInstant());
        assertThat(businessDays).isEqualTo(1);
    }

    @Test
    void hasElapsed_trueOnceThresholdReached() {
        LocalDate from = SUNDAY;
        LocalDate to = from.plusDays(5); // 5 business days, no Saturday crossed
        boolean elapsed = BusinessDayCalculator.hasElapsed(
                from.atStartOfDay(ZoneOffset.UTC).toInstant(),
                to.atStartOfDay(ZoneOffset.UTC).toInstant(),
                5);
        assertThat(elapsed).isTrue();
    }

    @Test
    void hasElapsed_falseBeforeThreshold() {
        LocalDate from = SUNDAY;
        LocalDate to = from.plusDays(4); // only 4 business days
        boolean elapsed = BusinessDayCalculator.hasElapsed(
                from.atStartOfDay(ZoneOffset.UTC).toInstant(),
                to.atStartOfDay(ZoneOffset.UTC).toInstant(),
                5);
        assertThat(elapsed).isFalse();
    }

    @Test
    void sameInstantIsZeroBusinessDaysElapsed() {
        var instant = SUNDAY.atStartOfDay(ZoneOffset.UTC).toInstant();
        assertThat(BusinessDayCalculator.businessDaysElapsed(instant, instant)).isZero();
    }
}
