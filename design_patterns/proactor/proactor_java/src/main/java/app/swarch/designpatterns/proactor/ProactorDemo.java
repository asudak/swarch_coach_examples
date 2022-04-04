package app.swarch.designpatterns.proactor;

import java.io.BufferedReader;
import java.io.DataOutputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.nio.ByteBuffer;
import java.nio.channels.AsynchronousChannel;
import java.nio.channels.AsynchronousServerSocketChannel;
import java.nio.channels.AsynchronousSocketChannel;
import java.util.*;
import java.util.concurrent.*;
import java.util.function.Consumer;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

class Proactor {

    private final Demultiplexer demultiplexer;
    private final Map<EventType, CompletionHandler> completionHandlers = new HashMap<>();
    private boolean stopped = false;

    public Proactor(Demultiplexer demultiplexer) {
        this.demultiplexer = demultiplexer;
    }

    public void registerCompletionHandler(EventType type, CompletionHandler completionHandler) throws IOException {
        completionHandlers.put(type, completionHandler);
    }

    public void eventLoop() throws InterruptedException {
        while (!stopped) {
            CompletionEvent completionEvent = demultiplexer.getCompletionEvent();
            completionHandlers.get(completionEvent.getType()).handleEvent(completionEvent);
        }
    }

    public void stop() {
        this.stopped = true;
        this.demultiplexer.close();
    };
}

class Demultiplexer {

    private final CompletionEventQueue queue;
    private boolean closed = false;

    public Demultiplexer(CompletionEventQueue queue) {
        this.queue = queue;
    }

    public CompletionEvent getCompletionEvent() throws InterruptedException {
        while (!closed) {
            CompletionEvent completionEvent = queue.dequeue();
            if (completionEvent != null) {
                return completionEvent;
            }
        }
        return null;
    }

    public void close() {
        this.closed = true;
    }
}

class CompletionEventQueue {

    private final BlockingQueue<CompletionEvent> queue = new LinkedBlockingQueue<>();

    public CompletionEvent dequeue() throws InterruptedException {
        return queue.poll(100, TimeUnit.MILLISECONDS);
    }

    public void enqueue(CompletionEvent event) throws InterruptedException {
        queue.put(event);
    }
}

class Context {

    private ByteBuffer buffer;
    private AsynchronousSocketChannel socketChannel;

    public ByteBuffer getBuffer() {
        return buffer;
    }

    public Context setBuffer(ByteBuffer buffer) {
        this.buffer = buffer;
        return this;
    }

    public AsynchronousSocketChannel getSocketChannel() {
        return socketChannel;
    }

    public Context setSocketChannel(AsynchronousSocketChannel socketChannel) {
        this.socketChannel = socketChannel;
        return this;
    }
}

class CompletionEvent {

    private final EventType type;
    private final Handle handle;
    private final Context context;

    public CompletionEvent(EventType type, Handle handle, Context context) {
        this.type = type;
        this.handle = handle;
        this.context = context;
    }

    public EventType getType() {
        return type;
    }

    public Handle getHandle() {
        return handle;
    }

    public Context getContext() {
        return context;
    }
}

enum EventType {

    ACCEPT,
    READ,
    WRITE
}

abstract class CompletionHandler {

    protected final AsynchronousOperationProcessor processor;

    public CompletionHandler(AsynchronousOperationProcessor processor) {
        this.processor = processor;
    }

    public abstract void handleEvent(CompletionEvent completionEvent);
}

class AcceptCompletionHandler extends CompletionHandler {

    public AcceptCompletionHandler(AsynchronousOperationProcessor processor) {
        super(processor);
    }

    @Override
    public void handleEvent(CompletionEvent completionEvent) {
        Handle acceptHandle = completionEvent.getHandle();
        Handle readHandle = new Handle(completionEvent.getContext().getSocketChannel());
        Context context = completionEvent.getContext() == null ? null : new Context();
        processor.execute(new AcceptAsynchronousOperation(acceptHandle, null));
        processor.execute(new ReadAsynchronousOperation(readHandle, context));
    }
}

class ReadCompletionHandler extends CompletionHandler {

    public ReadCompletionHandler(AsynchronousOperationProcessor processor) {
        super(processor);
    }

    @Override
    public void handleEvent(CompletionEvent completionEvent) {
        Context context = completionEvent.getContext();
        ByteBuffer inputBuffer = completionEvent.getContext().getBuffer();
        inputBuffer.rewind();
        byte[] buffer = new byte[inputBuffer.limit()];
        inputBuffer.get(buffer);
        inputBuffer.rewind();
        String message = new String(buffer);
        if (message.endsWith("\n")) {
            System.out.print(message);
            processor.execute(new WriteAsynchronousOperation(completionEvent.getHandle(), context));
        } else {
            processor.execute(new ReadAsynchronousOperation(completionEvent.getHandle(), context));
        }
    }
}

class WriteCompletionHandler extends CompletionHandler {

    public WriteCompletionHandler(AsynchronousOperationProcessor processor) {
        super(processor);
    }

    @Override
    public void handleEvent(CompletionEvent completionEvent) {
        AsynchronousSocketChannel socketChannel = (AsynchronousSocketChannel) completionEvent.getHandle().getChannel();
        try {
            socketChannel.close();
        } catch (IOException e) {
            e.printStackTrace();
        }
    }
}

class Handle {

    private final AsynchronousChannel channel;

    public Handle(AsynchronousChannel channel) {
        this.channel = channel;
    }

    public AsynchronousChannel getChannel() {
        return channel;
    }
}

class AsynchronousOperationProcessor {

    private final CompletionEventQueue queue;

    public AsynchronousOperationProcessor(CompletionEventQueue queue) {
        this.queue = queue;
    }

    public void execute(AsynchronousOperation operation) {
        operation.execute(completionEvent -> {
            try {
                queue.enqueue(completionEvent);
            } catch (InterruptedException e) {
                e.printStackTrace();
            }
        });
    }
}

abstract class AsynchronousOperation {

    protected final Handle handle;
    protected final Context context;

    public AsynchronousOperation(Handle handle, Context context) {
        this.handle = handle;
        this.context = context;
    }

    public abstract void execute(Consumer<CompletionEvent> eventConsumer);
}

class AcceptAsynchronousOperation extends AsynchronousOperation {

    public AcceptAsynchronousOperation(Handle handle, Context context) {
        super(handle, context);
    }

    @Override
    public void execute(Consumer<CompletionEvent> eventConsumer) {
        AsynchronousServerSocketChannel channel = (AsynchronousServerSocketChannel) handle.getChannel();
        channel.accept(null, new java.nio.channels.CompletionHandler<>() {
            @Override
            public void completed(AsynchronousSocketChannel result, Object attachment) {
                eventConsumer.accept(new CompletionEvent(EventType.ACCEPT, new Handle(channel), new Context().setSocketChannel(result)));
            }

            @Override
            public void failed(Throwable e, Object attachment) {
                e.printStackTrace();
            }
        });
    }
}

class ReadAsynchronousOperation extends AsynchronousOperation {

    public ReadAsynchronousOperation(Handle handle, Context context) {
        super(handle, context);
    }

    @Override
    public void execute(Consumer<CompletionEvent> eventConsumer) {
        AsynchronousSocketChannel socketChannel = (AsynchronousSocketChannel) handle.getChannel();
        ByteBuffer inputBuffer = ByteBuffer.allocate(2048);
        socketChannel.read(inputBuffer, null, new java.nio.channels.CompletionHandler<>() {
            @Override
            public void completed(Integer result, Object attachment) {
                inputBuffer.rewind();
                byte[] bytes = new byte[result];
                inputBuffer.get(bytes);
                ByteBuffer newBuffer = context.getBuffer() == null ? ByteBuffer.wrap(bytes) : ByteBuffer
                    .allocate(context.getBuffer().limit() + bytes.length)
                    .put(context.getBuffer())
                    .put(bytes)
                    .rewind();
                eventConsumer.accept(
                    new CompletionEvent(
                        EventType.READ,
                        new Handle(socketChannel),
                        context.setBuffer(newBuffer)
                    )
                );
            }

            @Override
            public void failed(Throwable e, Object attachment) {
                e.printStackTrace();
            }
        });
    }
}

class WriteAsynchronousOperation extends AsynchronousOperation {

    private static final Pattern CLIENT_ID = Pattern.compile("^.*(Client [^!]+)!$");

    public WriteAsynchronousOperation(Handle handle, Context context) {
        super(handle, context);
    }

    @Override
    public void execute(Consumer<CompletionEvent> eventConsumer) {
        AsynchronousSocketChannel socketChannel = (AsynchronousSocketChannel) handle.getChannel();
        ByteBuffer contextBuffer = context.getBuffer();
        contextBuffer.rewind();
        byte[] buffer = new byte[contextBuffer.limit()];
        contextBuffer.get(buffer);
        contextBuffer.rewind();
        String clientMessage = new String(buffer).trim();
        Matcher matcher = CLIENT_ID.matcher(clientMessage);
        String reply = "Hi, Stranger. I'm Server!";
        if (matcher.matches()) {
            reply = String.format("Hi, %s. I'm Server!\n", matcher.group(1));
        }
        socketChannel.write(ByteBuffer.wrap(reply.getBytes()), null, new java.nio.channels.CompletionHandler<>() {
            @Override
            public void completed(Integer result, Object attachment) {
                eventConsumer.accept(new CompletionEvent(EventType.WRITE, new Handle(socketChannel), context));
            }

            @Override
            public void failed(Throwable e, Object attachment) {
                e.printStackTrace();
            }
        });
    }
}

public class ProactorDemo {

    public static void main(String[] args) throws Exception {
        int serverPort = new Random().nextInt(10000, 30000);
        AsynchronousServerSocketChannel serverChannel = AsynchronousServerSocketChannel.open();
        serverChannel.bind(new InetSocketAddress(serverPort));

        CompletionEventQueue queue = new CompletionEventQueue();
        AsynchronousOperationProcessor processor = new AsynchronousOperationProcessor(queue);
        processor.execute(new AcceptAsynchronousOperation(new Handle(serverChannel), null));

        Proactor proactor = new Proactor(new Demultiplexer(queue));
        proactor.registerCompletionHandler(EventType.ACCEPT, new AcceptCompletionHandler(processor));
        proactor.registerCompletionHandler(EventType.READ, new ReadCompletionHandler(processor));
        proactor.registerCompletionHandler(EventType.WRITE, new WriteCompletionHandler(processor));

        Thread serverThread = new Thread(() -> {
            try {
                proactor.eventLoop();
            } catch (InterruptedException e) {
                e.printStackTrace();
            }
        });
        serverThread.setDaemon(true);
        serverThread.start();

        int numberOfClients = 3;
        CountDownLatch countDownLatch = new CountDownLatch(numberOfClients);
        for (int i = 1; i <= numberOfClients; i++) {
            new Thread(() -> {
                try {
                    try (Socket clientSocket = new Socket("localhost", serverPort)) {
                        DataOutputStream writer = new DataOutputStream(clientSocket.getOutputStream());
                        writer.writeBytes("Hi, Server. I'm Client " + Thread.currentThread().getId() + "!\n");
                        BufferedReader reader = new BufferedReader(new InputStreamReader(clientSocket.getInputStream()));
                        String message = reader.readLine();
                        System.out.println(message);
                    }
                } catch (Exception e) {
                    e.printStackTrace();
                } finally {
                    countDownLatch.countDown();
                }
            }).start();
        }
        countDownLatch.await();

        proactor.stop();
        serverChannel.close();
    }
}
