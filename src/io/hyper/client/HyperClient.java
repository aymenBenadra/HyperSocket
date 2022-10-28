package io.hyper.client;

import java.io.*;
import java.net.Socket;
import java.time.format.DateTimeFormatter;
import java.util.Date;
import java.util.Scanner;

public class HyperClient {
    private static final Scanner scanner = new Scanner(System.in);
    private final Socket socket;
    private final BufferedWriter bufferedWriter;
    private final BufferedReader bufferedReader;
    private final DataInputStream dataInputStream;
    private final DataOutputStream dataOutputStream;
    private final String username;

    public HyperClient(Socket socket, String username) {
        this.username = username;
        try {
            this.socket = socket;

            // BufferedReader and BufferedWriter are used for text messages
            this.bufferedReader = new BufferedReader(new InputStreamReader(socket.getInputStream()));
            this.bufferedWriter = new BufferedWriter(new OutputStreamWriter(socket.getOutputStream()));

            // DataInputStream and DataOutputStream are used for files
            this.dataInputStream = new DataInputStream(socket.getInputStream());
            this.dataOutputStream = new DataOutputStream(socket.getOutputStream());
        } catch (IOException e) {
            e.printStackTrace();
            throw new RuntimeException("Error while creating client!");
        }
    }

    public static void main(String[] args) {
        // Splash screen for application
        System.out.println("""
                                   _______  _______  _______  _______  _______  _______  _        _______ _________
                |\\     /||\\     /|(  ____ )(  ____ \\(  ____ )(  ____ \\(  ___  )(  ____ \\| \\    /\\(  ____ \\\\__   __/
                | )   ( |( \\   / )| (    )|| (    \\/| (    )|| (    \\/| (   ) || (    \\/|  \\  / /| (    \\/   ) (  \s
                | (___) | \\ (_) / | (____)|| (__    | (____)|| (_____ | |   | || |      |  (_/ / | (__       | |  \s
                |  ___  |  \\   /  |  _____)|  __)   |     __)(_____  )| |   | || |      |   _ (  |  __)      | |  \s
                | (   ) |   ) (   | (      | (      | (\\ (         ) || |   | || |      |  ( \\ \\ | (         | |  \s
                | )   ( |   | |   | )      | (____/\\| ) \\ \\__/\\____) || (___) || (____/\\|  /  \\ \\| (____/\\   | |  \s
                |/     \\|   \\_/   |/       (_______/|/   \\__/\\_______)(_______)(_______/|_/    \\/(_______/   )_(  \s
                                                                                                                  \s""");

        System.out.println("Enter your Username for the group chat: ");
        String username = scanner.nextLine();
        try (Socket socket = new Socket("localhost", 1234)) {
            HyperClient client = new HyperClient(socket, username);

            // Send username to server
            client.bufferedWriter.write(username);
            client.bufferedWriter.newLine();
            client.bufferedWriter.flush();

            // Start a new thread to listen for messages from the server
            client.listenForMessages();

            // Send messages to the server
            client.sendMessages();
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    public void sendMessages() {
        try {
            while (!socket.isClosed()) {
                System.out.print(username + "> ");
                String message = scanner.nextLine();

                if (message.startsWith(":file")) {
                    String filename = message.split(" ", 2)[1];
                    System.out.println("Sending file " + filename + " to server...");
                    sendTextMessage(":file");
                    sendFile(filename);
                } else {
                    sendTextMessage(message);
                }
            }
        } catch (IOException e) {
            e.printStackTrace();
            closeEverything();
        }
    }

    private void sendTextMessage(String message) throws IOException {
        bufferedWriter.write(message);
        bufferedWriter.newLine();
        bufferedWriter.flush();
    }

    public void sendFile(String fileName) throws IOException {
        File file = new File(fileName);

        if (!file.exists()) {
            System.out.println("File does not exist!");
            return;
        }

        // Send file name to server
        dataOutputStream.writeUTF(file.getName());
        dataOutputStream.flush();

        // Send file size to server
        dataOutputStream.writeLong(file.length());
        dataOutputStream.flush();

        // Send file to server in chunks of 4 * 1024 bytes at a time
        FileInputStream fileInputStream = new FileInputStream(file);
        byte[] buffer = new byte[4096];
        while (fileInputStream.read(buffer) > 0) {
            dataOutputStream.write(buffer);
        }

        // Flush the data output stream to make sure all data is sent
        dataOutputStream.flush();

        // Close the file input stream
        fileInputStream.close();

        System.out.println("File sent successfully!");
    }

    public void listenForMessages() {
        new Thread(() -> {
            try {
                while (!socket.isClosed()) {
                    String incomingMessage = bufferedReader.readLine();
                    if (incomingMessage == null) {
                        System.out.println("Server has closed the connection!");
                        closeEverything();
                        System.exit(0);
                    }

                    if (incomingMessage.equals(":file")) {
                        System.out.println("Receiving file from server...");
                        String fileName = dataInputStream.readUTF();
                        long fileSize = dataInputStream.readLong();
                        receiveFile(fileName, fileSize);
                    } else {
                        System.out.println();
                        System.out.println(incomingMessage);
                        System.out.print(username + "> ");
                    }
                }
            } catch (IOException e) {
                e.printStackTrace();
                closeEverything();
            }
        }).start();
    }

    private void receiveFile(String fileName, long fileSize) {
        try {
            // Create a new file with the name of the file that the client sent with date and time appended
            String dateTime = DateTimeFormatter.ofPattern("yyyy-MM-dd_HH-mm-ss").format(new Date().toInstant());
            String newFileName = fileName.substring(0, fileName.lastIndexOf(".")) + "_" + dateTime
                    + fileName.substring(fileName.lastIndexOf("."));
            File file = new File("src/io/hyper/client/files/" + newFileName);
            FileOutputStream fileOutputStream = new FileOutputStream(file);

            // Create a new byte array to store the file data
            byte[] buffer = new byte[4096];

            // Read the file data from the client and write it to the file output stream
            int readBytes;
            long totalReadBytes = 0;
            while ((readBytes = dataInputStream.read(buffer)) > 0) {
                fileOutputStream.write(buffer, 0, readBytes);
                totalReadBytes += readBytes;

                // If the total number of bytes read is equal to the file size, break the loop
                if (totalReadBytes == fileSize) {
                    break;
                }
            }

            fileOutputStream.close();

            if (totalReadBytes == fileSize && file.createNewFile()) {
                System.out.println("File received successfully!");
            } else {
                System.out.println("File received unsuccessfully!");
            }
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    private void closeEverything() {
        try {
            if (socket != null) {
                socket.close();
            }
            if (bufferedReader != null) {
                bufferedReader.close();
            }
            if (bufferedWriter != null) {
                bufferedWriter.close();
            }
        } catch (IOException e) {
            e.printStackTrace();
        }
    }
}