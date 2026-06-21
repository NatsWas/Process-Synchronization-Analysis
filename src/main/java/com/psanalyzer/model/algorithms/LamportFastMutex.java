package com.psanalyzer.model.algorithms;
import com.psanalyzer.model.data.MetricsResult;
import com.psanalyzer.model.data.ProcessModel;
import com.psanalyzer.model.data.SimConfig;
import com.psanalyzer.model.data.TraceEvent;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicIntegerArray;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.Consumer;
public class LamportFastMutex extends BaseAlgorithm {
    @Override
    public String getName() { return "Lamport's Fast Mutual Exclusion"; }
    @Override
    public String getDescription() {
        return "Fast mutual exclusion algorithm requiring only 5 writes "
             + "and 2 reads in the absence of contention. "
             + "Guarantees mutual exclusion and deadlock freedom "
             + "but allows starvation of individual processes.";
    }
    @Override
    public MetricsResult simulate(SimConfig config, Consumer<TraceEvent> cb) {
        int n = Math.max(config.getNumberOfProcesses(), 2);
        int maxCS = config.getExtraParamInt("criticalSectionCount", 4);
        int csWorkMs = config.getExtraParamInt("csWorkMs", 2);
        int remainderMs = config.getExtraParamInt("remainderMs", 1);
        AtomicIntegerArray b = new AtomicIntegerArray(n);
        AtomicInteger x = new AtomicInteger(0);
        AtomicInteger y = new AtomicInteger(0);
        AtomicLong time = new AtomicLong(0);
        AtomicLong[] busyTicks = new AtomicLong[n];
        for (int i = 0; i < n; i++) busyTicks[i] = new AtomicLong(0);
        AtomicInteger inCS = new AtomicInteger(0);
        AtomicBoolean violationDetected = new AtomicBoolean(false);
        List<ProcessModel> processes = new ArrayList<>();
        for (int i = 0; i < n; i++) {
            ProcessModel p = new ProcessModel(i, 0, maxCS);
            p.setStartTime(0);
            processes.add(p);
            emitEvent(cb, time.get(), i, 0, TraceEvent.EventType.PROCESS_START, "P" + i + " starts");
        }
        List<Throwable> errors = new CopyOnWriteArrayList<>();
        CountDownLatch startLatch = new CountDownLatch(1);
        List<Thread> threads = new ArrayList<>();
        for (int idx = 0; idx < n; idx++) {
            final int i = idx;
            final int pid = i + 1;
            Thread t = new Thread(() -> {
                try { startLatch.await(); } catch (InterruptedException e) {
                    Thread.currentThread().interrupt(); return;
                }
                try {
                    for (int cs = 0; cs < maxCS; cs++) {
                        if (violationDetected.get()) break;
                        boolean restart;
                        do {
                            restart = false;
                            if (violationDetected.get()) break;
                            // b[i] = true
                            b.set(i, 1);
                            emitEvent(cb, time.incrementAndGet(), i, 0,
                                TraceEvent.EventType.ACQUIRE_REQUEST,
                                "P" + i + ": b[" + i + "]=true -> entered entry protocol");
                            // x = i
                            x.set(pid);
                            emitEvent(cb, time.incrementAndGet(), i, 0,
                                TraceEvent.EventType.ACQUIRE_REQUEST,
                                "P" + i + ": x=" + pid + " -> wrote process id to x");
                            // if y != 0
                            if (y.get() != 0) {
                                b.set(i, 0);
                                emitEvent(cb, time.incrementAndGet(), i, 0,
                                    TraceEvent.EventType.BUSY_WAIT,
                                    "P" + i + ": y=" + y.get() + " != 0, contention -> await y=0");
                                while (y.get() != 0 && !violationDetected.get()) {
                                    processes.get(i).addWaitingTime(1);
                                    busyTicks[i].incrementAndGet();
                                    Thread.yield();
                                }
                                restart = true;
                                continue;
                            }
                            // y = i
                            y.set(pid);
                            emitEvent(cb, time.incrementAndGet(), i, 0,
                                TraceEvent.EventType.ACQUIRE_REQUEST,
                                "P" + i + ": y=" + pid + " -> claimed CS intent");
                            // if x != i
                            if (x.get() != pid) {
                                b.set(i, 0);
                                emitEvent(cb, time.incrementAndGet(), i, 0,
                                    TraceEvent.EventType.BUSY_WAIT,
                                    "P" + i + ": x=" + x.get() + " != " + pid + " -> contention on x");
                                for (int j = 0; j < n; j++) {
                                    if (j == i) continue;
                                    while (b.get(j) == 1 && !violationDetected.get()) {
                                        processes.get(i).addWaitingTime(1);
                                        busyTicks[i].incrementAndGet();
                                        Thread.yield();
                                    }
                                }

                                if (y.get() != pid) {
                                    emitEvent(cb, time.incrementAndGet(), i, 0,
                                        TraceEvent.EventType.BUSY_WAIT,
                                        "P" + i + ": lost CS claim -> await y=0");
                                    while (y.get() != 0 && !violationDetected.get()) {
                                        processes.get(i).addWaitingTime(1);
                                        busyTicks[i].incrementAndGet();
                                        Thread.yield();
                                    }
                                    restart = true;
                                }
                            }

                        } while (restart);
                        if (violationDetected.get()) { b.set(i, 0); break; }
                        int simultaneous = inCS.incrementAndGet();
                        if (simultaneous > 1) {
                            violationDetected.set(true);
                            emitEvent(cb, time.incrementAndGet(), i, 0,
                                TraceEvent.EventType.CUSTOM,
                                "⚠ MUTEX VIOLATION: " + simultaneous + " processes in CS at P" + i);
                            inCS.decrementAndGet();
                            y.set(0);
                            b.set(i, 0);
                            break; 
                        }
                        if (cs == 0) {
                            processes.get(i).setResponseTime(time.get());
                        }
                        emitEvent(cb, time.incrementAndGet(), i, 0,
                            TraceEvent.EventType.ACQUIRE_SUCCESS,
                            "P" + i + " enters Critical Section #" + (cs + 1));
                        Thread.sleep(csWorkMs);
                        time.addAndGet(csWorkMs);
                        y.set(0);
                        emitEvent(cb, time.incrementAndGet(), i, 0,
                            TraceEvent.EventType.RELEASE,
                            "P" + i + " exits CS #" + (cs + 1) + " -> y=0");
                        b.set(i, 0);
                        emitEvent(cb, time.incrementAndGet(), i, 0,
                            TraceEvent.EventType.CUSTOM,
                            "P" + i + ": b[" + i + "]=false -> Remainder Section");
                        inCS.decrementAndGet();
                        Thread.sleep(remainderMs);
                        time.addAndGet(remainderMs);
                    }
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    errors.add(e);
                    b.set(i, 0);
                    y.compareAndSet(pid, 0);
                }
                processes.get(i).setFinishTime(time.get());
            });
            threads.add(t);
        }
        for (Thread t : threads) t.start();
        startLatch.countDown(); 
        for (Thread t : threads) {
            try { t.join(30_000); } 
            catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                t.interrupt();
            }
        }

        long finalTime = Math.max(time.get(), 1L);
        long totalBusyTicks = 0;
        for (int i = 0; i < n; i++) {
            if (processes.get(i).getFinishTime() <= 0)
                processes.get(i).setFinishTime(finalTime);
            totalBusyTicks += busyTicks[i].get();
            emitEvent(cb, finalTime, i, 0,
                TraceEvent.EventType.PROCESS_FINISH,
                "P" + i + " finished");
        }
        return computeMetrics(getName(), processes, finalTime, totalBusyTicks);
    }
}