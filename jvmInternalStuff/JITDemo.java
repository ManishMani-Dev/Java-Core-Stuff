package jvmInternalStuff;

/**
 * <h1>📘 JIT Compilation Breakdown for {@code jvmInternalStuff.JITDemo}</h1>
 *
 * This JavaDoc explains the JIT (Just-In-Time) compilation logs related to the {@code jvmInternalStuff.JITDemo} class,
 * showing how the JVM progressively optimized the methods at runtime.
 * <h2>▶️ How to Compile and Run with JIT Logs</h2>
 * <pre>{@code
 * javac jvmInternalStuff.JITDemo.java
 * java -XX:+UnlockDiagnosticVMOptions -XX:+PrintCompilation -XX:+PrintInlining jvmInternalStuff.JITDemo
 * }</pre>
 *
 * <h2>🔍 1. Relevant JIT Log Output</h2>
 * <pre>{@code
 * 21    5       3       jvmInternalStuff.JITDemo::compute (4 bytes)
 * 21    6       4       jvmInternalStuff.JITDemo::compute (4 bytes)
 * 21    5       3       jvmInternalStuff.JITDemo::compute (4 bytes)   made not entrant
 * 23    7 %     3       jvmInternalStuff.JITDemo::main @ 4 (37 bytes)
 *                               @ 12   jvmInternalStuff.JITDemo::compute (4 bytes)   inline
 *                               @ 28  java/lang/invoke/MethodHandle::invokeBasic (not loaded)   not inlineable
 *                               @ 33  java/io/PrintStream::println (not loaded)   not inlineable
 * 23    8       3       jvmInternalStuff.JITDemo::main (37 bytes)
 *                               @ 12   jvmInternalStuff.JITDemo::compute (4 bytes)   inline
 *                               @ 28  java/lang/invoke/MethodHandle::invokeBasic (not loaded)   not inlineable
 *                               @ 33  java/io/PrintStream::println (not loaded)   not inlineable
 * 23    9 %     4       jvmInternalStuff.JITDemo::main @ 4 (37 bytes)
 * 25    7 %     3       jvmInternalStuff.JITDemo::main @ 4 (37 bytes)   made not entrant
 *                               @ 12   jvmInternalStuff.JITDemo::compute (4 bytes)   inline (hot)
 * 26    9 %     4       jvmInternalStuff.JITDemo::main @ 4 (37 bytes)   made not entrant
 * }</pre>
 *
 * <h2>📚 2. Quick Primer: Column Meanings</h2>
 * <ul>
 *   <li><b>Time</b> – JVM timestamp in milliseconds when compilation occurred.</li>
 *   <li><b>CompID</b> – Internal compilation identifier.</li>
 *   <li><b>Level</b>:
 *     <ul>
 *       <li>1 – C1 (client compiler, simple optimizations)</li>
 *       <li>3 – C1 with profiling (tiered compilation)</li>
 *       <li>4 – C2 (aggressive, high-performance compiler)</li>
 *     </ul>
 *   </li>
 *   <li><b>Method</b> – Fully qualified method name being compiled.</li>
 *   <li><b>@</b> – Bytecode index of inlined/not-inlined methods within the compiled method.</li>
 *   <li><b>Inline status</b> – Indicates whether a method was inlined, not inlined, or not loaded.</li>
 *   <li><b>made not entrant</b> – Previous compiled version is invalidated, no longer used for new calls.</li>
 * </ul>
 *
 * <h2>🧠 3. Detailed Line-by-Line Breakdown</h2>
 * <ul>
 *   <li>
 *     <pre>{@code
 *     21    5       3       jvmInternalStuff.JITDemo::compute (4 bytes)
 *     }</pre>
 *     ➤ The {@code compute()} method is first compiled by the C1 compiler (Level 3 = client tier with profiling).<br>
 *     ➤ This tier quickly compiles the method while collecting runtime profiling data for future optimizations.
 *   </li>
 *
 *   <li>
 *     <pre>{@code
 *     21    6       4       jvmInternalStuff.JITDemo::compute (4 bytes)
 *     }</pre>
 *     ➤ Almost immediately, {@code compute()} is recompiled by the more aggressive C2 compiler (Level 4).<br>
 *     ➤ The method has been deemed "hot" enough to justify deeper optimizations using the profiling info from C1.
 *   </li>
 *
 *   <li>
 *     <pre>{@code
 *     21    5       3       jvmInternalStuff.JITDemo::compute (4 bytes)   made not entrant}
 *     </pre>
 *     ➤ The earlier C1 version of {@code compute()} (CompID 5) is now invalidated (marked {@code not entrant}).<br>
 *     ➤ This means it will no longer be used for new invocations — all future calls will use the optimized C2 version.
 *   </li>
 *
 *   <li>
 *     <pre>{@code
 *     23    7 %     3       jvmInternalStuff.JITDemo::main @ 4 (37 bytes)
 *                               @ 12   jvmInternalStuff.JITDemo::compute (4 bytes)   inline
 *                               @ 28  java/lang/invoke/MethodHandle::invokeBasic (not loaded)   not inlineable
 *                               @ 33  java/io/PrintStream::println (not loaded)   not inlineable
 *     }</pre>
 *     ➤ The {@code main()} method is compiled by C1 with profiling (CompID 7).<br>
 *     ➤ The {@code %} sign indicates that compilation was triggered by a method being hot or at a backedge (loop backjump).<br>
 *     ➤ Inlined calls:
 *     <ul>
 *       <li><b>@12</b> – {@code compute()} is successfully inlined (short method, marked as hot).</li>
 *       <li><b>@28</b> – {@code MethodHandle::invokeBasic} is not inlined — it's not yet loaded or unsuitable.</li>
 *       <li><b>@33</b> – {@code PrintStream::println} is also not inlined — likely because it's not loaded or too large.</li>
 *     </ul>
 *   </li>
 *
 *   <li>
 *     <pre>{@code
 *     23    8       3       jvmInternalStuff.JITDemo::main (37 bytes)
 *                               @ 12   jvmInternalStuff.JITDemo::compute (4 bytes)   inline
 *                               @ 28  java/lang/invoke/MethodHandle::invokeBasic (not loaded)   not inlineable
 *                               @ 33  java/io/PrintStream::println (not loaded)   not inlineable
 *     }</pre>
 *     ➤ The {@code main()} method is recompiled again at C1 Level 3 (CompID 8).<br>
 *     ➤ Inlining remains the same — {@code compute()} is inlined again, others are still not inlineable.
 *   </li>
 *
 *   <li>
 *     <pre>{@code
 *     23    9 %     4       jvmInternalStuff.JITDemo::main @ 4 (37 bytes)
 *     }</pre>
 *     ➤ {@code main()} gets promoted to the C2 compiler (CompID 9) because it’s considered hot enough now.<br>
 *     ➤ The JVM leverages earlier profiling data to apply advanced optimizations.
 *   </li>
 *
 *   <li>
 *     <pre>{@code
 *     25    7 %     3       jvmInternalStuff.JITDemo::main @ 4 (37 bytes)   made not entrant
 *                               @ 12   jvmInternalStuff.JITDemo::compute (4 bytes)   inline (hot)
 *     }</pre>
 *     ➤ The initial C1 version of {@code main()} (CompID 7) is invalidated.<br>
 *     ➤ {@code compute()} at bytecode index 12 is inlined and marked explicitly as {@code inline (hot)} — meaning it’s called frequently and thus optimized aggressively.
 *   </li>
 *
 *   <li>
 *     <pre>{@code
 *     26    9 %     4       jvmInternalStuff.JITDemo::main @ 4 (37 bytes)   made not entrant
 *     }</pre>
 *     ➤ Surprisingly, the C2 version (CompID 9) is also marked {@code made not entrant}.<br>
 *     ➤ This usually happens when:
 *     <ul>
 *       <li>The JVM detects a deoptimization trigger (e.g., uncommon trap or incorrect speculation).</li>
 *       <li>New profiling data suggests the need for recompilation with different optimization strategies.</li>
 *       <li>JVM internal thresholds have shifted — adapting dynamically to runtime behavior.</li>
 *     </ul>
 *   </li>
 * </ul>
 *
 * <h2>💡 Summary</h2>
 * This log shows how the JVM uses tiered compilation:
 * <ul>
 *   <li>Starts with C1 (faster compilation, good for startup)</li>
 *   <li>Uses profiling to promote hot methods to C2 (slower to compile, highly optimized)</li>
 *   <li>Continuously adapts by recompiling and invalidating outdated code paths</li>
 * </ul>
 */
public class JITDemo {
    public static void main(String[] args) {
        long sum = 0;  //@ 4 lineNo
        for (int i = 0; i < 1_000_000; i++) {
            sum += compute(i);
        }
        System.out.println("Sum: " + sum);
    }

    public static int compute(int x) {
        return x * 2;     //@ 12 lineNo
    }
}