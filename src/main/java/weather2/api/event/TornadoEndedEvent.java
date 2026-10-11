package weather2.api.event;

import cpw.mods.fml.common.eventhandler.Event;
import weather2.api.TornadoSnapshot;

public final class TornadoEndedEvent extends Event {

    private final TornadoSnapshot snapshot;
    private final TornadoEndReason reason;

    public TornadoEndedEvent(TornadoSnapshot snapshot, TornadoEndReason reason) {
        this.snapshot = snapshot;
        this.reason = reason;
    }

    public TornadoSnapshot getSnapshot() {
        return this.snapshot;
    }

    public TornadoEndReason getReason() {
        return this.reason;
    }
}
