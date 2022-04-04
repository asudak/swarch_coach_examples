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


class AsynchronousService:

    def __init__(self, synchronous_service: ThreadPoolExecutor):
        self.completed_task_queue = Queue()
        self.synchronous_service = synchronous_service

    def submit(self, task: Task):
        future: Future = self.synchronous_service.submit(task.execute)

        def add_to_completion_queue(completed_future: Future):
            self.completed_task_queue.put(completed_future.result())

        future.add_done_callback(add_to_completion_queue)

    def get_completed_task(self) -> Optional[Task]:
        try:
            return self.completed_task_queue.get_nowait()
        except Empty:
            return None


# This is a Python-based pseudocode written with the intent
# to provide intuition about the Half-sync/half-async pattern
if __name__ == '__main__':
    synchronous_service = ThreadPoolExecutor(max_workers=10)
    asynchronous_service = AsynchronousService(synchronous_service)

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

    synchronous_service.shutdown()
