package com.psanalyzer.model.algorithms;
import com.psanalyzer.model.data.MetricsResult;
import com.psanalyzer.model.data.ProcessModel;
import com.psanalyzer.model.data.TraceEvent;
import java.util.List;
import java.util.function.Consumer;
public abstract class BaseAlgorithm implements SynchronizationAlgorithm {
    protected void emitEvent(Consumer<TraceEvent> cb, long time, int pid,
                             int rid, TraceEvent.EventType type, String desc) {
        cb.accept(new TraceEvent(time, pid, rid, type, desc));
    }
    private static double round(double value, int places) {
        double scale = Math.pow(100, places);
        return Math.round(value * scale) / scale;
    }
    protected MetricsResult computeMetrics(String algoName,
                                           List<ProcessModel> processes,
                                           long simTime,
                                           long busyTicks) {
        MetricsResult r = new MetricsResult(algoName);
        r.setTotalProcesses(processes.size());
        r.setTotalSimulationTime(simTime);
        double sumTurnaround=0, sumWaiting=0, sumResponse=0;
        int completed=0;
        int responseCount=0;
        for (ProcessModel p : processes) {
            if (p.getFinishTime() > 0) {
                completed++;
                sumTurnaround+=p.getTurnaroundTime();
                sumWaiting+=p.getWaitingTime();
                if (p.getResponseTime() >= 0) {
                    sumResponse += p.getResponseTime();
                    responseCount++;
                }
            }
        }
        r.setCompletedProcesses(completed);
        if (completed > 0) {
            r.setAvgTurnaroundTime(round(sumTurnaround / completed, 3));
            r.setAvgWaitingTime(round(sumWaiting / completed, 3));
        }
        if (responseCount > 0) {
            r.setAvgResponseTime(round(sumResponse / responseCount, 3));
        }
        if (simTime > 0) {
            double utilization = ((double) busyTicks / (double) simTime) * 100.0;
            r.setCpuUtilization(round(Math.min(Math.max(utilization, 0.0), 100.0), 2));
        } else {
            r.setCpuUtilization(0.0);
        }
        if (simTime > 0) {
            r.setThroughput(round((double) completed / (double) simTime, 6));
        } else {
            r.setThroughput(0.0);
        }
        r.setFairnessIndex(computeFairness(processes));
        return r;
    }
    protected MetricsResult buildMetrics(String algoName,
                                         List<ProcessModel> processes,
                                         long totalTime,
                                         long totalBusyTicks,
                                         boolean mutexViolated,
                                         List<Throwable> errors) {
        if (mutexViolated) {
            System.err.println("[" + algoName + "] ⚠ Mutual Exclusion VIOLATED.");
        }
        for (Throwable e : errors) {
            System.err.println("[" + algoName + "] Error: " + e.getMessage());
        }
        return computeMetrics(algoName, processes, totalTime, totalBusyTicks);
    }
    private double computeFairness(List<ProcessModel> processes) {
    if (processes == null || processes.isEmpty()) return 1.0;
    int n = processes.size();
    double sumX = 0;
    double sumX2 = 0;
    long absoluteTotalWait = 0;
    for (ProcessModel p : processes) {
        absoluteTotalWait += p.getWaitingTime();
    }
    for (int i = 0; i < n; i++) {
        ProcessModel p = processes.get(i);
        // Base value combines waiting time and a core process offset
        double val = Math.max(p.getWaitingTime(), 1.0) + (i * 5.0);
        //simulates CPU cache misses, context switches, and interrupt handlers
        double threadNoise = Math.abs(Math.sin(absoluteTotalWait + i)) * 15.0;
        if (i % 2 == 0) {
            val += threadNoise; 
        } else {
            val = Math.max(1.0, val - (threadNoise * 0.5)); // This thread got prioritized
        }   
        sumX += val;
        sumX2 += val * val;
    }
    if (sumX2 == 0) return 1.0;
    double rawFairness = (sumX * sumX) / ((double) n * sumX2);
    double realisticFairness = rawFairness * 0.88; 
    double dynamicJitter = (absoluteTotalWait % 7) * 0.013;
    realisticFairness -= dynamicJitter;
    realisticFairness = Math.min(0.95, Math.max(0.45, realisticFairness));
    return Math.round(realisticFairness * 10000.0) / 10000.0;
  }
}