package com.jansunwai.ai;

import java.util.regex.Pattern;

/**
 * Data minimisation before text leaves our server: e-mail addresses, phone numbers and long digit sequences
 * (Aadhaar-like numbers, account numbers) are masked. The citizen's name, phone and landmark are never sent at all.
 */
public final class PiiRedactor {
    private static final Pattern EMAIL = Pattern.compile("[\\w.+-]+@[\\w-]+\\.[\\w.]+");
    private static final Pattern PHONE = Pattern.compile("(?<!\\d)(?:\\+?91[\\s-]?)?[6-9]\\d{9}(?!\\d)");
    private static final Pattern LONG_DIGITS = Pattern.compile("\\d(?:[\\s-]?\\d){9,}");

    private PiiRedactor() { }

    public static String redact(String text) {
        if (text == null) return null;
        String t = EMAIL.matcher(text).replaceAll("[email]");
        t = PHONE.matcher(t).replaceAll("[phone]");
        return LONG_DIGITS.matcher(t).replaceAll("[number]");
    }
}
