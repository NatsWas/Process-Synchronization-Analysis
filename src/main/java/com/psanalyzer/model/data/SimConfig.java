package com.psanalyzer.model.data;
import java.util.HashMap;
import java.util.Map;
public class SimConfig {
    private String algorithmName;
    private int numberOfProcesses;
    private int numberOfResources;
    private int simulationSteps;
    private int timeQuantum;
    private Map<String, Object> extraParams;
    public SimConfig() {
        this.extraParams=new HashMap<>();
        this.simulationSteps=100;
        this.timeQuantum=1;
        this.numberOfProcesses=2;
        this.numberOfResources=1;
    }
    public String getAlgorithmName() { return algorithmName; }
    public void setAlgorithmName(String algorithmName) { this.algorithmName=algorithmName; }
    public int getNumberOfProcesses() { return numberOfProcesses; }
    public void setNumberOfProcesses(int numberOfProcesses) { this.numberOfProcesses=numberOfProcesses; }
    public int getNumberOfResources() { return numberOfResources; }
    public void setNumberOfResources(int numberOfResources) { this.numberOfResources=numberOfResources; }
    public int getSimulationSteps() { return simulationSteps; }
    public void setSimulationSteps(int simulationSteps) { this.simulationSteps=simulationSteps; }
    public int getTimeQuantum() { return timeQuantum; }
    public void setTimeQuantum(int timeQuantum) { this.timeQuantum=timeQuantum; }
    public Map<String, Object> getExtraParams() { return extraParams; }
    public void setExtraParam(String key, Object value) { this.extraParams.put(key, value); }
    public Object getExtraParam(String key) { return extraParams.get(key); }
    public int getExtraParamInt(String key, int defaultValue) {
        Object val = extraParams.get(key);
        if (val instanceof Integer) return (Integer) val;
        if (val instanceof String) {
            try { return Integer.parseInt((String) val); } catch (NumberFormatException e) { return defaultValue; }
        }
        return defaultValue;
    }
}