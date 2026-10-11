package weather2.api;

public final class TornadoOptions {

    private final int intensity;
    private final int durationTicks;
    private final boolean pullPlayers;
    private final boolean pullLivingEntities;

    public TornadoOptions(int intensity, int durationTicks, boolean pullPlayers, boolean pullLivingEntities) {
        if (intensity < 0 || intensity > 5) {
            throw new IllegalArgumentException("Tornado intensity must be between 0 and 5");
        }
        if (durationTicks <= 0) {
            throw new IllegalArgumentException("Tornado duration must be positive");
        }
        this.intensity = intensity;
        this.durationTicks = durationTicks;
        this.pullPlayers = pullPlayers;
        this.pullLivingEntities = pullLivingEntities;
    }

    public static TornadoOptions visual(int intensity, int durationTicks) {
        return new TornadoOptions(intensity, durationTicks, false, false);
    }

    public int getIntensity() {
        return this.intensity;
    }

    public int getDurationTicks() {
        return this.durationTicks;
    }

    public boolean getPullPlayers() {
        return this.pullPlayers;
    }

    public boolean getPullLivingEntities() {
        return this.pullLivingEntities;
    }
}
