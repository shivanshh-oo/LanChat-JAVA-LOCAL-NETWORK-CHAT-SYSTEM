





































































































import java.io.*;
import java.net.*;
import java.util.concurrent.*;

public class ChatServer {
    private final int port;
    private ServerSocket serverSocket;
    private final ConcurrentMap<String, ClientHandler> clients = new ConcurrentHashMap<>();
    private volatile boolean running = true;
    private final File logFile = new File("chat_log.txt");
    private BufferedWriter logWriter;

    public ChatServer(int port) {
        this.port = port;
    }

    public void start() {
        try {
            serverSocket = new ServerSocket(port);
            logWriter = new BufferedWriter(new FileWriter(logFile, true)); // append mode
            System.out.println("Server started on port " + port);
            startAdminConsole();

            while (running) {
                Socket clientSocket = serverSocket.accept();
                // New connection -> create handler (handler will manage username handshake)
                ClientHandler handler = new ClientHandler(clientSocket, this);
                new Thread(handler).start();
            }
        } catch (IOException e) {
            if (running) {
                System.err.println("Server error: " + e.getMessage());
            }
        } finally {
            shutdown();
        }
    }

    // Called by ClientHandler after a successful username registration
    public boolean addClient(String username, ClientHandler handler) {
        ClientHandler previous = clients.putIfAbsent(username, handler);
        return previous == null;
    }

    public void removeClient(String username) {
        clients.remove(username);
    }

    public String[] getUserList() {
        return clients.keySet().toArray(new String[0]);
    }

    public void broadcast(Message m) {
        String out = Utils.buildBroadcastMessage(m);
        for (ClientHandler ch : clients.values()) {
            ch.sendRaw(out);
        }
        log(m.toString());
    }

    public void sendPrivate(Message m, String recipient) {
        ClientHandler ch = clients.get(recipient);
        if (ch != null) {
            ch.sendRaw(Utils.buildPrivateMessage(m));
            // also log private message (indicate recipient)
            log("[PRIVATE] " + m.getTimestamp() + " " + m.getSender() + " -> " + recipient + ": " + m.getContent());
        } else {
            // If recipient not found, send a system message back to sender
            ClientHandler senderHandler = clients.get(m.getSender());
            if (senderHandler != null) {
                senderHandler.sendRaw(Utils.buildSystem("User '" + recipient + "' not found or offline."));
            }
        }
    }

    public void sendUserListTo(ClientHandler handler) {
        String[] users = getUserList();
        handler.sendRaw(Utils.buildUserList(users));
    }

    public void log(String line) {
        synchronized (logWriter) {
            try {
                logWriter.write(line);
                logWriter.newLine();
                logWriter.flush();
            } catch (IOException e) {
                System.err.println("Logging error: " + e.getMessage());
            }
        }
    }

    // Server admin console for commands like /kick and /shutdown
    private void startAdminConsole() {
        Thread admin = new Thread(() -> {
            BufferedReader console = new BufferedReader(new InputStreamReader(System.in));
            String cmd;
            try {
                while (running && (cmd = console.readLine()) != null) {
                    if (cmd.trim().isEmpty()) continue;
                    if (cmd.equalsIgnoreCase("/shutdown")) {
                        System.out.println("Shutting down server...");
                        broadcast(new Message("SERVER", null, "Server is shutting down.", MessageType.SYSTEM, Utils.timestamp()));
                        shutdown();
                    } else if (cmd.startsWith("/kick ")) {
                        String[] parts = cmd.split("\\s+", 2);
                        if (parts.length >= 2) {
                            String user = parts[1].trim();
                            kickUser(user);
                        } else {
                            System.out.println("Usage: /kick <username>");
                        }
                    } else if (cmd.equalsIgnoreCase("/list")) {
                        System.out.println("Active users: " + String.join(", ", getUserList()));
                    } else if (cmd.equalsIgnoreCase("/help")) {
                        System.out.println("Admin commands: /kick <user>, /list, /shutdown, /help");
                    } else {
                        System.out.println("Unknown admin command. Type /help");
                    }
                }
            } catch (IOException e) {
                if (running) System.err.println("Admin console error: " + e.getMessage());
            }
        }, "AdminConsole");
        admin.setDaemon(true);
        admin.start();
    }

    public void kickUser(String username) {
        ClientHandler ch = clients.get(username);
        if (ch != null) {
            ch.sendRaw(Utils.buildSystem("You have been kicked by server admin."));
            ch.kickAndClose();
            System.out.println("Kicked user: " + username);
            log("KICKED: " + username + " at " + Utils.timestamp());
        } else {
            System.out.println("User not found: " + username);
        }
    }

    public void shutdown() {
        running = false;
        try {
            // Close server socket to break accept()
            if (serverSocket != null && !serverSocket.isClosed()) {
                serverSocket.close();
            }
        } catch (IOException ignored) { }

        // Disconnect all clients
        for (ClientHandler ch : clients.values()) {
            ch.sendRaw(Utils.buildSystem("Server is shutting down."));
            ch.kickAndClose();
        }
        clients.clear();

        // close log
        try {
            if (logWriter != null) logWriter.close();
        } catch (IOException ignored) { }

        System.out.println("Server terminated.");
        // exit JVM only if started as a standalone server (safe guard)
        System.exit(0);
    }

    public static void main(String[] args) {
        int port = 12345;
        if (args.length >= 1) {
            try { port = Integer.parseInt(args[0]); } catch (NumberFormatException ignored) {}
        }
        ChatServer server = new ChatServer(port);
        server.start();
    }
}
