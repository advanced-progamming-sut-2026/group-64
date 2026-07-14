package ir.sharif.pvz.model;

/**
 * One of the twenty greenhouse slots. A pot is a permanent slot: once
 * unlocked it stays unlocked; a flower occupies it until collected.
 */
public class GreenhousePot {

    private boolean unlocked;
    private String plantType;
    private long readyAtMillis;

    public GreenhousePot(boolean unlocked) {
        this.unlocked = unlocked;
    }

    public boolean isUnlocked() {
        return unlocked;
    }

    public void unlock() {
        this.unlocked = true;
    }

    public boolean isEmpty() {
        return plantType == null;
    }

    public String getPlantType() {
        return plantType;
    }

    public long getReadyAtMillis() {
        return readyAtMillis;
    }

    public boolean isReady(long nowMillis) {
        return plantType != null && nowMillis >= readyAtMillis;
    }

    public void plant(String type, long readyAtMillis) {
        this.plantType = type;
        this.readyAtMillis = readyAtMillis;
    }

    public void finishNow(long nowMillis) {
        this.readyAtMillis = nowMillis;
    }

    public void clear() {
        this.plantType = null;
        this.readyAtMillis = 0;
    }
}
