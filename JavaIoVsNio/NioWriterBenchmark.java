package JavaIoVsNio;

import java.io.*;
import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;
import java.util.concurrent.*;

public class NioWriterBenchmark {
    public static void main(String[] args) throws Exception {
        ExecutorService executor = Executors.newFixedThreadPool(8);
        Runnable task = () -> {
            try (FileOutputStream fos = new FileOutputStream("output_nio_" + Thread.currentThread().getId() + ".txt");
                 FileChannel channel = fos.getChannel()) {
                String data = "Hello World!\n".repeat(1_000_000);
                ByteBuffer buffer = ByteBuffer.wrap(data.getBytes());
                while (buffer.hasRemaining()) {
                    channel.write(buffer);
                }
            } catch (IOException e) {
                e.printStackTrace();
            }
        };

        long start = System.currentTimeMillis();
        for (int i = 0; i < 8; i++) executor.submit(task);
        executor.shutdown();
        executor.awaitTermination(5, TimeUnit.MINUTES);
        long end = System.currentTimeMillis();

        System.out.println("NIO Time: " + (end - start) + " ms");
    }
}

