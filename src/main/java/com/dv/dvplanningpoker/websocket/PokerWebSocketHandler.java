package com.dv.dvplanningpoker.websocket;

import com.dv.dvplanningpoker.model.Participant;
import com.dv.dvplanningpoker.service.PokerRoomService;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.CloseStatus;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;
import org.springframework.web.socket.handler.TextWebSocketHandler;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Secure WebSocket endpoint for Refinement Poker.
 *
 * Security responsibilities:
 *
 * - limits total open WebSocket connections
 * - limits inbound WebSocket message size
 * - rate-limits messages per connection
 * - rate-limits CREATE_ROOM / JOIN_ROOM attempts
 * - validates JSON structure
 * - accepts only known actions
 * - never trusts client-supplied host information
 * - never broadcasts state across rooms
 * - never exposes WebSocket session IDs
 * - exposes participant IDs only where needed
 * - allows only the real room host to remove participants
 * - never broadcasts the private host token
 * - handles malformed requests without exposing server details
 */
@Component
public class PokerWebSocketHandler
        extends TextWebSocketHandler {

    /*
     * =========================================================
     * SECURITY LIMITS
     * =========================================================
     */

    private static final int MAX_OPEN_CONNECTIONS =
            200;

    private static final int MAX_TEXT_MESSAGE_SIZE =
            4 * 1024;

    private static final int MAX_MESSAGES_PER_WINDOW =
            25;

    private static final long MESSAGE_RATE_WINDOW_MS =
            10_000L;

    private static final int MAX_ROOM_ENTRY_ATTEMPTS =
            5;

    private static final long ROOM_ENTRY_WINDOW_MS =
            60_000L;

    private static final int MAX_PROTOCOL_VIOLATIONS =
            5;

    private static final int MAX_ACTION_LENGTH =
            32;

    private static final int MAX_NAME_INPUT_LENGTH =
            80;

    private static final int MAX_ROOM_CODE_INPUT_LENGTH =
            32;

    private static final int MAX_MODE_INPUT_LENGTH =
            20;

    private static final int MAX_SELECTION_INPUT_LENGTH =
            32;

    private static final int MAX_PARTICIPANT_ID_INPUT_LENGTH =
            64;

    /*
     * =========================================================
     * DEPENDENCIES / STATE
     * =========================================================
     */

    private final PokerRoomService pokerRoomService;

    private final ObjectMapper objectMapper;

    /**
     * All currently connected WebSocket sessions.
     */
    private final Map<String, WebSocketSession> sessions =
            new ConcurrentHashMap<>();

    /**
     * Per-connection abuse protection.
     */
    private final Map<String, ConnectionSecurityState> securityStates =
            new ConcurrentHashMap<>();

    public PokerWebSocketHandler(
            PokerRoomService pokerRoomService,
            ObjectMapper objectMapper) {

        this.pokerRoomService =
                pokerRoomService;

        this.objectMapper =
                objectMapper;
    }

    /*
     * =========================================================
     * CONNECTION
     * =========================================================
     */

    @Override
    public void afterConnectionEstablished(
            WebSocketSession session) throws Exception {

        if (sessions.size() >=
                MAX_OPEN_CONNECTIONS) {

            session.close(
                    CloseStatus.POLICY_VIOLATION
            );

            return;
        }

        session.setTextMessageSizeLimit(
                MAX_TEXT_MESSAGE_SIZE
        );

        sessions.put(
                session.getId(),
                session
        );

        securityStates.put(
                session.getId(),
                new ConnectionSecurityState()
        );
    }

    /*
     * =========================================================
     * INBOUND MESSAGE
     * =========================================================
     */

    @Override
    protected void handleTextMessage(
            WebSocketSession session,
            TextMessage message) {

        if (session == null ||
                !session.isOpen()) {

            return;
        }

        ConnectionSecurityState securityState =
                securityStates.get(
                        session.getId()
                );

        if (securityState == null) {

            closeQuietly(
                    session,
                    CloseStatus.POLICY_VIOLATION
            );

            return;
        }

        String payload =
                message.getPayload();

        if (payload == null ||
                payload.length() >
                        MAX_TEXT_MESSAGE_SIZE) {

            sendError(
                    session,
                    "Invalid request."
            );

            closeQuietly(
                    session,
                    CloseStatus.POLICY_VIOLATION
            );

            return;
        }

        long now =
                System.currentTimeMillis();

        if (!securityState.allowMessage(
                now
        )) {

            sendError(
                    session,
                    "Too many requests. Please reconnect and try again."
            );

            closeQuietly(
                    session,
                    CloseStatus.POLICY_VIOLATION
            );

            return;
        }

        try {

            JsonNode json =
                    objectMapper.readTree(
                            payload
                    );

            if (json == null ||
                    !json.isObject()) {

                protocolViolation(
                        session,
                        securityState,
                        "Invalid request."
                );

                return;
            }

            String action =
                    requiredText(
                            json,
                            "action",
                            MAX_ACTION_LENGTH,
                            false
                    )
                            .toUpperCase();

            switch (action) {

                /*
                 * =================================================
                 * CREATE ROOM
                 * =================================================
                 */

                case "CREATE_ROOM" -> {

                    if (!securityState.allowRoomEntryAttempt(
                            now
                    )) {

                        sendError(
                                session,
                                "Too many room attempts. Please wait and try again."
                        );

                        return;
                    }

                    String name =
                            requiredText(
                                    json,
                                    "name",
                                    MAX_NAME_INPUT_LENGTH,
                                    false
                            );

                    PokerRoomService.CreateRoomResult result =
                            pokerRoomService.createRoom(
                                    session.getId(),
                                    name
                            );

                    sendRoomCreated(
                            session,
                            result.roomCode()
                    );

                    broadcastRoomState(
                            session.getId()
                    );
                }

                /*
                 * =================================================
                 * JOIN ROOM
                 * =================================================
                 */

                case "JOIN_ROOM" -> {

                    if (!securityState.allowRoomEntryAttempt(
                            now
                    )) {

                        sendError(
                                session,
                                "Too many room attempts. Please wait and try again."
                        );

                        return;
                    }

                    String name =
                            requiredText(
                                    json,
                                    "name",
                                    MAX_NAME_INPUT_LENGTH,
                                    false
                            );

                    String roomCode =
                            requiredText(
                                    json,
                                    "roomCode",
                                    MAX_ROOM_CODE_INPUT_LENGTH,
                                    false
                            );

                    PokerRoomService.JoinRoomResult result =
                            pokerRoomService.joinRoom(
                                    session.getId(),
                                    roomCode,
                                    name
                            );

                    sendRoomJoined(
                            session,
                            result.roomCode()
                    );

                    broadcastRoomState(
                            session.getId()
                    );
                }

                /*
                 * =================================================
                 * REMOVE PARTICIPANT
                 * =================================================
                 *
                 * Client sends only the public participantId.
                 *
                 * The service verifies:
                 *
                 * - requester is actually host
                 * - target belongs to requester's room
                 * - target is not the host
                 */

                case "REMOVE_PARTICIPANT" -> {

                    String participantId =
                            requiredText(
                                    json,
                                    "participantId",
                                    MAX_PARTICIPANT_ID_INPUT_LENGTH,
                                    false
                            );

                    PokerRoomService.RemoveParticipantResult result =
                            pokerRoomService.removeParticipant(
                                    session.getId(),
                                    participantId
                            );

                    /*
                     * Locate target socket using the SERVER-ONLY
                     * session ID returned by PokerRoomService.
                     *
                     * This ID is never sent to a browser.
                     */
                    WebSocketSession targetSession =
                            sessions.get(
                                    result.targetSessionId()
                            );

                    if (targetSession != null &&
                            targetSession.isOpen()) {

                        /*
                         * Tell the removed browser why it is leaving.
                         */
                        sendRemovedFromRoom(
                                targetSession
                        );

                        /*
                         * Then disconnect that socket.
                         */
                        closeQuietly(
                                targetSession,
                                CloseStatus.NORMAL
                        );
                    }

                    /*
                     * Update only remaining users in this room.
                     */
                    broadcastUsingRemainingSessions(
                            result.remainingSessionIds()
                    );
                }

                /*
                 * =================================================
                 * VOTE
                 * =================================================
                 */

                case "VOTE" -> {

                    String selection =
                            requiredText(
                                    json,
                                    "selection",
                                    MAX_SELECTION_INPUT_LENGTH,
                                    false
                            );

                    pokerRoomService.vote(
                            session.getId(),
                            selection
                    );

                    broadcastRoomState(
                            session.getId()
                    );
                }

                /*
                 * =================================================
                 * CHANGE MODE
                 * =================================================
                 */

                case "CHANGE_MODE" -> {

                    String mode =
                            requiredText(
                                    json,
                                    "mode",
                                    MAX_MODE_INPUT_LENGTH,
                                    false
                            );

                    boolean changed =
                            pokerRoomService.changeMode(
                                    session.getId(),
                                    mode
                            );

                    if (!changed) {

                        sendError(
                                session,
                                "Only the host can change the activity."
                        );

                        return;
                    }

                    broadcastRoomState(
                            session.getId()
                    );
                }

                /*
                 * =================================================
                 * START TIMER
                 * =================================================
                 */

                case "START_TIMER" -> {

                    boolean started =
                            pokerRoomService.startTimer(
                                    session.getId()
                            );

                    if (!started) {

                        sendError(
                                session,
                                "Only the host can start the timer before reveal."
                        );

                        return;
                    }

                    broadcastRoomState(
                            session.getId()
                    );
                }

                /*
                 * =================================================
                 * REVEAL
                 * =================================================
                 */

                case "REVEAL" -> {

                    boolean revealed =
                            pokerRoomService.reveal(
                                    session.getId()
                            );

                    if (!revealed) {

                        sendError(
                                session,
                                "Only the host can reveal the votes."
                        );

                        return;
                    }

                    broadcastRoomState(
                            session.getId()
                    );
                }

                /*
                 * =================================================
                 * RESET
                 * =================================================
                 */

                case "RESET" -> {

                    boolean reset =
                            pokerRoomService.reset(
                                    session.getId()
                            );

                    if (!reset) {

                        sendError(
                                session,
                                "Only the host can start the next round."
                        );

                        return;
                    }

                    broadcastRoomState(
                            session.getId()
                    );
                }

                /*
                 * =================================================
                 * UNKNOWN ACTION
                 * =================================================
                 */

                default ->
                        protocolViolation(
                                session,
                                securityState,
                                "Unknown action."
                        );
            }

        } catch (IllegalArgumentException |
                 IllegalStateException ex) {

            sendError(
                    session,
                    safeClientMessage(
                            ex.getMessage()
                    )
            );

        } catch (Exception ex) {

            protocolViolation(
                    session,
                    securityState,
                    "Invalid request."
            );
        }
    }

    /*
     * =========================================================
     * ROOM-SCOPED BROADCAST
     * =========================================================
     */

    /**
     * Broadcasts authoritative state only to the participants
     * belonging to sourceSessionId's room.
     */
    private void broadcastRoomState(
            String sourceSessionId) {

        try {

            String roomCode =
                    pokerRoomService
                            .getRoomCodeForSession(
                                    sourceSessionId
                            );

            if (roomCode == null) {
                return;
            }

            List<String> roomSessionIds =
                    pokerRoomService
                            .getSessionIdsForRoom(
                                    sourceSessionId
                            );

            List<Participant> participants =
                    pokerRoomService
                            .getParticipants(
                                    sourceSessionId
                            );

            boolean revealed =
                    pokerRoomService
                            .isRevealed(
                                    sourceSessionId
                            );

            String mode =
                    pokerRoomService
                            .getCurrentMode(
                                    sourceSessionId
                            );

            long timerEndEpochMs =
                    pokerRoomService
                            .getTimerEndEpochMs(
                                    sourceSessionId
                            );

            for (String targetSessionId :
                    roomSessionIds) {

                WebSocketSession targetSession =
                        sessions.get(
                                targetSessionId
                        );

                if (targetSession == null ||
                        !targetSession.isOpen()) {

                    continue;
                }

                Participant currentUser =
                        pokerRoomService
                                .getParticipant(
                                        targetSessionId
                                );

                if (currentUser == null) {

                    continue;
                }

                boolean targetIsHost =
                        pokerRoomService
                                .isHostSession(
                                        targetSessionId
                                );

                /*
                 * Build participant data specifically for this
                 * receiving client.
                 *
                 * participantId is exposed only to the host,
                 * because only the host needs it for removal.
                 */
                List<Map<String, Object>> participantData =
                        participants.stream()
                                .map(
                                        participant -> {

                                            Map<String, Object> data =
                                                    new LinkedHashMap<>();

                                            data.put(
                                                    "name",
                                                    participant.getName()
                                            );

                                            data.put(
                                                    "host",
                                                    participant.isHost()
                                            );

                                            data.put(
                                                    "voted",
                                                    participant.isVoted()
                                            );

                                            /*
                                             * Only host receives
                                             * participant IDs.
                                             *
                                             * Host's own ID is not
                                             * necessary for removal.
                                             */
                                            if (targetIsHost &&
                                                    !participant.isHost()) {

                                                data.put(
                                                        "participantId",
                                                        participant
                                                                .getParticipantId()
                                                );
                                            }

                                            /*
                                             * Keep selections secret
                                             * until reveal.
                                             */
                                            if (revealed) {

                                                data.put(
                                                        "selection",
                                                        participant
                                                                .getSelection()
                                                );
                                            }

                                            return data;
                                        }
                                )
                                .toList();

                Map<String, Object> response =
                        new LinkedHashMap<>();

                response.put(
                        "type",
                        "STATE"
                );

                response.put(
                        "roomCode",
                        roomCode
                );

                response.put(
                        "mode",
                        mode
                );

                response.put(
                        "revealed",
                        revealed
                );

                response.put(
                        "youAreHost",
                        targetIsHost
                );

                response.put(
                        "timerEndEpochMs",
                        timerEndEpochMs
                );

                response.put(
                        "participants",
                        participantData
                );

                sendJson(
                        targetSession,
                        response
                );
            }

        } catch (IllegalArgumentException |
                 IllegalStateException ignored) {

            /*
             * Room may have expired or closed during operation.
             */
        }
    }

    /**
     * Uses one remaining participant to identify and broadcast
     * updated room state.
     */
    private void broadcastUsingRemainingSessions(
            List<String> remainingSessionIds) {

        if (remainingSessionIds == null ||
                remainingSessionIds.isEmpty()) {

            return;
        }

        for (String sessionId :
                remainingSessionIds) {

            if (pokerRoomService.getParticipant(
                    sessionId
            ) != null) {

                broadcastRoomState(
                        sessionId
                );

                return;
            }
        }
    }

    /*
     * =========================================================
     * DIRECT EVENTS
     * =========================================================
     */

    private void sendRoomCreated(
            WebSocketSession session,
            String roomCode) {

        Map<String, Object> response =
                new LinkedHashMap<>();

        response.put(
                "type",
                "ROOM_CREATED"
        );

        response.put(
                "roomCode",
                roomCode
        );

        sendJson(
                session,
                response
        );
    }

    private void sendRoomJoined(
            WebSocketSession session,
            String roomCode) {

        Map<String, Object> response =
                new LinkedHashMap<>();

        response.put(
                "type",
                "ROOM_JOINED"
        );

        response.put(
                "roomCode",
                roomCode
        );

        sendJson(
                session,
                response
        );
    }

    /**
     * Sent only to a participant removed by the host.
     */
    private void sendRemovedFromRoom(
            WebSocketSession session) {

        Map<String, Object> response =
                new LinkedHashMap<>();

        response.put(
                "type",
                "REMOVED_FROM_ROOM"
        );

        response.put(
                "message",
                "You were removed from this room by the host."
        );

        sendJson(
                session,
                response
        );
    }

    private void sendRoomClosed(
            WebSocketSession session) {

        Map<String, Object> response =
                new LinkedHashMap<>();

        response.put(
                "type",
                "ROOM_CLOSED"
        );

        response.put(
                "message",
                "The host left and this room has been closed."
        );

        sendJson(
                session,
                response
        );
    }

    /*
     * =========================================================
     * JSON VALIDATION
     * =========================================================
     */

    private String requiredText(
            JsonNode json,
            String fieldName,
            int maxLength,
            boolean allowBlank) {

        JsonNode value =
                json.get(
                        fieldName
                );

        if (value == null ||
                !value.isTextual()) {

            throw new IllegalArgumentException(
                    "Invalid request."
            );
        }

        String text =
                value.asText();

        if (text.length() >
                maxLength) {

            throw new IllegalArgumentException(
                    "Invalid request."
            );
        }

        if (!allowBlank &&
                text.isBlank()) {

            throw new IllegalArgumentException(
                    "Invalid request."
            );
        }

        return text;
    }

    /*
     * =========================================================
     * PROTOCOL VIOLATIONS
     * =========================================================
     */

    private void protocolViolation(
            WebSocketSession session,
            ConnectionSecurityState securityState,
            String message) {

        int violations =
                securityState.recordViolation();

        sendError(
                session,
                message
        );

        if (violations >=
                MAX_PROTOCOL_VIOLATIONS) {

            closeQuietly(
                    session,
                    CloseStatus.POLICY_VIOLATION
            );
        }
    }

    /*
     * =========================================================
     * SAFE ERROR RESPONSES
     * =========================================================
     */

    private void sendError(
            WebSocketSession session,
            String message) {

        Map<String, Object> response =
                new LinkedHashMap<>();

        response.put(
                "type",
                "ERROR"
        );

        response.put(
                "message",
                safeClientMessage(
                        message
                )
        );

        sendJson(
                session,
                response
        );
    }

    private String safeClientMessage(
            String message) {

        if (message == null ||
                message.isBlank()) {

            return "Request failed.";
        }

        if (message.length() > 200) {

            return "Request failed.";
        }

        return message;
    }

    /*
     * =========================================================
     * SAFE OUTBOUND SEND
     * =========================================================
     */

    private void sendJson(
            WebSocketSession session,
            Map<String, Object> response) {

        if (session == null ||
                !session.isOpen()) {

            return;
        }

        try {

            String payload =
                    objectMapper
                            .writeValueAsString(
                                    response
                            );

            synchronized (session) {

                if (!session.isOpen()) {
                    return;
                }

                session.sendMessage(
                        new TextMessage(
                                payload
                        )
                );
            }

        } catch (Exception ignored) {

            /*
             * Never expose serialization/transport details.
             */
        }
    }

    /*
     * =========================================================
     * DISCONNECT
     * =========================================================
     */

    @Override
    public void afterConnectionClosed(
            WebSocketSession session,
            CloseStatus status) {

        sessions.remove(
                session.getId()
        );

        securityStates.remove(
                session.getId()
        );

        PokerRoomService.LeaveResult leaveResult =
                pokerRoomService.leave(
                        session.getId()
                );

        /*
         * A participant removed by the host has already had their
         * session -> room binding removed by PokerRoomService.
         *
         * Therefore this safely becomes a no-op.
         */
        if (leaveResult.roomCode() == null) {

            return;
        }

        if (leaveResult.roomClosed()) {

            for (String affectedSessionId :
                    leaveResult.affectedSessionIds()) {

                WebSocketSession affectedSession =
                        sessions.get(
                                affectedSessionId
                        );

                if (affectedSession == null) {
                    continue;
                }

                sendRoomClosed(
                        affectedSession
                );

                closeQuietly(
                        affectedSession,
                        CloseStatus.NORMAL
                );
            }

            return;
        }

        broadcastUsingRemainingSessions(
                leaveResult.affectedSessionIds()
        );
    }

    /*
     * =========================================================
     * TRANSPORT ERROR
     * =========================================================
     */

    @Override
    public void handleTransportError(
            WebSocketSession session,
            Throwable exception) {

        closeQuietly(
                session,
                CloseStatus.SERVER_ERROR
        );
    }

    /*
     * =========================================================
     * SAFE CLOSE
     * =========================================================
     */

    private void closeQuietly(
            WebSocketSession session,
            CloseStatus status) {

        if (session == null) {
            return;
        }

        try {

            if (session.isOpen()) {

                session.close(
                        status
                );
            }

        } catch (Exception ignored) {

            /*
             * Nothing else required during cleanup.
             */
        }
    }

    /*
     * =========================================================
     * RATE LIMIT STATE
     * =========================================================
     */

    private static final class ConnectionSecurityState {

        private final Deque<Long> messageTimes =
                new ArrayDeque<>();

        private final Deque<Long> roomEntryAttemptTimes =
                new ArrayDeque<>();

        private int protocolViolations;

        public synchronized boolean allowMessage(
                long now) {

            removeOlderThan(
                    messageTimes,
                    now -
                            MESSAGE_RATE_WINDOW_MS
            );

            if (messageTimes.size() >=
                    MAX_MESSAGES_PER_WINDOW) {

                return false;
            }

            messageTimes.addLast(
                    now
            );

            return true;
        }

        public synchronized boolean allowRoomEntryAttempt(
                long now) {

            removeOlderThan(
                    roomEntryAttemptTimes,
                    now -
                            ROOM_ENTRY_WINDOW_MS
            );

            if (roomEntryAttemptTimes.size() >=
                    MAX_ROOM_ENTRY_ATTEMPTS) {

                return false;
            }

            roomEntryAttemptTimes.addLast(
                    now
            );

            return true;
        }

        public synchronized int recordViolation() {

            protocolViolations++;

            return protocolViolations;
        }

        private void removeOlderThan(
                Deque<Long> timestamps,
                long cutoff) {

            while (!timestamps.isEmpty() &&
                    timestamps.peekFirst() <
                            cutoff) {

                timestamps.removeFirst();
            }
        }
    }
}