import java.time.LocalDateTime;

public class Message {
    private final String sender;
    private final String recipient; // null for broadcast/system
    private final String content;
    private final MessageType type;
    private final String timestamp;

    public Message(String sender, String recipient, String content, MessageType type, String timestamp) {
        this.sender = sender;
        this.recipient = recipient;
        this.content = content;
        this.type = type;
        this.timestamp = timestamp;
    }

    public String getSender() { return sender; }
    public String getRecipient() { return recipient; }
    public String getContent() { return content; }
    public MessageType getType() { return type; }
    public String getTimestamp() { return timestamp; }

    @Override
    public String toString() {
        return "[" + timestamp + "] " + (sender != null ? sender : "SYSTEM") + ": " + content;
    }
}
