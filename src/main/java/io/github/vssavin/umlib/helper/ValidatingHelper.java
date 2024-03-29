package io.github.vssavin.umlib.helper;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * @author vssavin on 08.01.2022
 */
public class ValidatingHelper {
    private static final Pattern VALID_EMAIL_ADDRESS_REGEX =
            Pattern.compile("^[A-Z0-9._%+-]+@[A-Z0-9.-]+\\.[A-Z]{2,6}$", Pattern.CASE_INSENSITIVE);

    public static boolean isValidEmail(String emailStr) {
        Matcher matcher = VALID_EMAIL_ADDRESS_REGEX.matcher(emailStr);
        return matcher.find();
    }
}
