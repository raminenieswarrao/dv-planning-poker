package com.dv.dvplanningpoker.auth;

import com.dv.dvplanningpoker.auth.SupabaseAuthService.AuthSession;
import com.dv.dvplanningpoker.auth.SupabaseAuthService.AuthUser;
import com.dv.dvplanningpoker.auth.SupabaseAuthService.RegistrationResult;
import com.dv.dvplanningpoker.auth.SupabaseAuthService.SupabaseAuthException;
import com.dv.dvplanningpoker.auth.SupabaseProfileService.UserProfile;
import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseCookie;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.Map;

@RestController
@RequestMapping("/api/auth")
public class AuthController {

    private static final String ACCESS_COOKIE =
            "dv_access_token";

    private static final String REFRESH_COOKIE =
            "dv_refresh_token";

    private static final Duration REFRESH_COOKIE_DURATION =
            Duration.ofDays(30);

    private final SupabaseAuthService authService;

    private final SupabaseProfileService profileService;

    public AuthController(
            SupabaseAuthService authService,
            SupabaseProfileService profileService) {

        this.authService =
                authService;

        this.profileService =
                profileService;
    }

    /* =========================================================
       REGISTER
       ========================================================= */

    @PostMapping("/register")
    public ResponseEntity<Map<String, Object>> register(
            @RequestBody RegisterRequest request,
            HttpServletRequest servletRequest,
            HttpServletResponse servletResponse) {

        try {

            if (request == null) {

                return badRequest(
                        "Invalid registration request."
                );
            }

            if (!safeEquals(
                    request.password(),
                    request.confirmPassword()
            )) {

                return badRequest(
                        "Passwords do not match."
                );
            }

            RegistrationResult result =
                    authService.register(
                            request.email(),
                            request.password(),
                            request.name(),
                            request.gender()
                    );

            if (result.session() != null) {

                writeSessionCookies(
                        result.session(),
                        servletRequest,
                        servletResponse
                );
            }

            Map<String, Object> response =
                    new LinkedHashMap<>();

            response.put(
                    "success",
                    true
            );

            response.put(
                    "emailConfirmationRequired",
                    result.emailConfirmationRequired()
            );

            response.put(
                    "user",
                    publicUser(
                            result.user()
                    )
            );

            if (result.emailConfirmationRequired()) {

                response.put(
                        "message",
                        "Account created. Please confirm your email before signing in."
                );

            } else {

                response.put(
                        "message",
                        "Account created successfully."
                );
            }

            return ResponseEntity
                    .status(
                            HttpStatus.CREATED
                    )
                    .body(
                            response
                    );

        } catch (IllegalArgumentException ex) {

            return badRequest(
                    safeMessage(
                            ex.getMessage(),
                            "Unable to create account."
                    )
            );

        } catch (SupabaseAuthException ex) {

            return authError(
                    ex,
                    "Unable to create account."
            );

        } catch (IllegalStateException ex) {

            return serviceUnavailable();

        } catch (Exception ex) {

            return serviceUnavailable();
        }
    }

    /* =========================================================
       LOGIN
       ========================================================= */

    @PostMapping("/login")
    public ResponseEntity<Map<String, Object>> login(
            @RequestBody LoginRequest request,
            HttpServletRequest servletRequest,
            HttpServletResponse servletResponse) {

        try {

            if (request == null) {

                return unauthorized(
                        "Invalid email or password."
                );
            }

            AuthSession session =
                    authService.login(
                            request.email(),
                            request.password()
                    );

            writeSessionCookies(
                    session,
                    servletRequest,
                    servletResponse
            );

            Map<String, Object> response =
                    new LinkedHashMap<>();

            response.put(
                    "success",
                    true
            );

            response.put(
                    "message",
                    "Signed in successfully."
            );

            response.put(
                    "user",
                    publicUser(
                            session.user()
                    )
            );

            return ResponseEntity.ok(
                    response
            );

        } catch (IllegalArgumentException ex) {

            return unauthorized(
                    "Invalid email or password."
            );

        } catch (SupabaseAuthException ex) {

            if (ex.getStatusCode() == 400 ||
                    ex.getStatusCode() == 401) {

                return unauthorized(
                        "Invalid email or password."
                );
            }

            return authError(
                    ex,
                    "Unable to sign in."
            );

        } catch (IllegalStateException ex) {

            return serviceUnavailable();

        } catch (Exception ex) {

            return serviceUnavailable();
        }
    }

    /* =========================================================
       CURRENT USER
       ========================================================= */

    @GetMapping("/me")
    public ResponseEntity<Map<String, Object>> me(
            HttpServletRequest request,
            HttpServletResponse response) {

        String accessToken =
                readCookie(
                        request,
                        ACCESS_COOKIE
                );

        /*
         * First try the existing access token.
         */
        if (accessToken != null) {

            try {

                AuthUser user =
                        authService.getCurrentUser(
                                accessToken
                        );

                return authenticatedUserResponse(
                        user,
                        accessToken
                );

            } catch (SupabaseAuthException ex) {

                /*
                 * 401 normally means the short-lived
                 * access token expired.
                 *
                 * Try refresh token below.
                 */
                if (ex.getStatusCode() != 401) {

                    return authError(
                            ex,
                            "Unable to verify session."
                    );
                }

            } catch (Exception ignored) {

                /*
                 * Refresh token may still recover session.
                 */
            }
        }

        String refreshToken =
                readCookie(
                        request,
                        REFRESH_COOKIE
                );

        if (refreshToken == null) {

            clearSessionCookies(
                    request,
                    response
            );

            return unauthenticatedResponse();
        }

        try {

            AuthSession refreshed =
                    authService.refreshSession(
                            refreshToken
                    );

            /*
             * Supabase rotates refresh tokens.
             * Always save both fresh values.
             */
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

            return authenticatedUserResponse(
                    user,
                    refreshed.accessToken()
            );

        } catch (Exception ex) {

            clearSessionCookies(
                    request,
                    response
            );

            return unauthenticatedResponse();
        }
    }

    /* =========================================================
       CHANGE PASSWORD
       ========================================================= */

    @PostMapping("/change-password")
    public ResponseEntity<Map<String, Object>> changePassword(
            @RequestBody ChangePasswordRequest requestBody,
            HttpServletRequest request,
            HttpServletResponse response) {

        if (requestBody == null) {

            return badRequest(
                    "Invalid password request."
            );
        }

        if (!safeEquals(
                requestBody.newPassword(),
                requestBody.confirmPassword()
        )) {

            return badRequest(
                    "Passwords do not match."
            );
        }

        String accessToken =
                readCookie(
                        request,
                        ACCESS_COOKIE
                );

        if (accessToken == null) {

            AuthSession refreshed =
                    tryRefreshSession(
                            request,
                            response
                    );

            if (refreshed == null) {

                return unauthorized(
                        "Please sign in."
                );
            }

            accessToken =
                    refreshed.accessToken();
        }

        try {

            AuthUser user =
                    authService.changePassword(
                            accessToken,
                            requestBody.newPassword()
                    );

            Map<String, Object> result =
                    new LinkedHashMap<>();

            result.put(
                    "success",
                    true
            );

            result.put(
                    "message",
                    "Password changed successfully."
            );

            result.put(
                    "user",
                    publicUser(
                            user
                    )
            );

            return ResponseEntity.ok(
                    result
            );

        } catch (SupabaseAuthException ex) {

            if (ex.getStatusCode() == 401) {

                AuthSession refreshed =
                        tryRefreshSession(
                                request,
                                response
                        );

                if (refreshed == null) {

                    return unauthorized(
                            "Please sign in."
                    );
                }

                try {

                    AuthUser user =
                            authService.changePassword(
                                    refreshed.accessToken(),
                                    requestBody.newPassword()
                            );

                    Map<String, Object> result =
                            new LinkedHashMap<>();

                    result.put(
                            "success",
                            true
                    );

                    result.put(
                            "message",
                            "Password changed successfully."
                    );

                    result.put(
                            "user",
                            publicUser(
                                    user
                            )
                    );

                    return ResponseEntity.ok(
                            result
                    );

                } catch (Exception retryException) {

                    return badRequest(
                            "Unable to change password."
                    );
                }
            }

            return authError(
                    ex,
                    "Unable to change password."
            );

        } catch (IllegalArgumentException ex) {

            return badRequest(
                    safeMessage(
                            ex.getMessage(),
                            "Unable to change password."
                    )
            );

        } catch (Exception ex) {

            return serviceUnavailable();
        }
    }

    /* =========================================================
       LOGOUT
       ========================================================= */

    @PostMapping("/logout")
    public ResponseEntity<Map<String, Object>> logout(
            HttpServletRequest request,
            HttpServletResponse response) {

        String accessToken =
                readCookie(
                        request,
                        ACCESS_COOKIE
                );

        try {

            authService.logout(
                    accessToken
            );

        } catch (Exception ignored) {
        }

        clearSessionCookies(
                request,
                response
        );

        Map<String, Object> result =
                new LinkedHashMap<>();

        result.put(
                "success",
                true
        );

        result.put(
                "message",
                "Signed out successfully."
        );

        return ResponseEntity.ok(
                result
        );
    }

    /* =========================================================
       PROFILE-AWARE AUTH RESPONSE
       ========================================================= */

    private ResponseEntity<Map<String, Object>>
    authenticatedUserResponse(
            AuthUser user,
            String accessToken) {

        Map<String, Object> result =
                new LinkedHashMap<>();

        result.put(
                "authenticated",
                true
        );

        result.put(
                "user",
                publicUser(
                        user
                )
        );

        /*
         * Authentication should continue to work even if
         * profile retrieval temporarily fails.
         */
        UserProfile profile =
                loadProfileSafely(
                        accessToken,
                        user
                );

        result.put(
                "profile",
                publicProfile(
                        profile
                )
        );

        return ResponseEntity.ok(
                result
        );
    }

    private UserProfile loadProfileSafely(
            String accessToken,
            AuthUser user) {

        if (user == null ||
                user.id() == null ||
                user.id().isBlank()) {

            return null;
        }

        try {

            return profileService.getProfile(
                    accessToken,
                    user.id()
            );

        } catch (Exception ignored) {

            /*
             * A temporary profile API problem must not
             * invalidate the authenticated session.
             */
            return null;
        }
    }

    /* =========================================================
       REFRESH HELPER
       ========================================================= */

    private AuthSession tryRefreshSession(
            HttpServletRequest request,
            HttpServletResponse response) {

        String refreshToken =
                readCookie(
                        request,
                        REFRESH_COOKIE
                );

        if (refreshToken == null) {

            clearSessionCookies(
                    request,
                    response
            );

            return null;
        }

        try {

            AuthSession session =
                    authService.refreshSession(
                            refreshToken
                    );

            writeSessionCookies(
                    session,
                    request,
                    response
            );

            return session;

        } catch (Exception ex) {

            clearSessionCookies(
                    request,
                    response
            );

            return null;
        }
    }

    /* =========================================================
       COOKIES
       ========================================================= */

    private void writeSessionCookies(
            AuthSession session,
            HttpServletRequest request,
            HttpServletResponse response) {

        if (session == null ||
                session.accessToken() == null ||
                session.refreshToken() == null) {

            throw new IllegalStateException(
                    "Authentication session is invalid."
            );
        }

        boolean secure =
                request.isSecure();

        long accessSeconds =
                session.expiresIn();

        if (accessSeconds < 60) {

            accessSeconds =
                    60;
        }

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

        for (Cookie cookie :
                cookies) {

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

    /* =========================================================
       RESPONSE HELPERS
       ========================================================= */

    private ResponseEntity<Map<String, Object>>
    unauthenticatedResponse() {

        Map<String, Object> result =
                new LinkedHashMap<>();

        result.put(
                "authenticated",
                false
        );

        return ResponseEntity.ok(
                result
        );
    }

    private Map<String, Object> publicUser(
            AuthUser user) {

        Map<String, Object> result =
                new LinkedHashMap<>();

        if (user == null) {
            return result;
        }

        result.put(
                "id",
                user.id()
        );

        result.put(
                "email",
                user.email()
        );

        result.put(
                "emailConfirmed",
                user.emailConfirmed()
        );

        return result;
    }

    private Map<String, Object> publicProfile(
            UserProfile profile) {

        if (profile == null) {

            return null;
        }

        Map<String, Object> result =
                new LinkedHashMap<>();

        result.put(
                "id",
                profile.id()
        );

        result.put(
                "name",
                profile.name()
        );

        result.put(
                "gender",
                profile.gender()
        );

        result.put(
                "avatarUrl",
                profile.avatarUrl()
        );

        result.put(
                "role",
                profile.role()
        );

        return result;
    }

    private ResponseEntity<Map<String, Object>>
    badRequest(
            String message) {

        return errorResponse(
                HttpStatus.BAD_REQUEST,
                message
        );
    }

    private ResponseEntity<Map<String, Object>>
    unauthorized(
            String message) {

        return errorResponse(
                HttpStatus.UNAUTHORIZED,
                message
        );
    }

    private ResponseEntity<Map<String, Object>>
    serviceUnavailable() {

        return errorResponse(
                HttpStatus.SERVICE_UNAVAILABLE,
                "Authentication service is temporarily unavailable."
        );
    }

    private ResponseEntity<Map<String, Object>>
    authError(
            SupabaseAuthException ex,
            String fallbackMessage) {

        int upstreamStatus =
                ex.getStatusCode();

        HttpStatus status;

        if (upstreamStatus == 400 ||
                upstreamStatus == 422) {

            status =
                    HttpStatus.BAD_REQUEST;

        } else if (upstreamStatus == 401 ||
                upstreamStatus == 403) {

            status =
                    HttpStatus.UNAUTHORIZED;

        } else if (upstreamStatus == 409) {

            status =
                    HttpStatus.CONFLICT;

        } else if (upstreamStatus == 429) {

            status =
                    HttpStatus.TOO_MANY_REQUESTS;

        } else {

            status =
                    HttpStatus.SERVICE_UNAVAILABLE;
        }

        String message =
                safeMessage(
                        ex.getMessage(),
                        fallbackMessage
                );

        return errorResponse(
                status,
                message
        );
    }

    private ResponseEntity<Map<String, Object>>
    errorResponse(
            HttpStatus status,
            String message) {

        Map<String, Object> result =
                new LinkedHashMap<>();

        result.put(
                "success",
                false
        );

        result.put(
                "message",
                message
        );

        return ResponseEntity
                .status(
                        status
                )
                .body(
                        result
                );
    }

    private String safeMessage(
            String message,
            String fallback) {

        if (message == null ||
                message.isBlank() ||
                message.length() > 200) {

            return fallback;
        }

        return message;
    }

    private boolean safeEquals(
            String first,
            String second) {

        if (first == null ||
                second == null) {

            return false;
        }

        if (first.length() !=
                second.length()) {

            return false;
        }

        int difference =
                0;

        for (int index = 0;
             index < first.length();
             index++) {

            difference |=
                    first.charAt(index)
                            ^
                            second.charAt(index);
        }

        return difference == 0;
    }

    /* =========================================================
       REQUEST TYPES
       ========================================================= */

    public record RegisterRequest(
            String name,
            String email,
            String password,
            String confirmPassword,
            String gender) {
    }

    public record LoginRequest(
            String email,
            String password) {
    }

    public record ChangePasswordRequest(
            String newPassword,
            String confirmPassword) {
    }
}