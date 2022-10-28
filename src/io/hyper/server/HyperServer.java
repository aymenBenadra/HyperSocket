package io.hyper.server;

import io.hyper.client.HyperClientHandler;

import java.io.IOException;
import java.net.ServerSocket;
import java.net.Socket;

public class HyperServer {
    static final int PORT = 1234;

    public static void main(String[] args) {
        try (ServerSocket serverSocket = new ServerSocket(PORT)) {
            System.out.println("Server started on port " + PORT);
            while (!serverSocket.isClosed()) {
                Socket socket = serverSocket.accept();
                System.out.println("A new client has connected!");

                HyperClientHandler hyperClientHandler = new HyperClientHandler(socket);

                Thread thread = new Thread(hyperClientHandler);
                thread.start();
            }
        } catch (IOException e) {
            e.printStackTrace();
        }
    }
}
