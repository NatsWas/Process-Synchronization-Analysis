package com.psanalyzer.model.data;
import java.util.LinkedList;
import java.util.Queue;
public class ResourceModel {
     public enum Type { MUTEX, SEMAPHORE, MONITOR, LOCK }
    private int id;
    private String name;
    private Type type;
    private int capacity;
    private int available;
    private long busyTime;
    private long totalTime;
    private Queue<Integer> waitingQueue;
    public ResourceModel(int id, String name, Type type, int capacity) {
        this.id=id;
        this.name=name;
        this.type=type;
        this.capacity=capacity;
        this.available=capacity;
        this.busyTime=0;
        this.totalTime=0;
        this.waitingQueue=new LinkedList<>();
    }
    public boolean acquire(int processId) {
        if (available > 0) {
            available--;
            return true;
        }
        waitingQueue.offer(processId);
        return false;
    }
    public int release() {
        available++;
        if (!waitingQueue.isEmpty()) {
            return waitingQueue.poll();
        }
        return -1;
    }
    public double getUtilization() {
        if (totalTime==0) return 0.0;
        return (double) busyTime / totalTime * 100.0;
    }
    public void recordBusy(long ticks) { busyTime += ticks; }
    public void recordTotal(long ticks) { totalTime += ticks; }
    public int getId() { return id; }
    public String getName() { return name; }
    public Type getType() { return type; }
    public int getCapacity() { return capacity; }
    public int getAvailable() { return available; }
    public long getBusyTime() { return busyTime; }
    public long getTotalTime() { return totalTime; }
    public Queue<Integer> getWaitingQueue() { return waitingQueue; }
}