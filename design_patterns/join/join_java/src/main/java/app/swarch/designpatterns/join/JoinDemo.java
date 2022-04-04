package app.swarch.designpatterns.join;


import java.util.*;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.function.Function;
import java.util.stream.Stream;

/**
 * Reply received from a Channel, after submitting the message.
 * For Asynchronous Channels it is Reply<Void>.
 *
 * @param <T> - value sent within the reply.
 */
interface Reply<T> {

    T get();
}

/**
 * Message sent to a Channel.
 *
 * @param <T> - value sent within the message.
 */
interface Message<T> {

    T get();
}

/**
 * Channel, which accepts the Messages and responds with Replies.
 *
 * @param <M> - Message value type
 * @param <R> - Reply value type
 */
abstract class Channel<M, R> {

    // Monitor object for the queue to implement kind of a blocking-empty check.
    private final Object queueMonitor = new Object();
    // Blocking queue for incoming messages, which are popped when particular JoinPattern matches,
    // which means all other channels joined in that pattern also have incoming messages
    protected BlockingQueue<Message<M>> messageQueue = new LinkedBlockingQueue<>();

    /**
     * Method of sending messages to the Channel.
     *
     * @param message - submitted by a client and can carry a specific value or Void.
     * @return reply - Asynchronous Channels return immediately Reply<Void>. Synchronous Channels block until
     *      reply is provided through {@link #provideReply(Reply)}.
     * @throws InterruptedException - can be thrown by the Synchronous Channels, since they are blocking the thread
     *      until reply is provided.
     */
    public abstract Reply<R> send(Message<M> message) throws InterruptedException;

    /**
     * When Join Pattern fully matched and got executed by Scheduler, it provides the execution result as a reply
     * to the waiting channel, so that channel can deliver the reply to the calling client.
     *
     * @param reply - execution result from Join Pattern
     * @throws InterruptedException - can be thrown by Synchronous Channels, since they are putting Reply to the queue,
     *    so that it can be provided to the client in the waiting thread.
     */
    public void provideReply(Reply<R> reply) throws InterruptedException {
    }

    /**
     * Blocking method to wait until there are changes in the queue. Join Pattern cannot poll the queues of its
     * Channels, because even if all the Channels do have messages, the pattern may not be necessarily executed.
     * It depends on the Scheduler's Pattern Selection Policy - there can be several matching Patterns with the same
     * Channels.
     *
     * @return true, if Channel's queue has messages.
     * @throws InterruptedException - the method uses waiting for monitor object, which can produce the exception
     */
    public boolean waitUntilHasMessages() throws InterruptedException {
        if (!messageQueue.isEmpty()) {
            return true;
        }
        synchronized (queueMonitor) {
            if (!messageQueue.isEmpty()) {
                return true;
            }
            queueMonitor.wait();
        }
        return !messageQueue.isEmpty();
    }

    /**
     * Non-blocking method to check if Channel's queue has messages. Returns result immediately. It is used by the
     * Scheduler to select all matching Join Patterns.
     *
     * @return true, if Channel's queue has messages.
     */
    public boolean hasMessages() {
        return !messageQueue.isEmpty();
    }

    /**
     * Blocking operation to retrieve and remove the head of the Channel's Message queue.
     * @return the head Message from the queue.
     * @throws InterruptedException - blocking queue may produce the exception
     */
    public Message<M> getMessage() throws InterruptedException {
        return messageQueue.take();
    }

    /**
     * Puts the Message into the queue and notifies Monitor.
     * @param message - new sent Message
     * @throws InterruptedException - putting into the blocking queue may produce the exception
     */
    protected void processMessage(Message<M> message) throws InterruptedException {
        messageQueue.put(message);
        synchronized (queueMonitor) {
            queueMonitor.notifyAll();
        }
    }
}

/**
 * Synchronous Channel blocks when sending the Message until Reply is available.
 *
 * @param <M> - Message value type
 * @param <R> - Reply value type
 */
class SynchronousChannel<M, R> extends Channel<M, R>{

    // Queue for the provided replies
    protected BlockingQueue<Reply<R>> replyQueue = new LinkedBlockingQueue<>();

    @Override
    public void provideReply(Reply<R> reply) throws InterruptedException {
        replyQueue.put(reply);
    }

    @Override
    public Reply<R> send(Message<M> message) throws InterruptedException {
        processMessage(message);
        return replyQueue.take();
    }
}

/**
 * Asynchronous Channel doesn't block when sending the Message. It responds with Reply<Void> immediately.
 *
 * @param <M> - Message value type
 */
class AsynchronousChannel<M> extends Channel<M, Void> {

    @Override
    public Reply<Void> send(Message<M> message) {
        new Thread(() -> {
            try {
                processMessage(message);
            } catch (InterruptedException e) {
                e.printStackTrace();
            }
        }).start();
        return () -> null;
    }
}

/**
 * Join definition of several channels. It is expected to have at leas 1 Output Channel.
 * If Output Channel is Synchronous, then Client expects to receive a value after sending a Message to it (a Reply).
 * Output Channel can also be Asynchronous, then Client just expects to execute some asynchronous operation,
 * when Pattern is matched.
 *
 * Other so-called Input Channels are all Asynchronous Channels, which can accept Messages with actual values or just
 * empty Messages (with Void values). In case of empty Messages, such Channels are used as conditions for Pattern
 * matching.
 *
 * @param <T> - return type of the function that is executed when Pattern is matched.
 */
class JoinPattern<T> {

    // Output Channel is referenced separately from other (Input) Channel,
    // so that Pattern can provide the function execution result to it,
    // and consequently to the waiting Client, if Output Channel is Synchronous
    private Channel<?, T> outputChannel;
    // Input Channels are always Asynchronous
    private final List<AsynchronousChannel<?>> inputChannels = new ArrayList<>();
    // Function executed, when Pattern is matched. Result of the execution is provided as a Reply
    // to the Output Channel
    private Function<List<Message<?>>, T> function;

    /**
     * Specify the initial Output Channel.
     *
     * @param outputChannel - initial Channel, which is used as Output of this Pattern execution
     * @return this Join Pattern
     */
    public JoinPattern<T> with(Channel<?, T> outputChannel) {
        Objects.requireNonNull(outputChannel);
        this.outputChannel = outputChannel;
        return this;
    }

    /**
     * Join next Asynchronous Input Channel.
     *
     * @param inputChannel - one of the joined Asynchronous Channels
     * @return this Join Pattern
     */
    public JoinPattern<T> and(AsynchronousChannel<?> inputChannel) {
        Objects.requireNonNull(inputChannel);
        this.inputChannels.add(inputChannel);
        return this;
    }

    /**
     * Specify function, which needs to be executed upon Pattern matching.
     *
     * @param function to execute
     * @return this Join Pattern
     */
    public JoinPattern<T> onMatch(Function<List<Message<?>>, T> function) {
        this.function = function;
        return this;
    }

    /**
     * Blocks until Channels wake up to respond whether they have messages in their queues or not.
     *
     * @throws InterruptedException - can be produced when waiting for Channels
     */
    public void waitUntilMatches() throws InterruptedException {
        if (outputChannel.waitUntilHasMessages()) {
            inputChannels.forEach(channel -> {
                try {
                    channel.waitUntilHasMessages();
                } catch (InterruptedException e) {
                    throw new RuntimeException(e);
                }
            });
        }
    }

    /**
     * Non-blocking method to check whether all Pattern's Channels received Messages.
     * @return true if all Channels have at least one Message in their queues.
     */
    public boolean matches() {
        return outputChannel.hasMessages() && inputChannels.stream().allMatch(Channel::hasMessages);
    }

    /**
     * Executed by Scheduler, if Pattern matches and Scheduler chooses it according to its Selection Policy
     *
     * @throws InterruptedException - providing reply to the Output Channel can cause the exception,
     *      since Synchronous Channels are passing Replies through the blocking queue.
     */
    public void execute() throws InterruptedException {
        List<Message<?>> messages = Stream
            .concat(
                Stream.of(outputChannel.getMessage()),
                Stream.of(
                    inputChannels
                        .stream()
                        .map(channel -> {
                            try {
                                return channel.getMessage();
                            } catch (InterruptedException e) {
                                throw new RuntimeException(e);
                            }
                        })
                        .toArray(Message<?>[]::new)
                )
            )
            .toList();
        // Function execution result is provided to Output Channel as a Reply,
        // so that the Channel can return it to the waiting Client
        outputChannel.provideReply(() -> function.apply(messages));
    }
}

/**
 * Selection Policy helps Scheduler to decide how to choose between multiple matching Patterns.
 */
interface PatternSelectionPolicy {

    /**
     * Select a single Join Pattern from the list of multiple matched Patterns.
     *
     * @param joinPatterns - list of matched Join Patterns
     * @return selected Join Pattern
     */
    JoinPattern<?> select(List<JoinPattern<?>> joinPatterns);
}

/**
 * Selects first occurrence from the ordered list of Patterns.
 */
class FirstOrderedSelectionPolicy implements PatternSelectionPolicy {

    @Override
    public JoinPattern<?> select(List<JoinPattern<?>> joinPatterns) {
        for (JoinPattern<?> joinPattern : joinPatterns) {
            if (joinPattern.matches()) {
                return joinPattern;
            }
        }
        return null;
    }
}

/**
 * Selects first occurrence from the list of Patterns after randomly reshuffling it.
 */
class FirstRandomSelectionPolicy implements PatternSelectionPolicy {

    @Override
    public JoinPattern<?> select(List<JoinPattern<?>> joinPatterns) {
        List<JoinPattern<?>> reshuffledPatterns = new ArrayList<>(joinPatterns);
        Collections.shuffle(reshuffledPatterns);
        for (JoinPattern<?> joinPattern : reshuffledPatterns) {
            if (joinPattern.matches()) {
                return joinPattern;
            }
        }
        return null;
    }
}

/**
 * Runs a loop to check for Patterns that matched (all Patterns' Channels received Messages),
 * and if there are several of them, selects one for execution according to its Selection Policy.
 */
class Scheduler {

    // Selection Policy to decide how to choose from multiple matched Patterns.
    private final PatternSelectionPolicy selectionPolicy;
    // List if provided Join Patterns
    private final List<JoinPattern<?>> joinPatterns;
    // Flag to terminate the loop, when stopped
    private boolean stopped = false;
    // Monitor object for communicating the signals between Join Pattern specific threads
    // and Scheduler's main loop.
    private final Object monitor = new Object();

    public Scheduler(PatternSelectionPolicy selectionPolicy, List<JoinPattern<?>> joinPatterns) {
        this.selectionPolicy = selectionPolicy;
        this.joinPatterns = joinPatterns;
    }

    /**
     * Runs a loop to wait for Patterns that matched, thus all Channels received the messages.
     * Selects one Patterns for execution based on Selection Policy.
     */
    public void run() {
        // Scheduler starts a separate thread for each Pattern, so that
        // main loop can be placed into wait-state. Main loop thread is waken up
        // if at least one Patter-specific thread notifies about matching.
        for (JoinPattern<?> joinPattern : joinPatterns) {
            Thread t = new Thread(() -> {
                while (!stopped) {
                    try {
                        joinPattern.waitUntilMatches();
                        synchronized (monitor) {
                            monitor.notify();
                        }
                    } catch (InterruptedException e) {
                        e.printStackTrace();
                    }
                }
            });
            t.setDaemon(true);
            t.start();
        }
        while (!stopped) {
            try {
                synchronized (monitor) {
                    monitor.wait();
                    // Here Scheduler once again retrieves all matched Pattern and then selects one for execution
                    List<JoinPattern<?>> matchingPatterns = joinPatterns.stream().filter(JoinPattern::matches).toList();
                    JoinPattern<?> pattern = selectionPolicy.select(matchingPatterns);
                    if (pattern != null) {
                        pattern.execute();
                    }
                }
            } catch (InterruptedException e) {
                e.printStackTrace();
            }
        }
    }

    /**
     * Stops the Scheduler's loop
     */
    public void stop() {
        this.stopped = true;
    }
}



public class JoinDemo {

    public static void main(String[] args) throws Exception {
        SynchronousChannel<String, String> channelA = new SynchronousChannel<>();
        AsynchronousChannel<String> channelB = new AsynchronousChannel<>();
        AsynchronousChannel<String> channelC = new AsynchronousChannel<>();
        AsynchronousChannel<String> channelD = new AsynchronousChannel<>();
        AsynchronousChannel<String> channelE = new AsynchronousChannel<>();
        JoinPattern<String> joinPattern1 = new JoinPattern<String>()
            .with(channelA)
            .and(channelB)
            .and(channelC)
            .onMatch(messages -> "Pattern 1 received messages: " + String.join(",", messages.stream().map(m -> (String) m.get()).toList()));
        JoinPattern<String> joinPattern2 = new JoinPattern<String>()
            .with(channelA)
            .and(channelB)
            .and(channelD)
            .onMatch(messages -> "Pattern 2 received messages: " + String.join(",", messages.stream().map(m -> (String) m.get()).toList()));
        JoinPattern<String> joinPattern3 = new JoinPattern<String>()
            .with(channelA)
            .and(channelB)
            .and(channelE)
            .onMatch(messages -> "Pattern 3 received messages: " + String.join(",", messages.stream().map(m -> (String) m.get()).toList()));
        Scheduler scheduler = new Scheduler(new FirstRandomSelectionPolicy(), List.of(
            joinPattern1,
            joinPattern2,
            joinPattern3
        ));
        Thread t = new Thread(scheduler::run);
        t.setDaemon(true);
        t.start();

        channelB.send(() -> "B");
        channelC.send(() -> "C");
        channelD.send(() -> "D");
        channelE.send(() -> "E");

        System.out.println(channelA.send(() -> "A").get());

        channelB.send(() -> "B");

        System.out.println(channelA.send(() -> "A").get());

        channelB.send(() -> "B");

        System.out.println(channelA.send(() -> "A").get());

        scheduler.stop();
    }
}
