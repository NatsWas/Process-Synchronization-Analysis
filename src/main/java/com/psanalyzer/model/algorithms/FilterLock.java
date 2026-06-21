package com.psanalyzer.model.algorithms;
import com.psanalyzer.model.data.*;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicIntegerArray;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.locks.LockSupport;
import java.util.function.Consumer;
public class FilterLock extends BaseAlgorithm {
    @Override
    public String getName() { return "Filter Lock"; }
    @Override
    public String getDescription() {
        return "N-process generalization of Peterson's algorithm using N-1 levels. " +
               "Guarantees mutual exclusion, deadlock-freedom, and starvation-freedom. " +
               "Bounded waiting r=∞ (no fairness guarantee — threads may be overtaken).";
    }
    @Override
    public MetricsResult simulate(SimConfig config, Consumer<TraceEvent> cb) {
        int nRaw = config.getNumberOfProcesses();
        if (nRaw < 2) nRaw = 4;
        final int n = nRaw;
        int maxCS = config.getExtraParamInt("criticalSectionCount", 3);
        AtomicLong[] startTime    = new AtomicLong[n];
        AtomicLong[] finishTime   = new AtomicLong[n]; 
        AtomicLong[] responseTime = new AtomicLong[n]; 
        AtomicLong[] waitingTime  = new AtomicLong[n]; 
        for (int i = 0; i < n; i++) {
            startTime[i]= new AtomicLong(0L);
            finishTime[i]= new AtomicLong(0L);
            responseTime[i]= new AtomicLong(-1L);
            waitingTime[i]= new AtomicLong(0L);
        }
        AtomicIntegerArray level=new AtomicIntegerArray(n);
        AtomicIntegerArray waiting=new AtomicIntegerArray(n);
        for (int i = 0; i < n; i++) waiting.set(i, -1);
        AtomicLong    time=new AtomicLong(0);
        AtomicLong totalBusy=new AtomicLong(0);
        AtomicLong csWork=new AtomicLong(0);
        AtomicInteger inCS=new AtomicInteger(0);
        List<Thread> threads=new ArrayList<>();
        List<Throwable> errors = Collections.synchronizedList(new ArrayList<>());
        for (int i = 0; i < n; i++) {
            emitEvent(cb, time.get(), i, 0,
                    TraceEvent.EventType.PROCESS_START, "P" + i + " starts");
        }
        for (int idx = 0; idx < n; idx++) {
            final int i = idx;
            Thread t = new Thread(() -> {
                try {
                    startTime[i].set(time.get());
                    for (int cs = 0; cs < maxCS; cs++) {
                        for (int lv = 1; lv < n; lv++) {
                            level.set(i, lv);
                            emitEvent(cb, time.incrementAndGet(), i, 0,
                                    TraceEvent.EventType.ACQUIRE_REQUEST,
                                    "P" + i + ": level[" + i + "]=" + lv);
                            waiting.set(lv, i);
                            emitEvent(cb, time.incrementAndGet(), i, 0,
                                    TraceEvent.EventType.ACQUIRE_REQUEST,
                                    "P" + i + ": waiting[" + lv + "]=" + i
                                    + " (victim at level " + lv + ")");
                            while (true) {
                                boolean competition = false;
                                for (int k = 0; k < n; k++) {
                                    if (k != i && level.get(k) >= lv) {
                                        competition = true;
                                        break;
                                    }
                                }
                                if (competition && waiting.get(lv) == i) {
                                    emitEvent(cb, time.incrementAndGet(), i, 0,
                                            TraceEvent.EventType.BUSY_WAIT,
                                            "P" + i + ": spinning at level " + lv
                                            + " (victim, competition exists)");
                                    waitingTime[i].incrementAndGet();
                                    totalBusy.incrementAndGet();
                                    LockSupport.parkNanos(1L);
                                } else {
                                    emitEvent(cb, time.incrementAndGet(), i, 0,
                                            TraceEvent.EventType.ACQUIRE_REQUEST,
                                            "P" + i + ": passed level " + lv);
                                    break;
                                }
                            }
                        }
                        int simultaneous = inCS.incrementAndGet();
                        if (simultaneous > 1) {
                            emitEvent(cb, time.incrementAndGet(), i, 0,
                                    TraceEvent.EventType.CUSTOM,
                                    "WARNING: MUTEX VIOLATION — "
                                    + simultaneous + " threads in CS simultaneously!");
                        }
                        long csTick = time.incrementAndGet();
                        responseTime[i].compareAndSet(-1L, csTick);
                        emitEvent(cb, csTick, i, 0,
                                TraceEvent.EventType.ACQUIRE_SUCCESS,
                                "P" + i + " enters CS #" + (cs + 1));
                        csWork.addAndGet(2);
                        emitEvent(cb, time.incrementAndGet(), i, 0,
                                TraceEvent.EventType.CUSTOM,
                                "P" + i + " CS work ticks so far: " + csWork.get());
                        Thread.sleep(2);
                        time.addAndGet(2);
                        level.set(i, 0);
                        emitEvent(cb, time.incrementAndGet(), i, 0,
                                TraceEvent.EventType.RELEASE,
                                "P" + i + ": exits CS #" + (cs + 1)
                                + " -> level[" + i + "]=0");
                        inCS.decrementAndGet();
                        emitEvent(cb, time.incrementAndGet(), i, 0,
                                TraceEvent.EventType.CUSTOM,
                                "P" + i + " in Remainder Section");
                        Thread.sleep(1);
                    }
                    finishTime[i].set(time.get());
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    errors.add(e);
                } finally {
                    level.set(i, 0);
                }
            });
            threads.add(t);
        }
        for (Thread t : threads) t.start();
        for (Thread t : threads) {
            try {
                t.join();
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                t.interrupt();
            }
        }
        if (!errors.isEmpty()) {
            throw new RuntimeException(
                    "FilterLock simulation failed — thread interrupted: "
                    + errors.get(0).getMessage(), errors.get(0));
        }
        List<ProcessModel> processes = new ArrayList<>();
        for (int i = 0; i < n; i++) {
            ProcessModel p = new ProcessModel(i, 0, maxCS);
            p.setStartTime(startTime[i].get());   
            p.setFinishTime(finishTime[i].get());  
            long rt = responseTime[i].get();
            p.setResponseTime(rt < 0 ? 0L : rt);  
            p.addWaitingTime(waitingTime[i].get()); 
            emitEvent(cb, time.get(), i, 0,
                    TraceEvent.EventType.PROCESS_FINISH,
                    "P" + i + " finished"
                    + " | startTick="  + startTime[i].get()
                    + " finishTick="   + finishTime[i].get()
                    + " TAT="          + (finishTime[i].get() - startTime[i].get())
                    + " responseTick=" + p.getResponseTime()
                    + " waitTicks="    + waitingTime[i].get());

            processes.add(p);
        }
        long finalTime = time.get();
        emitEvent(cb, finalTime, -1, 0,
                TraceEvent.EventType.CUSTOM,
                "CS work ticks: " + csWork.get()
                + " | spin ticks: " + totalBusy.get()
                + " | finalTime: "  + finalTime);
        return computeMetrics(getName(), processes, finalTime, totalBusy.get());
    }
}