package com.dv.dvplanningpoker.model;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Represents one isolated refinement room.
 *
 * Each room has its own:
 *
 * - room code
 * - host
 * - participants
 * - voting activity
 * - timer
 * - reveal state
 * - creation/activity timestamps
 *
 * Security-sensitive validation, authentication, authorization,
 * rate limiting, room creation, and room lookup are handled by
 * PokerRoomService and PokerWebSocketHandler.
 *
 * This class only stores server-side room state.
 */
public final class PokerRoom {

    /**
     * Public invitation code for this room.
     *
     * Example:
     * K7P4-X9Q2-WM
     *
     * This code may be shared with participants.
     */
    private final String roomCode;

    /**
     * SHA-256 hash of the private host token.
     *
     * IMPORTANT:
     *
     * The raw host token must never be stored here.
     * Only its hash is retained by the server.
     */
    private final byte[] hostTokenHash;

    /**
     * Participants belonging only to this room.
     *
     * Key:
     * WebSocket session ID
     *
     * Value:
     * Participant
     */
    private final Map<String, Participant> participants =
            new LinkedHashMap<>();

    /**
     * WebSocket session currently acting as host.
     */
    private String hostSessionId;

    /**
     * Current room activity.
     *
     * Expected values:
     *
     * ESTIMATE
     * GAME
     * FOOD
     *
     * Validation is performed by PokerRoomService.
     */
    private String currentMode =
            "ESTIMATE";

    /**
     * Whether the current round has been revealed.
     */
    private boolean revealed;

    /**
     * Server epoch timestamp when the timer expires.
     *
     * 0 means no active timer.
     */
    private long timerEndEpochMs;

    /**
     * Time the room was created.
     */
    private final long createdAtEpochMs;

    /**
     * Most recent activity in this room.
     *
     * Used later for stale-room cleanup.
     */
    private long lastActivityEpochMs;

    public PokerRoom(
            String roomCode,
            byte[] hostTokenHash,
            String hostSessionId) {

        if (roomCode == null ||
                roomCode.isBlank()) {

            throw new IllegalArgumentException(
                    "Room code is required."
            );
        }

        if (hostTokenHash == null ||
                hostTokenHash.length == 0) {

            throw new IllegalArgumentException(
                    "Host token hash is required."
            );
        }

        if (hostSessionId == null ||
                hostSessionId.isBlank()) {

            throw new IllegalArgumentException(
                    "Host session is required."
            );
        }

        this.roomCode =
                roomCode;

        /*
         * Defensive copy.
         *
         * Prevents the caller from modifying the original byte array
         * after the PokerRoom has been created.
         */
        this.hostTokenHash =
                hostTokenHash.clone();

        this.hostSessionId =
                hostSessionId;

        this.createdAtEpochMs =
                System.currentTimeMillis();

        this.lastActivityEpochMs =
                createdAtEpochMs;
    }

    public String getRoomCode() {

        return roomCode;
    }

    /**
     * Returns a defensive copy of the host-token hash.
     *
     * Never expose the internal byte array directly.
     */
    public byte[] getHostTokenHash() {

        return hostTokenHash.clone();
    }

    public String getHostSessionId() {

        return hostSessionId;
    }

    /**
     * Changes the current host session.
     *
     * This should only be called after server-side authorization
     * inside PokerRoomService.
     */
    public void setHostSessionId(
            String hostSessionId) {

        if (hostSessionId == null ||
                hostSessionId.isBlank()) {

            throw new IllegalArgumentException(
                    "Host session is required."
            );
        }

        this.hostSessionId =
                hostSessionId;

        touch();
    }

    /**
     * Returns the mutable participant map.
     *
     * IMPORTANT:
     *
     * This is intended only for trusted server-side code.
     *
     * Never serialize or send this map directly to a browser because
     * it contains internal WebSocket session IDs.
     */
    public Map<String, Participant> getParticipantMap() {

        return participants;
    }

    /**
     * Returns a snapshot of participants without exposing
     * the participant map itself.
     */
    public List<Participant> getParticipants() {

        return new ArrayList<>(
                participants.values()
        );
    }

    /**
     * Returns one participant by internal WebSocket session ID.
     */
    public Participant getParticipant(
            String sessionId) {

        if (sessionId == null) {
            return null;
        }

        return participants.get(
                sessionId
        );
    }

    public int getParticipantCount() {

        return participants.size();
    }

    public boolean isEmpty() {

        return participants.isEmpty();
    }

    public boolean containsSession(
            String sessionId) {

        return sessionId != null
                &&
                participants.containsKey(
                        sessionId
                );
    }

    public String getCurrentMode() {

        return currentMode;
    }

    /**
     * Mode values must already have been validated by
     * PokerRoomService before reaching this method.
     */
    public void setCurrentMode(
            String currentMode) {

        if (currentMode == null ||
                currentMode.isBlank()) {

            throw new IllegalArgumentException(
                    "Room mode is required."
            );
        }

        this.currentMode =
                currentMode;

        touch();
    }

    public boolean isRevealed() {

        return revealed;
    }

    public void setRevealed(
            boolean revealed) {

        this.revealed =
                revealed;

        touch();
    }

    public long getTimerEndEpochMs() {

        return timerEndEpochMs;
    }

    public void setTimerEndEpochMs(
            long timerEndEpochMs) {

        if (timerEndEpochMs < 0) {

            throw new IllegalArgumentException(
                    "Timer value cannot be negative."
            );
        }

        this.timerEndEpochMs =
                timerEndEpochMs;

        touch();
    }

    public long getCreatedAtEpochMs() {

        return createdAtEpochMs;
    }

    public long getLastActivityEpochMs() {

        return lastActivityEpochMs;
    }

    /**
     * Records activity in this room.
     *
     * Used for automatic stale-room expiration.
     */
    public void touch() {

        lastActivityEpochMs =
                System.currentTimeMillis();
    }

    /**
     * Clears all selections for the current round.
     *
     * Participant membership remains unchanged.
     */
    public void clearSelections() {

        participants.values()
                .forEach(
                        Participant::clearSelection
                );

        touch();
    }

    /**
     * Resets voting state while keeping participants in the room.
     */
    public void resetRound() {

        clearSelections();

        revealed =
                false;

        timerEndEpochMs =
                0L;

        touch();
    }

    /*
     * Security note:
     *
     * Intentionally do NOT implement toString() containing:
     *
     * - roomCode
     * - hostTokenHash
     * - hostSessionId
     * - participant session IDs
     *
     * This reduces the risk of sensitive room information
     * accidentally appearing in logs.
     */
}