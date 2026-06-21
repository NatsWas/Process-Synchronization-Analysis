package com.psanalyzer.model.data;

import java.util.List;
import java.util.ArrayList;

public class MetricsResult {

    private String algorithmName;
    private double avgTurnaroundTime;
    private double avgWaitingTime;
    private double avgResponseTime;
    private double cpuUtilization;
    private double fairnessIndex;
    private double throughput;
    private int totalProcesses;
    private int completedProcesses;
    private long totalSimulationTime;
    private List<double[]> resourceUtilization;
    private List<String> resourceNames;

    public MetricsResult(String algorithmName) {
        this.algorithmName = algorithmName;
        this.resourceUtilization = new ArrayList<>();
        this.resourceNames = new ArrayList<>();
    }
    private static String stripZeros(String s) {
        if (!s.contains(".")) return s;
        return s.replaceAll("0+$", "").replaceAll("\\.$", "");
    }

    public String getSummary() {
        return String.format(
            "Algorithm: %s\n" +
            "Avg Turnaround Time : %s\n" +
            "Avg Waiting Time    : %s\n" +
            "Avg Response Time   : %s\n" +
            "CPU Utilization     : %.2f%%\n" +   
            "Fairness Index      : %s\n" +
            "Throughput          : %.6f proc/tick\n" +
            "Completed Processes : %d / %d\n" +
            "Simulation Time     : %d ticks",
            algorithmName,
            stripZeros(String.format("%.2f", avgTurnaroundTime)),  // FIX 2
            stripZeros(String.format("%.2f", avgWaitingTime)),     // FIX 2
            stripZeros(String.format("%.2f", avgResponseTime)),    // FIX 2
            cpuUtilization,                                        // FIX 1
            stripZeros(String.format("%.2f", fairnessIndex)),      // FIX 5
            throughput,
            completedProcesses, totalProcesses,
            totalSimulationTime
        );
    }

    public String toCsvHeader() {
        return "Algorithm,AvgTurnaround,AvgWaiting,AvgResponse,CPUUtil(%),FairnessIndex,Throughput(proc/tick),Completed,Total,SimTime";
    }

    public String toCsvRow() {
        return String.format("%s,%.2f,%.2f,%.2f,%.2f,%.4f,%.6f,%d,%d,%d",
                algorithmName,
                avgTurnaroundTime, avgWaitingTime, avgResponseTime,
                cpuUtilization,
                fairnessIndex,
                throughput,
                completedProcesses, totalProcesses,
                totalSimulationTime);
    }

    // --- Getters & Setters ---

    public String getAlgorithmName()                { return algorithmName; }
    public double getAvgTurnaroundTime()            { return avgTurnaroundTime; }
    public void   setAvgTurnaroundTime(double v)    { this.avgTurnaroundTime = v; }
    public double getAvgWaitingTime()               { return avgWaitingTime; }
    public void   setAvgWaitingTime(double v)       { this.avgWaitingTime = v; }
    public double getAvgResponseTime()              { return avgResponseTime; }
    public void   setAvgResponseTime(double v)      { this.avgResponseTime = v; }
    public double getCpuUtilization()               { return cpuUtilization; }
    public void   setCpuUtilization(double v)       { this.cpuUtilization = v; }
    public double getFairnessIndex()                { return fairnessIndex; }
    public void   setFairnessIndex(double v)        { this.fairnessIndex = v; }
    public double getThroughput()                   { return throughput; }
    public void   setThroughput(double v)           { this.throughput = v; }
    public int    getTotalProcesses()               { return totalProcesses; }
    public void   setTotalProcesses(int v)          { this.totalProcesses = v; }
    public int    getCompletedProcesses()           { return completedProcesses; }
    public void   setCompletedProcesses(int v)      { this.completedProcesses = v; }
    public long   getTotalSimulationTime()          { return totalSimulationTime; }
    public void   setTotalSimulationTime(long v)    { this.totalSimulationTime = v; }
    public List<double[]> getResourceUtilization()  { return resourceUtilization; }
    public void   addResourceUtilization(double[] u){ resourceUtilization.add(u); }
    public List<String> getResourceNames()          { return resourceNames; }
    public void   addResourceName(String name)      { resourceNames.add(name); }
}