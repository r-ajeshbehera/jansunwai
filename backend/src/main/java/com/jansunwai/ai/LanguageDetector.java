package com.jansunwai.ai;

/**
 * Script-based language identification: Odia letters (U+0B00-U+0B7F) -> "or", Devanagari (U+0900-U+097F) -> "hi",
 * otherwise "en". Works offline and is reliable for these three languages because they use different scripts.
 * (Hindi written in Latin letters, "Hinglish", is detected as "en"; the AI provider can refine it.)
 */
public final class LanguageDetector {
    private LanguageDetector() { }

    public static String detect(String text) {
        if (text == null) return "en";
        int odia = 0, devanagari = 0, latin = 0;
        for (int i = 0; i < text.length(); i++) {
            char ch = text.charAt(i);
            if (ch >= '଀' && ch <= '୿') odia++;
            else if (ch >= 'ऀ' && ch <= 'ॿ') devanagari++;
            else if (Character.isLetter(ch) && ch < 128) latin++;
        }
        if (odia > 0 && odia >= devanagari && odia * 2 >= latin) return "or";
        if (devanagari > 0 && devanagari * 2 >= latin) return "hi";
        return "en";
    }
}
