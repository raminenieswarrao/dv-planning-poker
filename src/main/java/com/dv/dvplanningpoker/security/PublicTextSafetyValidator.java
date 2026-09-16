package com.dv.dvplanningpoker.security;

import org.springframework.stereotype.Component;

import java.text.Normalizer;
import java.util.Arrays;
import java.util.Locale;
import java.util.Set;
import java.util.regex.Pattern;


/**
 * Central validation for user-controlled text that can become
 * visible to other users.
 *
 * Examples:
 *
 * - profile / signup name
 * - tournament name
 * - tournament description
 * - tournament location
 *
 * SECURITY GOALS:
 *
 * 1. Reject obvious HTML / script injection attempts.
 * 2. Reject dangerous control / invisible characters.
 * 3. Reject offensive / explicit public-facing content.
 * 4. Detect simple obfuscation:
 *
 *      f.u.c.k
 *      f@ck
 *      p0rn
 *      p-o-r-n
 *
 * This is server-side validation.
 *
 * Frontend validation must never be treated as the security layer.
 */
@Component
public class PublicTextSafetyValidator {

    /*
     * =========================================================
     * DANGEROUS MARKUP
     * =========================================================
     */

    private static final Pattern HTML_LIKE_PATTERN =
            Pattern.compile(
                    "<[^>]*>"
            );


    private static final Pattern DANGEROUS_SCHEME_PATTERN =
            Pattern.compile(
                    "(?i)"
                            +
                            "\\b("
                            +
                            "javascript"
                            +
                            "|vbscript"
                            +
                            "|data\\s*:\\s*text/html"
                            +
                            ")\\s*:"
            );


    /*
     * Unicode control characters should not appear inside
     * public display text.
     *
     * \p{Cc}
     *     control characters
     *
     * \p{Cf}
     *     invisible formatting characters such as
     *     zero-width / directional controls
     */
    private static final Pattern UNSAFE_CONTROL_PATTERN =
            Pattern.compile(
                    "[\\p{Cc}\\p{Cf}]"
            );


    /*
     * =========================================================
     * EXACT BLOCKED WORDS
     * =========================================================
     *
     * We intentionally use exact normalized tokens instead of
     * naive substring matching.
     *
     * Example:
     *
     * "Scunthorpe"
     *
     * should not accidentally match a smaller blocked word.
     */
    private static final Set<String> BLOCKED_WORDS =
            Set.of(

                    /*
                     * Explicit / adult content.
                     */

                    "porn",
                    "porno",
                    "pornography",
                    "pornographic",
                    "pornstar",
                    "nude",
                    "nudes",
                    "nudity",
                    "hentai",
                    "sexcam",
                    "sexchat",
                    "sexting",
                    "dildo",
                    "blowjob",
                    "handjob",
                    "cumshot",
                    "masturbate",
                    "masturbation",

                    /*
                     * Strong profanity.
                     */

                    "fuck",
                    "fucks",
                    "fucked",
                    "fucker",
                    "fuckers",
                    "fucking",
                    "motherfucker",
                    "motherfuckers",
                    "motherfucking",
                    "shit",
                    "shits",
                    "shitty",
                    "bullshit",
                    "bitch",
                    "bitches",
                    "asshole",
                    "assholes"
            );


    /*
     * =========================================================
     * COMPACT BLOCKED EXPRESSIONS
     * =========================================================
     *
     * These are long enough that checking a compact version is
     * useful for detecting punctuation-separated text.
     *
     * Example:
     *
     * p-o-r-n-h-u-b
     *
     * becomes:
     *
     * pornhub
     */
    private static final Set<String> BLOCKED_COMPACT_EXPRESSIONS =
            Set.of(
                    "pornhub",
                    "pornography",
                    "pornographic",
                    "onlyfans",
                    "motherfucker",
                    "motherfuckers",
                    "motherfucking",
                    "blowjob",
                    "handjob",
                    "cumshot",
                    "sexcam",
                    "sexchat",
                    "masturbation"
            );


    /*
     * Extremely long words are unusual in the fields this
     * application exposes and are often abuse / payload attempts.
     */
    private static final int MAX_SINGLE_TOKEN_LENGTH =
            80;


    /* =========================================================
       PUBLIC VALIDATION
       ========================================================= */

    /**
     * Validate required or optional public text.
     *
     * Null is allowed here because the caller already decides
     * whether a field is required.
     *
     * This method never modifies the caller's text.
     */
    public void validate(
            String value,
            String fieldLabel) {

        if (value == null) {

            return;
        }


        String label =
                safeFieldLabel(
                        fieldLabel
                );


        if (
                UNSAFE_CONTROL_PATTERN
                        .matcher(
                                value
                        )
                        .find()
        ) {

            throw unsafeContent(
                    label
            );
        }


        if (
                HTML_LIKE_PATTERN
                        .matcher(
                                value
                        )
                        .find()
        ) {

            throw unsafeContent(
                    label
            );
        }


        if (
                DANGEROUS_SCHEME_PATTERN
                        .matcher(
                                value
                        )
                        .find()
        ) {

            throw unsafeContent(
                    label
            );
        }


        String normalized =
                normalizeForSafety(
                        value
                );


        if (normalized.isBlank()) {

            return;
        }


        validateTokenLengths(
                normalized,
                label
        );


        validateBlockedWords(
                normalized,
                label
        );


        validateCompactExpressions(
                normalized,
                label
        );
    }


    /* =========================================================
       BLOCKED WORD CHECK
       ========================================================= */

    private void validateBlockedWords(
            String normalized,
            String fieldLabel) {

        String tokenized =
                normalized
                        .replaceAll(
                                "[^a-z0-9]+",
                                " "
                        )
                        .trim();


        if (tokenized.isBlank()) {

            return;
        }


        String[] tokens =
                tokenized.split(
                        "\\s+"
                );


        for (String token : tokens) {

            String canonical =
                    collapseRepeatedCharacters(
                            token
                    );


            if (
                    BLOCKED_WORDS.contains(
                            token
                    )
                            ||
                            BLOCKED_WORDS.contains(
                                    canonical
                            )
            ) {

                throw unsafeContent(
                        fieldLabel
                );
            }
        }
    }


    /* =========================================================
       COMPACT EXPRESSION CHECK
       ========================================================= */

    private void validateCompactExpressions(
            String normalized,
            String fieldLabel) {

        String compact =
                normalized
                        .replaceAll(
                                "[^a-z0-9]",
                                ""
                        );


        if (compact.isBlank()) {

            return;
        }


        String collapsedCompact =
                collapseRepeatedCharacters(
                        compact
                );


        for (
                String expression :
                BLOCKED_COMPACT_EXPRESSIONS
        ) {

            if (
                    compact.contains(
                            expression
                    )
                            ||
                            collapsedCompact.contains(
                                    expression
                            )
            ) {

                throw unsafeContent(
                        fieldLabel
                );
            }
        }
    }


    /* =========================================================
       TOKEN LENGTH
       ========================================================= */

    private void validateTokenLengths(
            String normalized,
            String fieldLabel) {

        Arrays
                .stream(
                        normalized.split(
                                "\\s+"
                        )
                )
                .filter(
                        token ->
                                !token.isBlank()
                )
                .forEach(
                        token -> {

                            if (
                                    token.length() >
                                            MAX_SINGLE_TOKEN_LENGTH
                            ) {

                                throw unsafeContent(
                                        fieldLabel
                                );
                            }
                        }
                );
    }


    /* =========================================================
       NORMALIZATION
       ========================================================= */

    private String normalizeForSafety(
            String value) {

        /*
         * Normalize Unicode so characters with combining marks
         * cannot easily bypass comparison.
         */
        String normalized =
                Normalizer.normalize(
                        value,
                        Normalizer.Form.NFKD
                );


        /*
         * Remove combining marks.
         */
        normalized =
                normalized.replaceAll(
                        "\\p{M}+",
                        ""
                );


        normalized =
                normalized
                        .toLowerCase(
                                Locale.ROOT
                        );


        /*
         * Basic leetspeak normalization.
         *
         * Examples:
         *
         * p0rn -> porn
         * f@ck -> fack
         * sh1t -> shit
         */
        normalized =
                normalized
                        .replace(
                                '0',
                                'o'
                        )
                        .replace(
                                '1',
                                'i'
                        )
                        .replace(
                                '3',
                                'e'
                        )
                        .replace(
                                '4',
                                'a'
                        )
                        .replace(
                                '5',
                                's'
                        )
                        .replace(
                                '7',
                                't'
                        )
                        .replace(
                                '8',
                                'b'
                        )
                        .replace(
                                '@',
                                'a'
                        )
                        .replace(
                                '$',
                                's'
                        );


        return normalized;
    }


    /* =========================================================
       REPEATED CHARACTER NORMALIZATION
       ========================================================= */

    private String collapseRepeatedCharacters(
            String value) {

        if (
                value == null
                        ||
                        value.length() <
                                2
        ) {

            return value;
        }


        StringBuilder result =
                new StringBuilder();


        char previous =
                0;


        int repeated =
                0;


        for (
                int index = 0;
                index < value.length();
                index++
        ) {

            char current =
                    value.charAt(
                            index
                    );


            if (
                    current ==
                            previous
            ) {

                repeated++;

            } else {

                previous =
                        current;

                repeated =
                        1;
            }


            /*
             * Keep at most two repeated characters.
             *
             * Example:
             *
             * fuuuuuck
             *
             * becomes:
             *
             * fuuck
             */
            if (
                    repeated <=
                            2
            ) {

                result.append(
                        current
                );
            }
        }


        return result.toString();
    }


    /* =========================================================
       FIELD LABEL
       ========================================================= */

    private String safeFieldLabel(
            String fieldLabel) {

        if (
                fieldLabel == null
                        ||
                        fieldLabel.isBlank()
        ) {

            return "Text";
        }


        String label =
                fieldLabel
                        .trim();


        if (
                label.length() >
                        40
        ) {

            return "Text";
        }


        return label;
    }


    /* =========================================================
       SAFE ERROR
       ========================================================= */

    private IllegalArgumentException unsafeContent(
            String fieldLabel) {

        /*
         * Do not tell the attacker which exact rule or blocked
         * word was triggered.
         */
        return new IllegalArgumentException(
                fieldLabel
                        +
                        " contains inappropriate or unsafe content."
        );
    }
}