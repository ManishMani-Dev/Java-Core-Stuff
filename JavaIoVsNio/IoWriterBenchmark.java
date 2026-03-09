package JavaIoVsNio;

import java.io.*;
import java.util.concurrent.*;

public class IoWriterBenchmark {
    public static void main(String[] args) throws Exception {
        ExecutorService executor = Executors.newFixedThreadPool(8);
        Runnable task = () -> {
            try (FileOutputStream fos = new FileOutputStream("output_io_" + Thread.currentThread().getId() + ".txt")) {
                String data = "Hello World!\n".repeat(1_000_000); // 1 million lines
                fos.write(data.getBytes());
            } catch (IOException e) {
                e.printStackTrace();
            }
        };

        long start = System.currentTimeMillis();
        for (int i = 0; i < 8; i++) executor.submit(task);
        executor.shutdown();
        executor.awaitTermination(5, TimeUnit.MINUTES);
        long end = System.currentTimeMillis();

        System.out.println("IO Time: " + (end - start) + " ms");
    }
}

