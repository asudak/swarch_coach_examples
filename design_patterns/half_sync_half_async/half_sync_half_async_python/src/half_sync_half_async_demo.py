import threading, time
from typing import Optional
from queue import Queue, Empty
from concurrent.futures import Future, ThreadPoolExecutor


class Task:

    def __init__(self):
        self.result = None

    def execute(self):
        time.sleep(1)
        thread = threading.current_thread()
        self.result = "Task %s is executing in thread %s" % (self, thread.name)
        return self

    def on_complete(self):
        print(self.result)
        thread = threading.current_thread()
        print("Executing completion handler in thread %s for task %s" % (thread.name, self))


class ThreadPool:

    def __init__(self):
        self.executor_service = ThreadPoolExecutor(max_workers=10)
        self.shutdown_executed = False

    def execute(self, task: Task) -> Future:
        return self.executor_service.submit(task.execute)

    def shutdown(self):
        self.shutdown_executed = True
        self.executor_service.shutdown(cancel_futures=True)

    def is_shutdown(self) -> bool:
        return self.shutdown_executed


class TaskQueue:

    def __init__(self):
        self.tasks = Queue()

    def enqueue(self, task: Task):
        self.tasks.put(task)

    def dequeue(self) -> Task:
        return self.tasks.get()


class CompletedTaskQueue:

    def __init__(self):
        self.completed_tasks = Queue()

    def enqueue(self, task: Task):
        self.completed_tasks.put(task)

    def dequeue(self) -> Optional[Task]:
        try:
            return self.completed_tasks.get_nowait()
        except Empty:
            return None


class SynchronousService:

    def __init__(self, task_queue: TaskQueue, completed_task_queue: CompletedTaskQueue):
        self.task_queue = task_queue
        self.completed_task_queue = completed_task_queue
        self.thread_pool = ThreadPool()
        self.stopped = False

    def run(self):
        def worker():
            while self.stopped is False:
                task: Task = self.task_queue.dequeue()
                if self.stopped is False and self.thread_pool.is_shutdown() is False:
                    future: Future = self.thread_pool.execute(task)

                    def add_to_completion_queue(completed_future: Future):
                        self.completed_task_queue.enqueue(completed_future.result())

                    future.add_done_callback(add_to_completion_queue)

        threading.Thread(target=worker, daemon=True).start()

    def stop(self):
        self.stopped = True
        self.thread_pool.shutdown()
        # submit "poison pill" to the queue to unblock the loop
        self.task_queue.enqueue(Task())


class AsynchronousService:

    def __init__(self, task_queue: TaskQueue, completed_task_queue: CompletedTaskQueue):
        self.task_queue = task_queue
        self.completed_task_queue = completed_task_queue

    def submit(self, task: Task):
        self.task_queue.enqueue(task)

    def get_completed_task(self) -> Task:
        return self.completed_task_queue.dequeue()


# This is a Python-based pseudocode written with the intent
# to provide intuition about the Half-sync/half-async pattern
if __name__ == '__main__':
    task_queue = TaskQueue()
    completed_task_queue = CompletedTaskQueue()
    asynchronous_service = AsynchronousService(task_queue, completed_task_queue)
    synchronous_service = SynchronousService(task_queue, completed_task_queue)
    synchronous_service.run()

    asynchronous_service.submit(Task())

    # There should be no completed tasks at this point, since the only previously
    # submitted task sleeps for 1000ms
    completed_task = asynchronous_service.get_completed_task()
    assert completed_task is None

    asynchronous_service.submit(Task())

    expected_tasks_to_complete = 2
    while expected_tasks_to_complete > 0:
        completed_task = asynchronous_service.get_completed_task()
        if completed_task is not None:
            completed_task.on_complete()
            expected_tasks_to_complete -= 1
        # This sleep here is just for demo purposes, in real-life situation
        # it would be some other logic executed by the main thread until
        # there are completed tasks in the queue, which it can pick up and run
        # completion handlers
        time.sleep(1)
        # ... do something else

    synchronous_service.stop()
