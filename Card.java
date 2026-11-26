/**
 * Card - a single playing card of the standard 52-card deck.
 *
 * A card is identified by a rank ('A', '2'..'9', 'T', 'J', 'Q', 'K') and a suit
 * ('S', 'H', 'D', 'C'). Its textual form ("AS", "TH", ...) is exactly the
 * two-character code used by the Blackjack Protocol (BP). Instances are immutable.
 */
class Card {
    /** Rank character: 'A', '2'-'9', 'T' (ten), 'J', 'Q' or 'K'. */
    private final char rank;
    /** Suit character: 'S' (spades), 'H' (hearts), 'D' (diamonds) or 'C' (clubs). */
    private final char suit;

    /**
     * Creates a card.
     *
     * @param rank rank character of the card
     * @param suit suit character of the card
     */
    public Card(char rank, char suit) {
        this.rank = rank;
        this.suit = suit;
    }

    /**
     * Returns the Blackjack value of the card.
     * An Ace is returned as 11; the downgrade to 1 when a hand would exceed 21
     * is handled at hand level (see {@link Hand#total()}).
     *
     * @return 11 for an Ace, 10 for T/J/Q/K, the face value for 2-9
     */
    int value() {
        if (rank == 'A') return 11;
        if (rank == 'T' || rank == 'J' || rank == 'Q' || rank == 'K') return 10;
        return rank - '0'; // digit character -> numeric value
    }

    /**
     * Tells whether this card is an Ace (needed for the 11 -> 1 adjustment).
     *
     * @return true if the rank is 'A'
     */
    boolean isAce() { return rank == 'A'; }

    /**
     * Textual form of the card as sent on the network.
     *
     * @return the two-character BP code, e.g. "AS" or "TH"
     */
    @Override
    public String toString() { return "" + rank + suit; }
}