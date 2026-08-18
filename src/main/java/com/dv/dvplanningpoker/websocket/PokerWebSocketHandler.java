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

import java.io.IOException;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * WebSocket endpoint for the shared refinement room.
 */
@Component
public class PokerWebSocketHandler extends TextWebSocketHandler {

    private final PokerRoomService pokerRoomService;

    private final ObjectMapper objectMapper;

    private final Map<String, WebSocketSession> sessions =
            new ConcurrentHashMap<>();

    public PokerWebSocketHandler(
            PokerRoomService pokerRoomService,
            ObjectMapper objectMapper) {

        this.pokerRoomService =
                pokerRoomService;

        this.objectMapper =
                objectMapper;
    }

    @Override
    public void afterConnectionEstablished(
            WebSocketSession session) {

        sessions.put(
                session.getId(),
                session
        );
    }

    @Override
    protected void handleTextMessage(
            WebSocketSession session,
            TextMessage message) throws Exception {

        try {

            JsonNode json =
                    objectMapper.readTree(
                            message.getPayload()
                    );

            String action =
                    json.path("action")
                            .asText();

            switch (action) {

                case "JOIN" -> {

                    pokerRoomService.join(
                            session.getId(),
                            json.path("name")
                                    .asText()
                    );

                    broadcastState();
                }

                case "VOTE" -> {

                    pokerRoomService.vote(
                            session.getId(),
                            json.path("selection")
                                    .asText()
                    );

                    broadcastState();
                }

                case "CHANGE_MODE" -> {

                    boolean changed =
                            pokerRoomService.changeMode(
                                    session.getId(),
                                    json.path("mode")
                                            .asText()
                            );

                    if (!changed) {

                        sendError(
                                session,
                                "Only the host can change the activity."
                        );

                        return;
                    }

                    broadcastState();
                }

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

                    broadcastState();
                }

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

                    broadcastState();
                }

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

                    broadcastState();
                }

                default ->
                        sendError(
                                session,
                                "Unknown action."
                        );
            }

        } catch (IllegalArgumentException |
                 IllegalStateException ex) {

            sendError(
                    session,
                    ex.getMessage()
            );
        }
    }

    /**
     * Broadcasts one authoritative room state.
     *
     * Important:
     * selections are NOT sent before reveal.
     */
    private void broadcastState() {

        List<Participant> participants =
                pokerRoomService
                        .getParticipants();

        boolean revealed =
                pokerRoomService
                        .isRevealed();

        String mode =
                pokerRoomService
                        .getCurrentMode();

        long timerEndEpochMs =
                pokerRoomService
                        .getTimerEndEpochMs();

        for (WebSocketSession session :
                sessions.values()) {

            if (!session.isOpen()) {
                continue;
            }

            Participant currentUser =
                    pokerRoomService
                            .getParticipant(
                                    session.getId()
                            );

            /*
             * A socket may be connected but not joined yet.
             */
            if (currentUser == null) {
                continue;
            }

            Map<String, Object> response =
                    new LinkedHashMap<>();

            response.put(
                    "type",
                    "STATE"
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
                    currentUser.isHost()
            );

            response.put(
                    "timerEndEpochMs",
                    timerEndEpochMs
            );

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
                                         * Keep every selection secret
                                         * until the host reveals.
                                         */
                                        if (revealed) {

                                            data.put(
                                                    "selection",
                                                    participant.getSelection()
                                            );
                                        }

                                        return data;
                                    }
                            )
                            .toList();

            response.put(
                    "participants",
                    participantData
            );

            try {

                String payload =
                        objectMapper
                                .writeValueAsString(
                                        response
                                );

                synchronized (session) {

                    session.sendMessage(
                            new TextMessage(
                                    payload
                            )
                    );
                }

            } catch (IOException ignored) {

                /*
                 * A client can disconnect between isOpen()
                 * and sendMessage(). The close callback
                 * will clean up the room.
                 */
            }
        }
    }

    private void sendError(
            WebSocketSession session,
            String message) throws IOException {

        Map<String, Object> response =
                Map.of(
                        "type",
                        "ERROR",
                        "message",
                        message == null
                                ? "Request failed."
                                : message
                );

        synchronized (session) {

            session.sendMessage(
                    new TextMessage(
                            objectMapper
                                    .writeValueAsString(
                                            response
                                    )
                    )
            );
        }
    }

    @Override
    public void afterConnectionClosed(
            WebSocketSession session,
            CloseStatus status) {

        sessions.remove(
                session.getId()
        );

        pokerRoomService.leave(
                session.getId()
        );

        broadcastState();
    }
}