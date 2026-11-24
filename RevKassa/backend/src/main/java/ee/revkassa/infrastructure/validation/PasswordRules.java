package ee.revkassa.infrastructure.validation;

import java.nio.charset.StandardCharsets;
import java.util.Optional;

/**
 * Rules for every new password (set with a link, changed by the member, first admin). Login does not check them,
 * so passwords set before the rules existed keep working.
 * <ul>
 *   <li>at least 10 characters and at most 72 bytes in UTF-8 (bcrypt uses only the first 72 bytes)</li>
 *   <li>an uppercase letter, a lowercase letter and a special character (not a letter, digit or space)</li>
 *   <li>no character three times in a row ("ooo") and no run of three letters or digits ("abc", "123", "cba")</li>
 * </ul>
 * Upper and lower case count as the same character for the last two rules. Messages are stable English texts:
 * the frontend shows its own Estonian list of rules.
 */
public final class PasswordRules {

    public static final int MIN_LENGTH = 10;
    public static final int MAX_BYTES = 72;
    private static final int MAX_IN_A_ROW = 2;

    private PasswordRules() {
    }

    /**
     * @return the first rule the password breaks, empty if it is fine
     */
    public static Optional<String> findViolation(String password) {
        if (password.codePointCount(0, password.length()) < MIN_LENGTH) {
            return Optional.of("must be at least " + MIN_LENGTH + " characters");
        }
        if (password.getBytes(StandardCharsets.UTF_8).length > MAX_BYTES) {
            return Optional.of("must be at most " + MAX_BYTES + " bytes");
        }
        if (password.codePoints().noneMatch(Character::isUpperCase)) {
            return Optional.of("must contain an uppercase letter");
        }
        if (password.codePoints().noneMatch(Character::isLowerCase)) {
            return Optional.of("must contain a lowercase letter");
        }
        if (password.codePoints().noneMatch(PasswordRules::isSpecial)) {
            return Optional.of("must contain a special character");
        }
        if (hasRepeatedCharacter(password)) {
            return Optional.of("must not have the same character more than " + MAX_IN_A_ROW + " times in a row");
        }
        if (hasSequence(password)) {
            return Optional.of("must not have more than " + MAX_IN_A_ROW + " letters or digits in sequence (abc, 123)");
        }
        return Optional.empty();
    }

    private static boolean isSpecial(int codePoint) {
        return !Character.isLetterOrDigit(codePoint) && !Character.isWhitespace(codePoint);
    }

    private static boolean hasRepeatedCharacter(String password) {
        int[] chars = normalized(password);
        for (int i = 2; i < chars.length; i++) {
            if (chars[i] == chars[i - 1] && chars[i - 1] == chars[i - 2]) {
                return true;
            }
        }
        return false;
    }

    // Up ("abc", "123") or down ("cba", "321"); only within letters or within digits, so "9:;" does not count
    private static boolean hasSequence(String password) {
        int[] chars = normalized(password);
        for (int i = 2; i < chars.length; i++) {
            int step = chars[i - 1] - chars[i - 2];
            boolean sameKind = sameKind(chars[i - 2], chars[i - 1]) && sameKind(chars[i - 1], chars[i]);
            if (sameKind && Math.abs(step) == 1 && chars[i] - chars[i - 1] == step) {
                return true;
            }
        }
        return false;
    }

    private static boolean sameKind(int first, int second) {
        return (Character.isLetter(first) && Character.isLetter(second))
                || (Character.isDigit(first) && Character.isDigit(second));
    }

    private static int[] normalized(String password) {
        return password.codePoints().map(Character::toLowerCase).toArray();
    }
}
