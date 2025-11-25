import java.io.*;
import java.net.*;

public class ChatClient {
    private final String host;
    private final int port;
    private Socket socket;
    private PrintWriter out;
    private BufferedReader in;
    private BufferedReader console;

    public ChatClient(String host, int port) {
        this.host = host;
        this.port = port;
    }

    public void start() {
        try {
            socket = new Socket(host, port);
            out = new PrintWriter(socket.getOutputStream(), true);
            in  = new BufferedReader(new InputStreamReader(socket.getInputStream()));
            console = new BufferedReader(new InputStreamReader(System.in));

            // Handshake for username
            String serverResp;
            String username;
            while (true) {
                System.out.print("Enter username: ");
                username = console.readLine();
                if (username == null) return;
                username = username.trim();
                if (username.isEmpty()) continue;
                out.println("JOIN::" + username);
                serverResp = in.readLine();
                if (serverResp == null) {
                    System.out.println("Server closed connection during handshake.");
                    return;
                } else if (serverResp.equals("USERNAME_ACCEPTED")) {
                    System.out.println("Username accepted. You can now chat. Type /help for client commands.");
                    break;
                } else if (serverResp.startsWith("USERNAME_REJECTED::")) {
                    System.out.println("Username rejected: " + serverResp.substring("USERNAME_REJECTED::".length()));
                } else {
                    System.out.println("Unexpected response: " + serverResp);
                }
            }

            // Start thread to listen to server messages
            Thread reader = new Thread(this::readLoop, "ServerReader");
            reader.start();

            // Main loop: read console and send messages
            String line;
            while ((line = console.readLine()) != null) {
                line = line.trim();
                if (line.isEmpty()) continue;
                if (line.equalsIgnoreCase("/exit")) {
                    out.println("EXIT::");
                    break; // exit loop -> cleanup
                } else if (line.equalsIgnoreCase("/list")) {
                    out.println("LIST::");
                } else if (line.startsWith("/w ")) {
                    // Format: /w username message...
                    String rest = line.substring(3).trim();
                    int idx = rest.indexOf(' ');
                    if (idx >= 0) {
                        String recipient = rest.substring(0, idx).trim();
                        String message = rest.substring(idx + 1).trim();
                        out.println("PMSG::" + recipient + "::" + message);
                    } else {
                        System.out.println("Usage: /w <username> <message>");
                    }
                } else if (line.equalsIgnoreCase("/help")) {
                    System.out.println("Client commands:");
                    System.out.println("  /w <user> <message>  -> private message");
                    System.out.println("  /list                -> show active users");
                    System.out.println("  /exit                -> quit");
                } else {
                    // Broadcast
                    out.println("MSG::" + line);
                }
            }

            // cleanup
            close();
        } catch (IOException e) {
            System.err.println("Connection error: " + e.getMessage());
        }
    }

    private void readLoop() {
        try {
            String inLine;
            while ((inLine = in.readLine()) != null) {
                // Parse protocol messages from server
                if (inLine.startsWith("SYSTEM::")) {
                    System.out.println("[SYSTEM] " + inLine.substring(8));
                } else if (inLine.startsWith("MESSAGE::")) {
                    // MESSAGE::<timestamp>::<sender>::<content>
                    String[] parts = inLine.split("::", 4);
                    if (parts.length == 4) {
                        System.out.println("[" + parts[1] + "] " + parts[2] + ": " + parts[3]);
                    } else {
                        System.out.println("Malformed MESSAGE: " + inLine);
                    }
                } else if (inLine.startsWith("PRIVATE::")) {
                    // PRIVATE::<timestamp>::<sender>::<content>
                    String[] parts = inLine.split("::", 4);
                    if (parts.length == 4) {
                        System.out.println("[" + parts[1] + "] (private) " + parts[2] + ": " + parts[3]);
                    } else {
                        System.out.println("Malformed PRIVATE: " + inLine);
                    }
                } else if (inLine.startsWith("USERLIST::")) {
                    String list = inLine.substring("USERLIST::".length());
                    if (list.isEmpty()) {
                        System.out.println("Active users: (none)");
                    } else {
                        System.out.println("Active users: " + list.replace(",", ", "));
                    }
                } else if (inLine.startsWith("USERNAME_REJECTED::")) {
                    System.out.println("Username rejected by server: " + inLine.substring("USERNAME_REJECTED::".length()));
                } else {
                    // fallback
                    System.out.println(inLine);
                }
            }
        } catch (IOException e) {
            // stream closed
        } finally {
            try { close(); } catch (Exception ignored) {}
        }
    }

    private void close() {
        try { if (out != null) out.close(); } catch (Exception ignored) {}
        try { if (in != null) in.close(); } catch (Exception ignored) {}
        try { if (socket != null && !socket.isClosed()) socket.close(); } catch (Exception ignored) {}
        System.out.println("Disconnected.");
        // end JVM for client
        System.exit(0);
    }

    public static void main(String[] args) {
        String host = null;
        int port = 12345;
        if (args.length >= 1) host = args[0];
        if (args.length >= 2) {
            try { port = Integer.parseInt(args[1]); } catch (NumberFormatException ignored) {}
        }
        try {
            BufferedReader rdr = new BufferedReader(new InputStreamReader(System.in));
            if (host == null) {
                System.out.print("Enter server IP (or press ENTER for localhost): ");
                String h = rdr.readLine();
                if (h == null || h.trim().isEmpty()) host = "localhost";
                else host = h.trim();
            }
        } catch (IOException ignored) {
            host = "localhost";
        }

        ChatClient client = new ChatClient(host, port);
        client.start();
    }
}
