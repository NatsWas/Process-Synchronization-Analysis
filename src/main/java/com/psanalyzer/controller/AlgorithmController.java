package com.psanalyzer.controller;
import com.psanalyzer.model.algorithms.*;
import com.psanalyzer.model.data.SimConfig;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;
public class AlgorithmController {
    private final Map<String, SynchronizationAlgorithm> registry = new LinkedHashMap<>();
    public AlgorithmController() {
    register(new PetersonAlgorithm());
    register(new DekkerAlgorithm());
    register(new LamportFastMutex());
    register(new MonitorProducerConsumer());
    register(new DiningPhilosophers());
    register(new ReadersWriters());
    register(new SleepingBarber());
    register(new FilterLock());
    register(new EisenbergMcGuire());
    register(new CigaretteSmokers());
    register(new BlackWhiteBakery());
    register(new YangAnderson());
}
    private void register(SynchronizationAlgorithm algo) {
        registry.put(algo.getName(), algo);
    }
    public SynchronizationAlgorithm getAlgorithm(String name) {
        return registry.get(name);
    }
    public Set<String> getAlgorithmNames() {
        return registry.keySet();
    }
    public Map<String, SynchronizationAlgorithm> getRegistry() {
        return registry;
    }
    public SimConfig buildDefaultConfig(String algoName) {
        SimConfig cfg = new SimConfig();
        cfg.setAlgorithmName(algoName);
        switch (algoName) {
            case "Peterson's Algorithm":
            case "Dekker's Algorithm":
                cfg.setNumberOfProcesses(2);
                cfg.setExtraParam("criticalSectionCount", 5);
                break;
            case "Lamport's Fast Mutual Exclusion":
                cfg.setNumberOfProcesses(4);
                cfg.setExtraParam("criticalSectionCount", 4);
                break;
            case "Monitor Producer-Consumer":
                cfg.setNumberOfProcesses(4);
                cfg.setExtraParam("bufferSize", 5);
                cfg.setExtraParam("producers", 2);
                cfg.setExtraParam("consumers", 2);
                cfg.setExtraParam("itemsEach", 4);
                break;
            case "Dining Philosophers":
                cfg.setNumberOfProcesses(5);
                cfg.setExtraParam("mealsPerPhilosopher", 3);
                break;
            case "Readers-Writers (Writer Priority)":
                cfg.setNumberOfProcesses(5);
                cfg.setExtraParam("readers", 3);
                cfg.setExtraParam("writers", 2);
                cfg.setExtraParam("operationsEach", 4);
                break;
            case "Sleeping Barber":
                cfg.setNumberOfProcesses(6);
                cfg.setExtraParam("waitingChairs", 3);
                break;
            case "Filter Lock":
            case "Eisenberg & McGuire":
            case "Black-White Bakery Algorithm":
                    cfg.setNumberOfProcesses(4);
                    cfg.setExtraParam("criticalSectionCount", 4);
                    break;
            case "Cigarette Smokers":
                cfg.setNumberOfProcesses(4);
                cfg.setExtraParam("rounds", 6);
                break;
            case "Yang-Anderson Algorithm":
                cfg.setNumberOfProcesses(4);
                cfg.setExtraParam("criticalSectionCount", 3);
                break;
            default:
                cfg.setNumberOfProcesses(4);
                cfg.setExtraParam("criticalSectionCount", 4);
        }
        return cfg;
    }
}
