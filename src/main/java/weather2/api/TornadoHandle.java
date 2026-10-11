package weather2.api;

public final class TornadoHandle {

    private final int dimensionId;
    private final long id;

    public TornadoHandle(int dimensionId, long id) {
        this.dimensionId = dimensionId;
        this.id = id;
    }

    public int getDimensionId() {
        return this.dimensionId;
    }

    public long getId() {
        return this.id;
    }

    @Override
    public boolean equals(Object other) {
        if (this == other) return true;
        if (!(other instanceof TornadoHandle)) return false;
        TornadoHandle handle = (TornadoHandle) other;
        return this.dimensionId == handle.dimensionId && this.id == handle.id;
    }

    @Override
    public int hashCode() {
        return 31 * this.dimensionId + (int) (this.id ^ (this.id >>> 32));
    }
}
