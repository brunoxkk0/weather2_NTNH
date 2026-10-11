package weather2.api.event;

import cpw.mods.fml.common.eventhandler.Cancelable;
import cpw.mods.fml.common.eventhandler.Event;
import weather2.api.TornadoSnapshot;

@Cancelable
public final class TornadoFormEvent extends Event {

    private final TornadoSnapshot snapshot;
    private final TornadoOrigin origin;

    public TornadoFormEvent(TornadoSnapshot snapshot, TornadoOrigin origin) {
        this.snapshot = snapshot;
        this.origin = origin;
    }

    public TornadoSnapshot getSnapshot() {
        return this.snapshot;
    }

    public TornadoOrigin getOrigin() {
        return this.origin;
    }

    @Override
    public boolean isCancelable() {
        return true;
    }
}
