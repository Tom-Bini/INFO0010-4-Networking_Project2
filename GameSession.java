import java.io.*;
import java.net.*;
import java.util.ArrayList;

/**
 * GameSession - one client connection = one thread = one game of Blackjack.
 *
 * A session is created by {@link BlackjackServer} for every accepted socket.
 * It owns ALL the state of the game (deck, bankroll, hands, current bet), so
 * threads never share mutable data and no synchronization is required.
 *
 * Life cycle of run():
 *   1. set a 30 s read timeout on the socket;
 *   2. loop: read one complete request (lines up to the blank line),
 *      validate it, dispatch it to a handler, write the response;
 *   3. leave the loop on QUIT, end of game, client disconnection or error,
 *      and always close the socket in the finally block.
 */
public class GameSession extends Thread {
    /** Socket of the client; closed when the session ends. */
    private final Socket socket;
    /** Text reader on the socket; readLine() takes care of CRLF line splitting. */
    private BufferedReader in;
    /** Raw byte output of the socket; every response is written then flushed. */
    private OutputStream out;
    /** Main-loop flag; set to false to end the session. */
    private boolean running = true;
    /** Sequence number of the client (only used in log messages). */
    int identifiant;

    /** Chips of the player; starts at 100. The bet is deducted when a round starts. */
    private int bankroll = 100;
    /** True between a successful BET and the settlement of the round. */
    private boolean roundInProgress = false;
    /** The 52-card deck of this session (never reshuffled). */
    private Deck deck;
    /** Cards of the player in the current round. */
    private Hand clientHand;
    /** Cards of the dealer (the house) in the current round. */
    private Hand dealerHand;
    /** Bet of the current round. */
    long amount;

    /**
     * Prepares a session: builds and shuffles the deck for this client.
     * The thread itself is started by the caller with start().
     *
     * @param socket connected client socket
     * @param i      sequence number of the client
     */
    public GameSession(Socket socket, int i) {
        this.socket = socket;
        deck = new Deck();
        identifiant = i;
    }

    /**
     * Thread body: request/response loop for one client.
     *
     * Message framing: a BP request is a set of lines ended by an empty line
     * (CRLF CRLF). The inner loop accumulates lines until the empty line.
     * Because BufferedReader buffers the stream, several requests arriving in
     * one TCP segment, or one request split over several segments, are handled
     * correctly.
     *
     * A request must consist of exactly one line, otherwise "WRONG" is sent.
     * Exceptions: a silent client triggers SocketTimeoutException (30 s), a
     * vanished client triggers SocketException/IOException; in every case the
     * socket is closed and only this thread ends.
     */
    @Override
    public void run() {
        try {
            socket.setSoTimeout(30000); // a client silent for 30 s -> SocketTimeoutException
            in  = new BufferedReader(new InputStreamReader(socket.getInputStream()));
            out = socket.getOutputStream();

            while (running) {
                // --- read one full request: lines until the blank line ---
                ArrayList<String> line = new ArrayList<>();
                boolean noRead = false;
                while (true) {
                    String read = in.readLine();
                    if (read == null) { // end of stream: client closed the connection
                        running = false;
                        noRead = true;
                        break;
                    }
                    if (read.isEmpty()) break; // blank line = end of the request
                    line.add(read);
                }
                if (noRead) break;
                // --- a valid request has exactly one line ---
                if (line.size() != 1) { send("WRONG"); continue; }
                // --- dispatch on the keyword (first word) ---
                String[] parts = line.get(0).split(" ");
                switch (parts[0]) {
                    case "BET"   -> handleBet(parts);
                    case "HIT"   -> handleHit(parts);
                    case "STAND" -> handleStand(parts);
                    case "BANK"  -> handleBank(parts);
                    case "QUIT"  -> handleQuit(parts);
                    default      -> send("WRONG"); // unknown request, connection stays open
                }
            }
        } catch (SocketTimeoutException e) {
            System.err.println("The client " + identifiant + " did not answer in time.");
        } catch (SocketException e) {
            System.err.println("Socket closed");
        } catch (IOException e) {
            System.err.println("Network error: " + e);
        } finally {
            // Always release the connection, whatever happened above.
            try { socket.close(); } catch (IOException e) { }
        }
    }

    /**
     * Sends one BP message: every line followed by CRLF, then an empty line
     * (so the message ends with CRLF CRLF), and flushes the stream so the
     * bytes really leave the program.
     *
     * @param lines the lines of the message, without line terminator
     * @throws IOException if the socket cannot be written
     */
    private void send(String... lines) throws IOException {
        StringBuilder sb = new StringBuilder();
        for (String l : lines) sb.append(l).append("\r\n");
        sb.append("\r\n");
        out.write(sb.toString().getBytes());
        out.flush();
    }

    /**
     * Handles "BET amount": validates the bet, starts a new round and deals.
     *
     * Checks, in this order:
     *   1. the request has exactly two words ("BET" and an amount) -> else "WRONG"
     *      (done first so that parts[1] can never be read out of bounds);
     *   2. amount is a strictly positive integer without leading zero -> else "INVALID BET";
     *   3. amount fits in a long and does not exceed the bankroll -> else "INSUFFICIENT FUNDS";
     *   4. no round is in progress -> else "ROUND IN PROGRESS".
     *
     * On success the bet is deducted, two cards are dealt to each side and the
     * hand is returned with the dealer's second card hidden. If the deal gives
     * a natural Blackjack (player and/or dealer) the round is settled at once:
     * BLACKJACK (pays 3:2, rounded down), PUSH 0 (both naturals) or LOSE
     * (dealer natural only), and GAME OVER is appended when the game ends.
     *
     * @param parts the request split on spaces ("BET", amount)
     * @throws IOException if the response cannot be sent
     */
    public void handleBet(String[] parts) throws IOException {
        if (parts.length != 2) {
            send("WRONG");
            return;
        }
        if (!parts[1].matches("[1-9][0-9]*")) { // strictly positive, no leading zero
            send("INVALID BET");
            return;
        }
        ArrayList<String> reply = new ArrayList<>();
        long newAmount;
        try {
            newAmount = Long.parseLong(parts[1]);
        } catch (NumberFormatException e) {
            send("INSUFFICIENT FUNDS"); // number too large for a long: certainly above the bankroll
            return;
        }
        if (newAmount > bankroll) {
            send("INSUFFICIENT FUNDS");
            return;
        }
        if (roundInProgress) { // lowest priority of the BET checks
            send("ROUND IN PROGRESS");
            return;
        }

        // --- the bet is accepted: start the round ---
        amount = newAmount;
        roundInProgress = true;
        bankroll -= amount; // the bet leaves the bankroll now
        clientHand = new Hand(deck);
        dealerHand = new Hand(deck);
        reply.add(clientHand.format("PLAYER", false));

        boolean playerNatural = clientHand.isNatural();
        boolean dealerNatural = dealerHand.isNatural();

        if (playerNatural || dealerNatural) {
            // Round settled on the deal: the dealer's hand is revealed.
            reply.add(dealerHand.format("DEALER", false));
            if (playerNatural && dealerNatural) {
                reply.add("PUSH 0");
                bankroll += amount; // bet returned
            } else if (playerNatural) {
                long gain = amount * 3 / 2; // 3:2, rounded down
                reply.add("BLACKJACK " + gain);
                bankroll += amount + gain; // bet back + winnings
            } else {
                reply.add("LOSE " + amount); // bet already deducted
            }
            roundInProgress = false;
            if (endOfGame()) {
                gameOver(reply);
                return;
            }
        } else {
            // Normal case: the dealer's second card stays hidden.
            reply.add(dealerHand.format("DEALER", true));
        }
        send(reply.toArray(new String[0]));
    }

    /**
     * Handles "HIT": gives one more card to the player.
     *
     * Responses:
     *   - no round in progress              -> "NO ACTIVE ROUND";
     *   - extra argument                    -> "WRONG";
     *   - deck already empty                -> treated as a stand (dealer plays);
     *   - total below 21                    -> the new hand (dealer still hidden);
     *   - total exactly 21                  -> the player's turn ends, the dealer plays;
     *   - total above 21 (bust)             -> dealer revealed without drawing, "LOSE";
     *   - last card of the deck taken without bust -> settled as if the player had stood.
     *
     * @param parts the request split on spaces
     * @throws IOException if the response cannot be sent
     */
    public void handleHit(String[] parts) throws IOException {
        if (!roundInProgress) {
            send("NO ACTIVE ROUND");
            return;
        }
        if (parts.length != 1) {
            send("WRONG");
            return;
        }

        ArrayList<String> reply = new ArrayList<>();
        if (deck.numberCard() < 1) {
            // (empty block: the empty-deck case is handled just below through draw())
        }
        boolean end = clientHand.draw(); // despite its name: true when a card was really drawn
        if (!end) {
            handleStand(null); // no card left: behave as a stand
            return;
        }
        int total = clientHand.total();
        if (total < 21) {
            if (deck.numberCard() <= 0) { // that was the last card: settle as a stand
                handleNoCard();
                return;
            }
            send(clientHand.format("PLAYER", false), dealerHand.format("DEALER", true));
            return;
        }
        if (total == 21) {
            if (deck.numberCard() <= 0) {
                handleNoCard();
                return;
            }
            handleStand(null); // 21 reached: the turn ends automatically
        }
        if (total > 21) {
            // Bust: the dealer's hidden card is revealed but the dealer draws nothing.
            reply.add(clientHand.format("PLAYER", false));
            reply.add(dealerHand.format("DEALER", false));
            reply.add("LOSE " + amount);
            roundInProgress = false;
            if (endOfGame()) {
                gameOver(reply);
                return;
            }
            send(reply.toArray(new String[0]));
        }
    }

    /**
     * Handles "STAND" (also called internally with null after a 21 or when the
     * deck runs out): the dealer reveals its hidden card and draws while its
     * total is 16 or less (and cards remain), then the round is settled.
     *
     * @param parts the request split on spaces, or null when called internally
     * @throws IOException if the response cannot be sent
     */
    public void handleStand(String[] parts) throws IOException {
        if (!roundInProgress) {
            send("NO ACTIVE ROUND");
            return;
        }
        if (parts != null) {
            if (parts.length != 1) {
                send("WRONG");
                return;
            }
        }

        ArrayList<String> reply = new ArrayList<>();

        // Fixed dealer strategy: hit on <= 16, stand on >= 17; stop if the deck is empty.
        while (dealerHand.total() <= 16 && deck.numberCard() > 0) {
            dealerHand.draw();
        }

        reply.add(clientHand.format("PLAYER", false));
        reply.add(dealerHand.format("DEALER", false));
        roundStatus(reply);
    }

    /**
     * Handles "BANK": reports the current bankroll (the bet of a round in
     * progress is already deducted). Allowed at any time.
     *
     * @param parts the request split on spaces
     * @throws IOException if the response cannot be sent
     */
    public void handleBank(String[] parts) throws IOException {
        if (parts.length != 1) {
            send("WRONG");
            return;
        }
        send("BANKROLL "+ bankroll);
    }

    /**
     * Handles "QUIT": ends the session at the client's request. Allowed at any
     * time, even in the middle of a round (the bet is simply lost with the session).
     *
     * "QUIT" must be alone on its line: a request such as "QUIT now" is not a
     * valid request, so it gets "WRONG" and the session goes on (same rule as for
     * HIT, STAND and BANK).
     *
     * For a valid QUIT no reply is sent: the loop of run() stops because
     * running is false, and the socket is closed by its finally block.
     *
     * @param parts the request split on spaces
     * @throws IOException if the "WRONG" response cannot be sent
     */
    public void handleQuit(String[] parts) throws IOException {
        if (parts.length != 1) {
            send("WRONG");
            return;
        }
        running = false;
    }

    /**
     * Checks, right after a round has been settled, whether the session must end.
     *
     * @return true if the bankroll is zero or fewer than 4 cards remain
     *         (a new round needs 2 cards for each side)
     */
    private boolean endOfGame() {
        if (bankroll <= 0) return true;
        if (deck.numberCard() < 4) return true;
        return false;
    }

    /**
     * Completes the settlement message with the GameOver block ("GAME OVER",
     * then "BANKROLL amount"), sends it and stops the session loop; the socket
     * is closed by run() afterwards. The game ends when the bankroll is zero or
     * fewer than 4 cards remain in the deck (see endOfGame()).
     *
     * @param reply lines already built for this response (hands + round status)
     * @throws IOException if the response cannot be sent
     */
    private void gameOver(ArrayList<String> reply) throws IOException {
        reply.add("GAME OVER");
        reply.add("BANKROLL " + bankroll);
        running = false;
        send(reply.toArray(new String[0]));
    }

    /**
     * Settles the round once the dealer's hand is final: compares the totals,
     * appends the RoundStatus line (WIN / PUSH 0 / LOSE), updates the bankroll,
     * then either ends the game (GAME OVER) or sends the response.
     *
     * Bankroll bookkeeping (the bet was deducted at BET time):
     *   WIN  -> +2*amount (bet back + 1:1 gain);  PUSH -> +amount;  LOSE -> +0.
     *
     * @param reply lines already built (PLAYER and DEALER hands, possibly an info line)
     * @throws IOException if the response cannot be sent
     */
    private void roundStatus(ArrayList<String> reply) throws IOException {
        if (dealerHand.total() > 21) {
            // Dealer busts: the player wins.
            reply.add("WIN " + amount);
            bankroll += 2*amount;
            roundInProgress = false;
            if (endOfGame()) {
                gameOver(reply);
                return;
            }
            send(reply.toArray(new String[0]));
        } else if (clientHand.total() > dealerHand.total() && clientHand.total() <= 21) {
            // Higher total than the dealer without busting.
            reply.add("WIN " + amount);
            bankroll += 2*amount;
            roundInProgress = false;
            if (endOfGame()) {
                gameOver(reply);
                return;
            }
            send(reply.toArray(new String[0]));
        } else if (clientHand.total() == dealerHand.total()) {
            // Same total: push, the bet is returned.
            reply.add("PUSH " + 0 );
            roundInProgress = false;
            bankroll += amount;
            if (endOfGame()) {
                gameOver(reply);
                return;
            }
            send(reply.toArray(new String[0]));
        } else {
            // Dealer higher: the bet is lost (already deducted).
            reply.add("LOSE " + amount);
            roundInProgress = false;
            if (endOfGame()) {
                gameOver(reply);
                return;
            }
            send(reply.toArray(new String[0]));
        }
    }

    /**
     * Settles a round that stops because the player's hit took the last card
     * of the deck without busting: both hands are shown and the round is settled
     * as if a stand had followed (the dealer cannot draw anything anyway).
     *
     * @throws IOException if the response cannot be sent
     */
    private void handleNoCard() throws IOException {
        ArrayList<String> reply = new ArrayList<>();
        reply.add(clientHand.format("PLAYER", false));
        reply.add(dealerHand.format("DEALER", false));
        roundStatus(reply);
    }
}