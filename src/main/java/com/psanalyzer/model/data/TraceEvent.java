package com.psanalyzer.model.data;

public class TraceEvent {
    public enum EventType {
        PROCESS_ARRIVE, PROCESS_START, PROCESS_FINISH,
        ACQUIRE_REQUEST, ACQUIRE_SUCCESS, ACQUIRE_FAIL,
        RELEASE, WAIT, SIGNAL, CONTEXT_SWITCH,
        BUSY_WAIT, BLOCK, UNBLOCK, CUSTOM
    }

    private long timestamp;
    private int processId;
    private int resourceId; // Retained to prevent breaking existing SimulationEngine instantiations
    private EventType type;
    private String description;

    public TraceEvent(long timestamp, int processId, int resourceId,
                      EventType type, String description) {
        this.timestamp = timestamp;
        this.processId = processId;
        this.resourceId = resourceId;
        this.type = type;
        this.description = description;
    }

    public long getTimestamp() { return timestamp; }
    public int getProcessId() { return processId; }
    public int getResourceId() { return resourceId; }
    public EventType getType() { return type; }
    public String getDescription() { return description; }

    @Override
    public String toString() {
        return String.format("[T=%04d] P%d | %-18s | %s",
                timestamp, processId, type.name(), description);
    }

    public String toCsv() {
        return String.format("%d,P%d,%s,\"%s\"",
                timestamp, processId, type.name(), description);
    }
}