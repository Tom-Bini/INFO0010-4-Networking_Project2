import java.util.*;

/**
 * Deck - one shuffled 52-card deck, owned by a single game session.
 *
 * The deck is built and shuffled once, when a client connects, and is never
 * reshuffled: every round of the session draws from what remains of it.
 * Since each GameSession owns its own Deck, no state is shared between threads.
 */
public class Deck {
    /** Number of cards still in the deck (kept in step with {@link #cards}). */
    private int numberCard = 52;
    /** The cards that remain to be drawn; the top of the stack is the next card. */
    private Stack<Card> cards;
    /** Temporary list used only to build and shuffle the 52 cards. */
    private ArrayList<Card> all;

    /**
     * Builds the 52 cards (13 ranks x 4 suits), shuffles them with
     * {@link Collections#shuffle(List)} and stacks them ready to be drawn.
     */
    public Deck() {
        String ranks = "A23456789TJQK";
        String suits = "SHDC";
        all = new ArrayList<>();
        for (char s : suits.toCharArray())
            for (char r : ranks.toCharArray()) {
                all.add(new Card(r, s));
            }
        Collections.shuffle(all);
        cards = new Stack<>();
        for (Card c : all) cards.push(c);
    }

    /**
     * Removes and returns the top card of the deck.
     * The caller must make sure the deck is not empty (see {@link #numberCard()}),
     * otherwise the underlying stack throws an EmptyStackException.
     *
     * @return the card drawn
     */
    public Card drawCard() {
        numberCard--;
        return cards.pop();
    }

    /**
     * Number of cards left in the deck.
     *
     * @return remaining cards (52 at creation, 0 when exhausted)
     */
    public int numberCard() {
        return numberCard;
    }
}