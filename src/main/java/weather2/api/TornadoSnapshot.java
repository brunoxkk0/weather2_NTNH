package weather2.api;

import weather2.weathersystem.storm.StormObject;

public final class TornadoSnapshot {

    private final int dimensionId;
    private final long id;
    private final int type;
    private final int intensity;
    private final double x;
    private final double y;
    private final double z;
    private final int remainingTicks;
    private final boolean pullPlayers;
    private final boolean pullLivingEntities;
    private final boolean controlled;
    private final TornadoHandle handle;

    public TornadoSnapshot(int dimensionId, StormObject storm) {
        this.dimensionId = dimensionId;
        this.id = storm.ID;
        this.handle = new TornadoHandle(dimensionId, storm.ID);
        this.type = storm.stormType;
        this.intensity = Math.max(0, Math.min(5, storm.levelCurIntensityStage - StormObject.STATE_FORMING));
        this.x = storm.pos.xCoord;
        this.y = storm.pos.yCoord;
        this.z = storm.pos.zCoord;
        this.remainingTicks = storm.tornadoRemainingTicks;
        this.pullPlayers = storm.pullPlayers;
        this.pullLivingEntities = storm.pullLivingEntities;
        this.controlled = storm.controlledTornado;
    }

    public int getDimensionId() {
        return this.dimensionId;
    }

    public long getId() {
        return this.id;
    }

    public int getType() {
        return this.type;
    }

    public int getIntensity() {
        return this.intensity;
    }

    public double getX() {
        return this.x;
    }

    public double getY() {
        return this.y;
    }

    public double getZ() {
        return this.z;
    }

    public int getRemainingTicks() {
        return this.remainingTicks;
    }

    public TornadoHandle getHandle() {
        return this.handle;
    }

    public boolean isWaterBased() {
        return this.type == StormObject.TYPE_WATER;
    }

    public boolean isPullPlayers() {
        return this.pullPlayers;
    }

    public boolean isPullLivingEntities() {
        return this.pullLivingEntities;
    }

    public boolean pullsPlayers() {
        return this.pullPlayers;
    }

    public boolean pullsLivingEntities() {
        return this.pullLivingEntities;
    }

    public boolean isControlled() {
        return this.controlled;
    }
}
