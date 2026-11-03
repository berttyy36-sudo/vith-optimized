package vithh.vithOptimized.threading;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Consumer;

public final class ParallelExecutor {
    private static final Logger LOGGER = LoggerFactory.getLogger("VithOptimized-Parallel");

    private static volatile ParallelExecutor INSTANCE;

    private final int threadCount;
    private final ForkJoinPool forkJoinPool;
    private final ExecutorService fixedThreadPool;
    private final AtomicInteger threadCounter = new AtomicInteger(1);

    private ParallelExecutor(int threads, boolean elevatedPriority) {
        this.threadCount = Math.max(2, threads);

        ForkJoinPool.ForkJoinWorkerThreadFactory fjpFactory = pool -> {
            ForkJoinWorkerThread worker = ForkJoinPool.defaultForkJoinWorkerThreadFactory.newThread(pool);
            worker.setName("vith-fjp-core-" + threadCounter.getAndIncrement());
            worker.setDaemon(true);
            if (elevatedPriority) {
                worker.setPriority(Math.min(Thread.MAX_PRIORITY, Thread.NORM_PRIORITY + 1));
            }
            return worker;
        };

        ThreadFactory threadFactory = r -> {
            Thread t = new Thread(r, "vith-cpu-core-" + threadCounter.getAndIncrement());
            t.setDaemon(true);
            if (elevatedPriority) {
                t.setPriority(Math.min(Thread.MAX_PRIORITY, Thread.NORM_PRIORITY + 1));
            }
            return t;
        };

        this.forkJoinPool = new ForkJoinPool(this.threadCount, fjpFactory, (t, e) -> {
            LOGGER.error("Uncaught exception in parallel worker thread {}:", t.getName(), e);
        }, true);

        this.fixedThreadPool = Executors.newFixedThreadPool(this.threadCount, threadFactory);
    }

    public static synchronized void init(int threads, boolean elevatedPriority) {
        if (INSTANCE != null) {
            if (INSTANCE.threadCount == threads) {
                return;
            }
            INSTANCE.shutdown();
        }
        INSTANCE = new ParallelExecutor(threads, elevatedPriority);
        LOGGER.info("ParallelExecutor initialized with {} CPU worker threads across logical cores.", threads);
    }

    public static ParallelExecutor getInstance() {
        if (INSTANCE == null) {
            init(Runtime.getRuntime().availableProcessors(), true);
        }
        return INSTANCE;
    }

    public static synchronized void shutdownInstance() {
        if (INSTANCE != null) {
            INSTANCE.shutdown();
            INSTANCE = null;
        }
    }

    public void shutdown() {
        try {
            forkJoinPool.shutdown();
            fixedThreadPool.shutdown();
        } catch (Throwable ignored) {}
    }

    public int getThreadCount() {
        return threadCount;
    }

    public ForkJoinPool getForkJoinPool() {
        return forkJoinPool;
    }

    public ExecutorService getFixedThreadPool() {
        return fixedThreadPool;
    }

    public void execute(Runnable task) {
        fixedThreadPool.execute(task);
    }

    public <T> Future<T> submit(Callable<T> task) {
        return fixedThreadPool.submit(task);
    }

    public <T> void parallelForEach(List<T> items, Consumer<T> consumer) {
        if (items == null || items.isEmpty()) return;
        if (items.size() <= 4) {
            for (T item : items) {
                consumer.accept(item);
            }
            return;
        }

        try {
            forkJoinPool.submit(() -> items.parallelStream().forEach(consumer)).get();
        } catch (InterruptedException | ExecutionException e) {
            for (T item : items) {
                consumer.accept(item);
            }
        }
    }
}
