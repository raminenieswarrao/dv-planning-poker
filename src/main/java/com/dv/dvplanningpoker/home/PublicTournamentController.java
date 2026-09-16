package com.dv.dvplanningpoker.home;

import com.dv.dvplanningpoker.home.PublicTournamentService.OverallScoreboard;
import com.dv.dvplanningpoker.home.PublicTournamentService.PublicTournamentServiceException;
import com.dv.dvplanningpoker.home.PublicTournamentService.RecentTournament;

import org.springframework.http.CacheControl;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;


/**
 * Public read-only APIs used by the home page.
 *
 * These endpoints do NOT require login.
 *
 * The browser never receives the Supabase secret key.
 */
@RestController
@RequestMapping("/api/public")
public class PublicTournamentController {

    private static final CacheControl PUBLIC_CACHE =
            CacheControl
                    .maxAge(
                            Duration.ofSeconds(
                                    30
                            )
                    )
                    .cachePublic();


    private final PublicTournamentService publicTournamentService;


    public PublicTournamentController(
            PublicTournamentService publicTournamentService) {

        this.publicTournamentService =
                publicTournamentService;
    }


    /* =========================================================
       RECENT COMPLETED TOURNAMENTS
       ========================================================= */

    @GetMapping("/tournaments/recent")
    public ResponseEntity<Map<String, Object>>
    getRecentTournaments() {

        try {

            List<RecentTournament> tournaments =
                    publicTournamentService
                            .getRecentCompletedTournaments();


            Map<String, Object> response =
                    successResponse();


            response.put(
                    "tournaments",
                    tournaments
            );


            response.put(
                    "count",
                    tournaments.size()
            );


            return ResponseEntity
                    .ok()
                    .cacheControl(
                            PUBLIC_CACHE
                    )
                    .body(
                            response
                    );


        } catch (
                PublicTournamentServiceException ex
        ) {

            return serviceError(
                    ex
            );


        } catch (
                Exception ex
        ) {

            return unavailable();
        }
    }


    /* =========================================================
       OVERALL PICKLEBALL SCOREBOARD
       ========================================================= */

    @GetMapping("/pickleball/scoreboard")
    public ResponseEntity<Map<String, Object>>
    getOverallScoreboard() {

        try {

            OverallScoreboard scoreboard =
                    publicTournamentService
                            .getOverallScoreboard();


            Map<String, Object> response =
                    successResponse();


            response.put(
                    "scoreboard",
                    scoreboard
            );


            return ResponseEntity
                    .ok()
                    .cacheControl(
                            PUBLIC_CACHE
                    )
                    .body(
                            response
                    );


        } catch (
                PublicTournamentServiceException ex
        ) {

            return serviceError(
                    ex
            );


        } catch (
                Exception ex
        ) {

            return unavailable();
        }
    }


    /* =========================================================
       RESPONSE HELPERS
       ========================================================= */

    private Map<String, Object> successResponse() {

        Map<String, Object> response =
                new LinkedHashMap<>();


        response.put(
                "success",
                true
        );


        return response;
    }


    private ResponseEntity<Map<String, Object>>
    serviceError(
            PublicTournamentServiceException ex) {

        HttpStatus status;


        if (
                ex.getStatusCode() ==
                        429
        ) {

            status =
                    HttpStatus.TOO_MANY_REQUESTS;

        } else {

            status =
                    HttpStatus.SERVICE_UNAVAILABLE;
        }


        return errorResponse(
                status,
                "Public tournament results are temporarily unavailable."
        );
    }


    private ResponseEntity<Map<String, Object>>
    unavailable() {

        return errorResponse(
                HttpStatus.SERVICE_UNAVAILABLE,
                "Public tournament results are temporarily unavailable."
        );
    }


    private ResponseEntity<Map<String, Object>>
    errorResponse(
            HttpStatus status,
            String message) {

        Map<String, Object> response =
                new LinkedHashMap<>();


        response.put(
                "success",
                false
        );


        response.put(
                "message",
                message
        );


        return ResponseEntity
                .status(
                        status
                )
                .body(
                        response
                );
    }
}