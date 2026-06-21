package com.psanalyzer.model.algorithms;

import com.psanalyzer.model.data.MetricsResult;
import com.psanalyzer.model.data.ProcessModel;
import com.psanalyzer.model.data.SimConfig;
import com.psanalyzer.model.data.TraceEvent;

import java.util.List;
import java.util.ArrayList;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicIntegerArray;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.Consumer;
public class PetersonAlgorithm extends BaseAlgorithm {
private static final int BUSY_LOG_INTERVAL = 10;
private static final long CS_WORK_UNITS = 2L;
private final AtomicLong[] workClock = {
new AtomicLong(0),
new AtomicLong(0)
};
@Override
public String getName() {
return "Peterson's Algorithm";
}
@Override
public String getDescription() {
return "Two-process mutual exclusion using "
+ "flag array and turn variable. "
+ "Guarantees mutual exclusion, "
+ "progress and bounded waiting.";
}
@Override
public MetricsResult simulate(SimConfig config, Consumer<TraceEvent> cb) {
workClock[0].set(0);
workClock[1].set(0);
final int maxCS = config.getExtraParamInt("criticalSectionCount", 5);
final ProcessModel p0 = new ProcessModel(0, 0, maxCS);
final ProcessModel p1 = new ProcessModel(1, 0, maxCS);
final AtomicIntegerArray flag = new AtomicIntegerArray(2);
final AtomicInteger turn = new AtomicInteger(0);
final AtomicLong seqGen = new AtomicLong(0);
//Infrastructure shared state
final AtomicLong busyTicks0 = new AtomicLong(0);
final AtomicLong busyTicks1 = new AtomicLong(0);
final AtomicInteger inCS = new AtomicInteger(0);
final AtomicBoolean mutexViolated = new AtomicBoolean(false);
final List<Throwable> errors = new CopyOnWriteArrayList<>();
final CountDownLatch startLatch = new CountDownLatch(1);
Thread thread0 = buildProcessThread(
0, 1, // i=0, j=1
maxCS,
flag, turn,
seqGen, 
busyTicks0,
inCS, mutexViolated, errors,
p0,
startLatch, cb);
Thread thread1 = buildProcessThread(
1, 0, 
maxCS,
flag, turn,
seqGen,
busyTicks1,
inCS, mutexViolated, errors,
p1,
startLatch, cb);
thread0.start();
thread1.start();
startLatch.countDown(); 
try {
thread0.join();
thread1.join();
} catch (InterruptedException e) {
Thread.currentThread().interrupt();
thread0.interrupt();
thread1.interrupt();
errors.add(e);
}
if (mutexViolated.get()) {
emitEvent(cb, seqGen.incrementAndGet(), -1, 0,
TraceEvent.EventType.CUSTOM,
"⚠ Simulation ended: Mutual Exclusion was VIOLATED");
}
emitEvent(cb, seqGen.incrementAndGet(), 0, 0,
TraceEvent.EventType.PROCESS_FINISH,
"Pi(P0) completed " + p0.getCsCount() + "/" + maxCS + " CS entries");
emitEvent(cb, seqGen.incrementAndGet(), 1, 0,
TraceEvent.EventType.PROCESS_FINISH,
"Pj(P1) completed " + p1.getCsCount() + "/" + maxCS + " CS entries");
final long totalTime = Math.max(p0.getFinishTime(), p1.getFinishTime());
final long totalBusyTicks = busyTicks0.get() + busyTicks1.get();
final List<ProcessModel> processes = new ArrayList<>();
processes.add(p0);
processes.add(p1);
return buildMetrics(getName(), processes, totalTime, totalBusyTicks,
mutexViolated.get(), errors);
}
private Thread buildProcessThread(
final int i, final int j,
final int maxCS,
final AtomicIntegerArray flag,
final AtomicInteger turn,
final AtomicLong seqGen, // W-1
final AtomicLong busyTicksI,
final AtomicInteger inCS,
final AtomicBoolean mutexViolated,
final List<Throwable> errors,
final ProcessModel pm,
final CountDownLatch startLatch,
final Consumer<TraceEvent> cb) {
return new Thread(() -> {
try {
startLatch.await();
} catch (InterruptedException e) {
Thread.currentThread().interrupt();
errors.add(e);
pm.setFinishTime(seqGen.get());
return;
}
pm.setStartTime(seqGen.get());
emitEvent(cb, seqGen.incrementAndGet(), i, 0,
TraceEvent.EventType.PROCESS_START,
"P" + i + " starts (thread alive, latch released)");
try {
for (int cs = 0; cs < maxCS; cs++) {
if (mutexViolated.get()) break;
flag.set(i, 1);
workClock[i].incrementAndGet();
emitEvent(cb, seqGen.incrementAndGet(), i, 0,
TraceEvent.EventType.ACQUIRE_REQUEST,
"P" + i + ": flag[" + i + "]=true → P" + i
+ " wants to enter CS"
+ " [localClock=" + workClock[i].get() + "]");
turn.set(j);
workClock[i].incrementAndGet();
emitEvent(cb, seqGen.incrementAndGet(), i, 0,
TraceEvent.EventType.ACQUIRE_REQUEST,
"P" + i + ": turn=" + j + " → P" + i
+ " gives turn to P" + j
+ " [localClock=" + workClock[i].get() + "]");
long busySeqStart = seqGen.get();
int spinCount = 0;
while (flag.get(j) == 1 && turn.get() == j) {
if (mutexViolated.get()) break;
busyTicksI.incrementAndGet();
spinCount++;
if (spinCount == 1 || spinCount % BUSY_LOG_INTERVAL == 0) {
emitEvent(cb, seqGen.incrementAndGet(), i, 0,
TraceEvent.EventType.BUSY_WAIT,
"P" + i + " busy-waiting (spin #" + spinCount + "): "
+ "flag[" + j + "]=" + (flag.get(j) == 1)
+ " AND turn=" + turn.get() + "==" + j
+ " → P" + j + " has priority");
}
try {
Thread.sleep(0);
} catch (InterruptedException ie) {
Thread.currentThread().interrupt();
break;
}
}
long busyElapsedTicks = seqGen.get() - busySeqStart;
if (busyElapsedTicks > 0) pm.addWaitingTime(busyElapsedTicks);
if (Thread.interrupted()) {
Thread.currentThread().interrupt(); 
flag.set(i, 0); 
throw new InterruptedException(
"P" + i + " interrupted during busy-wait");
}
if (mutexViolated.get()) break;
// Critical Section entry
int simultaneous = inCS.incrementAndGet();
if (simultaneous > 1) {
mutexViolated.set(true);
emitEvent(cb, seqGen.incrementAndGet(), i, 0,
TraceEvent.EventType.CUSTOM,
"⚠ MUTEX VIOLATION detected by P" + i
+ ": " + simultaneous
+ " processes in CS simultaneously!");
break;
}

emitEvent(cb, seqGen.incrementAndGet(), i, 0,
        TraceEvent.EventType.ACQUIRE_SUCCESS,
        "P" + i + " ENTERS Critical Section #" + (cs + 1));
if (pm.getResponseTime() < 0) {
    long responseTicks = seqGen.get() - pm.getStartTime();
    pm.setResponseTime(responseTicks);
}
try {
Thread.sleep(2); 
} finally {
workClock[i].addAndGet(CS_WORK_UNITS);
inCS.decrementAndGet();
flag.set(i, 0);
pm.incrementCsCount();
}
emitEvent(cb, seqGen.incrementAndGet(), i, 0,
TraceEvent.EventType.RELEASE,
"P" + i + " EXITS CS #" + (cs + 1)
+ " → flag[" + i + "]=false"
+ " (completed " + pm.getCsCount() + ")"
+ " [localClock=" + workClock[i].get() + "]");
//Remainder section 
emitEvent(cb, seqGen.incrementAndGet(), i, 0,
TraceEvent.EventType.CUSTOM,
"P" + i + " in Remainder Section (CS #" + (cs + 1) + " done)");
try {
Thread.sleep(1); 
} catch (InterruptedException e) {
Thread.currentThread().interrupt();
emitEvent(cb, seqGen.incrementAndGet(), i, 0,
TraceEvent.EventType.CUSTOM,
"P" + i + " interrupted in Remainder Section"
+ " after CS #" + (cs + 1)
+ " — stopping early"
+ " [completed " + pm.getCsCount() + "/" + maxCS + "]");
errors.add(e);
break; 
}
}
} catch (InterruptedException e) {
flag.set(i, 0);
Thread.currentThread().interrupt();
errors.add(e);
}
pm.setFinishTime(seqGen.get());
});
}
}