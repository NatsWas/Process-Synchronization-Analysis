package com.psanalyzer.model.simulation;
import com.psanalyzer.model.algorithms.SynchronizationAlgorithm;
import com.psanalyzer.model.data.MetricsResult;
import com.psanalyzer.model.data.SimConfig;
import com.psanalyzer.model.data.TraceEvent;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;
public class SimulationEngine {
    private SynchronizationAlgorithm algorithm;
    private SimConfig config;
    private List<TraceEvent> traceLog;
    private MetricsResult metricsResult;
    private List<Consumer<TraceEvent>> traceListeners;
    private List<Consumer<MetricsResult>> metricsListeners;
    private volatile boolean running;
    private volatile boolean paused;
    private int stepDelayMs;
    public SimulationEngine() {
        this.traceLog = new ArrayList<>();
        this.traceListeners = new ArrayList<>();
        this.metricsListeners = new ArrayList<>();
        this.stepDelayMs = 50;
        this.running = false;
        this.paused = false;
    }
    public void configure(SynchronizationAlgorithm algorithm,
                          SimConfig config) {
        this.algorithm = algorithm;
        this.config = config;
        this.traceLog.clear();
    }
    public void addTraceListener(Consumer<TraceEvent> listener) {
        traceListeners.add(listener);
    }
    public void addMetricsListener(Consumer<MetricsResult> listener) {
        metricsListeners.add(listener);
    }
    public void runAsync(Runnable onComplete) {
        running = true;
        paused = false;
        Thread thread = new Thread(() -> {
            try {
                metricsResult = algorithm.simulate(config, event -> {
                    traceLog.add(event);
                    for (Consumer<TraceEvent> l : traceListeners)
                        l.accept(event);
                    if (stepDelayMs > 0) {
                        try {
                            Thread.sleep(stepDelayMs);
                        } catch (InterruptedException ignored) {}
                    }
                    while (paused && running) {
                        try {
                            Thread.sleep(100);
                        } catch (InterruptedException ignored) {}
                    }
                });
                for (Consumer<MetricsResult> l : metricsListeners)
                    l.accept(metricsResult);
            } finally {
                running = false;
                if (onComplete != null) onComplete.run();
            }
        });
        thread.setDaemon(true);
        thread.start();
    }
    public MetricsResult runSync() {
        traceLog.clear();
        metricsResult = algorithm.simulate(config, event -> {
            traceLog.add(event);
            for (Consumer<TraceEvent> l : traceListeners)
                l.accept(event);
        });
        for (Consumer<MetricsResult> l : metricsListeners)
            l.accept(metricsResult);
        return metricsResult;
    }
    public void pause()  { paused = true; }
    public void resume() { paused = false; }
    public void stop()   { running = false; paused = false; }
    public boolean isRunning() { return running; }
    public boolean isPaused()  { return paused; }
    public List<TraceEvent> getTraceLog()      { return traceLog; }
    public MetricsResult    getMetricsResult() { return metricsResult; }
    public void setStepDelayMs(int ms)         { this.stepDelayMs = ms; }
}