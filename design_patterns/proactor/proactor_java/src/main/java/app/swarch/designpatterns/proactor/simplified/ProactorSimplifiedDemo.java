package app.swarch.designpatterns.proactor.simplified;

import java.io.BufferedReader;
import java.io.DataOutputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.nio.ByteBuffer;
import java.nio.channels.AsynchronousServerSocketChannel;
import java.nio.channels.AsynchronousSocketChannel;
import java.nio.channels.CompletionHandler;
import java.util.Random;
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

class AcceptCompletionHandler implements CompletionHandler<AsynchronousSocketChannel, Context> {

    private final AsynchronousServerSocketChannel serverSocketChannel;

    public AcceptCompletionHandler(AsynchronousServerSocketChannel serverSocketChannel) {
        this.serverSocketChannel = serverSocketChannel;
    }

    @Override
    public void completed(AsynchronousSocketChannel socketChannel, Context context) {

        serverSocketChannel.accept(new Context(), this);

        ByteBuffer inputBuffer = ByteBuffer.allocate(2048);
        ReadCompletionHandler readCompletionHandler = new ReadCompletionHandler(socketChannel, inputBuffer);
        socketChannel.read(inputBuffer, context, readCompletionHandler);
    }

    @Override
    public void failed(Throwable e, Context context) {
        e.printStackTrace();
    }
}

class ReadCompletionHandler implements CompletionHandler<Integer, Context> {

    private static final Pattern CLIENT_ID = Pattern.compile("^.*(Client [^!]+)!$");

    private final AsynchronousSocketChannel socketChannel;
    private final ByteBuffer inputBuffer;

    public ReadCompletionHandler(AsynchronousSocketChannel socketChannel, ByteBuffer inputBuffer) {
        this.socketChannel = socketChannel;
        this.inputBuffer = inputBuffer;
    }

    @Override
    public void completed(Integer result, Context context) {
        inputBuffer.rewind();
        byte[] bytes = new byte[result];
        inputBuffer.get(bytes);
        ByteBuffer newBuffer = context.getBuffer() == null ? ByteBuffer.wrap(bytes) : ByteBuffer
            .allocate(context.getBuffer().limit() + bytes.length)
            .put(context.getBuffer())
            .put(bytes)
            .rewind();
        context.setBuffer(newBuffer);
        byte[] buffer = new byte[newBuffer.limit()];
        newBuffer.get(buffer);
        newBuffer.rewind();
        String message = new String(buffer);
        if (message.endsWith("\n")) {
            System.out.print(message);
            Matcher matcher = CLIENT_ID.matcher(message.trim());
            String reply = "Hi, Stranger. I'm Server!";
            if (matcher.matches()) {
                reply = String.format("Hi, %s. I'm Server!\n", matcher.group(1));
            }
            WriteCompletionHandler writeCompletionHandler = new WriteCompletionHandler(socketChannel);
            ByteBuffer outputBuffer = ByteBuffer.wrap(reply.getBytes());
            socketChannel.write(outputBuffer, context, writeCompletionHandler);
        } else {
            ByteBuffer inputBuffer = ByteBuffer.allocate(2048);
            ReadCompletionHandler readCompletionHandler = new ReadCompletionHandler(socketChannel, inputBuffer);
            socketChannel.read(inputBuffer, context, readCompletionHandler);
        }
    }

    @Override
    public void failed(Throwable e, Context context) {
        e.printStackTrace();
    }
}

class WriteCompletionHandler implements CompletionHandler<Integer, Context> {

    private final AsynchronousSocketChannel socketChannel;

    public WriteCompletionHandler(AsynchronousSocketChannel socketChannel) {
        this.socketChannel = socketChannel;
    }

    @Override
    public void completed(Integer bytesWritten, Context context) {
        try {
            socketChannel.close();
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    @Override
    public void failed(Throwable e, Context context) {
        e.printStackTrace();
    }

}

public class ProactorSimplifiedDemo {

    public static void main(String[] args) throws Exception {
        int serverPort = new Random().nextInt(10000, 30000);
        AsynchronousServerSocketChannel serverChannel = AsynchronousServerSocketChannel.open();
        serverChannel.bind(new InetSocketAddress(serverPort));

        serverChannel.accept(new Context(), new AcceptCompletionHandler(serverChannel));

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

        serverChannel.close();
    }
}
