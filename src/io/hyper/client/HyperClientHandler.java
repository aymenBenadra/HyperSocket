package io.hyper.client;

import java.io.*;
import java.net.Socket;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Date;

public class HyperClientHandler implements Runnable {
    public static ArrayList<HyperClientHandler> clientHandlers = new ArrayList<>();
    private Socket socket;
    private BufferedReader bufferedReader;
    private BufferedWriter bufferedWriter;
    private DataInputStream dataInputStream;
    private DataOutputStream dataOutputStream;
    private String clientUsername;

    public HyperClientHandler(Socket socket) {
        try {
            this.socket = socket;

            // BufferedReader and BufferedWriter are used for text messages
            this.bufferedReader = new BufferedReader(new InputStreamReader(socket.getInputStream()));
            this.bufferedWriter = new BufferedWriter(new OutputStreamWriter(socket.getOutputStream()));

            // DataInputStream and DataOutputStream are used for files
            this.dataInputStream = new DataInputStream(socket.getInputStream());
            this.dataOutputStream = new DataOutputStream(socket.getOutputStream());

            this.clientUsername = bufferedReader.readLine();
            clientHandlers.add(this);

            this.broadcastMessage("Server: " + clientUsername + " has entered the chat!");
        } catch (IOException e) {
            closeEverything();
        }
    }

    @Override
    public void run() {
        try {
            while (!socket.isClosed()) {
                String clientMessage = bufferedReader.readLine();

                if (clientMessage == null) {
                    closeEverything();
                    break;
                }

                // If the message is a command, execute it
                if (clientMessage.startsWith(":")) {
                    switch (clientMessage.split(" ")[0]) {
                        case ":quit" -> closeEverything();
                        case ":list" -> this.broadcastMessage("Server: Active users: " + String.join(", ", clientHandlers.stream()
                                    .map(clientHandler -> "@" + clientHandler.clientUsername)
                                    .toList()), this.clientUsername);
                        case ":file" -> {
                            String fileName = dataInputStream.readUTF();
                            long fileSize = dataInputStream.readLong();
                            this.broadcastFile(this.receiveFile(fileName, fileSize));
                        }
                        default -> this.broadcastMessage("Server: Invalid command!", this.clientUsername);
                    }
                } else if (clientMessage.startsWith("@")) {
                    String[] splitMessage = clientMessage.split(" ", 2);
                    String recipientUsername = splitMessage[0].substring(1);
                    String message = splitMessage[1];
                    this.broadcastMessage(clientUsername + ": " + message, recipientUsername);
                } else {
                    this.broadcastMessage(clientUsername + ": " + clientMessage);
                }
            }
        } catch (IOException e) {
            closeEverything();
        }
    }

    private File receiveFile(String fileName, long fileSize) {
        try {
            // Create a new file with the name of the file that the client sent with date and time appended
            String dateTime = DateTimeFormatter.ofPattern("yyyy-MM-dd_HH-mm-ss").format(new Date().toInstant());
            String newFileName = fileName.substring(0, fileName.lastIndexOf(".")) + "_" + dateTime
                    + fileName.substring(fileName.lastIndexOf("."));
            File file = new File(newFileName);

            // Create a new file output stream to write the file to the server
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

            // Close the file output stream
            fileOutputStream.close();

            // Broadcast the message to all clients
            this.broadcastMessage("Server: " + clientUsername + " has sent a file: " + fileName);

            return file;
        } catch (IOException e) {
            e.printStackTrace();
            return null;
        }
    }

    public void broadcastMessage(String message) throws IOException {
        for (HyperClientHandler clientHandler : clientHandlers) {
            if (clientHandler.clientUsername.equals(this.clientUsername)) {
                continue;
            }
            clientHandler.bufferedWriter.write(message);
            clientHandler.bufferedWriter.newLine();
            clientHandler.bufferedWriter.flush();
        }
    }

    public void broadcastMessage(String message, String username) throws IOException {
        for (HyperClientHandler clientHandler : clientHandlers) {
            if (clientHandler.clientUsername.equals(username)) {
                clientHandler.bufferedWriter.write(message);
                clientHandler.bufferedWriter.newLine();
                clientHandler.bufferedWriter.flush();
                return;
            }
        }
        broadcastMessage("Server: User not found! (try using :users to see active users).", this.clientUsername);
    }

    public void broadcastFile(File file) throws IOException {
        for (HyperClientHandler clientHandler : clientHandlers) {
            if (clientHandler.clientUsername.equals(this.clientUsername)) {
                continue;
            }

            // Check if file is not null
            if (file == null) {
                clientHandler.bufferedWriter.write("Server: File not found!");
                clientHandler.bufferedWriter.newLine();
                clientHandler.bufferedWriter.flush();
                break;
            }

            // Send file flag
            clientHandler.bufferedWriter.write(":file");
            clientHandler.bufferedWriter.newLine();
            clientHandler.bufferedWriter.flush();

            // Send the file name and size to the client
            clientHandler.dataOutputStream.writeUTF(file.getName());
            clientHandler.dataOutputStream.flush();

            clientHandler.dataOutputStream.writeLong(file.length());
            clientHandler.dataOutputStream.flush();

            // Create a new FileInputStream to read the file from the server
            FileInputStream fileInputStream = new FileInputStream(file);

            // Create a new byte array to store the file data
            byte[] buffer = new byte[4096];
            int readBytes;
            long totalReadBytes = 0;
            while ((readBytes = fileInputStream.read(buffer)) != -1) {
                clientHandler.dataOutputStream.write(buffer, 0, readBytes);
                totalReadBytes += readBytes;

                // If the total number of bytes read is equal to the file size, break the loop
                if (totalReadBytes == file.length()) {
                    break;
                }
            }

            // Flush the data output stream
            clientHandler.dataOutputStream.flush();

            // Close the file input stream
            fileInputStream.close();

            // Broadcast the message to all clients
            if (totalReadBytes == file.length()) {
                clientHandler.bufferedWriter.write("Server: File sent successfully!");
            } else {
                clientHandler.bufferedWriter.write("Server: File not sent!");
            }
            clientHandler.bufferedWriter.newLine();
            clientHandler.bufferedWriter.flush();
        }
    }

    public void closeEverything() {
        try {
            removeClientHandler();
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

    public void removeClientHandler() throws IOException {
        clientHandlers.remove(this);
        broadcastMessage("Server: " + this.clientUsername + " has left the chat!");
    }
}
