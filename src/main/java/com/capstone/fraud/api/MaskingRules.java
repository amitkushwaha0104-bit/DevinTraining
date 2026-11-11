package com.capstone.fraud.api;

import java.util.regex.Pattern;

/**
 * Shared masking rules. Intake may never carry raw card or account
 * numbers; any run of 12 or more digits is rejected outright.
 */
public final class MaskingRules {

    private static final Pattern DIGIT_RUN = Pattern.compile("\\d{12,}");
    private static final Pattern MARKUP = Pattern.compile("[<>]");

    private MaskingRules() {
    }

    public static boolean containsUnmaskedIdentifier(String value) {
        return value != null && DIGIT_RUN.matcher(value).find();
    }

    public static boolean containsMarkup(String value) {
        return value != null && MARKUP.matcher(value).find();
    }
}
