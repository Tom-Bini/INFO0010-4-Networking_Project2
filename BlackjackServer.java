import java.io.*;
import java.net.*;

/**
 * BlackjackServer - entry point of the server ("java BlackjackServer").
 *
 * The main thread only accepts TCP connections. Each accepted connection is
 * handed to a new {@link GameSession} thread, which owns the whole game of
 * that client (deck, bankroll, hands). A slow or misbehaving client can thus
 * only block its own thread, never the acceptance of new clients.
 */
public class BlackjackServer {
    /**
     * Opens the listening socket and loops forever on accept().
     * The program is stopped with CTRL+C (no shutdown hook is used).
     *
     * @param args unused (the program is launched without argument)
     */
    public static void main(String[] args) {
        int port = 2244; // 2xxx, where xxx = last three digits of the student ID
        int i = 0; // sequence number given to each client (used in log messages)
        try (ServerSocket ss = new ServerSocket(port)) {
            System.out.println("Server listening on port " + port);
            while (true) {
                Socket s = ss.accept(); // blocks until a client connects
                new GameSession(s, i).start(); // one thread per connection
                System.out.println("The client " + i + " join the server");
                i++;
            }
        } catch (IOException e) {
            System.err.println("Error : " + e);
        }
    }
}