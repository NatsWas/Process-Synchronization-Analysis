package com.psanalyzer.model.simulation;
import java.util.PriorityQueue;
public class EventQueue {
    private PriorityQueue<SimEvent> queue;
    public EventQueue() {
        this.queue=new PriorityQueue<>();
    }
    public void addEvent(SimEvent event) {
        queue.offer(event);
    }
    public SimEvent nextEvent() {
        return queue.poll();
    }
    public SimEvent peekEvent() {
        return queue.peek();
    }
    public boolean isEmpty() {
        return queue.isEmpty();
    }
    public int size() {
        return queue.size();
    }
    public void clear() {
        queue.clear();
    }
}