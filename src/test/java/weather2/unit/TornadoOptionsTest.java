package weather2.unit;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import org.junit.Test;

import weather2.api.TornadoOptions;

public class TornadoOptionsTest {

    @Test
    public void visualOptionsDisablePulling() {
        TornadoOptions options = TornadoOptions.visual(2, 2400);

        assertTrue(options.getPullPlayers() == false);
        assertFalse(options.getPullLivingEntities());
    }

    @Test
    public void rejectsInvalidIntensityAndDuration() {
        assertInvalid(-1, 1);
        assertInvalid(6, 1);
        assertInvalid(1, 0);
        assertInvalid(1, -1);
    }

    private static void assertInvalid(int intensity, int duration) {
        try {
            new TornadoOptions(intensity, duration, false, false);
            fail("Expected invalid tornado options to be rejected");
        } catch (IllegalArgumentException expected) {
            // Expected validation at the public options boundary.
        }
    }
}
