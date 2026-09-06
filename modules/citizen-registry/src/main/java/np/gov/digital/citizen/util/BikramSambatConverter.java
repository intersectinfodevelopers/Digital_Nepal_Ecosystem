package np.gov.digital.citizen.util;

import org.springframework.stereotype.Component;

import java.time.LocalDate;
import java.time.temporal.ChronoUnit;

/**
 * Converts between the Bikram Sambat (B.S.) calendar — the calendar used on
 * every Nepali citizenship certificate and national ID — and the Gregorian
 * (A.D.) calendar the rest of this system stores dates in.
 *
 * B.S. has no fixed month-length formula (unlike A.D. leap years, which
 * follow a simple rule); each month's length is fixed by the Nepal
 * government's Nepal Panchang and only exists as a lookup table. The table
 * below is ported from Medic Mobile's `bikram-sambat` library
 * (https://github.com/medic/bikram-sambat, Apache-2.0,
 * test-data/daysInMonth.json), which is used in production health record
 * systems across Nepal. It covers B.S. 1970–2090 — 13 April 1913 to
 * 13 April 2034 A.D. — which comfortably spans the lifetime of anyone who
 * could plausibly be registering as a citizen today.
 *
 * Epoch: 1 Baishakh 1970 B.S. = 13 April 1913 A.D.
 */
@Component
public class BikramSambatConverter {

    private static final int EPOCH_BS_YEAR = 1970;
    private static final LocalDate EPOCH_AD_DATE = LocalDate.of(1913, 4, 13);

    // Row index 0 = B.S. 1970. Twelve values per row = days in Baishakh
    // through Chaitra for that B.S. year. See class javadoc for source.
    private static final int[][] DAYS_IN_MONTH = {
        {31, 31, 32, 31, 31, 31, 30, 29, 30, 29, 30, 30}, // 1970
        {31, 31, 32, 31, 32, 30, 30, 29, 30, 29, 30, 30}, // 1971
        {31, 32, 31, 32, 31, 30, 30, 30, 29, 29, 30, 31}, // 1972
        {30, 32, 31, 32, 31, 30, 30, 30, 29, 30, 29, 31}, // 1973
        {31, 31, 32, 31, 31, 31, 30, 29, 30, 29, 30, 30}, // 1974
        {31, 31, 32, 32, 30, 31, 30, 29, 30, 29, 30, 30}, // 1975
        {31, 32, 31, 32, 31, 30, 30, 30, 29, 29, 30, 31}, // 1976
        {30, 32, 31, 32, 31, 30, 30, 30, 29, 30, 29, 31}, // 1977
        {31, 31, 32, 31, 31, 31, 30, 29, 30, 29, 30, 30}, // 1978
        {31, 31, 32, 32, 31, 30, 30, 29, 30, 29, 30, 30}, // 1979
        {31, 32, 31, 32, 31, 30, 30, 30, 29, 29, 30, 31}, // 1980
        {31, 31, 31, 32, 31, 31, 29, 30, 30, 29, 30, 30}, // 1981
        {31, 31, 32, 31, 31, 31, 30, 29, 30, 29, 30, 30}, // 1982
        {31, 31, 32, 32, 31, 30, 30, 29, 30, 29, 30, 30}, // 1983
        {31, 32, 31, 32, 31, 30, 30, 30, 29, 29, 30, 31}, // 1984
        {31, 31, 31, 32, 31, 31, 29, 30, 30, 29, 30, 30}, // 1985
        {31, 31, 32, 31, 31, 31, 30, 29, 30, 29, 30, 30}, // 1986
        {31, 32, 31, 32, 31, 30, 30, 29, 30, 29, 30, 30}, // 1987
        {31, 32, 31, 32, 31, 30, 30, 30, 29, 29, 30, 31}, // 1988
        {31, 31, 31, 32, 31, 31, 30, 29, 30, 29, 30, 30}, // 1989
        {31, 31, 32, 31, 31, 31, 30, 29, 30, 29, 30, 30}, // 1990
        {31, 32, 31, 32, 31, 30, 30, 29, 30, 29, 30, 30}, // 1991
        {31, 32, 31, 32, 31, 30, 30, 30, 29, 30, 29, 31}, // 1992
        {31, 31, 31, 32, 31, 31, 30, 29, 30, 29, 30, 30}, // 1993
        {31, 31, 32, 31, 31, 31, 30, 29, 30, 29, 30, 30}, // 1994
        {31, 32, 31, 32, 31, 30, 30, 30, 29, 29, 30, 30}, // 1995
        {31, 32, 31, 32, 31, 30, 30, 30, 29, 30, 29, 31}, // 1996
        {31, 31, 32, 31, 31, 31, 30, 29, 30, 29, 30, 30}, // 1997
        {31, 31, 32, 31, 31, 31, 30, 29, 30, 29, 30, 30}, // 1998
        {31, 32, 31, 32, 31, 30, 30, 30, 29, 29, 30, 31}, // 1999
        {30, 32, 31, 32, 31, 30, 30, 30, 29, 30, 29, 31}, // 2000
        {31, 31, 32, 31, 31, 31, 30, 29, 30, 29, 30, 30}, // 2001
        {31, 31, 32, 32, 31, 30, 30, 29, 30, 29, 30, 30}, // 2002
        {31, 32, 31, 32, 31, 30, 30, 30, 29, 29, 30, 31}, // 2003
        {30, 32, 31, 32, 31, 30, 30, 30, 29, 30, 29, 31}, // 2004
        {31, 31, 32, 31, 31, 31, 30, 29, 30, 29, 30, 30}, // 2005
        {31, 31, 32, 32, 31, 30, 30, 29, 30, 29, 30, 30}, // 2006
        {31, 32, 31, 32, 31, 30, 30, 30, 29, 29, 30, 31}, // 2007
        {31, 31, 31, 32, 31, 31, 29, 30, 30, 29, 29, 31}, // 2008
        {31, 31, 32, 31, 31, 31, 30, 29, 30, 29, 30, 30}, // 2009
        {31, 31, 32, 32, 31, 30, 30, 29, 30, 29, 30, 30}, // 2010
        {31, 32, 31, 32, 31, 30, 30, 30, 29, 29, 30, 31}, // 2011
        {31, 31, 31, 32, 31, 31, 29, 30, 30, 29, 30, 30}, // 2012
        {31, 31, 32, 31, 31, 31, 30, 29, 30, 29, 30, 30}, // 2013
        {31, 31, 32, 32, 31, 30, 30, 29, 30, 29, 30, 30}, // 2014
        {31, 32, 31, 32, 31, 30, 30, 30, 29, 29, 30, 31}, // 2015
        {31, 31, 31, 32, 31, 31, 29, 30, 30, 29, 30, 30}, // 2016
        {31, 31, 32, 31, 31, 31, 30, 29, 30, 29, 30, 30}, // 2017
        {31, 32, 31, 32, 31, 30, 30, 29, 30, 29, 30, 30}, // 2018
        {31, 32, 31, 32, 31, 30, 30, 30, 29, 30, 29, 31}, // 2019
        {31, 31, 31, 32, 31, 31, 30, 29, 30, 29, 30, 30}, // 2020
        {31, 31, 32, 31, 31, 31, 30, 29, 30, 29, 30, 30}, // 2021
        {31, 32, 31, 32, 31, 30, 30, 30, 29, 29, 30, 30}, // 2022
        {31, 32, 31, 32, 31, 30, 30, 30, 29, 30, 29, 31}, // 2023
        {31, 31, 31, 32, 31, 31, 30, 29, 30, 29, 30, 30}, // 2024
        {31, 31, 32, 31, 31, 31, 30, 29, 30, 29, 30, 30}, // 2025
        {31, 32, 31, 32, 31, 30, 30, 30, 29, 29, 30, 31}, // 2026
        {30, 32, 31, 32, 31, 30, 30, 30, 29, 30, 29, 31}, // 2027
        {31, 31, 32, 31, 31, 31, 30, 29, 30, 29, 30, 30}, // 2028
        {31, 31, 32, 31, 32, 30, 30, 29, 30, 29, 30, 30}, // 2029
        {31, 32, 31, 32, 31, 30, 30, 30, 29, 29, 30, 31}, // 2030
        {30, 32, 31, 32, 31, 30, 30, 30, 29, 30, 29, 31}, // 2031
        {31, 31, 32, 31, 31, 31, 30, 29, 30, 29, 30, 30}, // 2032
        {31, 31, 32, 32, 31, 30, 30, 29, 30, 29, 30, 30}, // 2033
        {31, 32, 31, 32, 31, 30, 30, 30, 29, 29, 30, 31}, // 2034
        {30, 32, 31, 32, 31, 31, 29, 30, 30, 29, 29, 31}, // 2035
        {31, 31, 32, 31, 31, 31, 30, 29, 30, 29, 30, 30}, // 2036
        {31, 31, 32, 32, 31, 30, 30, 29, 30, 29, 30, 30}, // 2037
        {31, 32, 31, 32, 31, 30, 30, 30, 29, 29, 30, 31}, // 2038
        {31, 31, 31, 32, 31, 31, 29, 30, 30, 29, 30, 30}, // 2039
        {31, 31, 32, 31, 31, 31, 30, 29, 30, 29, 30, 30}, // 2040
        {31, 31, 32, 32, 31, 30, 30, 29, 30, 29, 30, 30}, // 2041
        {31, 32, 31, 32, 31, 30, 30, 30, 29, 29, 30, 31}, // 2042
        {31, 31, 31, 32, 31, 31, 29, 30, 30, 29, 30, 30}, // 2043
        {31, 31, 32, 31, 31, 31, 30, 29, 30, 29, 30, 30}, // 2044
        {31, 32, 31, 32, 31, 30, 30, 29, 30, 29, 30, 30}, // 2045
        {31, 32, 31, 32, 31, 30, 30, 30, 29, 29, 30, 31}, // 2046
        {31, 31, 31, 32, 31, 31, 30, 29, 30, 29, 30, 30}, // 2047
        {31, 31, 32, 31, 31, 31, 30, 29, 30, 29, 30, 30}, // 2048
        {31, 32, 31, 32, 31, 30, 30, 30, 29, 29, 30, 30}, // 2049
        {31, 32, 31, 32, 31, 30, 30, 30, 29, 30, 29, 31}, // 2050
        {31, 31, 31, 32, 31, 31, 30, 29, 30, 29, 30, 30}, // 2051
        {31, 31, 32, 31, 31, 31, 30, 29, 30, 29, 30, 30}, // 2052
        {31, 32, 31, 32, 31, 30, 30, 30, 29, 29, 30, 30}, // 2053
        {31, 32, 31, 32, 31, 30, 30, 30, 29, 30, 29, 31}, // 2054
        {31, 31, 32, 31, 31, 31, 30, 29, 30, 29, 30, 30}, // 2055
        {31, 31, 32, 31, 32, 30, 30, 29, 30, 29, 30, 30}, // 2056
        {31, 32, 31, 32, 31, 30, 30, 30, 29, 29, 30, 31}, // 2057
        {30, 32, 31, 32, 31, 30, 30, 30, 29, 30, 29, 31}, // 2058
        {31, 31, 32, 31, 31, 31, 30, 29, 30, 29, 30, 30}, // 2059
        {31, 31, 32, 32, 31, 30, 30, 29, 30, 29, 30, 30}, // 2060
        {31, 32, 31, 32, 31, 30, 30, 30, 29, 29, 30, 31}, // 2061
        {30, 32, 31, 32, 31, 31, 29, 30, 29, 30, 29, 31}, // 2062
        {31, 31, 32, 31, 31, 31, 30, 29, 30, 29, 30, 30}, // 2063
        {31, 31, 32, 32, 31, 30, 30, 29, 30, 29, 30, 30}, // 2064
        {31, 32, 31, 32, 31, 30, 30, 30, 29, 29, 30, 31}, // 2065
        {31, 31, 31, 32, 31, 31, 29, 30, 30, 29, 29, 31}, // 2066
        {31, 31, 32, 31, 31, 31, 30, 29, 30, 29, 30, 30}, // 2067
        {31, 31, 32, 32, 31, 30, 30, 29, 30, 29, 30, 30}, // 2068
        {31, 32, 31, 32, 31, 30, 30, 30, 29, 29, 30, 31}, // 2069
        {31, 31, 31, 32, 31, 31, 29, 30, 30, 29, 30, 30}, // 2070
        {31, 31, 32, 31, 31, 31, 30, 29, 30, 29, 30, 30}, // 2071
        {31, 32, 31, 32, 31, 30, 30, 29, 30, 29, 30, 30}, // 2072
        {31, 32, 31, 32, 31, 30, 30, 30, 29, 29, 30, 31}, // 2073
        {31, 31, 31, 32, 31, 31, 30, 29, 30, 29, 30, 30}, // 2074
        {31, 31, 32, 31, 31, 31, 30, 29, 30, 29, 30, 30}, // 2075
        {31, 32, 31, 32, 31, 30, 30, 30, 29, 29, 30, 30}, // 2076
        {31, 32, 31, 32, 31, 30, 30, 30, 29, 30, 29, 31}, // 2077
        {31, 31, 31, 32, 31, 31, 30, 29, 30, 29, 30, 30}, // 2078
        {31, 31, 32, 31, 31, 31, 30, 29, 30, 29, 30, 30}, // 2079
        {31, 32, 31, 32, 31, 30, 30, 30, 29, 29, 30, 30}, // 2080
        {31, 32, 31, 32, 31, 30, 30, 30, 29, 30, 29, 31}, // 2081
        {31, 31, 32, 31, 31, 31, 30, 29, 30, 29, 30, 30}, // 2082
        {31, 31, 32, 31, 31, 31, 30, 29, 30, 29, 30, 30}, // 2083
        {31, 31, 32, 31, 31, 30, 30, 30, 29, 30, 30, 30}, // 2084
        {31, 32, 31, 32, 30, 31, 30, 30, 29, 30, 30, 30}, // 2085
        {30, 32, 31, 32, 31, 30, 30, 30, 29, 30, 30, 30}, // 2086
        {31, 31, 32, 31, 31, 31, 30, 30, 29, 30, 30, 30}, // 2087
        {30, 31, 32, 32, 30, 31, 30, 30, 29, 30, 30, 30}, // 2088
        {30, 32, 31, 32, 31, 30, 30, 30, 29, 30, 30, 30}, // 2089
        {30, 32, 31, 32, 31, 30, 30, 30, 29, 30, 30, 30}, // 2090
    };

    private static final int MIN_BS_YEAR = EPOCH_BS_YEAR;
    private static final int MAX_BS_YEAR = EPOCH_BS_YEAR + DAYS_IN_MONTH.length - 1;

    /**
     * Converts a Bikram Sambat date (as printed on a citizenship
     * certificate) to the equivalent Gregorian date.
     *
     * @param bsYear  B.S. year, e.g. 2054
     * @param bsMonth B.S. month, 1 (Baishakh) – 12 (Chaitra)
     * @param bsDay   B.S. day of month, 1-based
     * @throws IllegalArgumentException if the date is out of the supported
     *                                  range (B.S. 1970–2090) or not a real
     *                                  date in that B.S. year
     */
    public LocalDate toAd(int bsYear, int bsMonth, int bsDay) {
        validateRange(bsYear);
        if (bsMonth < 1 || bsMonth > 12) {
            throw new IllegalArgumentException("B.S. month must be 1-12, got: " + bsMonth);
        }
        int[] monthLengths = DAYS_IN_MONTH[bsYear - EPOCH_BS_YEAR];
        if (bsDay < 1 || bsDay > monthLengths[bsMonth - 1]) {
            throw new IllegalArgumentException(
                    "B.S. " + bsYear + "-" + bsMonth + " has " + monthLengths[bsMonth - 1]
                            + " days; got day " + bsDay);
        }

        long totalDaysFromEpoch = 0;
        for (int year = EPOCH_BS_YEAR; year < bsYear; year++) {
            for (int monthLength : DAYS_IN_MONTH[year - EPOCH_BS_YEAR]) {
                totalDaysFromEpoch += monthLength;
            }
        }
        for (int month = 0; month < bsMonth - 1; month++) {
            totalDaysFromEpoch += monthLengths[month];
        }
        totalDaysFromEpoch += (bsDay - 1);

        return EPOCH_AD_DATE.plusDays(totalDaysFromEpoch);
    }

    /** Converts a Gregorian date back to its Bikram Sambat equivalent. */
    public int[] toBs(LocalDate adDate) {
        if (adDate.isBefore(EPOCH_AD_DATE)) {
            throw new IllegalArgumentException(
                    "Date is before the supported B.S. " + MIN_BS_YEAR + " epoch (" + EPOCH_AD_DATE + ")");
        }

        long daysSinceEpoch = ChronoUnit.DAYS.between(EPOCH_AD_DATE, adDate);

        int bsYear = EPOCH_BS_YEAR;
        for (; bsYear <= MAX_BS_YEAR; bsYear++) {
            long yearLength = 0;
            for (int monthLength : DAYS_IN_MONTH[bsYear - EPOCH_BS_YEAR]) {
                yearLength += monthLength;
            }
            if (daysSinceEpoch < yearLength) break;
            daysSinceEpoch -= yearLength;
        }
        if (bsYear > MAX_BS_YEAR) {
            throw new IllegalArgumentException(
                    "Date is beyond the supported B.S. " + MAX_BS_YEAR + " range: " + adDate);
        }

        int[] monthLengths = DAYS_IN_MONTH[bsYear - EPOCH_BS_YEAR];
        int bsMonth = 1;
        for (int monthLength : monthLengths) {
            if (daysSinceEpoch < monthLength) break;
            daysSinceEpoch -= monthLength;
            bsMonth++;
        }
        int bsDay = (int) daysSinceEpoch + 1;

        return new int[] { bsYear, bsMonth, bsDay };
    }

    /** Whether this B.S. year falls within the supported lookup range. */
    public boolean isSupported(int bsYear) {
        return bsYear >= MIN_BS_YEAR && bsYear <= MAX_BS_YEAR;
    }

    private void validateRange(int bsYear) {
        if (!isSupported(bsYear)) {
            throw new IllegalArgumentException(
                    "B.S. year " + bsYear + " is outside the supported range "
                            + MIN_BS_YEAR + "-" + MAX_BS_YEAR);
        }
    }
}
