package coach.swarch.designpatterns.halfsynchalfasync.dummy;

import java.util.concurrent.*;

class ThreadPool {

    public CompletableFuture<Task> execute(Task task) {
        CompletableFuture<Task> completableFuture = new CompletableFuture<>();
        // Here threadpool just starts the new thread for simplicity of the dummy
        // to avoid implementing the actual pool of threads
        new Thread(() -> {
            task.execute();
            completableFuture.complete(task);
        }).start();
        return completableFuture;
    }
}

class Task {

    public void execute() {
        System.out.printf("Task executed in the thread '%s'%n", Thread.currentThread().getName());
    }

    public void onComplete() {
        System.out.printf("Task onComplete called in the thread '%s'%n", Thread.currentThread().getName());
    }
}

class TaskQueue {

    private final BlockingQueue<Task> tasks = new LinkedBlockingQueue<>();

    public void enqueue(Task task) {
        tasks.add(task);
    }

    public Task dequeue() throws Exception {
        return tasks.poll(1, TimeUnit.SECONDS);
    }
}

class CompletedTaskQueue {

    private final BlockingQueue<Task> tasks = new LinkedBlockingQueue<>();

    public void enqueue(Task task) {
        tasks.add(task);
    }

    public Task dequeue() throws Exception {
        return tasks.poll(1, TimeUnit.SECONDS);
    }
}

class SynchronousService {

    private final ThreadPool threadPool = new ThreadPool();
    private final TaskQueue taskQueue;
    private final CompletedTaskQueue completedTaskQueue;

    public SynchronousService(TaskQueue taskQueue, CompletedTaskQueue completedTaskQueue) {
        this.taskQueue = taskQueue;
        this.completedTaskQueue = completedTaskQueue;
    }

    public void run() {
        Thread loopThread = new Thread(() -> {
            while (true) {
                try {
                    Task task = taskQueue.dequeue();
                    if (task != null) {
                        CompletableFuture<Task> future = threadPool.execute(task);
                        future.whenComplete((completedTask, error) -> completedTaskQueue.enqueue(completedTask));
                    }
                } catch (Exception e) {
                    e.printStackTrace();
                }
            }
        });
        loopThread.setDaemon(true);
        loopThread.start();
    }
}

class AsynchronousService {

    private final TaskQueue taskQueue;
    private final CompletedTaskQueue completedTaskQueue;

    public AsynchronousService(TaskQueue taskQueue, CompletedTaskQueue completedTaskQueue) {
        this.taskQueue = taskQueue;
        this.completedTaskQueue = completedTaskQueue;
    }

    public void submit(Task task) {
        taskQueue.enqueue(task);
    }

    public Task getCompletedTask() throws Exception {
        return completedTaskQueue.dequeue();
    }
}

/**
 * Dummy Demo is intended to be as simple for understanding as possible, so it may contain
 * the code, which may not completely work or may not work in certain situations.
 */
public class HalfSyncHalfAsyncDummyDemo {

    public static void main(String[] args) throws Exception {
        TaskQueue taskQueue = new TaskQueue();
        CompletedTaskQueue completedTaskQueue = new CompletedTaskQueue();
        AsynchronousService asynchronousService = new AsynchronousService(taskQueue, completedTaskQueue);
        SynchronousService synchronousService = new SynchronousService(taskQueue, completedTaskQueue);
        synchronousService.run();

        asynchronousService.submit(new Task());

        Task completedTask = asynchronousService.getCompletedTask();
        if (completedTask != null) {
            completedTask.onComplete();
        }
    }
}
