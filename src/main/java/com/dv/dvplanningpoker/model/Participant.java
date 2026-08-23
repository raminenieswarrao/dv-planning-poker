package com.dv.dvplanningpoker.model;

import java.util.UUID;

/**
 * A participant currently connected to a refinement room.
 *
 * Security model:
 *
 * - sessionId is internal server state only.
 * - participantId is the safe public identifier used by the UI.
 * - sessionId must NEVER be sent to browsers.
 * - participantId may be sent to clients and used for host actions
 *   such as removing a participant.
 *
 * Room data is intentionally stored in memory only.
 */
public class Participant {

    /**
     * Internal Spring WebSocket session identifier.
     *
     * SECURITY:
     * Never expose this value to the frontend.
     */
    private final String sessionId;

    /**
     * Public identifier for this participant.
     *
     * The frontend may safely receive this value.
     *
     * It allows the host to request:
     *
     * REMOVE_PARTICIPANT
     *
     * without exposing internal WebSocket session IDs.
     */
    private final String participantId;

    /**
     * Validated participant display name.
     */
    private final String name;

    /**
     * Whether this participant is the room host.
     *
     * Host authority is still verified server-side.
     * The browser cannot grant itself host privileges.
     */
    private boolean host;

    /**
     * Current estimate/game/food selection.
     *
     * null means the participant has not voted.
     */
    private String selection;

    public Participant(
            String sessionId,
            String name,
            boolean host) {

        if (sessionId == null ||
                sessionId.isBlank()) {

            throw new IllegalArgumentException(
                    "Participant session is required."
            );
        }

        if (name == null ||
                name.isBlank()) {

            throw new IllegalArgumentException(
                    "Participant name is required."
            );
        }

        this.sessionId =
                sessionId;

        /*
         * UUID.randomUUID() generates an unpredictable random UUID.
         *
         * This value is intentionally independent from the
         * WebSocket session ID.
         */
        this.participantId =
                UUID.randomUUID()
                        .toString();

        this.name =
                name;

        this.host =
                host;
    }

    /**
     * INTERNAL SERVER USE ONLY.
     *
     * Do not include this value in WebSocket responses.
     */
    public String getSessionId() {

        return sessionId;
    }

    /**
     * Safe identifier that may be exposed to clients.
     */
    public String getParticipantId() {

        return participantId;
    }

    public String getName() {

        return name;
    }

    public boolean isHost() {

        return host;
    }

    /**
     * Server-side use only.
     *
     * Host status must never be accepted from the browser.
     */
    public void setHost(
            boolean host) {

        this.host =
                host;
    }

    public String getSelection() {

        return selection;
    }

    public void setSelection(
            String selection) {

        this.selection =
                selection;
    }

    public void clearSelection() {

        this.selection =
                null;
    }

    public boolean isVoted() {

        return selection != null;
    }

    /*
     * Compatibility helpers retained for the earlier
     * estimate-only implementation.
     */
    public String getVote() {

        return selection;
    }

    public void setVote(
            String vote) {

        this.selection =
                vote;
    }

    /*
     * Security note:
     *
     * Intentionally do NOT implement a toString() containing:
     *
     * - sessionId
     * - participantId
     * - selection
     *
     * This reduces accidental sensitive information appearing
     * in logs.
     */
}