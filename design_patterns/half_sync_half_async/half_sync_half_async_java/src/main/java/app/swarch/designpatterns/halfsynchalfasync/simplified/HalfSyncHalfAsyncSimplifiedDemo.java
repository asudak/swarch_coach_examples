package app.swarch.designpatterns.halfsynchalfasync.simplified;

import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;

abstract class Task<R> {

    protected R result;

    abstract R executeSynchronously();

    abstract void onComplete();

    public void saveResult(R result) {
        this.result = result;
    }
}

class AsynchronousService {

    private final ThreadPoolExecutor synchronousService;
    private final BlockingQueue<Task<?>> completedTaskQueue = new LinkedBlockingQueue<>();

    public AsynchronousService(ThreadPoolExecutor synchronousService) {
        this.synchronousService = synchronousService;
    }

    public <R> void submit(Task<R> task) {
        synchronousService.execute(() -> {
            try {
                R result = task.executeSynchronously();
                task.saveResult(result);
                completedTaskQueue.put(task);
            } catch (InterruptedException e) {
                e.printStackTrace();
            }
        });
    }

    public Task<?> pollCompletedTask() {
        return completedTaskQueue.poll();
    }
}

public class HalfSyncHalfAsyncSimplifiedDemo {

    public static void main(String[] args) throws Exception {
        BlockingQueue<Runnable> taskQueue = new LinkedBlockingQueue<>();
        ThreadPoolExecutor synchronousService = new ThreadPoolExecutor(10, 10, 10, TimeUnit.SECONDS, taskQueue);
        AsynchronousService asynchronousService = new AsynchronousService(synchronousService);

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

        synchronousService.shutdown();
    }

}
