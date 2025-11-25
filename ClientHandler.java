import java.io.*;
import java.net.*;

public class ClientHandler implements Runnable {
    private final Socket socket;
    private final ChatServer server;
    private PrintWriter out;
    private BufferedReader in;
    private String username;
    private volatile boolean active = true;

    public ClientHandler(Socket socket, ChatServer server) {
        this.socket = socket;
        this.server = server;
    }

    public void sendRaw(String s) {
        if (out != null) {
            out.println(s);
        }
    }

    public void kickAndClose() {
        active = false;
        try { socket.close(); } catch (IOException ignored) {}
    }

    @Override
    public void run() {
        try {
            out = new PrintWriter(socket.getOutputStream(), true);
            in  = new BufferedReader(new InputStreamReader(socket.getInputStream()));

            // Handshake - expect: "JOIN::<username>"
            while (active && (username == null)) {
                String line = in.readLine();
                if (line == null) {
                    active = false;
                    break;
                }







































































                
                if (line.startsWith("JOIN::")) {
                    String requested = line.substring(6).trim();
                    if (requested.isEmpty()) {
                        sendRaw("USERNAME_REJECTED::Empty username");
                    } else if (server.addClient(requested, this)) {
                        username = requested;
                        sendRaw("USERNAME_ACCEPTED");
                        // Notify others
                        String ts = Utils.timestamp();
                        Message sys = new Message("SERVER", null, username + " joined the chat.", MessageType.SYSTEM, ts);
                        server.broadcast(sys);
                        server.log("JOIN: " + username + " at " + ts);
                        break;
                    } else {
                        sendRaw("USERNAME_REJECTED::Username already taken");
                    }
                } else {
                    sendRaw("USERNAME_REJECTED::Protocol error; send JOIN::<username>");
                }
            }

            // Main loop - expect protocols: MSG::<text>, PMSG::<recipient>::<text>, LIST::, EXIT::
            String input;
            while (active && (input = in.readLine()) != null) {
                if (input.startsWith("MSG::")) {
                    String content = input.substring(5);
                    Message m = new Message(username, null, content, MessageType.CHAT, Utils.timestamp());
                    server.broadcast(m);
                } else if (input.startsWith("PMSG::")) {
                    // PMSG::<recipient>::<message>
                    String payload = input.substring(6);
                    int sep = payload.indexOf("::");
                    if (sep >= 0) {
                        String recipient = payload.substring(0, sep);
                        String content = payload.substring(sep + 2);
                        Message m = new Message(username, recipient, content, MessageType.PRIVATE, Utils.timestamp());
                        server.sendPrivate(m, recipient);
                        // also inform sender that private sent (optional)
                        sendRaw(Utils.buildSystem("Private message sent to " + recipient));
                    } else {
                        sendRaw(Utils.buildSystem("Invalid private message format. Use: PMSG::recipient::message"));
                    }
                } else if (input.startsWith("LIST::")) {
                    server.sendUserListTo(this);
                } else if (input.startsWith("EXIT::")) {
                    break;
                } else {
                    sendRaw(Utils.buildSystem("Unknown command or wrong protocol. Type normal text to broadcast, or /w user message for private."));
                }
            }

        } catch (IOException e) {
            // Client disconnected with an exception
        } finally {
            cleanup();
        }
    }

    private void cleanup() {
        if (username != null) {
            server.removeClient(username);
            String ts = Utils.timestamp();
            Message sys = new Message("SERVER", null, username + " left the chat.", MessageType.SYSTEM, ts);
            server.broadcast(sys);
            server.log("LEFT: " + username + " at " + ts);
        }
        try { if (in != null) in.close(); } catch (IOException ignored) {}
        if (out != null) out.close();
        try { if (!socket.isClosed()) socket.close(); } catch (IOException ignored) {}
    }
}
