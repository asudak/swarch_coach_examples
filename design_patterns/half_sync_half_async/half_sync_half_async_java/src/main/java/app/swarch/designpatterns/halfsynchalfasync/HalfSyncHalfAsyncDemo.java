package app.swarch.designpatterns.halfsynchalfasync;

import java.util.concurrent.*;

/**
 * Thread pool for executing incoming tasks in separate threads
 */
class ThreadPool {

    private final ExecutorService executorService = Executors.newFixedThreadPool(10);

    public void execute(Runnable runnable) {
        executorService.execute(runnable);
    }

    public void shutdown() {
        executorService.shutdown();
    }

    public boolean isShutdown() {
        return executorService.isShutdown();
    }
}

/**
 * Retrieves tasks from the queue for synchronous execution
 */
class SynchronousService {

    private final TaskQueue taskQueue;
    private final CompletedTaskQueue completedTaskQueue;
    private final ThreadPool threadPool = new ThreadPool();
    private boolean stopped = false;

    public SynchronousService(TaskQueue taskQueue, CompletedTaskQueue completedTaskQueue) {
        this.taskQueue = taskQueue;
        this.completedTaskQueue = completedTaskQueue;
    }

    @SuppressWarnings({"unchecked", "rawtypes"})
    public void run() {
        Thread t = new Thread(() -> {
            while (!stopped) {
                try {
                    Task task = taskQueue.take();
                    if (!stopped && !threadPool.isShutdown()) {
                        threadPool.execute(() -> {
                            try {
                                task.saveResult(task.executeSynchronously());
                                completedTaskQueue.put(task);
                            } catch (InterruptedException e) {
                                e.printStackTrace();
                            }
                        });
                    }
                } catch (Exception e) {
                    e.printStackTrace();
                }
            }
        });
        t.setDaemon(true);
        t.start();
    }

    public void stop() throws InterruptedException {
        this.stopped = true;
        this.threadPool.shutdown();
        // submitting "poison pill" task to terminate the blocked thread
        // waiting for the queue
        this.taskQueue.put(new Task<>());
    }
}

class Task<R> {

    protected R result;

    R executeSynchronously() {
        return null;
    }

    void onComplete() {
    }

    void saveResult(R result) {
        this.result = result;
    }
}

/**
 * Queue for tasks coming from Asynchronous Service
 */
class TaskQueue extends LinkedBlockingQueue<Task<?>> {
}

/**
 * Queue for the completed tasks
 */
class CompletedTaskQueue extends LinkedBlockingQueue<Task<?>> {
}

/**
 * Processes tasks asynchronously, thus submits to the Task Queue for further
 * processing by the Synchronous Service.
 */
class AsynchronousService {

    private final TaskQueue taskQueue;
    private final CompletedTaskQueue completedTaskQueue;

    public AsynchronousService(TaskQueue taskQueue, CompletedTaskQueue completedTaskQueue) {
        this.taskQueue = taskQueue;
        this.completedTaskQueue = completedTaskQueue;
    }

    public <R> void submit(Task<R> task) throws InterruptedException {
        taskQueue.put(task);
    }

    public Task<?> pollCompletedTask() {
        return completedTaskQueue.poll();
    }
}

public class HalfSyncHalfAsyncDemo {

    public static void main(String[] args) throws Exception {
        TaskQueue taskQueue = new TaskQueue();
        CompletedTaskQueue completedTaskQueue = new CompletedTaskQueue();
        AsynchronousService asynchronousService = new AsynchronousService(taskQueue, completedTaskQueue);
        SynchronousService synchronousService = new SynchronousService(taskQueue, completedTaskQueue);
        synchronousService.run();

        class DemoTask extends Task<String> {
            @Override
            String executeSynchronously() {
                try {
                    Thread.sleep(1000);
                    return String.format("Executed task %s synchronously in the thread '%s'", this.hashCode(), Thread.currentThread().getName());
                } catch (Exception e) {
                    e.printStackTrace();
                }
                return null;
            }

            @Override
            void onComplete() {
                System.out.println(this.result);
                System.out.printf("Completed task %s in the thread '%s'%n", this.hashCode(), Thread.currentThread().getName());
            }
        }

        // Submit the first task for processing
        asynchronousService.submit(new DemoTask());

        // There should be no completed tasks at this point, since the only previously
        // submitted task sleeps for 1000ms
        Task<?> completedTask = asynchronousService.pollCompletedTask();
        assert completedTask == null;

        // Submit the second task for processing
        asynchronousService.submit(new DemoTask());

        int expectedTasksToComplete = 2;
        while (expectedTasksToComplete > 0) {
            completedTask = asynchronousService.pollCompletedTask();
            if (completedTask != null) {
                completedTask.onComplete();
                expectedTasksToComplete--;
            }
            // This sleep here is just for demo purposes, in real-life situation
            // it would be some other logic executed by the main thread until
            // there are completed tasks in the queue, which it can pick up and run
            // completion handlers
            Thread.sleep(100);
            // ... do something else
        }

        synchronousService.stop();
    }
}
