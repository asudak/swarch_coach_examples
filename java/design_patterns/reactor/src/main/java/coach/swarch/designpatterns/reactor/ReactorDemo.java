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
import java.util.stream.Stream;

class Reactor {

    private final Demultiplexer demultiplexer;
    private final Map<EventType, EventHandler> eventHandlers = new HashMap<>();
    private boolean stopped = false;

    public Reactor(Demultiplexer demultiplexer) {
        this.demultiplexer = demultiplexer;
    }

    public void registerEventHandler(EventType type, EventHandler eventHandler) throws IOException {
        eventHandlers.put(type, eventHandler);
        demultiplexer.registerHandle(type, eventHandler.getHandle());
    }

    public void eventLoop() throws IOException {
        while (!stopped) {
            demultiplexer
                .select()
                .forEach(type -> {
                    try {
                        eventHandlers.get(type).handleEvent();
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
        selector.wakeup();
        selector.close();
    }

    public void registerHandle(EventType type, Handle handle) throws IOException {
        handle.getChannel().register(selector, type.getKey());
    }

    public Stream<EventType> select() throws IOException {
        selector.select();
        Set<SelectionKey> readyHandles = selector.selectedKeys();
        return readyHandles
            .stream()
            .flatMap(EventType::types)
            .filter(Objects::nonNull);
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

abstract class EventHandler {

    protected final Handle handle;
    protected final Reactor reactor;

    public EventHandler(Handle handle, Reactor reactor) {
        this.handle = handle;
        this.reactor = reactor;
    }

    public Handle getHandle() {
        return handle;
    }

    public abstract void handleEvent() throws IOException;
}

class AcceptEventHandler extends EventHandler {

    public AcceptEventHandler(Handle handle, Reactor reactor) {
        super(handle, reactor);
    }

    @Override
    public void handleEvent() throws IOException {
        ServerSocketChannel serverSocketChannel = (ServerSocketChannel) handle.getChannel();
        SocketChannel socketChannel = serverSocketChannel.accept();
        if (socketChannel != null) {
            socketChannel.configureBlocking(false);
            reactor.registerEventHandler(
                EventType.READ,
                new ReadEventHandler(new Handle(socketChannel), reactor)
            );
        }
    }
}

class ReadEventHandler extends EventHandler {

    public ReadEventHandler(Handle handle, Reactor reactor) {
        super(handle, reactor);
    }

    @Override
    public void handleEvent() throws IOException {
        SocketChannel socketChannel = (SocketChannel) handle.getChannel();
        ByteBuffer inputBuffer = ByteBuffer.allocate(2048);
        socketChannel.read(inputBuffer);
        inputBuffer.flip();
        byte[] buffer = new byte[inputBuffer.limit()];
        inputBuffer.get(buffer);
        String message = new String(buffer);
        System.out.print(message);
        if (message.endsWith("\n")) {
            reactor.registerEventHandler(
                EventType.WRITE,
                new WriteEventHandler(new Handle(socketChannel), reactor)
            );
        }
    }
}

class WriteEventHandler extends EventHandler {

    public WriteEventHandler(Handle handle, Reactor reactor) {
        super(handle, reactor);
    }

    @Override
    public void handleEvent() throws IOException {
        SocketChannel socketChannel = (SocketChannel) handle.getChannel();
        socketChannel.write(ByteBuffer.wrap("Hi, Client. I'm Server!\n".getBytes()));
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

        Reactor reactor = new Reactor(new Demultiplexer(selector));
        reactor.registerEventHandler(
            EventType.ACCEPT,
            new AcceptEventHandler(new Handle(serverChannel), reactor)
        );

        Thread serverThread = new Thread(() -> {
            try {
                reactor.eventLoop();
            } catch (IOException e) {
                e.printStackTrace();
            }
        });
        serverThread.setDaemon(true);
        serverThread.start();

        for (int i = 1; i <= 3; i++) {
            Socket clientSocket = new Socket("localhost", serverPort);
            DataOutputStream writer = new DataOutputStream(clientSocket.getOutputStream());
            writer.writeBytes("Hi, Server. I'm Client " + i + "!\n");
            BufferedReader reader = new BufferedReader(new InputStreamReader(clientSocket.getInputStream()));
            String message = reader.readLine();
            System.out.println(message);
            clientSocket.close();
        }

        reactor.stop();
        serverChannel.socket().close();
    }
}
