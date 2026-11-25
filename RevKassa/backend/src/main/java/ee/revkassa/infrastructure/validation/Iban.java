package ee.revkassa.infrastructure.validation;

import java.math.BigInteger;
import java.util.regex.Pattern;

/**
 * IBAN rules (ISO 13616): country code, two check digits, then 11-30 letters or digits; moving the first four
 * characters to the end and turning letters into numbers (A = 10 ... Z = 35) must leave remainder 1 when divided by 97.
 */
public final class Iban {

    private static final Pattern FORMAT = Pattern.compile("[A-Z]{2}[0-9]{2}[A-Z0-9]{11,30}");
    private static final BigInteger MODULUS = BigInteger.valueOf(97);

    private Iban() {
    }

    // "ee38 2200 2210 2014 5685" -> "EE382200221020145685"
    public static String normalize(String iban) {
        return iban.replaceAll("\\s+", "").toUpperCase();
    }

    public static boolean isValid(String iban) {
        String normalized = normalize(iban);
        return FORMAT.matcher(normalized).matches() && hasValidCheckDigits(normalized);
    }

    // "EE382200221020145685" -> "EE38 2200 2210 2014 5685", as banks print it
    public static String group(String iban) {
        return iban.replaceAll("(.{4})(?!$)", "$1 ");
    }

    private static boolean hasValidCheckDigits(String iban) {
        String rearranged = iban.substring(4) + iban.substring(0, 4);
        StringBuilder digits = new StringBuilder();
        for (char c : rearranged.toCharArray()) {
            digits.append(Character.getNumericValue(c));
        }
        return new BigInteger(digits.toString()).mod(MODULUS).intValue() == 1;
    }
}
