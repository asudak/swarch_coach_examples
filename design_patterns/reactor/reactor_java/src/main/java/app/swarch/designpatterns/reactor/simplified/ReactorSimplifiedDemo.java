package app.swarch.designpatterns.reactor.simplified;

import java.io.BufferedReader;
import java.io.DataOutputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.nio.ByteBuffer;
import java.nio.channels.SelectionKey;
import java.nio.channels.Selector;
import java.nio.channels.ServerSocketChannel;
import java.nio.channels.SocketChannel;
import java.util.*;
import java.util.concurrent.CountDownLatch;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

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

abstract class EventHandler {

    protected final Selector demultiplexer;

    public EventHandler(Selector demultiplexer) {
        this.demultiplexer = demultiplexer;
    }

    public abstract void handleEvent(SelectionKey handle) throws IOException;
}

class AcceptEventHandler extends EventHandler{

    public AcceptEventHandler(Selector demultiplexer) {
        super(demultiplexer);
    }

    @Override
    public void handleEvent(SelectionKey handle) throws IOException {
        ServerSocketChannel serverSocketChannel = (ServerSocketChannel) handle.channel();
        SocketChannel socketChannel = serverSocketChannel.accept();
        if (socketChannel != null) {
            socketChannel.configureBlocking(false);
            socketChannel.register(demultiplexer, SelectionKey.OP_READ, new Context());
        }
    }
}

class ReadEventHandler extends EventHandler {

    public ReadEventHandler(Selector demultiplexer) {
        super(demultiplexer);
    }

    @Override
    public void handleEvent(SelectionKey handle) throws IOException {
        SocketChannel socketChannel = (SocketChannel) handle.channel();
        Context context = (Context) handle.attachment();
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
            socketChannel.register(demultiplexer, SelectionKey.OP_WRITE, context);
        }
    }
}

class WriteEventHandler extends EventHandler {

    private static final Pattern CLIENT_ID = Pattern.compile("^.*(Client [^!]+)!$");

    public WriteEventHandler(Selector demultiplexer) {
        super(demultiplexer);
    }

    @Override
    public void handleEvent(SelectionKey handle) throws IOException {
        SocketChannel socketChannel = (SocketChannel) handle.channel();
        Context context = (Context) handle.attachment();
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

class Reactor {

    private static final List<Integer> SUPPORTED_OPS = List.of(
        SelectionKey.OP_ACCEPT,
        SelectionKey.OP_READ,
        SelectionKey.OP_WRITE
    );

    private final Selector demultiplexer;
    private final Map<Integer, EventHandler> eventHandlers = new HashMap<>();
    private boolean stopped = false;

    public Reactor() throws IOException {
        demultiplexer = Selector.open();
        eventHandlers.put(SelectionKey.OP_ACCEPT, new AcceptEventHandler(demultiplexer));
        eventHandlers.put(SelectionKey.OP_READ, new ReadEventHandler(demultiplexer));
        eventHandlers.put(SelectionKey.OP_WRITE, new WriteEventHandler(demultiplexer));
    }

    public Selector getDemultiplexer() {
        return demultiplexer;
    }

    public void eventLoop() throws IOException {
        while (!stopped) {
            demultiplexer.select();
            demultiplexer.selectedKeys()
                .forEach(key -> {
                    SUPPORTED_OPS
                        .stream()
                        .filter(op -> key.isValid() && (key.readyOps() & op) != 0)
                        .forEach(op -> {
                            try {
                                eventHandlers.get(op).handleEvent(key);
                            } catch (IOException e) {
                                e.printStackTrace();
                            }
                        });
                });
        }
    }

    public void stop() throws IOException {
        stopped = true;
        if (demultiplexer.isOpen()) {
            demultiplexer.wakeup();
            demultiplexer.close();
        }
    };
}

public class ReactorSimplifiedDemo {

    public static void main(String[] args) throws Exception {
        int serverPort = new Random().nextInt(10000, 30000);
        ServerSocketChannel serverChannel = ServerSocketChannel.open();
        serverChannel.socket().bind(new InetSocketAddress(serverPort));
        serverChannel.configureBlocking(false);

        Reactor reactor = new Reactor();
        serverChannel.register(reactor.getDemultiplexer(), SelectionKey.OP_ACCEPT);

        Thread serverThread = new Thread(() -> {
            try {
                reactor.eventLoop();
            } catch (IOException e) {
                e.printStackTrace();
            }
        });
        serverThread.setDaemon(true);
        serverThread.start();

        int numberOfClients = 33;
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
