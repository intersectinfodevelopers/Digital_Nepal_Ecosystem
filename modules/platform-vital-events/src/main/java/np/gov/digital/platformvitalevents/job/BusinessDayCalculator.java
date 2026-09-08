package np.gov.digital.platformvitalevents.job;

import java.time.DayOfWeek;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;

/**
 * Business-day counting for Governance Tiers §6's auto-escalation rule
 * ("a ward submission un-acted-on for 5 business days moves
 * PENDING_APPROVAL -&gt; CAO_REVIEW automatically"). Nepal's work week runs
 * Sunday through Friday with Saturday as the sole weekly holiday — NOT
 * the Saturday+Sunday weekend a generic "business days" implementation
 * would assume. Public holidays beyond the weekly Saturday are
 * deliberately not modelled here (there is no holiday calendar anywhere
 * in this codebase yet); this counts only Saturdays as non-business
 * days.
 */
public final class BusinessDayCalculator {

    private BusinessDayCalculator() {}

    /**
     * Number of business days (non-Saturdays) strictly between
     * {@code from} (exclusive) and {@code to} (inclusive), both
     * interpreted as UTC calendar dates.
     */
    public static int businessDaysElapsed(Instant from, Instant to) {
        LocalDate start = LocalDate.ofInstant(from, ZoneOffset.UTC);
        LocalDate end = LocalDate.ofInstant(to, ZoneOffset.UTC);

        int count = 0;
        LocalDate cursor = start;
        while (cursor.isBefore(end)) {
            cursor = cursor.plusDays(1);
            if (cursor.getDayOfWeek() != DayOfWeek.SATURDAY) {
                count++;
            }
        }
        return count;
    }

    public static boolean hasElapsed(Instant from, Instant to, int businessDays) {
        return businessDaysElapsed(from, to) >= businessDays;
    }
}
