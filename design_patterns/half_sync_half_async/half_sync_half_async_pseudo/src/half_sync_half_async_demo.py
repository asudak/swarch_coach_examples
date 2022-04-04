class Task:

    def execute(self):
        print("Task is executing")

    def onComplete(self):
        print("Executing completion handler")

class Future:
    completedTask: Task

    def complete(self, task: Task):
        self.completedTask = task

    def then(self, function):
        function(self.completedTask)

class Thread:

    def start(self, runnable):
        runnable()

class ThreadPool:
    workerThreads = []

    def execute(self, task: Task) -> Future:
        thread: Thread = self.workerThreads.pop()
        future = Future()
        def runnable():
            task.execute()
            future.complete(task)
        thread.start(runnable)
        return future

class TaskQueue:
    tasks = []

    def enqueue(self, task: Task):
        self.tasks.append(task)

    def dequeue(self) -> Task:
        return self.tasks.pop()

class CompletedTaskQueue:
    completedTasks = []

    def enqueue(self, task: Task):
        self.completedTasks.append(task)

    def dequeue(self) -> Task:
        return self.completedTasks.pop()

class SynchronousService:
    taskQueue: TaskQueue
    completedTaskQueue: CompletedTaskQueue
    threadPool: ThreadPool

    def __init__(self, taskQueue, completedTaskQueue):
        self.taskQueue = taskQueue
        self.completedTaskQueue = completedTaskQueue
        self.threadPool = ThreadPool()

    def run(self):
        def runnable():
            while True:
                task: Task = self.taskQueue.dequeue()
                future: Future = self.threadPool.execute(task)
                def completionHandler(task: Task):
                    self.completedTaskQueue.enqueue(task)
                future.then(completionHandler)
        Thread().start(runnable)

class AsynchronousService:
    taskQueue: TaskQueue
    completedTaskQueue: CompletedTaskQueue

    def __init__(self, taskQueue, completedTaskQueue):
        self.taskQueue = taskQueue
        self.completedTaskQueue = completedTaskQueue

    def submit(self, task: Task):
        self.taskQueue.enqueue(task)

    def getCompletedTask(self) -> Task:
        return self.completedTaskQueue.dequeue()

# This is a Python-based pseudocode written with the intent
# to provide intuition about the Half-sync/half-async pattern.
# It is not a real runnable code
if __name__ == '__main__':
    taskQueue = TaskQueue()
    completedTaskQueue = CompletedTaskQueue()
    asynchronousService = AsynchronousService(taskQueue, completedTaskQueue)
    synchronousService = SynchronousService(taskQueue, completedTaskQueue)
    synchronousService.run()

    asynchronousService.submit(Task())

    completedTask: Task = asynchronousService.getCompletedTask()
    if completedTask:
        completedTask.onComplete()
