import java.util.*;

/**
 * Hand - the cards held by one participant (the player or the dealer) in the
 * current round. It draws from the session's {@link Deck}, computes the best
 * Blackjack total and formats itself as a BP line ("PLAYER AS KH 21").
 */
public class Hand {
    /** Cards of the hand, in the order they were drawn. */
    private ArrayList<Card> hand = new ArrayList<>();
    /** Number of cards currently in the hand. */
    private int nbrCard = 0;
    /** Deck the cards are drawn from. */
    private Deck deck;

    /**
     * Creates a hand and deals its two initial cards from the deck.
     * The caller guarantees at least two cards are available (the session
     * only starts a round when 4 or more cards remain).
     *
     * @param deck the deck to draw from
     */
    public Hand(Deck deck) {
        this.deck = deck;
        this.draw();
        this.draw();
    }

    /**
     * Draws one card from the deck into this hand.
     *
     * @return true if a card was drawn, false if the deck was empty
     */
    public boolean draw() {
        if (deck.numberCard() > 0) {
            Card card = deck.drawCard();
            hand.add(card);
            nbrCard++;
            return true;
        } else {
            return false;
        }
    }

    /**
     * Computes the best total of the hand: every Ace counts 11, then Aces are
     * downgraded to 1, one at a time, while the total exceeds 21.
     *
     * @return the best achievable total of the hand
     */
    public int total() {
        int nbrAce = 0;
        int total = 0;
        for (int i = 0; i < nbrCard; i++) {
            Card card = hand.get(i);
            if (card.isAce()) nbrAce++;
            total += card.value();
        }
        // Soft hand -> hard hand: turn an 11 into a 1 as long as we are busted.
        while (total > 21 && nbrAce > 0) {
            total -= 10;
            nbrAce--;
        }
        return total;
    }

    /**
     * Tells whether the hand exceeds 21.
     * (Not called by the current code: busts are detected with {@link #total()}.)
     *
     * @return true if the best total is above 21
     */
    public boolean isBust() {
        return this.total() > 21;
    }

    /**
     * Tells whether the hand is a natural Blackjack: exactly two cards
     * totalling 21 (an Ace and a ten-value card).
     *
     * @return true for a natural Blackjack
     */
    public boolean isNatural() {
        return (nbrCard == 2 && this.total() == 21);
    }

    /**
     * Formats the hand as a BP line.
     * <ul>
     *   <li>hideSecond == false : "WHO card1 card2 ... total"</li>
     *   <li>hideSecond == true  : "WHO card1 ??" (second card face down; used
     *       for the dealer while the round is in progress)</li>
     * </ul>
     *
     * @param who        label of the hand, "PLAYER" or "DEALER"
     * @param hideSecond whether the second card must stay hidden
     * @return the formatted line, without CRLF
     */
    public String format(String who, boolean hideSecond) {
        if (hideSecond) {
            return "" + who + " " + hand.get(0).toString() + " ??";
        } else {
            String format = "" + who + " ";
            for (int i = 0; i < nbrCard; i++) {
                format += hand.get(i).toString() + " ";
            }
            format += this.total();
            return format;
        }
    }
}