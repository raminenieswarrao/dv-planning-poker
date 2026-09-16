package com.dv.dvplanningpoker.auth;

import com.dv.dvplanningpoker.auth.SupabaseAuthService.AuthSession;
import com.dv.dvplanningpoker.auth.SupabaseAuthService.AuthUser;
import com.dv.dvplanningpoker.auth.SupabaseAuthService.SupabaseAuthException;
import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseCookie;
import org.springframework.stereotype.Component;

import java.time.Duration;

/**
 * Resolves the currently authenticated Supabase user.
 *
 * Other controllers can use this instead of duplicating
 * access-token / refresh-token cookie handling.
 */
@Component
public class AuthSessionResolver {

    private static final String ACCESS_COOKIE =
            "dv_access_token";

    private static final String REFRESH_COOKIE =
            "dv_refresh_token";

    private static final Duration REFRESH_COOKIE_DURATION =
            Duration.ofDays(30);

    private final SupabaseAuthService authService;

    public AuthSessionResolver(
            SupabaseAuthService authService) {

        this.authService =
                authService;
    }

    /* =====================================================
       RESOLVE CURRENT SESSION
       ===================================================== */

    public ResolvedSession resolve(
            HttpServletRequest request,
            HttpServletResponse response) {

        String accessToken =
                readCookie(
                        request,
                        ACCESS_COOKIE
                );

        /*
         * Try the current access token first.
         */
        if (accessToken != null) {

            try {

                AuthUser user =
                        authService.getCurrentUser(
                                accessToken
                        );

                return new ResolvedSession(
                        accessToken,
                        user
                );

            } catch (SupabaseAuthException ex) {

                /*
                 * A 401 normally means the access token expired.
                 * We can attempt refresh below.
                 */
                if (ex.getStatusCode() != 401) {

                    return null;
                }

            } catch (Exception ignored) {

                /*
                 * Refresh token may still recover the session.
                 */
            }
        }

        /*
         * Try refresh token.
         */
        String refreshToken =
                readCookie(
                        request,
                        REFRESH_COOKIE
                );

        if (refreshToken == null) {

            return null;
        }

        try {

            AuthSession refreshed =
                    authService.refreshSession(
                            refreshToken
                    );

            if (refreshed == null ||
                    refreshed.accessToken() == null ||
                    refreshed.accessToken().isBlank()) {

                clearSessionCookies(
                        request,
                        response
                );

                return null;
            }

            writeSessionCookies(
                    refreshed,
                    request,
                    response
            );

            AuthUser user =
                    refreshed.user();

            if (user == null) {

                user =
                        authService.getCurrentUser(
                                refreshed.accessToken()
                        );
            }

            if (user == null) {

                clearSessionCookies(
                        request,
                        response
                );

                return null;
            }

            return new ResolvedSession(
                    refreshed.accessToken(),
                    user
            );

        } catch (Exception ex) {

            clearSessionCookies(
                    request,
                    response
            );

            return null;
        }
    }

    /* =====================================================
       COOKIES
       ===================================================== */

    private void writeSessionCookies(
            AuthSession session,
            HttpServletRequest request,
            HttpServletResponse response) {

        boolean secure =
                request.isSecure();

        long accessSeconds =
                session.expiresIn();

        if (accessSeconds < 60) {

            accessSeconds =
                    60;
        }

        /*
         * Defensive upper bound.
         */
        accessSeconds =
                Math.min(
                        accessSeconds,
                        86_400
                );

        ResponseCookie accessCookie =
                ResponseCookie
                        .from(
                                ACCESS_COOKIE,
                                session.accessToken()
                        )
                        .httpOnly(
                                true
                        )
                        .secure(
                                secure
                        )
                        .sameSite(
                                "Strict"
                        )
                        .path(
                                "/"
                        )
                        .maxAge(
                                Duration.ofSeconds(
                                        accessSeconds
                                )
                        )
                        .build();

        ResponseCookie refreshCookie =
                ResponseCookie
                        .from(
                                REFRESH_COOKIE,
                                session.refreshToken()
                        )
                        .httpOnly(
                                true
                        )
                        .secure(
                                secure
                        )
                        .sameSite(
                                "Strict"
                        )
                        .path(
                                "/"
                        )
                        .maxAge(
                                REFRESH_COOKIE_DURATION
                        )
                        .build();

        response.addHeader(
                HttpHeaders.SET_COOKIE,
                accessCookie.toString()
        );

        response.addHeader(
                HttpHeaders.SET_COOKIE,
                refreshCookie.toString()
        );
    }

    private void clearSessionCookies(
            HttpServletRequest request,
            HttpServletResponse response) {

        boolean secure =
                request.isSecure();

        ResponseCookie accessCookie =
                ResponseCookie
                        .from(
                                ACCESS_COOKIE,
                                ""
                        )
                        .httpOnly(
                                true
                        )
                        .secure(
                                secure
                        )
                        .sameSite(
                                "Strict"
                        )
                        .path(
                                "/"
                        )
                        .maxAge(
                                Duration.ZERO
                        )
                        .build();

        ResponseCookie refreshCookie =
                ResponseCookie
                        .from(
                                REFRESH_COOKIE,
                                ""
                        )
                        .httpOnly(
                                true
                        )
                        .secure(
                                secure
                        )
                        .sameSite(
                                "Strict"
                        )
                        .path(
                                "/"
                        )
                        .maxAge(
                                Duration.ZERO
                        )
                        .build();

        response.addHeader(
                HttpHeaders.SET_COOKIE,
                accessCookie.toString()
        );

        response.addHeader(
                HttpHeaders.SET_COOKIE,
                refreshCookie.toString()
        );
    }

    private String readCookie(
            HttpServletRequest request,
            String cookieName) {

        Cookie[] cookies =
                request.getCookies();

        if (cookies == null) {

            return null;
        }

        for (Cookie cookie : cookies) {

            if (cookieName.equals(
                    cookie.getName()
            )) {

                String value =
                        cookie.getValue();

                if (value == null ||
                        value.isBlank()) {

                    return null;
                }

                return value;
            }
        }

        return null;
    }

    /* =====================================================
       SESSION RESULT
       ===================================================== */

    public record ResolvedSession(
            String accessToken,
            AuthUser user) {
    }
}