import java.io.*;
import java.net.*;
import java.util.*;
import java.util.concurrent.atomic.AtomicLong;

/**
 * BlackjackClient - console client ("java BlackjackClient").
 *
 * It connects to the server, reads commands typed by the user, turns them
 * into BP requests (keyword in upper case, terminated by CRLF CRLF), sends
 * them, then reads and displays the server's response (lines up to the blank
 * line). It does not validate the commands: the server is the authority and
 * answers with the appropriate error message.
 *
 * Inactivity protection: a watchdog thread closes the program if the user
 * types nothing for 30 s, mirroring the server's 30 s timeout.
 */
public class BlackjackClient {
    /** Address of the server (same machine). */
    private static final String HOST = "localhost";
    /** TCP port of the server (2xxx, xxx = last three digits of the student ID). */
    private static final int PORT = 2244;

    /**
     * Main loop: prompt, read a command, send it, display the response.
     * The loop ends on QUIT, on "GAME OVER", on end of keyboard input, when the
     * server closes the connection, or on a network error.
     *
     * @param args unused
     */
    public static void main(String[] args) {
        try (Socket socket = new Socket(HOST, PORT)) {
            socket.setSoTimeout(10000); // max 10 s waiting for a server response

            BufferedReader in = new BufferedReader(new InputStreamReader(socket.getInputStream()));
            OutputStream out = socket.getOutputStream();
            BufferedReader keyboard = new BufferedReader(new InputStreamReader(System.in));

            System.out.println("Connected. You start with 100 chips.");
            printHelp();

            // Time of the last user activity, shared with the watchdog thread.
            AtomicLong lastActionTime = new AtomicLong(System.currentTimeMillis());

            // Watchdog: exits the program after 30 s without keyboard activity.
            Thread watchdog = new Thread(() -> {
                while (true) {
                    try { Thread.sleep(500); } catch (InterruptedException e) {}

                    if (System.currentTimeMillis() - lastActionTime.get() >= 30000) {
                        System.out.println("\nConnection closed by the server (Inactivity timeout).");
                        System.exit(0);
                    }
                }
            });
            watchdog.setDaemon(true); // daemon: does not keep the JVM alive when the game ends
            watchdog.start();

            while (true) {
                lastActionTime.set(System.currentTimeMillis());

                System.out.print("> ");
                String line = keyboard.readLine();

                lastActionTime.set(System.currentTimeMillis());

                if (line == null) break; // end of keyboard input (Ctrl+Z then Enter on Windows)
                line = line.trim();
                if (line.isEmpty()) continue;

                String request = toRequest(line);
                send(out, request);

                if (request.equals("QUIT")) {
                    // the server closes the connection without answering
                    System.out.println("Leaving the table.");
                    System.out.println("Goodbye.");
                    break;
                }

                ArrayList<String> response = readResponse(in);
                if (response == null) {
                    System.out.println("Connection closed by the server.");
                    break;
                }
                for (String l : response) {
                    System.out.println(l);
                }
                if (response.contains("GAME OVER")) break; // the server is about to close the connection
            }
        } catch (ConnectException e) {
            System.err.println("Cannot connect to the server on port " + PORT + " (is it running?)");
        } catch (SocketTimeoutException e) {
            System.err.println("The server did not answer in time.");
        } catch (IOException e) {
            System.err.println("Network error: " + e);
        }
    }

    /**
     * Builds the request from the user's input: the command word is put in
     * upper case ("bet 50" -> "BET 50"); nothing else is validated.
     *
     * @param line text typed by the user (already trimmed, not empty)
     * @return the request, without CRLF
     */
    private static String toRequest(String line) {
        String[] parts = line.split("\\s+", 2);
        String command = parts[0].toUpperCase();
        if (parts.length > 1) return command + " " + parts[1];
        return command;
    }

    /**
     * Sends a request: the text followed by CRLF CRLF (the second CRLF is the
     * blank line that marks the end of the message), then flushes the stream.
     *
     * @param out     output stream of the socket
     * @param request the request, without terminator
     * @throws IOException if the socket cannot be written
     */
    private static void send(OutputStream out, String request) throws IOException {
        out.write((request + "\r\n\r\n").getBytes());
        out.flush();
    }

    /**
     * Reads one response: lines are collected until the empty line that ends
     * the message.
     *
     * @param in reader on the socket
     * @return the lines of the response (without the blank line), or null if
     *         the connection was closed before the end of the message
     * @throws IOException on network error (including read timeout)
     */
    private static ArrayList<String> readResponse(BufferedReader in) throws IOException {
        ArrayList<String> lines = new ArrayList<>();
        while (true) {
            String l = in.readLine();
            if (l == null) return null;
            if (l.isEmpty()) return lines;
            lines.add(l);
        }
    }

    /** Prints the list of commands available to the user. */
    private static void printHelp() {
        System.out.println("Commands:");
        System.out.println("  BET <amount>   place a bet and start a new round");
        System.out.println("  HIT            draw an additional card");
        System.out.println("  STAND          end your turn and let the dealer play");
        System.out.println("  BANK           check your current bankroll");
        System.out.println("  QUIT           leave the table");
    }
}