# A message sent to a channel
class Message:
    value: object

    # constructor accepts message value
    def __init__(self, value):
        self.value = value

    # retrieves the message value
    def get(self) -> object:
        return self.value


# A reply received from the channel
# after message sending
class Reply:
    value: object

    # constructor accepts reply value
    def __init__(self, value):
        self.value = value

    # retrieves the reply value
    def get(self) -> object:
        return self.value


# Queue for messages submitted to a channel
class MessageQueue:
    messages = []

    # get the head of the message queue
    def get(self) -> Message:
        return self.messages.pop()

    # add new message to the queue
    def put(self, message: Message):
        self.messages.append(message)

    # check if queue is empty
    def isEmpty(self) -> bool:
        return len(self.messages) > 0


# Queue for replies provided to a channel
# by pattern, when executing its function
class ReplyQueue:
    replies = []

    # get the head of the reply queue
    def get(self) -> Reply:
        return self.replies.pop()

    # add new reply to the queue
    def put(self, reply: Reply):
        self.replies.append(reply)

    # check if queue is empty
    def isEmpty(self) -> bool:
        return len(self.replies) > 0


# Accepts messages and returns replies
class Channel:
    messageQueue: MessageQueue = MessageQueue()

    # Send a message to the channel
    # By default (as in case with async channels)
    # replies immediate with empty value
    def send(self, message: Message) -> Reply:
        self.messageQueue.put(message)
        return Reply(None)

    # Reply provided by a pattern after it executed
    # its function
    def provideReply(self, reply: Reply):
        # by default does nothing,
        # replies are relevant for sync channels
        pass

    # Checks if the channel has messages to process
    def hasMessage(self) -> bool:
        return self.messageQueue.isEmpty() is False


# Sync channel blocks the caller until reply is available
class SynchronousChannel(Channel):
    replyQueue: ReplyQueue = ReplyQueue()

    # Send a message to the channel
    # Blocks until reply is available
    def send(self, message: Message) -> Reply:
        self.messageQueue.put(message)
        # in actual implementation getting reply
        # from the queue would be a blocking operation
        return Reply(self.replyQueue.get())

    # Reply provided by a pattern after it executed
    # its function
    def provideReply(self, reply: Reply):
        self.replyQueue.put(reply)


# Async channel does not block when sending a message
# It replies immediate with empty value
class AsynchronousChannel(Channel):
    pass


# Join definition combining several channels.
# Join pattern matches, when all its combined channels
# have at least 1 message to process
class JoinPattern:
    outputChannel: Channel
    inputChannels = []
    function: any

    # Join initial channel, which will be provided
    # with the result of pattern's function execution
    def when(self, channel: Channel):
        self.outputChannel = channel
        return self

    # Join async input channels
    def andAlso(self, channel: AsynchronousChannel):
        self.inputChannels.append(channel)
        return self

    # Provide a function to execute, when pattern matches
    def onMatch(self, function):
        self.function = function
        return self

    # Check if pattern matches
    def matches(self) -> bool:
        return self.outputChannel.hasMessage() \
               and all([inputChannel.hasMessage()
                        for inputChannel in self.inputChannels])

    # Execute pattern's function
    def execute(self):
        result = self.function()
        self.outputChannel.provideReply(Reply(result))


# Selection policy helps Scheduler to decide
# how to choose between multiple matching patterns.
class PatternSelectionPolicy:

    def select(self, patterns: []) -> JoinPattern:
        pass


# Select the first element in the provided list of patterns
class ConcreteSelectionPolicy(PatternSelectionPolicy):

    def select(self, patterns: []) -> JoinPattern:
        # select first element
        return patterns[0]


# Executes a 'runnable' function in a separate thread
class Thread:

    # Start the thread
    def start(self, runnable):
        # runs in a separate thread
        runnable()


# Runs a loop to check for patterns that matched
# (all pattern's channels received messages),
# and if there are several of them, selects one
# for execution according to its selection policy.
class Scheduler:
    joinPatterns = []
    selectionPolicy: PatternSelectionPolicy

    def __init__(self,
                 joinPatterns: [],
                 selectionPolicy: PatternSelectionPolicy):
        self.joinPatterns = joinPatterns
        self.selectionPolicy = selectionPolicy

    # Runs a loop to wait for Patterns that matched
    def run(self):
        def runnable():
            while True:
                matchingPatterns = [pattern
                                    for pattern
                                    in self.joinPatterns
                                    if pattern.matches()]
                selectedPattern = self.selectionPolicy\
                    .select(matchingPatterns)
                selectedPattern.execute()

        Thread().start(runnable)

# This is a Python-based pseudocode written with the intent
# to provide intuition about the join-calculus pattern.
# It is not a real runnable code
if __name__ == '__main__':
    # create 4 channels
    channel_a = SynchronousChannel()
    channel_b = AsynchronousChannel()
    channel_c = AsynchronousChannel()
    channel_d = AsynchronousChannel()
    # create 2 patterns with same output channel
    # and only 1 difference in input channels
    def function_1():
        return "1"
    pattern_1 = JoinPattern() \
        .when(channel_a) \
        .andAlso(channel_b) \
        .andAlso(channel_c) \
        .onMatch(function_1)
    def function_2():
        return "2"
    pattern_2 = JoinPattern() \
        .when(channel_a) \
        .andAlso(channel_b) \
        .andAlso(channel_d) \
        .onMatch(function_2)
    scheduler = Scheduler([pattern_1, pattern_2], ConcreteSelectionPolicy())
    scheduler.run()

    # here we first send messages to async input channels
    # so that when we send later message to sync channel A
    # scheduler is forced to choose between 2 patterns
    channel_b.send(Message("B"))
    channel_c.send(Message("C"))
    channel_d.send(Message("D"))

    # message to channel A causes both patterns (1 and 2)
    # to match, because all the other of their channels
    # also received messages. It is up to scheduler and
    # its selection policy to decide, which pattern to select
    # for execution
    reply = channel_a.send(Message("A"))

    # here we expect result to be "1"
    # because when choosing between patterns 1 and 2,
    # scheduler's policy selects the first match
    print(reply.get())
