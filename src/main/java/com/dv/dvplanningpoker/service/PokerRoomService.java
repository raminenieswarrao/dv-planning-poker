package com.dv.dvplanningpoker.service;

import com.dv.dvplanningpoker.model.Participant;
import com.dv.dvplanningpoker.model.PokerRoom;
import org.springframework.stereotype.Service;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.text.Normalizer;
import java.util.ArrayList;
import java.util.Base64;
import java.util.HashMap;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.regex.Pattern;

/**
 * Secure in-memory multi-room service for Refinement Poker.
 *
 * Security model:
 *
 * - Every room is isolated.
 * - Server generates secure room codes.
 * - Creator becomes host of that room only.
 * - One WebSocket session may belong to only one room.
 * - Host actions are authorized server-side.
 * - Host can remove participants from their room.
 * - Participant names are strictly validated.
 * - Profanity / abusive participant names are rejected.
 * - Voting options are allowlisted.
 * - Room and participant counts are bounded.
 * - Empty and stale rooms are removed.
 *
 * No database is used.
 * Restarting the application clears all rooms.
 */
@Service
public class PokerRoomService {

    public static final String MODE_ESTIMATE = "ESTIMATE";
    public static final String MODE_GAME = "GAME";
    public static final String MODE_FOOD = "FOOD";

    /*
     * =========================================================
     * SECURITY LIMITS
     * =========================================================
     */

    private static final int MAX_PARTICIPANTS_PER_ROOM = 20;

    private static final int MAX_ACTIVE_ROOMS = 100;

    /**
     * Participant names must contain at least 3 characters.
     */
    private static final int MIN_NAME_LENGTH = 3;

    private static final int MAX_NAME_LENGTH = 40;

    /**
     * Reject huge raw names before normalization.
     */
    private static final int MAX_RAW_NAME_LENGTH = 80;

    private static final int MAX_SELECTION_INPUT_LENGTH = 32;

    private static final int MAX_MODE_INPUT_LENGTH = 20;

    private static final int MAX_ROOM_CODE_INPUT_LENGTH = 32;

    private static final int MAX_HOST_TOKEN_INPUT_LENGTH = 128;

    private static final int MAX_PARTICIPANT_ID_INPUT_LENGTH = 64;

    private static final long TIMER_DURATION_MS = 30_000L;

    /**
     * Room expires after 2 hours without activity.
     */
    private static final long ROOM_IDLE_TTL_MS =
            2L * 60L * 60L * 1000L;

    /**
     * Hard maximum room lifetime: 8 hours.
     */
    private static final long ROOM_MAX_LIFETIME_MS =
            8L * 60L * 60L * 1000L;

    /*
     * =========================================================
     * ROOM CODE SECURITY
     * =========================================================
     */

    /**
     * Excludes visually confusing characters:
     *
     * I
     * O
     * 0
     * 1
     */
    private static final String ROOM_CODE_ALPHABET =
            "ABCDEFGHJKLMNPQRSTUVWXYZ23456789";

    /**
     * Example:
     *
     * ABCD-EFGH-JKLM-NPQR
     */
    private static final int ROOM_CODE_SYMBOL_COUNT = 16;

    private static final int HOST_TOKEN_BYTES = 32;

    private static final Pattern ROOM_CODE_PATTERN =
            Pattern.compile(
                    "^[A-HJ-NP-Z2-9]{4}"
                            + "-[A-HJ-NP-Z2-9]{4}"
                            + "-[A-HJ-NP-Z2-9]{4}"
                            + "-[A-HJ-NP-Z2-9]{4}$"
            );

    /*
     * =========================================================
     * PARTICIPANT NAME SECURITY
     * =========================================================
     */

    /**
     * Allowed participant-name characters:
     *
     * letters
     * numbers
     * spaces
     * periods
     * apostrophes
     * hyphens
     *
     * HTML/JavaScript control characters are intentionally excluded.
     */
    private static final Pattern SAFE_NAME_PATTERN =
            Pattern.compile(
                    "^[A-Za-z0-9][A-Za-z0-9 .'-]*$"
            );

    /**
     * Block URL-looking participant names.
     */
    private static final Pattern URL_PATTERN =
            Pattern.compile(
                    "(?i)"
                            + "("
                            + "https?://"
                            + "|www\\."
                            + "|(?:[a-z0-9-]+\\.)+"
                            + "(?:com|net|org|io|co|dev|app|xyz|info|biz|me|tv)"
                            + "\\b"
                            + ")"
            );

    /**
     * Prevent visual impersonation of application roles.
     */
    private static final Set<String> RESERVED_NAMES =
            Set.of(
                    "HOST",
                    "ADMIN",
                    "ADMINISTRATOR",
                    "SYSTEM",
                    "MODERATOR",
                    "DV HOST",
                    "DV ADMIN",
                    "DV SYSTEM"
            );

    /*
     * =========================================================
     * PROFANITY / ABUSE FILTER
     * =========================================================
     *
     * Broad English workplace-safety list.
     *
     * Matching is word-aware to reduce false positives.
     *
     * Examples blocked:
     *
     * fuck
     * Sai fuck
     * fuck Sai
     * f.u.c.k
     * f-u-c-k
     * f u c k
     * b1tch
     * sh1t
     * a55hole
     *
     * Example NOT blocked:
     *
     * Shital
     *
     * because SHITAL is not exactly a blocked word.
     */

    private static final Set<String> BLOCKED_NAME_TERMS =
            Set.of(
                    "ANAL",
                    "ANILINGUS",
                    "ANUS",
                    "ARSE",
                    "ARSEHOLE",
                    "ASS",
                    "ASSHAT",
                    "ASSHOLE",
                    "ASSMUNCH",
                    "ASSWIPE",

                    "BASTARD",
                    "BEANER",
                    "BITCH",
                    "BITCHES",
                    "BLOWJOB",
                    "BOLLOCKS",
                    "BOOBS",
                    "BUKKAKE",
                    "BULLSHIT",

                    "CHINK",
                    "COCK",
                    "COCKSUCKER",
                    "COON",
                    "CRAP",
                    "CUM",
                    "CUMSHOT",
                    "CUNT",

                    "DAMN",
                    "DICKHEAD",
                    "DICKWAD",
                    "DILDO",
                    "DOUCHE",
                    "DOUCHEBAG",
                    "DUMBASS",

                    "DYKE",

                    "EJACULATE",
                    "EJACULATION",

                    "FAG",
                    "FAGGOT",
                    "FELLATIO",
                    "FCK",
                    "FUCK",
                    "FUCKED",
                    "FUCKER",
                    "FUCKING",
                    "FUK",
                    "PHUCK",

                    "GOOK",

                    "HANDJOB",
                    "HENTAI",

                    "IDIOT",
                    "IMBECILE",

                    "JACKASS",

                    "KIKE",
                    "KYS",

                    "MASTURBATE",
                    "MASTURBATION",
                    "MORON",
                    "MOTHERFUCKER",

                    "NAKED",
                    "NIGGA",
                    "NIGGER",
                    "NUDE",
                    "NUDES",

                    "ORGASM",

                    "PAKI",
                    "PEDO",
                    "PEDOPHILE",
                    "PENIS",
                    "PISS",
                    "PISSED",
                    "PORN",
                    "PORNO",
                    "PORNOGRAPHY",
                    "PRICK",
                    "PUSSY",

                    "RAGHEAD",
                    "RAPE",
                    "RAPIST",
                    "RETARD",
                    "RETARDED",
                    "RIMJOB",

                    "SCUMBAG",
                    "SEX",
                    "SHIT",
                    "SHITHEAD",
                    "SHITTY",
                    "SLUT",
                    "SPIC",
                    "STFU",
                    "STUPID",
                    "SUCKER",

                    "TITS",
                    "TITTY",
                    "TITTIES",
                    "TOWELHEAD",
                    "TRANNY",
                    "TWAT",

                    "VAGINA",
                    "VULVA",

                    "WANK",
                    "WANKER",
                    "WETBACK",
                    "WHORE",
                    "WTF",

                    "XXX"
            );

    /*
     * =========================================================
     * ACTIVITY ALLOWLISTS
     * =========================================================
     */

    private static final Set<String> VALID_MODES =
            Set.of(
                    MODE_ESTIMATE,
                    MODE_GAME,
                    MODE_FOOD
            );

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

    /*
     * =========================================================
     * SERVER STATE
     * =========================================================
     */

    /**
     * roomCode -> room
     */
    private final Map<String, PokerRoom> rooms =
            new LinkedHashMap<>();

    /**
     * WebSocket session ID -> room code.
     *
     * Clients do not control this mapping.
     */
    private final Map<String, String> sessionRooms =
            new HashMap<>();

    private final SecureRandom secureRandom =
            new SecureRandom();

    /*
     * =========================================================
     * CREATE ROOM
     * =========================================================
     */

    public synchronized CreateRoomResult createRoom(
            String sessionId,
            String rawName) {

        cleanupExpiredRooms();

        validateSessionId(sessionId);

        ensureSessionNotAlreadyJoined(sessionId);

        if (rooms.size() >= MAX_ACTIVE_ROOMS) {

            throw new IllegalStateException(
                    "Too many active rooms. Please try again later."
            );
        }

        String name =
                validateAndNormalizeName(rawName);

        String roomCode =
                generateUniqueRoomCode();

        String hostToken =
                generateHostToken();

        byte[] hostTokenHash =
                sha256(hostToken);

        PokerRoom room =
                new PokerRoom(
                        roomCode,
                        hostTokenHash,
                        sessionId
                );

        Participant host =
                new Participant(
                        sessionId,
                        name,
                        true
                );

        room.getParticipantMap()
                .put(
                        sessionId,
                        host
                );

        rooms.put(
                roomCode,
                room
        );

        sessionRooms.put(
                sessionId,
                roomCode
        );

        room.touch();

        return new CreateRoomResult(
                roomCode,
                hostToken,
                host
        );
    }

    /*
     * =========================================================
     * JOIN ROOM
     * =========================================================
     */

    public synchronized JoinRoomResult joinRoom(
            String sessionId,
            String rawRoomCode,
            String rawName) {

        cleanupExpiredRooms();

        validateSessionId(sessionId);

        ensureSessionNotAlreadyJoined(sessionId);

        String roomCode =
                normalizeRoomCode(rawRoomCode);

        PokerRoom room =
                rooms.get(roomCode);

        if (room == null) {

            throw new IllegalArgumentException(
                    "Room not found or unavailable."
            );
        }

        if (room.getParticipantCount()
                >= MAX_PARTICIPANTS_PER_ROOM) {

            throw new IllegalStateException(
                    "Room not found or unavailable."
            );
        }

        String name =
                validateAndNormalizeName(rawName);

        String canonicalName =
                canonicalizeName(name);

        boolean duplicateName =
                room.getParticipants()
                        .stream()
                        .map(Participant::getName)
                        .map(this::canonicalizeName)
                        .anyMatch(canonicalName::equals);

        if (duplicateName) {

            throw new IllegalArgumentException(
                    "That name is already in the room."
            );
        }

        Participant participant =
                new Participant(
                        sessionId,
                        name,
                        false
                );

        room.getParticipantMap()
                .put(
                        sessionId,
                        participant
                );

        sessionRooms.put(
                sessionId,
                roomCode
        );

        room.touch();

        return new JoinRoomResult(
                roomCode,
                participant
        );
    }

    /*
     * =========================================================
     * HOST REMOVE PARTICIPANT
     * =========================================================
     */

    /**
     * Removes one participant from the host's room.
     *
     * The browser supplies the safe public participantId.
     *
     * Internal WebSocket session IDs are never supplied by clients.
     */
    public synchronized RemoveParticipantResult removeParticipant(
            String requesterSessionId,
            String rawParticipantId) {

        PokerRoom room =
                requireRoomForSession(
                        requesterSessionId
                );

        if (!isHost(
                room,
                requesterSessionId
        )) {

            throw new IllegalStateException(
                    "Only the host can remove participants."
            );
        }

        String participantId =
                validateParticipantId(
                        rawParticipantId
                );

        Participant target =
                room.getParticipants()
                        .stream()
                        .filter(
                                participant ->
                                        participant
                                                .getParticipantId()
                                                .equals(
                                                        participantId
                                                )
                        )
                        .findFirst()
                        .orElseThrow(
                                () ->
                                        new IllegalArgumentException(
                                                "Participant is no longer in the room."
                                        )
                        );

        /*
         * Never allow the host to remove themselves.
         */
        if (target.isHost()
                ||
                requesterSessionId.equals(
                        target.getSessionId()
                )) {

            throw new IllegalArgumentException(
                    "The host cannot remove themselves."
            );
        }

        String targetSessionId =
                target.getSessionId();

        room.getParticipantMap()
                .remove(
                        targetSessionId
                );

        sessionRooms.remove(
                targetSessionId,
                room.getRoomCode()
        );

        room.touch();

        return new RemoveParticipantResult(
                room.getRoomCode(),
                targetSessionId,
                new ArrayList<>(
                        room.getParticipantMap()
                                .keySet()
                )
        );
    }

    /*
     * =========================================================
     * LEAVE ROOM
     * =========================================================
     */

    public synchronized LeaveResult leave(
            String sessionId) {

        if (sessionId == null
                ||
                sessionId.isBlank()) {

            return LeaveResult.notJoined();
        }

        String roomCode =
                sessionRooms.remove(
                        sessionId
                );

        if (roomCode == null) {

            return LeaveResult.notJoined();
        }

        PokerRoom room =
                rooms.get(
                        roomCode
                );

        if (room == null) {

            return new LeaveResult(
                    roomCode,
                    false,
                    List.of()
            );
        }

        boolean hostLeaving =
                sessionId.equals(
                        room.getHostSessionId()
                );

        room.getParticipantMap()
                .remove(
                        sessionId
                );

        /*
         * Security decision:
         *
         * Closing the host connection destroys the room instead
         * of automatically promoting another participant.
         */
        if (hostLeaving) {

            List<String> affectedSessions =
                    new ArrayList<>(
                            room.getParticipantMap()
                                    .keySet()
                    );

            closeRoom(room);

            return new LeaveResult(
                    roomCode,
                    true,
                    affectedSessions
            );
        }

        if (room.isEmpty()) {

            closeRoom(room);

            return new LeaveResult(
                    roomCode,
                    true,
                    List.of()
            );
        }

        room.touch();

        return new LeaveResult(
                roomCode,
                false,
                new ArrayList<>(
                        room.getParticipantMap()
                                .keySet()
                )
        );
    }

    /*
     * =========================================================
     * VOTING
     * =========================================================
     */

    public synchronized void vote(
            String sessionId,
            String rawSelection) {

        PokerRoom room =
                requireRoomForSession(
                        sessionId
                );

        Participant participant =
                requireParticipant(
                        room,
                        sessionId
                );

        if (room.isRevealed()) {

            throw new IllegalStateException(
                    "This round has already been revealed."
            );
        }

        if (rawSelection == null
                ||
                rawSelection.length()
                        > MAX_SELECTION_INPUT_LENGTH) {

            throw new IllegalArgumentException(
                    "Invalid selection."
            );
        }

        String selection =
                rawSelection
                        .trim()
                        .toUpperCase(
                                Locale.ROOT
                        );

        if (!allowedSelections(
                room.getCurrentMode()
        ).contains(selection)) {

            throw new IllegalArgumentException(
                    "Invalid selection for the current activity."
            );
        }

        participant.setSelection(
                selection
        );

        room.touch();
    }

    /*
     * =========================================================
     * CHANGE MODE
     * =========================================================
     */

    public synchronized boolean changeMode(
            String sessionId,
            String rawMode) {

        PokerRoom room =
                requireRoomForSession(
                        sessionId
                );

        if (!isHost(
                room,
                sessionId
        )) {

            return false;
        }

        if (rawMode == null
                ||
                rawMode.length()
                        > MAX_MODE_INPUT_LENGTH) {

            throw new IllegalArgumentException(
                    "Unknown activity."
            );
        }

        String mode =
                rawMode
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

        if (!room.getCurrentMode()
                .equals(mode)) {

            room.setCurrentMode(
                    mode
            );

            room.resetRound();
        }

        room.touch();

        return true;
    }

    /*
     * =========================================================
     * REVEAL
     * =========================================================
     */

    public synchronized boolean reveal(
            String sessionId) {

        PokerRoom room =
                requireRoomForSession(
                        sessionId
                );

        if (!isHost(
                room,
                sessionId
        )) {

            return false;
        }

        room.setRevealed(
                true
        );

        room.setTimerEndEpochMs(
                0L
        );

        room.touch();

        return true;
    }

    /*
     * =========================================================
     * RESET
     * =========================================================
     */

    public synchronized boolean reset(
            String sessionId) {

        PokerRoom room =
                requireRoomForSession(
                        sessionId
                );

        if (!isHost(
                room,
                sessionId
        )) {

            return false;
        }

        room.resetRound();

        room.touch();

        return true;
    }

    /*
     * =========================================================
     * TIMER
     * =========================================================
     */

    public synchronized boolean startTimer(
            String sessionId) {

        PokerRoom room =
                requireRoomForSession(
                        sessionId
                );

        if (!isHost(
                room,
                sessionId
        )) {

            return false;
        }

        if (room.isRevealed()) {

            return false;
        }

        room.setTimerEndEpochMs(
                System.currentTimeMillis()
                        +
                        TIMER_DURATION_MS
        );

        room.touch();

        return true;
    }

    /*
     * =========================================================
     * ROOM STATE
     * =========================================================
     */

    public synchronized String getRoomCodeForSession(
            String sessionId) {

        if (sessionId == null) {
            return null;
        }

        cleanupExpiredRooms();

        return sessionRooms.get(
                sessionId
        );
    }

    public synchronized List<Participant> getParticipants(
            String sessionId) {

        PokerRoom room =
                requireRoomForSession(
                        sessionId
                );

        return room.getParticipants();
    }

    public synchronized Participant getParticipant(
            String sessionId) {

        if (sessionId == null) {
            return null;
        }

        PokerRoom room =
                getRoomForSessionOrNull(
                        sessionId
                );

        if (room == null) {
            return null;
        }

        return room.getParticipant(
                sessionId
        );
    }

    public synchronized boolean isRevealed(
            String sessionId) {

        return requireRoomForSession(
                sessionId
        ).isRevealed();
    }

    public synchronized String getCurrentMode(
            String sessionId) {

        return requireRoomForSession(
                sessionId
        ).getCurrentMode();
    }

    public synchronized long getTimerEndEpochMs(
            String sessionId) {

        return requireRoomForSession(
                sessionId
        ).getTimerEndEpochMs();
    }

    public synchronized boolean isHostSession(
            String sessionId) {

        PokerRoom room =
                getRoomForSessionOrNull(
                        sessionId
                );

        return room != null
                &&
                isHost(
                        room,
                        sessionId
                );
    }

    /**
     * Returns WebSocket session IDs for the caller's own room.
     *
     * Used only internally by PokerWebSocketHandler.
     */
    public synchronized List<String> getSessionIdsForRoom(
            String sessionId) {

        PokerRoom room =
                requireRoomForSession(
                        sessionId
                );

        return new ArrayList<>(
                room.getParticipantMap()
                        .keySet()
        );
    }

    /*
     * =========================================================
     * HOST TOKEN
     * =========================================================
     */

    public synchronized boolean verifyHostToken(
            String sessionId,
            String rawHostToken) {

        if (sessionId == null
                ||
                rawHostToken == null
                ||
                rawHostToken.isBlank()
                ||
                rawHostToken.length()
                        > MAX_HOST_TOKEN_INPUT_LENGTH) {

            return false;
        }

        PokerRoom room =
                getRoomForSessionOrNull(
                        sessionId
                );

        if (room == null) {
            return false;
        }

        if (!sessionId.equals(
                room.getHostSessionId()
        )) {

            return false;
        }

        byte[] suppliedHash =
                sha256(
                        rawHostToken
                );

        return MessageDigest.isEqual(
                suppliedHash,
                room.getHostTokenHash()
        );
    }

    /*
     * =========================================================
     * ROOM CODE GENERATION
     * =========================================================
     */

    private String generateUniqueRoomCode() {

        for (int attempt = 0;
             attempt < 100;
             attempt++) {

            StringBuilder raw =
                    new StringBuilder(
                            ROOM_CODE_SYMBOL_COUNT
                    );

            for (int index = 0;
                 index < ROOM_CODE_SYMBOL_COUNT;
                 index++) {

                raw.append(
                        ROOM_CODE_ALPHABET.charAt(
                                secureRandom.nextInt(
                                        ROOM_CODE_ALPHABET.length()
                                )
                        )
                );
            }

            String formatted =
                    formatRoomCode(
                            raw.toString()
                    );

            if (!rooms.containsKey(
                    formatted
            )) {

                return formatted;
            }
        }

        throw new IllegalStateException(
                "Unable to create a secure room. Please try again."
        );
    }

    private String formatRoomCode(
            String raw) {

        return raw.substring(
                0,
                4
        )
                +
                "-"
                +
                raw.substring(
                        4,
                        8
                )
                +
                "-"
                +
                raw.substring(
                        8,
                        12
                )
                +
                "-"
                +
                raw.substring(
                        12,
                        16
                );
    }

    private String normalizeRoomCode(
            String rawRoomCode) {

        if (rawRoomCode == null
                ||
                rawRoomCode.isBlank()
                ||
                rawRoomCode.length()
                        > MAX_ROOM_CODE_INPUT_LENGTH) {

            throw new IllegalArgumentException(
                    "Room not found or unavailable."
            );
        }

        String cleaned =
                rawRoomCode
                        .trim()
                        .toUpperCase(
                                Locale.ROOT
                        )
                        .replace(
                                "-",
                                ""
                        )
                        .replace(
                                " ",
                                ""
                        );

        if (cleaned.length()
                != ROOM_CODE_SYMBOL_COUNT) {

            throw new IllegalArgumentException(
                    "Room not found or unavailable."
            );
        }

        for (int index = 0;
             index < cleaned.length();
             index++) {

            if (ROOM_CODE_ALPHABET.indexOf(
                    cleaned.charAt(index)
            ) < 0) {

                throw new IllegalArgumentException(
                        "Room not found or unavailable."
                );
            }
        }

        String formatted =
                formatRoomCode(
                        cleaned
                );

        if (!ROOM_CODE_PATTERN
                .matcher(
                        formatted
                )
                .matches()) {

            throw new IllegalArgumentException(
                    "Room not found or unavailable."
            );
        }

        return formatted;
    }

    /*
     * =========================================================
     * HOST TOKEN GENERATION
     * =========================================================
     */

    private String generateHostToken() {

        byte[] bytes =
                new byte[
                        HOST_TOKEN_BYTES
                        ];

        secureRandom.nextBytes(
                bytes
        );

        return Base64
                .getUrlEncoder()
                .withoutPadding()
                .encodeToString(
                        bytes
                );
    }

    private byte[] sha256(
            String value) {

        try {

            MessageDigest digest =
                    MessageDigest.getInstance(
                            "SHA-256"
                    );

            return digest.digest(
                    value.getBytes(
                            StandardCharsets.UTF_8
                    )
            );

        } catch (NoSuchAlgorithmException ex) {

            throw new IllegalStateException(
                    "Secure hashing is unavailable.",
                    ex
            );
        }
    }

    /*
     * =========================================================
     * PARTICIPANT NAME VALIDATION
     * =========================================================
     */

    private String validateAndNormalizeName(
            String rawName) {

        if (rawName == null) {

            throw new IllegalArgumentException(
                    "Please enter your name."
            );
        }

        /*
         * Reject oversized raw input before normalization.
         */
        if (rawName.length()
                > MAX_RAW_NAME_LENGTH) {

            throw new IllegalArgumentException(
                    "Name must be 40 characters or fewer."
            );
        }

        String name =
                Normalizer.normalize(
                                rawName,
                                Normalizer.Form.NFKC
                        )
                        .trim();

        /*
         * Collapse repeated spaces.
         */
        while (name.contains(
                "  "
        )) {

            name =
                    name.replace(
                            "  ",
                            " "
                    );
        }

        if (name.isBlank()) {

            throw new IllegalArgumentException(
                    "Please enter your name."
            );
        }

        /*
         * User requested a minimum of 3 characters.
         */
        if (name.length()
                < MIN_NAME_LENGTH) {

            throw new IllegalArgumentException(
                    "Name must be at least 3 characters."
            );
        }

        if (name.length()
                > MAX_NAME_LENGTH) {

            throw new IllegalArgumentException(
                    "Name must be 40 characters or fewer."
            );
        }

        /*
         * Reject controls, invisible formatting characters,
         * bidirectional controls and line separators.
         */
        boolean containsUnsafeUnicode =
                name.codePoints()
                        .anyMatch(
                                codePoint -> {

                                    int type =
                                            Character.getType(
                                                    codePoint
                                            );

                                    return Character.isISOControl(
                                            codePoint
                                    )
                                            ||
                                            type
                                                    == Character.FORMAT
                                            ||
                                            type
                                                    == Character.LINE_SEPARATOR
                                            ||
                                            type
                                                    == Character.PARAGRAPH_SEPARATOR;
                                }
                        );

        if (containsUnsafeUnicode) {

            throw new IllegalArgumentException(
                    "Name contains unsupported characters."
            );
        }

        /*
         * Strict character allowlist.
         */
        if (!SAFE_NAME_PATTERN
                .matcher(
                        name
                )
                .matches()) {

            throw new IllegalArgumentException(
                    "Name may contain only letters, numbers, spaces, periods, apostrophes, and hyphens."
            );
        }

        /*
         * Do not allow URLs in participant names.
         */
        if (URL_PATTERN
                .matcher(
                        name
                )
                .find()) {

            throw new IllegalArgumentException(
                    "URLs are not allowed as participant names."
            );
        }

        String canonical =
                canonicalizeName(
                        name
                );

        /*
         * Prevent impersonation of application roles.
         */
        if (RESERVED_NAMES.contains(
                canonical
        )) {

            throw new IllegalArgumentException(
                    "That participant name is reserved."
            );
        }

        /*
         * Reject profanity / abusive / inappropriate names.
         *
         * Intentionally return a generic message so the filter
         * does not tell an attacker exactly which term matched.
         */
        if (containsBlockedNameContent(
                name
        )) {

            throw new IllegalArgumentException(
                    "Please use an appropriate participant name."
            );
        }

        return name;
    }

    /*
     * =========================================================
     * PROFANITY MATCHING
     * =========================================================
     */

    /**
     * Detects blocked words while avoiding simple substring
     * false positives.
     *
     * Examples:
     *
     * "Sai fuck"
     * tokens: SAI, FUCK
     * -> blocked
     *
     * "f.u.c.k"
     * tokens: F, U, C, K
     * combined: FUCK
     * -> blocked
     *
     * "Shital"
     * token: SHITAL
     * -> allowed
     *
     * because SHITAL is not exactly SHIT.
     */
    private boolean containsBlockedNameContent(
            String name) {

        String normalized =
                normalizeForModeration(
                        name
                );

        if (normalized.isBlank()) {
            return false;
        }

        String[] tokens =
                normalized.split(
                        "\\s+"
                );

        /*
         * First check individual words.
         */
        for (String token : tokens) {

            if (BLOCKED_NAME_TERMS.contains(
                    token
            )) {

                return true;
            }
        }

        /*
         * Then combine adjacent pieces.
         *
         * This catches:
         *
         * f.u.c.k
         * f-u-c-k
         * f u c k
         * fu.ck
         * mother-fucker
         *
         * without doing arbitrary substring matching inside
         * legitimate names.
         */
        for (int start = 0;
             start < tokens.length;
             start++) {

            StringBuilder combined =
                    new StringBuilder();

            /*
             * Participant names are short, but eight adjacent
             * chunks is already more than enough for evasion
             * detection.
             */
            int maxEnd =
                    Math.min(
                            tokens.length,
                            start + 8
                    );

            for (int end = start;
                 end < maxEnd;
                 end++) {

                combined.append(
                        tokens[end]
                );

                if (end > start
                        &&
                        BLOCKED_NAME_TERMS.contains(
                                combined.toString()
                        )) {

                    return true;
                }
            }
        }

        return false;
    }

    /**
     * Normalizes common leetspeak used to bypass profanity
     * filters.
     *
     * Examples:
     *
     * sh1t     -> SHIT
     * b1tch    -> BITCH
     * a55hole  -> ASSHOLE
     * p0rn     -> PORN
     */
    private String normalizeForModeration(
            String value) {

        if (value == null) {
            return "";
        }

        String normalized =
                Normalizer.normalize(
                                value,
                                Normalizer.Form.NFKC
                        )
                        .toUpperCase(
                                Locale.ROOT
                        );

        StringBuilder result =
                new StringBuilder(
                        normalized.length()
                );

        for (int index = 0;
             index < normalized.length();
             index++) {

            char character =
                    normalized.charAt(
                            index
                    );

            switch (character) {

                case '0' ->
                        result.append('O');

                case '1' ->
                        result.append('I');

                case '3' ->
                        result.append('E');

                case '4' ->
                        result.append('A');

                case '5' ->
                        result.append('S');

                case '7' ->
                        result.append('T');

                case '8' ->
                        result.append('B');

                case '9' ->
                        result.append('G');

                /*
                 * Treat allowed separators as word boundaries.
                 */
                case '.',
                     '-',
                     '\'' ->
                        result.append(' ');

                default ->
                        result.append(
                                character
                        );
            }
        }

        String moderationText =
                result.toString()
                        .trim();

        /*
         * Collapse repeated whitespace.
         */
        while (moderationText.contains(
                "  "
        )) {

            moderationText =
                    moderationText.replace(
                            "  ",
                            " "
                    );
        }

        return moderationText;
    }

    private String canonicalizeName(
            String name) {

        if (name == null) {
            return "";
        }

        return Normalizer.normalize(
                        name,
                        Normalizer.Form.NFKC
                )
                .trim()
                .toUpperCase(
                        Locale.ROOT
                );
    }

    /*
     * =========================================================
     * PARTICIPANT ID VALIDATION
     * =========================================================
     */

    private String validateParticipantId(
            String rawParticipantId) {

        if (rawParticipantId == null
                ||
                rawParticipantId.isBlank()
                ||
                rawParticipantId.length()
                        > MAX_PARTICIPANT_ID_INPUT_LENGTH) {

            throw new IllegalArgumentException(
                    "Invalid participant."
            );
        }

        String participantId =
                rawParticipantId.trim();

        try {

            return UUID.fromString(
                            participantId
                    )
                    .toString();

        } catch (IllegalArgumentException ex) {

            throw new IllegalArgumentException(
                    "Invalid participant."
            );
        }
    }

    /*
     * =========================================================
     * SESSION / ROOM AUTHORIZATION
     * =========================================================
     */

    private void validateSessionId(
            String sessionId) {

        if (sessionId == null
                ||
                sessionId.isBlank()
                ||
                sessionId.length() > 200) {

            throw new IllegalArgumentException(
                    "Invalid WebSocket session."
            );
        }
    }

    private void ensureSessionNotAlreadyJoined(
            String sessionId) {

        if (sessionRooms.containsKey(
                sessionId
        )) {

            throw new IllegalStateException(
                    "This connection has already joined a room."
            );
        }
    }

    private PokerRoom requireRoomForSession(
            String sessionId) {

        validateSessionId(
                sessionId
        );

        cleanupExpiredRooms();

        PokerRoom room =
                getRoomForSessionOrNull(
                        sessionId
                );

        if (room == null) {

            throw new IllegalStateException(
                    "Room is no longer available."
            );
        }

        if (!room.containsSession(
                sessionId
        )) {

            /*
             * Defensive state-consistency cleanup.
             */
            sessionRooms.remove(
                    sessionId
            );

            throw new IllegalStateException(
                    "Room is no longer available."
            );
        }

        return room;
    }

    private PokerRoom getRoomForSessionOrNull(
            String sessionId) {

        if (sessionId == null) {
            return null;
        }

        String roomCode =
                sessionRooms.get(
                        sessionId
                );

        if (roomCode == null) {
            return null;
        }

        return rooms.get(
                roomCode
        );
    }

    private Participant requireParticipant(
            PokerRoom room,
            String sessionId) {

        Participant participant =
                room.getParticipant(
                        sessionId
                );

        if (participant == null) {

            throw new IllegalStateException(
                    "Please join the room first."
            );
        }

        return participant;
    }

    /**
     * Host authorization comes exclusively from server-side state.
     */
    private boolean isHost(
            PokerRoom room,
            String sessionId) {

        return room != null
                &&
                sessionId != null
                &&
                sessionId.equals(
                        room.getHostSessionId()
                )
                &&
                room.containsSession(
                        sessionId
                );
    }

    /*
     * =========================================================
     * ALLOWED SELECTIONS
     * =========================================================
     */

    private Set<String> allowedSelections(
            String mode) {

        return switch (mode) {

            case MODE_GAME ->
                    GAME_OPTIONS;

            case MODE_FOOD ->
                    FOOD_OPTIONS;

            case MODE_ESTIMATE ->
                    ESTIMATE_OPTIONS;

            default ->
                    Set.of();
        };
    }

    /*
     * =========================================================
     * ROOM CLEANUP
     * =========================================================
     */

    private void cleanupExpiredRooms() {

        long now =
                System.currentTimeMillis();

        Iterator<Map.Entry<String, PokerRoom>> iterator =
                rooms.entrySet()
                        .iterator();

        while (iterator.hasNext()) {

            Map.Entry<String, PokerRoom> entry =
                    iterator.next();

            PokerRoom room =
                    entry.getValue();

            boolean idleExpired =
                    now
                            -
                            room.getLastActivityEpochMs()
                            >
                            ROOM_IDLE_TTL_MS;

            boolean lifetimeExpired =
                    now
                            -
                            room.getCreatedAtEpochMs()
                            >
                            ROOM_MAX_LIFETIME_MS;

            if (!idleExpired
                    &&
                    !lifetimeExpired) {

                continue;
            }

            for (String sessionId :
                    room.getParticipantMap()
                            .keySet()) {

                sessionRooms.remove(
                        sessionId,
                        room.getRoomCode()
                );
            }

            iterator.remove();
        }
    }

    /**
     * Completely destroys one room.
     */
    private void closeRoom(
            PokerRoom room) {

        if (room == null) {
            return;
        }

        for (String sessionId :
                room.getParticipantMap()
                        .keySet()) {

            sessionRooms.remove(
                    sessionId,
                    room.getRoomCode()
            );
        }

        rooms.remove(
                room.getRoomCode(),
                room
        );

        room.getParticipantMap()
                .clear();
    }

    /*
     * =========================================================
     * RESULT TYPES
     * =========================================================
     */

    public record CreateRoomResult(
            String roomCode,
            String hostToken,
            Participant participant) {
    }

    public record JoinRoomResult(
            String roomCode,
            Participant participant) {
    }

    /**
     * targetSessionId is SERVER ONLY.
     *
     * Never send it to the browser.
     */
    public record RemoveParticipantResult(
            String roomCode,
            String targetSessionId,
            List<String> remainingSessionIds) {
    }

    public record LeaveResult(
            String roomCode,
            boolean roomClosed,
            List<String> affectedSessionIds) {

        public static LeaveResult notJoined() {

            return new LeaveResult(
                    null,
                    false,
                    List.of()
            );
        }
    }
}