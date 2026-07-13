package ir.sharif.pvz.model;

/**
 * One in-game news entry shown in the news menu (e.g. a plant or level unlock).
 */
public class NewsItem {

    private final String text;
    private boolean read;

    public NewsItem(String text) {
        this.text = text;
    }

    public String getText() {
        return text;
    }

    public boolean isRead() {
        return read;
    }

    public void markRead() {
        this.read = true;
    }
}
