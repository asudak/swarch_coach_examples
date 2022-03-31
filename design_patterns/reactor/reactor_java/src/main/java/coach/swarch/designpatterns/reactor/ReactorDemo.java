package coach.swarch.designpatterns.reactor;

import java.io.BufferedReader;
import java.io.DataOutputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.nio.ByteBuffer;
import java.nio.channels.*;
import java.util.*;
import java.util.concurrent.CountDownLatch;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;

class Reactor {

    private final Demultiplexer demultiplexer;
    private final Map<EventType, EventHandler> eventHandlers = new HashMap<>();
    private boolean stopped = false;

    public Reactor(Demultiplexer demultiplexer) {
        this.demultiplexer = demultiplexer;
    }

    public void registerEventHandler(EventType type, EventHandler eventHandler) {
        eventHandlers.put(type, eventHandler);
    }

    public void registerHandle(EventType type, Handle handle, Context context) throws IOException {
        demultiplexer.registerHandle(type, handle, context);
    }

    public void eventLoop() throws IOException {
        while (!stopped) {
            demultiplexer
                .select()
                .forEach(event -> {
                    try {
                        eventHandlers.get(event.getType()).handleEvent(event);
                    } catch (IOException e) {
                        e.printStackTrace();
                    }
                });
        }
    }

    public void stop() throws IOException {
        this.stopped = true;
        this.demultiplexer.close();
    };
}

class Demultiplexer {

    private final Selector selector;

    public Demultiplexer(Selector selector) {
        this.selector = selector;
    }

    public void close() throws IOException {
        if (selector.isOpen()) {
            selector.wakeup();
            selector.close();
        }
    }

    public void registerHandle(EventType type, Handle handle, Context context) throws IOException {
        handle.getChannel().register(selector, type.getKey(), context);
    }

    public Stream<Event> select() throws IOException {
        selector.select();
        Set<SelectionKey> readyHandles = selector.selectedKeys();
        return readyHandles
            .stream()
            .flatMap(key -> EventType
                .types(key)
                .map(type -> new Event(
                    type,
                    new Handle(key.channel()),
                    key.attachment() == null ? new Context() : (Context) key.attachment())
                )
            );
    }
}

enum EventType {

    ACCEPT(SelectionKey.OP_ACCEPT),
    READ(SelectionKey.OP_READ),
    WRITE(SelectionKey.OP_WRITE);

    private final int key;

    EventType(int key) {
        this.key = key;
    }

    public int getKey() {
        return key;
    }

    public static Stream<EventType> types(SelectionKey key) {
        return Arrays.stream(values()).filter(type -> key.isValid() && (key.readyOps() & type.key) != 0);
    }
}

class Handle {

    private final SelectableChannel channel;

    public Handle(SelectableChannel channel) {
        this.channel = channel;
    }

    public SelectableChannel getChannel() {
        return channel;
    }
}

class Context {

    private ByteBuffer buffer;

    public ByteBuffer getBuffer() {
        return buffer;
    }

    public Context setBuffer(ByteBuffer buffer) {
        this.buffer = buffer;
        return this;
    }
}

class Event {

    private final EventType type;
    private final Handle handle;
    private final Context context;

    public Event(EventType type, Handle handle, Context context) {
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

abstract class EventHandler {

    protected final Demultiplexer demultiplexer;

    public EventHandler(Demultiplexer demultiplexer) {
        this.demultiplexer = demultiplexer;
    }

    public abstract void handleEvent(Event event) throws IOException;
}

class AcceptEventHandler extends EventHandler {

    public AcceptEventHandler(Demultiplexer demultiplexer) {
        super(demultiplexer);
    }

    @Override
    public void handleEvent(Event event) throws IOException {
        ServerSocketChannel serverSocketChannel = (ServerSocketChannel) event.getHandle().getChannel();
        SocketChannel socketChannel = serverSocketChannel.accept();
        if (socketChannel != null) {
            socketChannel.configureBlocking(false);
            demultiplexer.registerHandle(
                EventType.READ,
                new Handle(socketChannel),
                new Context()
            );
        }
    }
}

class ReadEventHandler extends EventHandler {

    public ReadEventHandler(Demultiplexer demultiplexer) {
        super(demultiplexer);
    }

    @Override
    public void handleEvent(Event event) throws IOException {
        SocketChannel socketChannel = (SocketChannel) event.getHandle().getChannel();
        Context context = event.getContext();
        ByteBuffer inputBuffer = ByteBuffer.allocate(2048);
        socketChannel.read(inputBuffer);
        inputBuffer.flip();
        ByteBuffer contextBuffer = context.getBuffer() == null ? inputBuffer : ByteBuffer
            .allocate(context.getBuffer().limit() + inputBuffer.limit())
            .put(context.getBuffer())
            .put(inputBuffer)
            .flip();
        context.setBuffer(contextBuffer);
        byte[] buffer = new byte[contextBuffer.limit()];
        contextBuffer.get(buffer);
        contextBuffer.rewind();
        String message = new String(buffer);
        if (message.endsWith("\n") && inputBuffer.limit() > 0) {
            System.out.print(message);
            demultiplexer.registerHandle(
                EventType.WRITE,
                new Handle(socketChannel),
                context
            );
        }
    }
}

class WriteEventHandler extends EventHandler {

    private static final Pattern CLIENT_ID = Pattern.compile("^.*(Client [^!]+)!$");

    public WriteEventHandler(Demultiplexer demultiplexer) {
        super(demultiplexer);
    }

    @Override
    public void handleEvent(Event event) throws IOException {
        SocketChannel socketChannel = (SocketChannel) event.getHandle().getChannel();
        Context context = event.getContext();
        ByteBuffer contextBuffer = context.getBuffer();
        byte[] buffer = new byte[contextBuffer.limit()];
        contextBuffer.get(buffer);
        contextBuffer.rewind();
        String clientMessage = new String(buffer).trim();
        Matcher matcher = CLIENT_ID.matcher(clientMessage);
        if (matcher.matches()) {
            socketChannel.write(ByteBuffer.wrap(String.format("Hi, %s. I'm Server!\n", matcher.group(1)).getBytes()));
        }
        socketChannel.close();
    }
}

public class ReactorDemo {

    public static void main(String[] args) throws Exception {
        int serverPort = new Random().nextInt(10000, 30000);
        ServerSocketChannel serverChannel = ServerSocketChannel.open();
        serverChannel.socket().bind(new InetSocketAddress(serverPort));
        serverChannel.configureBlocking(false);
        Selector selector = Selector.open();

        Demultiplexer demultiplexer = new Demultiplexer(selector);
        Reactor reactor = new Reactor(demultiplexer);
        reactor.registerEventHandler(EventType.ACCEPT, new AcceptEventHandler(demultiplexer));
        reactor.registerEventHandler(EventType.READ, new ReadEventHandler(demultiplexer));
        reactor.registerEventHandler(EventType.WRITE, new WriteEventHandler(demultiplexer));
        reactor.registerHandle(EventType.ACCEPT, new Handle(serverChannel), null);

        Thread serverThread = new Thread(() -> {
            try {
                reactor.eventLoop();
            } catch (IOException e) {
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

        reactor.stop();
        serverChannel.socket().close();
    }
}
