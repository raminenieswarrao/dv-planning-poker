package com.dv.dvplanningpoker.service;

import com.dv.dvplanningpoker.model.Participant;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Service
public class PokerRoomService {

    private final Map<String, Participant> participants = new LinkedHashMap<>();

    private boolean revealed = false;

    public synchronized Participant join(String sessionId, String name) {

        String cleanName = name == null ? "" : name.trim();

        if (cleanName.isEmpty()) {
            throw new IllegalArgumentException("Name is required.");
        }

        boolean duplicate = participants.values()
                .stream()
                .anyMatch(p -> p.getName().equalsIgnoreCase(cleanName));

        if (duplicate) {
            throw new IllegalArgumentException("That name is already in the room.");
        }

        boolean host = participants.isEmpty();

        Participant participant =
                new Participant(sessionId, cleanName, host);

        participants.put(sessionId, participant);

        return participant;
    }

    public synchronized void vote(String sessionId, String vote) {

        if (revealed) {
            return;
        }

        Participant participant = participants.get(sessionId);

        if (participant != null) {
            participant.setVote(vote);
        }
    }

    public synchronized boolean reveal(String sessionId) {

        Participant participant = participants.get(sessionId);

        if (participant == null || !participant.isHost()) {
            return false;
        }

        revealed = true;
        return true;
    }

    public synchronized boolean reset(String sessionId) {

        Participant participant = participants.get(sessionId);

        if (participant == null || !participant.isHost()) {
            return false;
        }

        participants.values()
                .forEach(p -> p.setVote(null));

        revealed = false;

        return true;
    }

    public synchronized void leave(String sessionId) {

        Participant removed = participants.remove(sessionId);

        if (removed != null && removed.isHost() && !participants.isEmpty()) {

            participants.values()
                    .iterator()
                    .next()
                    .setHost(true);
        }

        if (participants.isEmpty()) {
            revealed = false;
        }
    }

    public synchronized Participant getParticipant(String sessionId) {
        return participants.get(sessionId);
    }

    public synchronized List<Participant> getParticipants() {
        return new ArrayList<>(participants.values());
    }

    public synchronized boolean isRevealed() {
        return revealed;
    }
}