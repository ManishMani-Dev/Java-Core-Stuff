package GarbageCollectorStuff;

import java.util.ArrayList;

/**
 * This class (or method) demonstrates and analyzes detailed Java Garbage Collector (GC) logs
 * using JVM options to simulate low-memory conditions and trigger GC events.
 * <p>
 * The example uses the G1 Garbage Collector and JVM parameters to constrain heap size, causing
 * frequent GC and potential OutOfMemoryError situations.
 * <p>
 * <b>Compilation command:</b>
 * <pre>{@code
 * javac GarbageCollectorStuff/OOMSimulation.java
 * }</pre>
 * <p>
 * <b>Run command to simulate low-memory and enable GC logging:</b>
 * <pre>{@code
 * java -Xms20m -Xmx20m -XX:+PrintGCDetails GarbageCollectorStuff/OOMSimulation.java
 * }</pre>
 * <p>
 * JVM flags explained:
 * <ul>
 *   <li><b>-Xms20m</b>: Sets initial heap size to 20 MB.</li>
 *   <li><b>-Xmx20m</b>: Sets maximum heap size to 20 MB, limiting total heap memory.</li>
 *   <li><b>-XX:+PrintGCDetails</b>: Enables detailed GC logging output (deprecated; use -Xlog:gc* on newer JVMs).</li>
 * </ul>
 * <p>
 * <br>
 *
 * <h2>Example Garbage Collection (GC) Log Analysis with Deep Explanations.</h2>
 *
 * This walkthrough covers JVM G1 GC logging from startup,
 * highlighting key GC phases, heap region transitions, and memory usage.
 * It includes two detailed GC cycle stories to illustrate the G1 GC behavior.
 *
 * -----------------------------------------------------------------------------<br>
 * <h3>JVM Startup and Initialization Logs</h3>
 * <br>
 * -----------------------------------------------------------------------------
 *
 * <pre>{@code
 * [0.002s][warning][gc] -XX:+PrintGCDetails is deprecated. Use -Xlog:gc* instead.
 * }</pre>
 * The JVM warns that '-XX:+PrintGCDetails' is deprecated and suggests
 * using '-Xlog:gc*' for improved logging capabilities.
 *
 * <pre>{@code
 * [0.006s][info   ][gc,init] CardTable entry size: 512
 * }</pre>
 * CardTable is a write barrier structure that tracks memory regions
 * for efficient GC, with each entry covering 512 bytes of heap.
 *
 * <pre>{@code
 * [0.006s][info   ][gc     ] Using G1
 * }</pre>
 * Garbage First (G1) GC is selected, designed for predictable pauses
 * and region-based heap management.
 *
 * <pre>{@code
 * [0.008s][info   ][gc,init] Version: 21.0.7+6-Ubuntu-0ubuntu120.04 (release)
 * }</pre>
 * JVM version and build information.
 *
 * <pre>{@code
 * [0.008s][info   ][gc,init] CPUs: 8 total, 8 available
 * }</pre>
 * System has 8 CPUs, all available to JVM threads.
 *
 * <pre>{@code
 * [0.008s][info   ][gc,init] Memory: 15725M
 * }</pre>
 * Total system RAM available to JVM.
 *
 * <pre>{@code
 * [0.008s][info   ][gc,init] Large Page Support: Disabled
 * }</pre>
 * Large memory page optimization is turned off.
 *
 * <pre>{@code
 * [0.008s][info   ][gc,init] NUMA Support: Disabled
 * }</pre>
 * NUMA (Non-Uniform Memory Access) optimizations are disabled.
 *
 * <pre>{@code
 * [0.008s][info   ][gc,init] Compressed Oops: Enabled (32-bit)
 * }</pre>
 * JVM uses compressed ordinary object pointers to reduce memory footprint.
 *
 * <pre>{@code
 * [0.008s][info   ][gc,init] Heap Region Size: 1M
 * }</pre>
 * Heap is divided into regions of 1MB each for fine-grained GC control.
 *
 * <pre>{@code
 * [0.008s][info   ][gc,init] Heap Min Capacity: 20M
 * [0.008s][info   ][gc,init] Heap Initial Capacity: 20M
 * [0.008s][info   ][gc,init] Heap Max Capacity: 20M
 * }</pre>
 * Heap sizing parameters, all set to 20MB for this run.
 *
 * <pre>{@code
 * [0.008s][info   ][gc,init] Pre-touch: Disabled
 * }</pre>
 * JVM will not pre-allocate all pages at startup.
 *
 * <pre>{@code
 * [0.008s][info   ][gc,init] Parallel Workers: 8
 * [0.008s][info   ][gc,init] Concurrent Workers: 2
 * [0.008s][info   ][gc,init] Concurrent Refinement Workers: 8
 * }</pre>
 * Number of threads used for parallel GC phases and concurrent work.
 *
 * <pre>{@code
 * [0.008s][info   ][gc,init] Periodic GC: Disabled
 * }</pre>
 * No automatic periodic GC scheduling.
 * <br>
 * -----------------------------------------------------------------------------<br>
 * <h3>First GC Cycle Story: GC(0) - Young Generation Collection Triggered by Humongous Allocation</h3>
 * <br>
 *  -----------------------------------------------------------------------------
 *
 * <pre>{@code
 * [0.076s][info   ][gc,start    ] GC(0) Pause Young (Concurrent Start) (G1 Humongous Allocation)
 * }</pre>
 * This marks the start of the first GC event (GC(0)).
 * The JVM is pausing young generation to allocate a large "humongous" object.
 *
 * <pre>{@code
 * [0.076s][info   ][gc,task     ] GC(0) Using 2 workers of 8 for evacuation
 * }</pre>
 * Out of 8 available GC worker threads, 2 were assigned to evacuate (copy) live objects.
 *
 * Phase timings breakdown:
 * <pre>{@code
 * [0.077s][info   ][gc,phases   ] GC(0)   Pre Evacuate Collection Set: 0.1ms
 * [0.077s][info   ][gc,phases   ] GC(0)   Merge Heap Roots: 0.0ms
 * [0.077s][info   ][gc,phases   ] GC(0)   Evacuate Collection Set: 0.5ms
 * [0.077s][info   ][gc,phases   ] GC(0)   Post Evacuate Collection Set: 0.2ms
 * [0.077s][info   ][gc,phases   ] GC(0)   Other: 0.2ms
 * }</pre>
 * Each phase corresponds to internal GC tasks:
 * - Pre Evacuate Collection Set: preparing regions for collection.
 * - Merge Heap Roots: combining references to live objects.
 * - Evacuate Collection Set: copying live objects to survivor or old regions.
 * - Post Evacuate Collection Set: cleaning up metadata.
 * - Other: miscellaneous small tasks.
 *
 * <pre>{@code
 * [0.077s][info   ][gc,heap     ] GC(0) Eden regions: 1 -> 0 (out of 5 reserved)
 * [0.077s][info   ][gc,heap     ] GC(0) Survivor regions: 0 -> 1 (out of 2 reserved)
 * [0.077s][info   ][gc,heap     ] GC(0) Old regions: 2 -> 2 (unchanged)
 * [0.077s][info   ][gc,heap     ] GC(0) Humongous regions: 4 -> 4 (unchanged)
 * }</pre>
 *
 * Detailed heap region usage transitions:
 * <ul>
 *   <li><b>Eden regions: 1 → 0 (out of 5 reserved)</b><br>
 *     Before GC, one Eden region was allocated for new objects. After collection, it was fully reclaimed,
 *     freeing up all Eden space (usage dropped to zero). JVM has 5 reserved Eden regions total.</li>
 *
 *   <li><b>Survivor regions: 0 → 1 (out of 2 reserved)</b><br>
 *     Initially no Survivor regions were occupied. During GC, some live objects survived and were copied into
 *     one Survivor region for possible promotion in future cycles. JVM reserves two such regions.</li>
 *
 *   <li><b>Old regions: 2 → 2 (unchanged)</b><br>
 *     Old generation regions remained stable at two, indicating no promotions from young generation
 *     during this GC pause. These regions hold long-lived objects.</li>
 *
 *   <li><b>Humongous regions: 4 → 4 (unchanged)</b><br>
 *     Four regions were occupied by large (humongous) objects before and after GC,
 *     meaning no new large object allocation or reclamation occurred in this GC cycle.</li>
 * </ul>
 *
 * <pre>{@code
 * [0.077s][info   ][gc,metaspace] GC(0) Metaspace: 164K(384K)->164K(384K) ...
 * }</pre>
 * Metaspace usage remained stable before and after GC.
 *
 * <pre>{@code
 * [0.077s][info   ][gc          ] GC(0) Pause Young (Concurrent Start) (G1 Humongous Allocation) 6M->5M(20M) 1.030ms
 * }</pre>
 * Summary of GC event:
 * - Heap usage reduced from 6MB to 5MB (of total 20MB heap).
 * - Pause duration: ~1 millisecond.
 * - Pause reason: Young GC triggered by humongous object allocation.
 *
 * <pre>{@code
 * [0.077s][info   ][gc,cpu      ] GC(0) User=0.00s Sys=0.00s Real=0.00s
 * }</pre>
 * CPU time consumed during this GC was negligible.
 * <br>
 * -----------------------------------------------------------------------------<br>
 * <h3>Second GC Cycle Story: GC(5) - Promotion and Heap Growth</h3><br>
 * -----------------------------------------------------------------------------
 *
 * <pre>{@code
 * [0.533s][info   ][gc,start    ] GC(5) Pause Young (Normal) (G1 Evacuation Pause)
 * }</pre>
 * This GC event (GC(5)) is a normal young GC pause triggered by evacuation.
 *
 * <pre>{@code
 * [0.533s][info   ][gc,task     ] GC(5) Using 4 workers of 8 for evacuation
 * }</pre>
 * Four GC workers actively evacuated live objects from young regions.
 *
 * Heap region usage transitions:
 * <pre>{@code
 * [0.533s][info   ][gc,heap     ] GC(5) Eden regions: 0 -> 0 (out of 7 reserved)
 * [0.533s][info   ][gc,heap     ] GC(5) Survivor regions: 1 -> 0 (out of 1 reserved)
 * [0.533s][info   ][gc,heap     ] GC(5) Old regions: 7 -> 8
 * [0.533s][info   ][gc,heap     ] GC(5) Humongous regions: 0 -> 0
 * }</pre>
 *
 * Detailed explanation of this GC’s heap region transitions:
 * <ul>
 *   <li><b>Eden regions: 0 → 0 (out of 7 reserved)</b><br>
 *     No Eden regions were used before or after GC, indicating allocations might have been low or done elsewhere.</li>
 *
 *   <li><b>Survivor regions: 1 → 0 (out of 1 reserved)</b><br>
 *     Survivor region was occupied before GC but is completely free after collection,
 *     meaning survivors were either promoted to old generation or discarded.</li>
 *
 *   <li><b>Old regions: 7 → 8</b><br>
 *     Old generation grew by one region, indicating at least one survivor was promoted
 *     to old gen due to age or space criteria.</li>
 *
 *   <li><b>Humongous regions: 0 → 0</b><br>
 *     No humongous object regions were occupied before or after this GC cycle.</li>
 * </ul>
 *
 * <pre>{@code
 * [0.533s][info   ][gc          ] GC(5) Pause Young (Normal) (G1 Evacuation Pause) 14M->13M(30M) 2.400ms
 * }</pre>
 * Summary of GC(5):
 * - Heap usage dropped slightly from 14MB to 13MB (total heap size 30MB).
 * - GC pause lasted ~2.4 milliseconds.
 * - Reason: Normal young GC pause during evacuation.
 *
 * <pre>{@code
 * [0.533s][info   ][gc,cpu      ] GC(5) User=0.01s Sys=0.00s Real=0.00s
 * }</pre>
 * CPU time utilized during GC(5).
 *
 * <pre>{@code
 * [0.085s][info   ][gc,ergo     ] Attempting full compaction
 * }</pre>
 * The GC ergonomics subsystem is triggering a full heap compaction to reduce fragmentation.
 *
 * <pre>{@code
 * [0.085s][info   ][gc,start    ] GC(6) Pause Full (G1 Compaction Pause)
 * }</pre>
 * GC #6 begins a full pause for heap compaction.
 *
 * <pre>{@code
 * [0.087s][info   ][gc          ] GC(6) Pause Full (G1 Compaction Pause) 17M->17M(20M) 2.175ms
 * }</pre>
 * Full GC #6 pause summary:
 * - Heap usage remains 17MB before and after
 * - Duration: 2.175ms
 *
 * <pre>{@code
 * OutOfMemoryError after allocating 4 objects & arrayList 4 arraySize : 1000000
 * java.lang.OutOfMemoryError: Java heap space
 *         at GarbageCollectorStuff.OOMSimulation.main(OOMSimulation.java:118)
 * }</pre>
 * The JVM throws OutOfMemoryError because heap space is exhausted after allocating 4 objects and a large array list.
 *
 * <pre>{@code
 * [0.100s][info   ][gc,heap,exit   ] Heap
 * [0.100s][info   ][gc,heap,exit   ]  garbage-first heap   total 20480K, used 17882K [0x00000000fec00000, 0x0000000100000000)
 * [0.100s][info   ][gc,heap,exit   ]   region size 1024K, 1 young (1024K), 0 survivors (0K)
 * [0.100s][info   ][gc,heap,exit   ]  Metaspace       used 256K, committed 448K, reserved 1114112K
 * [0.100s][info   ][gc,heap,exit   ]   class space    used 16K, committed 128K, reserved 1048576K
 * }</pre>
 * Final heap status at JVM exit:
 * - Total heap size: 20MB, used: ~17.9MB
 * - Heap divided into regions of 1MB each
 * - Metaspace usage stats and class space usage detailed
 *
 * <br>
 *
 * -----------------------------------------------------------------------------<br>
 * <h3>Summary</h3><br>
 * -----------------------------------------------------------------------------<br>
 *
 * These two GC cycle stories illustrate:
 * - How G1 GC reclaims young generation space by evacuating live objects.
 * - Survivor regions act as buffers to hold young survivors before promotion.
 * - Old generation regions increase only when promotions occur.
 * - Humongous regions track allocation and collection of very large objects.
 * - Each GC event includes multiple phases to efficiently manage memory.
 *
 * Understanding the detailed transitions and phases helps diagnose GC behavior,
 * optimize JVM tuning parameters, and troubleshoot performance issues.
 *
 */

public class OOMSimulation {
    public static void main(String[] args) {
        int arraySize = 1_000_000; // Each array ~4MB
//        int arraySize = 2_000_000; // Each array ~4MB [approx]
//        int arraySize = 4_000_000; // Each array ~16MB [approx]
        int count = 0;
        ArrayList<int[]> arrayList = new ArrayList<>();

        try {
            while (true) {
                int[] memoryFill = new int[arraySize];
                arrayList.add(memoryFill);
                System.out.println("Allocated object #" + (++count));
            }
        } catch (OutOfMemoryError e) {
            System.err.println("OutOfMemoryError after allocating " + count + " objects & arrayList "
                    +arrayList.size() + " arraySize : "+arraySize);
            e.printStackTrace();
        }
    }
}

