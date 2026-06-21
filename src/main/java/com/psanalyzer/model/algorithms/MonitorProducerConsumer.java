package com.psanalyzer.model.algorithms;

import java.util.concurrent.atomic.AtomicLong;
import com.psanalyzer.model.data.MetricsResult;
import com.psanalyzer.model.data.ProcessModel;
import com.psanalyzer.model.data.SimConfig;
import com.psanalyzer.model.data.TraceEvent;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.locks.Condition;
import java.util.concurrent.locks.ReentrantLock;
import java.util.function.Consumer;

/**
 * Monitor Producer-Consumer — Iteration 5.
 * FIXED: Lost signal deadlocks when buffer hits 100% capacity (5/5) or small boundaries.
 * FIXED: Out-of-order execution trace timestamps by wrapping finishing events inside the lock.
 */
public class MonitorProducerConsumer extends BaseAlgorithm {

    @Override
    public String getName() { return "Monitor Producer-Consumer"; }

    @Override
    public String getDescription() {
        return "Bounded buffer using a monitor with two condition variables: "
                + "cv_full (producers wait when buffer full) and "
                + "cv_empty (consumers wait when buffer empty). "
                + "Document-canonical: char 'X' inserted by all producers, "
                + "char[] ring buffer, signalAll() used to eliminate MESA-semantic deadlocks.";
    }

    @Override
    public MetricsResult simulate(SimConfig config, Consumer<TraceEvent> cb) {

        final int bufferSize = config.getExtraParamInt("bufferSize", 5);
        final int numProducers = config.getExtraParamInt("producers", 2);
        final int numConsumers = config.getExtraParamInt("consumers", 2);
        final int itemsEach = config.getExtraParamInt("itemsEach", 4);

        // ── Monitor lock: fair=true → FIFO acquisition, bounded waiting ────────
        final ReentrantLock monitorLock = new ReentrantLock(true);

        // ── Condition Variables ────────────────────────────────────────────────
        final Condition cvFull = monitorLock.newCondition();  // producers wait here
        final Condition cvEmpty = monitorLock.newCondition(); // consumers wait here

        // ── Ring Buffer Structure ──────────────────────────────────────────────
        final char[] buf = new char[bufferSize];
        final int[] head = {0}; // next slot to consume
        final int[] tail = {0}; // next slot to produce into
        final int[] count = {0}; // sole occupancy source of truth

        // ── Clock Management ───────────────────────────────────────────────────
        final AtomicLong globalClock = new AtomicLong(1);
        final long[] sharedTime = {1};
        final long[] sharedBusy = {0};
        final long[] totalWaitTime = {0};

        // ── Process models ─────────────────────────────────────────────────────
        final int n = numProducers + numConsumers;
        final boolean[] firstResponseRecorded = new boolean[n];
        final List<ProcessModel> processes = new ArrayList<>();
        for (int i = 0; i < n; i++) {
            ProcessModel p = new ProcessModel(i, 0, itemsEach);
            p.setStartTime(0);
            p.setResponseTime(0);
            processes.add(p);
        }

        // t=0 Startup event
        emitEvent(cb, 0, -1, 0, TraceEvent.EventType.CUSTOM,
                "Monitor PC started | bufferSize=" + bufferSize
                + " producers=" + numProducers
                + " consumers=" + numConsumers
                + " itemsEach=" + itemsEach);

        List<Thread> threads = new ArrayList<>();

        // ════════════════════════════════════════════════════════════════════════
        // PRODUCER LOOP
        // ════════════════════════════════════════════════════════════════════════
        for (int i = 0; i < numProducers; i++) {
            final int pid = i;
            final char itemChar = 'X';

            Thread t = new Thread(() -> {
                for (int k = 0; k < itemsEach; k++) {

                    long reqTs = globalClock.getAndIncrement();
                    emitEvent(cb, reqTs, pid, 0,
                            TraceEvent.EventType.ACQUIRE_REQUEST,
                            "Producer P" + pid + " attempting to enter monitor");

                    monitorLock.lock();
                    try {
                        sharedTime[0] = Math.max(sharedTime[0], reqTs + 1);

                        emitEvent(cb, sharedTime[0]++, pid, 0,
                                TraceEvent.EventType.ACQUIRE_SUCCESS,
                                "Producer P" + pid + " entered monitor (lock acquired)");
                        if (!firstResponseRecorded[pid]) {
                            long acquireTs = sharedTime[0] - 1;
                            processes.get(pid).setResponseTime(acquireTs - reqTs);
                            firstResponseRecorded[pid] = true;
                        }

                        // Check for full buffer
                        while (count[0] == bufferSize) {
                            emitEvent(cb, sharedTime[0]++, pid, 0,
                                    TraceEvent.EventType.WAIT,
                                    "Producer P" + pid
                                    + " waiting on cv_full (buffer full: "
                                    + count[0] + "/" + bufferSize + ")"
                                    + " — releasing monitor lock atomically");

                            processes.get(pid).addWaitingTime(1);
                            totalWaitTime[0]++;

                            try {
                                cvFull.await();
                            } catch (InterruptedException ie) {
                                Thread.currentThread().interrupt();
                                emitEvent(cb, sharedTime[0]++, pid, 0,
                                        TraceEvent.EventType.CUSTOM,
                                        "Producer P" + pid
                                        + " interrupted while waiting on cv_full");
                                return;
                            }

                            emitEvent(cb, sharedTime[0]++, pid, 0,
                                    TraceEvent.EventType.CUSTOM,
                                    "Producer P" + pid
                                    + " woke from cv_full, reacquired monitor lock"
                                    + " (re-checking: count=" + count[0] + ")");
                        }

                        // Write to ring buffer
                        buf[tail[0]] = itemChar;
                        tail[0] = (tail[0] + 1) % bufferSize;
                        count[0]++;
                        int currentCount = count[0];
                        sharedBusy[0]++;

                        emitEvent(cb, sharedTime[0]++, pid, 0,
                                TraceEvent.EventType.CUSTOM,
                                "Producer P" + pid
                                + " PRODUCED item='" + itemChar
                                + "' | buffer count=" + currentCount + "/" + bufferSize);

                        // FIXED: signalAll() avoids thread bypass deadlocks on full configurations
                        emitEvent(cb, sharedTime[0]++, pid, 0,
                                TraceEvent.EventType.SIGNAL,
                                "Producer P" + pid + " SIGNAL on cv_empty");
                        cvEmpty.signalAll();

                    } finally {
                        long snapTime = sharedTime[0];
                        monitorLock.unlock();
                        globalClock.updateAndGet(v -> Math.max(v, snapTime));
                    }
                } // End item loop

                // ── Record finish time (FIXED: emitEvent moved INSIDE lock to preserve chronology) ──
                monitorLock.lock();
                try {
                    long finishTime = sharedTime[0]++;
                    processes.get(pid).setFinishTime(finishTime);
                    
                    emitEvent(cb, finishTime, pid, 0,
                            TraceEvent.EventType.PROCESS_FINISH,
                            "Producer P" + pid + " finished all " + itemsEach + " items");
                    
                    long snapTime = sharedTime[0];
                    globalClock.updateAndGet(v -> Math.max(v, snapTime));
                } finally {
                    monitorLock.unlock();
                }
            });
            threads.add(t);
        }

        // ════════════════════════════════════════════════════════════════════════
        // CONSUMER LOOP
        // ════════════════════════════════════════════════════════════════════════
        for (int i = 0; i < numConsumers; i++) {
            final int cid = i;
            final int pid = numProducers + i;

            Thread t = new Thread(() -> {
                for (int k = 0; k < itemsEach; k++) {

                    long reqTs = globalClock.getAndIncrement();
                    emitEvent(cb, reqTs, pid, 0,
                            TraceEvent.EventType.ACQUIRE_REQUEST,
                            "Consumer C" + cid + " attempting to enter monitor");

                    monitorLock.lock();
                    try {
                        sharedTime[0] = Math.max(sharedTime[0], reqTs + 1);

                        emitEvent(cb, sharedTime[0]++, pid, 0,
                                TraceEvent.EventType.ACQUIRE_SUCCESS,
                                "Consumer C" + cid + " entered monitor (lock acquired)");
                        if (!firstResponseRecorded[pid]) {
                            long acquireTs = sharedTime[0] - 1;
                            processes.get(pid).setResponseTime(acquireTs - reqTs);
                            firstResponseRecorded[pid] = true;
                        }

                        // Check for empty buffer
                        while (count[0] == 0) {
                            emitEvent(cb, sharedTime[0]++, pid, 0,
                                    TraceEvent.EventType.WAIT,
                                    "Consumer C" + cid
                                    + " waiting on cv_empty (buffer empty)"
                                    + " — releasing monitor lock atomically");

                            processes.get(pid).addWaitingTime(1);
                            totalWaitTime[0]++;

                            try {
                                cvEmpty.await();
                            } catch (InterruptedException ie) {
                                Thread.currentThread().interrupt();
                                emitEvent(cb, sharedTime[0]++, pid, 0,
                                        TraceEvent.EventType.CUSTOM,
                                        "Consumer C" + cid
                                        + " interrupted while waiting on cv_empty");
                                return;
                            }

                            emitEvent(cb, sharedTime[0]++, pid, 0,
                                    TraceEvent.EventType.CUSTOM,
                                    "Consumer C" + cid
                                    + " woke from cv_empty, reacquired monitor lock"
                                    + " (re-checking: count=" + count[0] + ")");
                        }

                        // Read from ring buffer
                        char item = buf[head[0]];
                        head[0] = (head[0] + 1) % bufferSize;
                        count[0]--;
                        int currentCount = count[0];
                        sharedBusy[0]++;

                        emitEvent(cb, sharedTime[0]++, pid, 0,
                                TraceEvent.EventType.CUSTOM,
                                "Consumer C" + cid
                                + " CONSUMED item='" + item
                                + "' | buffer count=" + currentCount + "/" + bufferSize);

                        // FIXED: Always issue notification when space frees up under MESA semantics
                        emitEvent(cb, sharedTime[0]++, pid, 0,
                                TraceEvent.EventType.SIGNAL,
                                "Consumer C" + cid + " SIGNAL on cv_full");
                        cvFull.signalAll();

                    } finally {
                        long snapTime = sharedTime[0];
                        monitorLock.unlock();
                        globalClock.updateAndGet(v -> Math.max(v, snapTime));
                    }
                } // End item loop

                // ── Record finish time (FIXED: emitEvent moved INSIDE lock to preserve chronology) ──
                monitorLock.lock();
                try {
                    long finishTime = sharedTime[0]++;
                    processes.get(pid).setFinishTime(finishTime);
                    
                    emitEvent(cb, finishTime, pid, 0,
                            TraceEvent.EventType.PROCESS_FINISH,
                            "Consumer C" + cid + " finished all " + itemsEach + " items");
                    
                    long snapTime = sharedTime[0];
                    globalClock.updateAndGet(v -> Math.max(v, snapTime));
                } finally {
                    monitorLock.unlock();
                }
            });
            threads.add(t);
        }

        // Start all threads
        for (Thread t : threads) t.start();

        // Join all threads
        for (Thread t : threads) {
            try {
                t.join();
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                break;
            }
        }

        // Final clock sync
        monitorLock.lock();
        try {
            sharedTime[0] = Math.max(sharedTime[0], globalClock.get());
        } finally {
            monitorLock.unlock();
        }

        emitEvent(cb, sharedTime[0]++, -1, 0,
                TraceEvent.EventType.CUSTOM,
                "Simulation complete - " + getName());

        return computeMetrics(getName(), processes, sharedTime[0], sharedBusy[0]);
    }
}