package com.dv.dvplanningpoker.service;

import com.dv.dvplanningpoker.model.Participant;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/**
 * In-memory state for one shared refinement room.
 *
 * No database is used. Restarting the Spring Boot service clears the room.
 */
@Service
public class PokerRoomService {

    public static final String MODE_ESTIMATE = "ESTIMATE";
    public static final String MODE_GAME = "GAME";
    public static final String MODE_FOOD = "FOOD";

    private static final long TIMER_DURATION_MS = 30_000L;

    private static final Set<String> VALID_MODES =
            Set.of(
                    MODE_ESTIMATE,
                    MODE_GAME,
                    MODE_FOOD
            );

    /*
     * Estimate scale requested for the team:
     * 1, 2, 3, 5, 13 and ? for questions / clarification.
     */
    private static final Set<String> ESTIMATE_OPTIONS =
            Set.of(
                    "1",
                    "2",
                    "3",
                    "5",
                    "13",
                    "?"
            );

    private static final Set<String> GAME_OPTIONS =
            Set.of(
                    "PING_PONG",
                    "BASKETBALL",
                    "PICKLEBALL",
                    "CRICKET",
                    "BADMINTON",
                    "GOLF",
                    "CHESS",
                    "BILLIARDS",
                    "VIDEO_GAMES",
                    "SKATING",
                    "ICE_SKATING",
                    "SWIMMING"
            );

    private static final Set<String> FOOD_OPTIONS =
            Set.of(
                    "COFFEE",
                    "LUNCH",
                    "SNACKS",
                    "RESTAURANT",
                    "PUB_AFTER_WORK"
            );

    private final Map<String, Participant> participants =
            new LinkedHashMap<>();

    private String hostSessionId;

    private String currentMode =
            MODE_ESTIMATE;

    private boolean revealed;

    private long timerEndEpochMs;

    /**
     * Adds a participant to the room.
     * The first participant becomes host.
     */
    public synchronized Participant join(
            String sessionId,
            String rawName) {

        if (sessionId == null ||
                sessionId.isBlank()) {

            throw new IllegalArgumentException(
                    "Invalid WebSocket session."
            );
        }

        String name =
                rawName == null
                        ? ""
                        : rawName.trim();

        if (name.isBlank()) {

            throw new IllegalArgumentException(
                    "Please enter your name."
            );
        }

        if (name.length() > 40) {

            throw new IllegalArgumentException(
                    "Name must be 40 characters or fewer."
            );
        }

        boolean duplicateName =
                participants.values()
                        .stream()
                        .anyMatch(
                                participant ->
                                        participant
                                                .getName()
                                                .equalsIgnoreCase(
                                                        name
                                                )
                        );

        if (duplicateName) {

            throw new IllegalArgumentException(
                    "That name is already in the room."
            );
        }

        boolean host =
                participants.isEmpty();

        Participant participant =
                new Participant(
                        sessionId,
                        name,
                        host
                );

        participants.put(
                sessionId,
                participant
        );

        if (host) {

            hostSessionId =
                    sessionId;
        }

        return participant;
    }

    /**
     * Removes a participant and promotes the next participant
     * if the host leaves.
     */
    public synchronized void leave(
            String sessionId) {

        Participant removed =
                participants.remove(
                        sessionId
                );

        if (removed == null) {
            return;
        }

        if (sessionId.equals(
                hostSessionId
        )) {

            hostSessionId =
                    null;

            participants.values()
                    .stream()
                    .findFirst()
                    .ifPresent(
                            participant -> {

                                participant.setHost(
                                        true
                                );

                                hostSessionId =
                                        participant.getSessionId();
                            }
                    );
        }

        if (participants.isEmpty()) {

            resetWholeRoom();
        }
    }

    /**
     * Records or replaces the participant's selection
     * for the current activity.
     */
    public synchronized void vote(
            String sessionId,
            String rawSelection) {

        Participant participant =
                requireParticipant(
                        sessionId
                );

        if (revealed) {

            throw new IllegalStateException(
                    "This round has already been revealed."
            );
        }

        String selection =
                rawSelection == null
                        ? ""
                        : rawSelection
                        .trim()
                        .toUpperCase(
                                Locale.ROOT
                        );

        if (!allowedSelections()
                .contains(selection)) {

            throw new IllegalArgumentException(
                    "Invalid selection for the current activity."
            );
        }

        participant.setSelection(
                selection
        );
    }

    /**
     * Changes Estimate / Games / Food & Break.
     * Host only. Switching activity starts a fresh round.
     */
    public synchronized boolean changeMode(
            String sessionId,
            String rawMode) {

        if (!isHost(
                sessionId
        )) {

            return false;
        }

        String mode =
                rawMode == null
                        ? ""
                        : rawMode
                        .trim()
                        .toUpperCase(
                                Locale.ROOT
                        );

        if (!VALID_MODES.contains(
                mode
        )) {

            throw new IllegalArgumentException(
                    "Unknown activity."
            );
        }

        if (!currentMode.equals(
                mode
        )) {

            currentMode =
                    mode;

            resetCurrentRound();
        }

        return true;
    }

    /**
     * Host reveals the current activity.
     */
    public synchronized boolean reveal(
            String sessionId) {

        if (!isHost(
                sessionId
        )) {

            return false;
        }

        revealed =
                true;

        timerEndEpochMs =
                0L;

        return true;
    }

    /**
     * Host starts the next round in the same activity.
     */
    public synchronized boolean reset(
            String sessionId) {

        if (!isHost(
                sessionId
        )) {

            return false;
        }

        resetCurrentRound();

        return true;
    }

    /**
     * Host can give the room a fresh 30 seconds at any time
     * before reveal.
     */
    public synchronized boolean startTimer(
            String sessionId) {

        if (!isHost(
                sessionId
        ) ||
                revealed) {

            return false;
        }

        timerEndEpochMs =
                System.currentTimeMillis()
                        + TIMER_DURATION_MS;

        return true;
    }

    public synchronized List<Participant> getParticipants() {

        return new ArrayList<>(
                participants.values()
        );
    }

    public synchronized Participant getParticipant(
            String sessionId) {

        return participants.get(
                sessionId
        );
    }

    public synchronized boolean isRevealed() {
        return revealed;
    }

    public synchronized String getCurrentMode() {
        return currentMode;
    }

    public synchronized long getTimerEndEpochMs() {
        return timerEndEpochMs;
    }

    private Participant requireParticipant(
            String sessionId) {

        Participant participant =
                participants.get(
                        sessionId
                );

        if (participant == null) {

            throw new IllegalStateException(
                    "Please join the room first."
            );
        }

        return participant;
    }

    private boolean isHost(
            String sessionId) {

        return sessionId != null &&
                sessionId.equals(
                        hostSessionId
                );
    }

    private Set<String> allowedSelections() {

        return switch (currentMode) {

            case MODE_GAME ->
                    GAME_OPTIONS;

            case MODE_FOOD ->
                    FOOD_OPTIONS;

            default ->
                    ESTIMATE_OPTIONS;
        };
    }

    private void resetCurrentRound() {

        participants.values()
                .forEach(
                        Participant::clearSelection
                );

        revealed =
                false;

        timerEndEpochMs =
                0L;
    }

    private void resetWholeRoom() {

        hostSessionId =
                null;

        currentMode =
                MODE_ESTIMATE;

        revealed =
                false;

        timerEndEpochMs =
                0L;
    }
}