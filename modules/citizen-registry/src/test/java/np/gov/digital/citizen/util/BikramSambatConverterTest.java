package np.gov.digital.citizen.util;

import org.junit.jupiter.api.Test;

import java.time.LocalDate;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class BikramSambatConverterTest {

    private final BikramSambatConverter converter = new BikramSambatConverter();

    @Test
    void epochDateRoundTrips() {
        // 1 Baishakh 1970 B.S. = 13 April 1913 A.D. — the library's own epoch.
        assertEquals(LocalDate.of(1913, 4, 13), converter.toAd(1970, 1, 1));
    }

    @Test
    void matchesPubliclyKnownNepaliNewYearDates() {
        // These are independently verifiable public facts (Nepali New Year
        // is 1 Baishakh every year) used here as a sanity check on the
        // embedded calendar table, not just the arithmetic around it.
        assertEquals(LocalDate.of(2024, 4, 13), converter.toAd(2081, 1, 1));
        assertEquals(LocalDate.of(2025, 4, 14), converter.toAd(2082, 1, 1));
        assertEquals(LocalDate.of(2026, 4, 14), converter.toAd(2083, 1, 1));
    }

    @Test
    void toBsIsTheInverseOfToAd() {
        LocalDate ad = converter.toAd(2054, 5, 12);
        int[] bs = converter.toBs(ad);
        assertEquals(2054, bs[0]);
        assertEquals(5, bs[1]);
        assertEquals(12, bs[2]);
    }

    @Test
    void rejectsAYearOutsideTheSupportedTable() {
        assertThrows(IllegalArgumentException.class, () -> converter.toAd(1969, 1, 1));
        assertThrows(IllegalArgumentException.class, () -> converter.toAd(2091, 1, 1));
    }

    @Test
    void rejectsADayThatDoesNotExistInAGivenBsMonth() {
        // B.S. 2054, month 1 (Baishakh) has 31 days per the embedded table.
        assertThrows(IllegalArgumentException.class, () -> converter.toAd(2054, 1, 32));
    }
}
