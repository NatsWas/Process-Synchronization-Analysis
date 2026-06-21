package com.psanalyzer.model.algorithms;
import com.psanalyzer.model.data.*;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.Semaphore;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.Consumer;
public class ReadersWriters extends BaseAlgorithm {
    @Override
    public String getName() { return "Readers-Writers (Writer Priority)"; }
    @Override
    public String getDescription() {
        return "Writer-priority solution using semaphores (canonical AW/WW/AR/WR variant): "
             + "mutex protects all shared counters atomically, "
             + "OKToRead/OKToWrite semaphores provide conditional wakeups, "
             + "writers have strict priority over readers, "
             + "WARNING: readers may starve under sustained writer load.";
    }
    @Override
    public MetricsResult simulate(SimConfig config, Consumer<TraceEvent> cb) {
        int numReaders = config.getExtraParamInt("readers", 3);
        int numWriters = config.getExtraParamInt("writers", 2);
        int opsEach    = config.getExtraParamInt("operationsEach", 4);
        int n = numReaders + numWriters;
        long[]      startTime    = new long[n];
        AtomicLong[] finishTime  = new AtomicLong[n];
        AtomicLong[] responseTime = new AtomicLong[n];
        AtomicLong[] waitingTime  = new AtomicLong[n];
        for (int i = 0; i < n; i++) {
            startTime[i]    = 0L;
            finishTime[i]   = new AtomicLong(0L);
            responseTime[i] = new AtomicLong(-1L);   // -1 = not yet set
            waitingTime[i]  = new AtomicLong(0L);
        }
        Semaphore mutex    = new Semaphore(1, true);
        Semaphore OKToRead  = new Semaphore(0, true);
        Semaphore OKToWrite = new Semaphore(0, true);
        AtomicInteger readCount     = new AtomicInteger(0); // AR
        AtomicInteger activeWriters = new AtomicInteger(0); // AW
        AtomicInteger waitingReaders = new AtomicInteger(0); // WR
        AtomicInteger waitingWriters = new AtomicInteger(0); // WW
        AtomicLong  globalTick  = new AtomicLong(0);
        AtomicLong  busyTicks   = new AtomicLong(0);
        List<Thread>    threads = new ArrayList<>();
        List<Throwable> errors  = new CopyOnWriteArrayList<>();

        emitEvent(cb, globalTick.incrementAndGet(), -1, 0,
                TraceEvent.EventType.CUSTOM,
                "Readers-Writers (Writer Priority) started | "
                + "readers=" + numReaders
                + " writers=" + numWriters
                + " opsEach=" + opsEach);
        for (int idx = 0; idx < numReaders; idx++) {
            final int i   = idx;
            final int pid = i;
            Thread t = new Thread(() -> {
                try {
                    for (int op = 0; op < opsEach; op++) {
                        emitEvent(cb, globalTick.incrementAndGet(), pid, 0,
                                TraceEvent.EventType.ACQUIRE_REQUEST,
                                "Reader R" + i + " wants to read");
                        mutex.acquire();
                        boolean mustWait = (activeWriters.get() + waitingWriters.get()) > 0;
                        if (mustWait) {
                            waitingReaders.incrementAndGet();            // WR++
                            emitEvent(cb, globalTick.incrementAndGet(), pid, 0,
                                    TraceEvent.EventType.BUSY_WAIT,
                                    "Reader R" + i
                                    + ": AW+WW > 0, WR++ -> blocking on OKToRead"
                                    + " | WR=" + waitingReaders.get()
                                    + " AW=" + activeWriters.get()
                                    + " WW=" + waitingWriters.get());
                            mutex.release();
                            long waitStart = System.currentTimeMillis();
                            OKToRead.acquire();                          // block
                            waitingTime[pid].addAndGet(
                                    System.currentTimeMillis() - waitStart);
                            mutex.acquire();
                            readCount.incrementAndGet();                 // AR++
                            waitingReaders.decrementAndGet();            // WR--
                            if (waitingWriters.get() == 0 && waitingReaders.get() > 0) {
                                OKToRead.release();   // wake the next waiting reader
                            }
                            emitEvent(cb, globalTick.incrementAndGet(), pid, 0,
                                    TraceEvent.EventType.SIGNAL,
                                    "Reader R" + i
                                    + ": woken, AR++ WR-- under mutex"
                                    + " | AR=" + readCount.get()
                                    + " WR=" + waitingReaders.get());
                            mutex.release();
                        } else {
                            OKToRead.release();                          // V(OKToRead): permit = 1
                            readCount.incrementAndGet();                 // AR++
                            OKToRead.acquire();                          // P(OKToRead): permit = 0  ← under mutex
                            emitEvent(cb, globalTick.incrementAndGet(), pid, 0,
                                    TraceEvent.EventType.SIGNAL,
                                    "Reader R" + i
                                    + ": AW+WW == 0, self-signal AR++"
                                    + " | AR=" + readCount.get());
                            mutex.release();                             // release AFTER consuming permit
                        }
                        long csTick = globalTick.incrementAndGet();
                        responseTime[pid].compareAndSet(-1L, csTick);

                        emitEvent(cb, csTick, pid, 0,
                                TraceEvent.EventType.ACQUIRE_SUCCESS,
                                "Reader R" + i
                                + " READING | AR=" + readCount.get()
                                + " AW=" + activeWriters.get()
                                + " WW=" + waitingWriters.get());

                        busyTicks.incrementAndGet();
                        Thread.sleep(2);                                 // simulate read work

                        // ---- EXIT PROTOCOL ---------------------------------

                        mutex.acquire();
                        int arAfter = readCount.decrementAndGet();       // AR--

                        emitEvent(cb, globalTick.incrementAndGet(), pid, 0,
                                TraceEvent.EventType.RELEASE,
                                "Reader R" + i
                                + " done reading | AR=" + arAfter
                                + " WW=" + waitingWriters.get());

                        if (arAfter == 0 && waitingWriters.get() > 0) {
                            OKToWrite.release();                         
                            emitEvent(cb, globalTick.incrementAndGet(), pid, 0,
                                    TraceEvent.EventType.SIGNAL,
                                    "Reader R" + i
                                    + ": last reader, WW > 0 -> V(OKToWrite)"
                                    + " | WW=" + waitingWriters.get());
                        }
                        mutex.release();
                        Thread.sleep(1);                                 
                    }
                    finishTime[pid].set(globalTick.incrementAndGet());

                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    errors.add(e);
                }
            });
            threads.add(t);
        }
        for (int idx = 0; idx < numWriters; idx++) {
            final int i   = idx;
            final int pid = numReaders + i;
            Thread t = new Thread(() -> {
                try {
                    for (int op = 0; op < opsEach; op++) {
                        emitEvent(cb, globalTick.incrementAndGet(), pid, 0,
                                TraceEvent.EventType.ACQUIRE_REQUEST,
                                "Writer W" + i + " wants to write");

                        mutex.acquire();
                        boolean mustWait = (activeWriters.get() + readCount.get()) > 0;
                        if (mustWait) {
                            // Database busy — register as waiting writer
                            waitingWriters.incrementAndGet();            // WW++
                            emitEvent(cb, globalTick.incrementAndGet(), pid, 0,
                                    TraceEvent.EventType.BUSY_WAIT,
                                    "Writer W" + i
                                    + ": AW+AR > 0, WW++ -> blocking on OKToWrite"
                                    + " | WW=" + waitingWriters.get()
                                    + " AW=" + activeWriters.get()
                                    + " AR=" + readCount.get());
                            mutex.release();
                            long waitStart = System.currentTimeMillis();
                            OKToWrite.acquire();                         // block
                            waitingTime[pid].addAndGet(
                                    System.currentTimeMillis() - waitStart);
                            mutex.acquire();
                            activeWriters.incrementAndGet();             // AW++
                            waitingWriters.decrementAndGet();            // WW--
                            emitEvent(cb, globalTick.incrementAndGet(), pid, 0,
                                    TraceEvent.EventType.SIGNAL,
                                    "Writer W" + i
                                    + ": woken, AW++ WW-- under mutex"
                                    + " | AW=" + activeWriters.get()
                                    + " WW=" + waitingWriters.get());
                            mutex.release();
                        } else {
                            OKToWrite.release();                         
                            activeWriters.incrementAndGet();             
                            OKToWrite.acquire();                         
                            emitEvent(cb, globalTick.incrementAndGet(), pid, 0,
                                    TraceEvent.EventType.SIGNAL,
                                    "Writer W" + i
                                    + ": AW+AR == 0, self-signal AW++"
                                    + " | AW=" + activeWriters.get());
                            mutex.release();                             
                        }
                        long csTick = globalTick.incrementAndGet();
                        responseTime[pid].compareAndSet(-1L, csTick);
                        emitEvent(cb, csTick, pid, 0,
                                TraceEvent.EventType.ACQUIRE_SUCCESS,
                                "Writer W" + i
                                + " WRITING exclusively"
                                + " | AW=" + activeWriters.get()
                                + " AR=" + readCount.get()
                                + " WW=" + waitingWriters.get()
                                + " WR=" + waitingReaders.get());
                        busyTicks.addAndGet(2);
                        Thread.sleep(2);                                 
                        mutex.acquire();
                        activeWriters.decrementAndGet();                 
                        emitEvent(cb, globalTick.incrementAndGet(), pid, 0,
                                TraceEvent.EventType.RELEASE,
                                "Writer W" + i
                                + " done writing"
                                + " | AW=" + activeWriters.get()
                                + " WW=" + waitingWriters.get()
                                + " WR=" + waitingReaders.get());
                        if (waitingWriters.get() > 0) {
                            OKToWrite.release();                        
                            emitEvent(cb, globalTick.incrementAndGet(), pid, 0,
                                    TraceEvent.EventType.SIGNAL,
                                    "Writer W" + i
                                    + ": WW > 0 -> V(OKToWrite) writer-to-writer handoff"
                                    + " | WW=" + waitingWriters.get());

                        } else if (waitingReaders.get() > 0) {
                            OKToRead.release();                          
                            emitEvent(cb, globalTick.incrementAndGet(), pid, 0,
                                    TraceEvent.EventType.SIGNAL,
                                    "Writer W" + i
                                    + ": WW == 0, WR > 0 -> V(OKToRead) single wakeup"
                                    + " | WR=" + waitingReaders.get());
                        }
                        mutex.release();

                        emitEvent(cb, globalTick.incrementAndGet(), pid, 0,
                                TraceEvent.EventType.CUSTOM,
                                "Writer W" + i
                                + " in Remainder Section"
                                + " | AW=" + activeWriters.get()
                                + " WW=" + waitingWriters.get());

                        Thread.sleep(1);                                 
                    }
                    finishTime[pid].set(globalTick.incrementAndGet());

                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    errors.add(e);
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
        List<ProcessModel> processes = new ArrayList<>();
        for (int i = 0; i < n; i++) {
            ProcessModel p = new ProcessModel(i, 0, opsEach);
            p.setStartTime(startTime[i]);
            p.setFinishTime(finishTime[i].get());

            long rt = responseTime[i].get();
            p.setResponseTime(rt < 0 ? 0L : rt);          

            p.addWaitingTime(waitingTime[i].get());

            emitEvent(cb, finishTime[i].get(), i, 0,
                    TraceEvent.EventType.PROCESS_FINISH,
                    (i < numReaders
                            ? "Reader R" + i
                            : "Writer W" + (i - numReaders))
                    + " finished"
                    + " | finishTime=" + finishTime[i].get()
                    + " responseTime=" + p.getResponseTime()
                    + " waitingTime=" + waitingTime[i].get());

            processes.add(p);
        }

        long finalTime = globalTick.get();
        return computeMetrics(getName(), processes, finalTime, busyTicks.get());
    }
}