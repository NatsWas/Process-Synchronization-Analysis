package com.psanalyzer.model.data;
public class ProcessModel {
    public enum State { READY, RUNNING, WAITING, BLOCKED, TERMINATED }
    private int    id;
    private String name;
    private State  state;
    private long   arrivalTime;
    private long   startTime;
    private long   finishTime;
    private long   waitingTime;
    private long   responseTime;
    private long   burstTime;
    private long   remainingTime;
    private int    priority;
    private int    csCount = 0;
    private long    turnaroundTime         = 0L;
    private boolean turnaroundExplicitlySet = false;

    public ProcessModel(int id, long arrivalTime, long burstTime) {
        this.id            = id;
        this.name          = "P" + id;
        this.arrivalTime   = arrivalTime;
        this.burstTime     = burstTime;
        this.remainingTime = burstTime;
        this.state         = State.READY;
        this.startTime     = -1;
        this.finishTime    = -1;
        this.waitingTime   = 0;
        this.responseTime  = -1;
        this.priority      = 0;
    }
    public long getTurnaroundTime() {
        if (turnaroundExplicitlySet) return turnaroundTime;
        if (finishTime < 0) return 0;
        return finishTime - arrivalTime;
    }
    public void setTurnaroundTime(long turnaroundTime) {
        this.turnaroundTime          = turnaroundTime;
        this.turnaroundExplicitlySet = true;
    }

    public void incrementCsCount()  { this.csCount++; }
    public int  getCsCount()        { return csCount; }

    public int    getId()                              { return id; }
    public String getName()                            { return name; }
    public State  getState()                           { return state; }
    public void   setState(State state)                { this.state = state; }
    public long   getArrivalTime()                     { return arrivalTime; }
    public long   getStartTime()                       { return startTime; }
    public void   setStartTime(long startTime)         { this.startTime = startTime; }
    public long   getFinishTime()                      { return finishTime; }
    public void   setFinishTime(long finishTime)       { this.finishTime = finishTime; }
    public long   getWaitingTime()                     { return waitingTime; }
    public void   setWaitingTime(long waitingTime)     { this.waitingTime = waitingTime; }
    public void   addWaitingTime(long t)               { this.waitingTime += t; }
    public long   getResponseTime()                    { return responseTime; }
    public void   setResponseTime(long responseTime)   { this.responseTime = responseTime; }
    public long   getBurstTime()                       { return burstTime; }
    public long   getRemainingTime()                   { return remainingTime; }
    public void   setRemainingTime(long t)             { this.remainingTime = t; }
    public int    getPriority()                        { return priority; }
    public void   setPriority(int priority)            { this.priority = priority; }
}