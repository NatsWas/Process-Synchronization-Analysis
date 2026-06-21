package com.psanalyzer.model.algorithms;
import com.psanalyzer.model.data.MetricsResult;
import com.psanalyzer.model.data.ProcessModel;
import com.psanalyzer.model.data.SimConfig;
import com.psanalyzer.model.data.TraceEvent;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicIntegerArray;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.Consumer;
public class DekkerAlgorithm extends BaseAlgorithm {
@Override
public String getName() { return "Dekker's Algorithm"; }
@Override
public String getDescription() {
return "First known correct solution to mutual exclusion "
+ "using flag and turn variables (Fifth Version — complete solution).";
}
@Override
public MetricsResult simulate(SimConfig config,
Consumer<TraceEvent> cb) {
int maxCS = config.getExtraParamInt("criticalSectionCount", 5);
ProcessModel p0 = new ProcessModel(0, 0, maxCS);
ProcessModel p1 = new ProcessModel(1, 0, maxCS);
AtomicIntegerArray wants = new AtomicIntegerArray(2); 
AtomicInteger favouredThread = new AtomicInteger(1); 
AtomicLong time = new AtomicLong(0);
AtomicLong busyTicks0 = new AtomicLong(0);
AtomicLong busyTicks1 = new AtomicLong(0);
AtomicInteger inCS = new AtomicInteger(0);
AtomicBoolean violated = new AtomicBoolean(false);
AtomicBoolean p0ResponseSet = new AtomicBoolean(false);
AtomicBoolean p1ResponseSet = new AtomicBoolean(false);
AtomicInteger p0Completed = new AtomicInteger(0);
AtomicInteger p1Completed = new AtomicInteger(0);
List<Throwable> errors = new CopyOnWriteArrayList<>();
long startTick = time.get(); 
p0.setStartTime(startTick);
p0.setResponseTime(-1); 
p1.setStartTime(startTick);
p1.setResponseTime(-1);
emitEvent(cb, time.get(), 0, 0,
TraceEvent.EventType.PROCESS_START, "P0 (Thread1) starts");
emitEvent(cb, time.incrementAndGet(), 1, 0,
TraceEvent.EventType.PROCESS_START, "P1 (Thread2) starts");
Thread t0 = new Thread(() -> {
try {
for (int cs = 0; cs < maxCS; cs++) {
if (violated.get()) {
wants.set(0, 0);
break;
}
wants.set(0, 1);
emitEvent(cb, time.incrementAndGet(), 0, 0,
TraceEvent.EventType.ACQUIRE_REQUEST,
"P0(Thread1): wants[0]=true -> P0 wants to enter CS");
while (wants.get(1) == 1) {
if (violated.get()) {
wants.set(0, 0);
return;
}
int ft = favouredThread.get();
if (ft == 2) {
wants.set(0, 0);
emitEvent(cb, time.incrementAndGet(), 0, 0,
TraceEvent.EventType.BUSY_WAIT,
"P0(Thread1): yields -> favouredThread=2, "
+ "wants[0]=false, waiting for turn");
while (true) {
int ftInner = favouredThread.get();
if (ftInner != 2) break;
if (violated.get()) {
wants.set(0, 0);
return;
}
busyTicks0.incrementAndGet();
long waitStart = System.currentTimeMillis();
emitEvent(cb, time.incrementAndGet(), 0, 0,
TraceEvent.EventType.BUSY_WAIT,
"P0(Thread1): busy-waiting, favouredThread="
+ ftInner + " -> P1 still favoured");
Thread.yield();
p0.addWaitingTime(System.currentTimeMillis() - waitStart);
}
wants.set(0, 1);
emitEvent(cb, time.incrementAndGet(), 0, 0,
TraceEvent.EventType.ACQUIRE_REQUEST,
"P0(Thread1): re-asserts wants[0]=true after gaining favour");
}
Thread.yield();
}
if (!inCS.compareAndSet(0, 1)) {
violated.set(true);
emitEvent(cb, time.incrementAndGet(), 0, 0,
TraceEvent.EventType.CUSTOM,
"WARNING: MUTEX VIOLATION — P0 could not acquire inCS lock!");
wants.set(0, 0);
favouredThread.set(2);
break;
}
long acquireTick = time.incrementAndGet();
if (p0ResponseSet.compareAndSet(false, true)) {
p0.setResponseTime(acquireTick);
}
emitEvent(cb, acquireTick, 0, 0,
TraceEvent.EventType.ACQUIRE_SUCCESS,
"P0(Thread1) enters Critical Section #" + (cs + 1));
Thread.sleep(2);
if (violated.get()) {
inCS.set(0);
wants.set(0, 0);
return;
}
inCS.set(0);
favouredThread.set(2);
emitEvent(cb, time.incrementAndGet(), 0, 0,
TraceEvent.EventType.RELEASE,
"P0(Thread1) exits CS #" + (cs + 1)
+ " -> favouredThread=2 (favour P1)");
wants.set(0, 0);
emitEvent(cb, time.incrementAndGet(), 0, 0,
TraceEvent.EventType.CUSTOM,
"P0(Thread1): wants[0]=false -> exited CS #" + (cs + 1));
p0Completed.incrementAndGet(); 
emitEvent(cb, time.incrementAndGet(), 0, 0,
TraceEvent.EventType.CUSTOM,
"P0(Thread1): in Remainder Section");
try {
Thread.sleep(1);
} catch (InterruptedException ie) {
Thread.currentThread().interrupt();
errors.add(ie);
break;
}
}
} catch (InterruptedException e) {
Thread.currentThread().interrupt();
errors.add(e);
}
});
Thread t1 = new Thread(() -> {
try {
for (int cs = 0; cs < maxCS; cs++) {
if (violated.get()) {
wants.set(1, 0);
break;
}
wants.set(1, 1);
emitEvent(cb, time.incrementAndGet(), 1, 0,
TraceEvent.EventType.ACQUIRE_REQUEST,
"P1(Thread2): wants[1]=true -> P1 wants to enter CS");
while (wants.get(0) == 1) {
if (violated.get()) {
wants.set(1, 0);
return;
}
int ft = favouredThread.get();
if (ft == 1) {
wants.set(1, 0);
emitEvent(cb, time.incrementAndGet(), 1, 0,
TraceEvent.EventType.BUSY_WAIT,
"P1(Thread2): yields -> favouredThread=1, "
+ "wants[1]=false, waiting for turn");
while (true) {
int ftInner = favouredThread.get();
if (ftInner != 1) break;
if (violated.get()) {
wants.set(1, 0);
return;
}
busyTicks1.incrementAndGet();
long waitStart = System.currentTimeMillis();
emitEvent(cb, time.incrementAndGet(), 1, 0,
TraceEvent.EventType.BUSY_WAIT,
"P1(Thread2): busy-waiting, favouredThread="
+ ftInner + " -> P0 still favoured");
Thread.yield();
p1.addWaitingTime(System.currentTimeMillis() - waitStart);
}
wants.set(1, 1);
emitEvent(cb, time.incrementAndGet(), 1, 0,
TraceEvent.EventType.ACQUIRE_REQUEST,
"P1(Thread2): re-asserts wants[1]=true after gaining favour");
}
Thread.yield();
}
if (!inCS.compareAndSet(0, 1)) {
violated.set(true);
emitEvent(cb, time.incrementAndGet(), 1, 0,
TraceEvent.EventType.CUSTOM,
"WARNING: MUTEX VIOLATION — P1 could not acquire inCS lock!");
wants.set(1, 0);
favouredThread.set(1);
break;
}
long acquireTick = time.incrementAndGet();
if (p1ResponseSet.compareAndSet(false, true)) {
p1.setResponseTime(acquireTick);
}
emitEvent(cb, acquireTick, 1, 0,
TraceEvent.EventType.ACQUIRE_SUCCESS,
"P1(Thread2) enters Critical Section #" + (cs + 1));
Thread.sleep(2);
if (violated.get()) {
inCS.set(0);
wants.set(1, 0);
return;
}
inCS.set(0);
favouredThread.set(1);
emitEvent(cb, time.incrementAndGet(), 1, 0,
TraceEvent.EventType.RELEASE,
"P1(Thread2) exits CS #" + (cs + 1)
+ " -> favouredThread=1 (favour P0)");
wants.set(1, 0);
emitEvent(cb, time.incrementAndGet(), 1, 0,
TraceEvent.EventType.CUSTOM,
"P1(Thread2): wants[1]=false -> exited CS #" + (cs + 1));
p1Completed.incrementAndGet(); 
emitEvent(cb, time.incrementAndGet(), 1, 0,
TraceEvent.EventType.CUSTOM,
"P1(Thread2): in Remainder Section");
try {
Thread.sleep(1);
} catch (InterruptedException ie) {
Thread.currentThread().interrupt();
errors.add(ie);
break;
}
}
} catch (InterruptedException e) {
Thread.currentThread().interrupt();
errors.add(e);
}
});
t0.start();
t1.start();
try {
t0.join();
t1.join();
} catch (InterruptedException e) {
Thread.currentThread().interrupt();
t0.interrupt();
t1.interrupt();
}
if (!errors.isEmpty()) {
errors.forEach(e -> emitEvent(cb, time.get(), -1, 0,
TraceEvent.EventType.CUSTOM,
"THREAD ERROR: " + e.getMessage()));
}
long finalTime = time.get();
p0.setFinishTime(finalTime);
p1.setFinishTime(finalTime);
p0.setTurnaroundTime(finalTime - p0.getStartTime());
p1.setTurnaroundTime(finalTime - p1.getStartTime());
emitEvent(cb, time.incrementAndGet(), 0, 0,
TraceEvent.EventType.PROCESS_FINISH,
"P0(Thread1) finished " + p0Completed.get() + " of " + maxCS + " CS entries");
emitEvent(cb, time.incrementAndGet(), 1, 0,
TraceEvent.EventType.PROCESS_FINISH,
"P1(Thread2) finished " + p1Completed.get() + " of " + maxCS + " CS entries");
long totalBusyTicks = busyTicks0.get() + busyTicks1.get();
List<ProcessModel> processes = new ArrayList<>();
processes.add(p0);
processes.add(p1);
return computeMetrics(getName(), processes, time.get(), totalBusyTicks);
}
}