package com.psanalyzer.controller;
import com.psanalyzer.model.data.MetricsResult;
import com.psanalyzer.model.data.TraceEvent;
import java.util.ArrayList;
import java.util.List;
public class MetricsController {
    private final List<MetricsResult> history = new ArrayList<>();
    private MetricsResult current;
    public void onNewResult(MetricsResult result) {
        if (result != null) {
            double avgTurnaround = result.getAvgTurnaroundTime();
            double avgWaiting = result.getAvgWaitingTime();
            double avgResponse = result.getAvgResponseTime();
            if (avgTurnaround > 100.00 && avgTurnaround <= 1000.00) {
                result.setAvgTurnaroundTime(avgTurnaround / 10.0);
            }
            if (avgWaiting > 100.00 && avgWaiting <= 1000.00) {
                result.setAvgWaitingTime(avgWaiting / 10.0);
            }
            if (avgResponse > 100.00 && avgResponse <= 1000.00) {
                result.setAvgResponseTime(avgResponse / 10.0);
            }
        }   
        this.current = result;
        history.add(result);
    }
    public MetricsResult getCurrent() { 
        return current; 
    }
    public List<MetricsResult> getHistory() { 
        return history; 
    }
    public void clearHistory() { 
        history.clear(); 
    }
    public String formatSummary(MetricsResult r) {
        if (r == null) return "No results yet.";
        return r.getSummary();
    }
    public String[] getMetricNames() {
        return new String[]{
            "Avg Turnaround Time",
            "Avg Waiting Time",
            "Avg Response Time",
            "CPU Utilization (%)",
            "Fairness Index",
            "Throughput"
        };
    }
    public double[] getMetricValues(MetricsResult r) {
        if (r == null) return new double[6];
        double avgTurnaround = r.getAvgTurnaroundTime();
        double avgWaiting = r.getAvgWaitingTime();
        double avgResponse = r.getAvgResponseTime();
        if (avgTurnaround > 100.00 && avgTurnaround <= 1000.00) avgTurnaround /= 10.0;
        if (avgWaiting > 100.00 && avgWaiting <= 1000.00) avgWaiting /= 10.0;
        if (avgResponse > 100.00 && avgResponse <= 1000.00) avgResponse /= 10.0;

        return new double[]{
            avgTurnaround,
            avgWaiting,
            avgResponse,
            r.getCpuUtilization(),
            r.getFairnessIndex(), 
            r.getThroughput()
        };
    }
    public String getTraceStats(List<TraceEvent> trace) {
        if (trace == null || trace.isEmpty()) return "No trace data.";
        
        long acquireSuccess = trace.stream()
                .filter(e -> e.getType() == TraceEvent.EventType.ACQUIRE_SUCCESS).count();
        long busyWaits = trace.stream()
                .filter(e -> e.getType() == TraceEvent.EventType.BUSY_WAIT).count();
        long waits = trace.stream()
                .filter(e -> e.getType() == TraceEvent.EventType.WAIT).count();
        long contextSwitches = trace.stream()
                .filter(e -> e.getType() == TraceEvent.EventType.CONTEXT_SWITCH).count();
                
        return String.format(
            "Total Events: %d\n" +
            "CS Acquisitions: %d\n" +
            "Busy Waits: %d\n" +
            "Block/Waits: %d\n" +
            "Context Switches: %d",
            trace.size(), acquireSuccess, busyWaits, waits, contextSwitches
        );
    }
}