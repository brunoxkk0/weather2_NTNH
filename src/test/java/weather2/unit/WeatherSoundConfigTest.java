package weather2.unit;

import static org.junit.Assert.assertEquals;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import weather2.config.ConfigMisc;

public class WeatherSoundConfigTest {

    private double oldTornadoScale;
    private double oldSirenScale;
    private double oldThunderScale;

    @Before
    public void saveValues() {
        this.oldTornadoScale = ConfigMisc.volTornadoScale;
        this.oldSirenScale = ConfigMisc.volSirenScale;
        this.oldThunderScale = ConfigMisc.volThunderScale;
    }

    @After
    public void restoreValues() {
        ConfigMisc.volTornadoScale = this.oldTornadoScale;
        ConfigMisc.volSirenScale = this.oldSirenScale;
        ConfigMisc.volThunderScale = this.oldThunderScale;
    }

    @Test
    public void scalesDefaultToOneAndNormalizeInvalidValues() {
        assertEquals(1.0D, ConfigMisc.volTornadoScale, 0.0D);
        assertEquals(1.0D, ConfigMisc.volSirenScale, 0.0D);
        assertEquals(1.0D, ConfigMisc.volThunderScale, 0.0D);

        ConfigMisc.volTornadoScale = Double.NaN;
        ConfigMisc.volSirenScale = Double.POSITIVE_INFINITY;
        ConfigMisc.volThunderScale = -0.25D;
        new ConfigMisc().hookUpdatedValues();

        assertEquals(1.0D, ConfigMisc.volTornadoScale, 0.0D);
        assertEquals(1.0D, ConfigMisc.volSirenScale, 0.0D);
        assertEquals(0.0D, ConfigMisc.volThunderScale, 0.0D);
    }

    @Test
    public void scalesClampToRangeAndKeepMute() {
        ConfigMisc.volTornadoScale = 1.5D;
        ConfigMisc.volSirenScale = 0.0D;
        ConfigMisc.volThunderScale = 0.4D;
        new ConfigMisc().hookUpdatedValues();

        assertEquals(1.0D, ConfigMisc.volTornadoScale, 0.0D);
        assertEquals(0.0D, ConfigMisc.volSirenScale, 0.0D);
        assertEquals(0.4D, ConfigMisc.volThunderScale, 0.0D);
    }
}
