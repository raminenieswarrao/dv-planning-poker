package com.dv.dvplanningpoker.config;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;

/**
 * Adds browser security headers to every HTTP response.
 *
 * The application intentionally:
 *
 * - does not allow framing
 * - does not allow external images
 * - does not allow plugins / objects
 * - does not use camera, microphone or geolocation
 * - only communicates with its own WebSocket endpoint
 *
 * IMPORTANT:
 *
 * index.html currently contains inline CSS and JavaScript,
 * therefore the CSP temporarily allows 'unsafe-inline'
 * for styles and scripts.
 *
 * A future hardening step can move CSS and JavaScript into
 * separate files and remove 'unsafe-inline'.
 */
@Component
public class SecurityHeadersFilter
        extends OncePerRequestFilter {

    private static final String CONTENT_SECURITY_POLICY =
            "default-src 'self'; "
                    +
                    "script-src 'self' 'unsafe-inline'; "
                    +
                    "style-src 'self' 'unsafe-inline'; "
                    +
                    "img-src 'self'; "
                    +
                    "font-src 'self'; "
                    +
                    "connect-src 'self' ws: wss:; "
                    +
                    "media-src 'none'; "
                    +
                    "object-src 'none'; "
                    +
                    "frame-src 'none'; "
                    +
                    "frame-ancestors 'none'; "
                    +
                    "worker-src 'none'; "
                    +
                    "base-uri 'self'; "
                    +
                    "form-action 'self'; "
                    +
                    "manifest-src 'self';";

    @Override
    protected void doFilterInternal(
            HttpServletRequest request,
            HttpServletResponse response,
            FilterChain filterChain)
            throws ServletException, IOException {

        /*
         * =====================================================
         * CONTENT SECURITY POLICY
         * =====================================================
         *
         * One of the most important browser protections.
         *
         * In particular:
         *
         * img-src 'self'
         *
         * means the browser may not load images from arbitrary
         * external websites.
         */
        response.setHeader(
                "Content-Security-Policy",
                CONTENT_SECURITY_POLICY
        );

        /*
         * =====================================================
         * CLICKJACKING PROTECTION
         * =====================================================
         *
         * Prevent another website from embedding Refinement Poker
         * inside an iframe.
         *
         * CSP frame-ancestors provides the modern protection.
         * X-Frame-Options provides compatibility with older clients.
         */
        response.setHeader(
                "X-Frame-Options",
                "DENY"
        );

        /*
         * =====================================================
         * MIME SNIFFING PROTECTION
         * =====================================================
         *
         * Prevent browsers from guessing another content type.
         */
        response.setHeader(
                "X-Content-Type-Options",
                "nosniff"
        );

        /*
         * =====================================================
         * REFERRER PROTECTION
         * =====================================================
         *
         * Do not leak the full application URL to another site.
         */
        response.setHeader(
                "Referrer-Policy",
                "no-referrer"
        );

        /*
         * =====================================================
         * BROWSER FEATURE RESTRICTIONS
         * =====================================================
         *
         * Refinement Poker has no business using these browser
         * capabilities.
         */
        response.setHeader(
                "Permissions-Policy",
                "camera=(), "
                        +
                        "microphone=(), "
                        +
                        "geolocation=(), "
                        +
                        "payment=(), "
                        +
                        "usb=(), "
                        +
                        "bluetooth=(), "
                        +
                        "serial=(), "
                        +
                        "hid=()"
        );

        /*
         * =====================================================
         * CROSS-ORIGIN ISOLATION
         * =====================================================
         *
         * Prevent unrelated sites from treating this application's
         * resources as cross-origin resources.
         */
        response.setHeader(
                "Cross-Origin-Resource-Policy",
                "same-origin"
        );

        response.setHeader(
                "Cross-Origin-Opener-Policy",
                "same-origin"
        );

        /*
         * =====================================================
         * LEGACY CROSS-DOMAIN POLICY
         * =====================================================
         */
        response.setHeader(
                "X-Permitted-Cross-Domain-Policies",
                "none"
        );

        /*
         * =====================================================
         * HTTPS / HSTS
         * =====================================================
         *
         * Only send HSTS when Spring considers the request secure.
         *
         * On Render, forwarded HTTPS information is respected
         * because application.properties contains:
         *
         * server.forward-headers-strategy=framework
         *
         * Local HTTP development is therefore unaffected.
         */
        if (request.isSecure()) {

            response.setHeader(
                    "Strict-Transport-Security",
                    "max-age=31536000; includeSubDomains"
            );
        }

        /*
         * =====================================================
         * SERVER INFORMATION
         * =====================================================
         *
         * Do not intentionally advertise technology information.
         */
        response.setHeader(
                "X-XSS-Protection",
                "0"
        );

        /*
         * Continue processing the request after adding headers.
         */
        filterChain.doFilter(
                request,
                response
        );
    }
}