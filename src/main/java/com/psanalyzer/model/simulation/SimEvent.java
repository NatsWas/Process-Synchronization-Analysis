package com.psanalyzer.model.simulation;
public class SimEvent implements Comparable<SimEvent> {
    public enum Type {
        PROCESS_ARRIVE, ACQUIRE_REQUEST, ACQUIRE_SUCCESS,
        ACQUIRE_FAIL, RELEASE, WAIT, SIGNAL, BUSY_WAIT,
        CONTEXT_SWITCH, PROCESS_FINISH, CUSTOM
    }
    private long timestamp;
    private int processId;
    private int resourceId;
    private Type type;
    private String description;
    private int priority;
    public SimEvent(long timestamp, int processId, int resourceId,
                    Type type, String description) {
        this.timestamp=timestamp;
        this.processId=processId;
        this.resourceId=resourceId;
        this.type=type;
        this.description=description;
        this.priority=0;
    }
    @Override
    public int compareTo(SimEvent other) {
        int cmp = Long.compare(this.timestamp, other.timestamp);
        if (cmp != 0) return cmp;
        return Integer.compare(this.priority, other.priority);
    }
    public long getTimestamp() { return timestamp; }
    public int getProcessId() { return processId; }
    public int getResourceId() { return resourceId; }
    public Type getType() { return type; }
    public String getDescription() { return description; }
    public int getPriority() { return priority; }
    public void setPriority(int priority) { this.priority = priority; }
}