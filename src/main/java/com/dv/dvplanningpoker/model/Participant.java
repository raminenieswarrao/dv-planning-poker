package com.dv.dvplanningpoker.model;

public class Participant {

    private final String sessionId;
    private final String name;

    private String vote;
    private boolean host;

    public Participant(String sessionId, String name, boolean host) {
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

    public String getVote() {
        return vote;
    }

    public void setVote(String vote) {
        this.vote = vote;
    }

    public boolean isVoted() {
        return vote != null;
    }

    public boolean isHost() {
        return host;
    }

    public void setHost(boolean host) {
        this.host = host;
    }
}