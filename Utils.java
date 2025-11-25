import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

public class Utils {
    private static final DateTimeFormatter fmt = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    public static String timestamp() {
        return LocalDateTime.now().format(fmt);
    }

    // Build server -> client protocol strings
    public static String buildBroadcastMessage(Message m) {
        // Format: MESSAGE::<timestamp>::<sender>::<content>
        return "MESSAGE::" + m.getTimestamp() + "::" + m.getSender() + "::" + m.getContent();
    }

    public static String buildPrivateMessage(Message m) {
        // Format: PRIVATE::<timestamp>::<sender>::<content>
        return "PRIVATE::" + m.getTimestamp() + "::" + m.getSender() + "::" + m.getContent();
    }

    public static String buildSystem(String content) {
        return "SYSTEM::" + content;
    }

    public static String buildUserList(String[] users) {
        // Format: USERLIST::user1,user2,user3
        return "USERLIST::" + String.join(",", users);
    }
}
