package com.dv.dvplanningpoker.model;

/**
 * A participant currently connected to the refinement room.
 *
 * Room data is intentionally in-memory only. A participant has one active
 * selection for the current activity (Estimate, Games, or Food & Break).
 */
public class Participant {

    private final String sessionId;
    private final String name;

    private boolean host;
    private String selection;

    public Participant(
            String sessionId,
            String name,
            boolean host) {

        this.sessionId = sessionId;
        this.name = name;
        this.host = host;
    }

    public String getSessionId() {
        return sessionId;
    }

    public String getName() {
        return name;
    }

    public boolean isHost() {
        return host;
    }

    public void setHost(boolean host) {
        this.host = host;
    }

    public String getSelection() {
        return selection;
    }

    public void setSelection(String selection) {
        this.selection = selection;
    }

    public void clearSelection() {
        this.selection = null;
    }

    public boolean isVoted() {
        return selection != null;
    }

    /*
     * Compatibility helpers for the earlier estimate-only implementation.
     */
    public String getVote() {
        return selection;
    }

    public void setVote(String vote) {
        this.selection = vote;
    }
}