package com.dv.dvplanningpoker.websocket;

import com.dv.dvplanningpoker.model.Participant;
import com.dv.dvplanningpoker.service.PokerRoomService;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.CloseStatus;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;
import org.springframework.web.socket.handler.TextWebSocketHandler;

import java.io.IOException;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@Component
public class PokerWebSocketHandler extends TextWebSocketHandler {

    private final PokerRoomService pokerRoomService;
    private final ObjectMapper objectMapper;

    private final Map<String, WebSocketSession> sessions =
            new ConcurrentHashMap<>();

    public PokerWebSocketHandler(
            PokerRoomService pokerRoomService,
            ObjectMapper objectMapper) {

        this.pokerRoomService = pokerRoomService;
        this.objectMapper = objectMapper;
    }

    @Override
    public void afterConnectionEstablished(WebSocketSession session) {
        sessions.put(session.getId(), session);
    }

    @Override
    protected void handleTextMessage(
            WebSocketSession session,
            TextMessage message) throws Exception {

        JsonNode json = objectMapper.readTree(message.getPayload());

        String action = json.path("action").asText();

        switch (action) {

            case "JOIN" -> handleJoin(session, json);

            case "VOTE" -> {
                String vote = json.path("vote").asText();

                pokerRoomService.vote(session.getId(), vote);

                broadcastState();
            }

            case "REVEAL" -> {
                pokerRoomService.reveal(session.getId());
                broadcastState();
            }

            case "RESET" -> {
                pokerRoomService.reset(session.getId());
                broadcastState();
            }

            default -> sendError(session, "Unknown action.");
        }
    }

    private void handleJoin(
            WebSocketSession session,
            JsonNode json) throws IOException {

        try {

            String name = json.path("name").asText();

            pokerRoomService.join(session.getId(), name);

            broadcastState();

        } catch (IllegalArgumentException ex) {

            sendError(session, ex.getMessage());
        }
    }

    private void broadcastState() {

        List<Participant> participants =
                pokerRoomService.getParticipants();

        boolean revealed = pokerRoomService.isRevealed();

        for (WebSocketSession session : sessions.values()) {

            if (!session.isOpen()) {
                continue;
            }

            Participant currentUser =
                    pokerRoomService.getParticipant(session.getId());

            if (currentUser == null) {
                continue;
            }

            Map<String, Object> response =
                    new LinkedHashMap<>();

            response.put("type", "STATE");
            response.put("revealed", revealed);
            response.put("youAreHost", currentUser.isHost());

            List<Map<String, Object>> participantData =
                    participants.stream()
                            .map(participant -> {

                                Map<String, Object> data =
                                        new LinkedHashMap<>();

                                data.put("name", participant.getName());
                                data.put("voted", participant.isVoted());
                                data.put("host", participant.isHost());

                                // IMPORTANT:
                                // Votes are not sent to browsers
                                // until the host reveals them.
                                if (revealed) {
                                    data.put(
                                            "vote",
                                            participant.getVote()
                                    );
                                }

                                return data;

                            }).toList();

            response.put("participants", participantData);

            try {

                String payload =
                        objectMapper.writeValueAsString(response);

                synchronized (session) {
                    session.sendMessage(
                            new TextMessage(payload)
                    );
                }

            } catch (IOException ignored) {
            }
        }
    }

    private void sendError(
            WebSocketSession session,
            String message) throws IOException {

        Map<String, Object> response =
                Map.of(
                        "type", "ERROR",
                        "message", message
                );

        session.sendMessage(
                new TextMessage(
                        objectMapper.writeValueAsString(response)
                )
        );
    }

    @Override
    public void afterConnectionClosed(
            WebSocketSession session,
            CloseStatus status) {

        sessions.remove(session.getId());

        pokerRoomService.leave(session.getId());

        broadcastState();
    }
}