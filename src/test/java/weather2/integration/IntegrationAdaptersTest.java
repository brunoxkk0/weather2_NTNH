package weather2.integration;

import static org.junit.Assert.assertEquals;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import weather2.climate.ClimateEngine;
import weather2.harness.TestEnvironment;
import weather2.integration.dsurround.DSurroundWeatherAdapter;

public class IntegrationAdaptersTest {

    @Before
    public void setUp() {
        TestEnvironment.resetAll();
    }

    @After
    public void tearDown() {
        TestEnvironment.resetAll();
    }

    @Test
    public void testPlanetDimensionAliases() {
        int eveDim = ClimateEngine.resolveDimensionId("eve");
        assertEquals(18, eveDim);

        int dunaDim = ClimateEngine.resolveDimensionId("duna");
        assertEquals(16, dunaDim);

        int laytheDim = ClimateEngine.resolveDimensionId("laythe");
        assertEquals(22, laytheDim);

        int tektoDim = ClimateEngine.resolveDimensionId("tekto");
        assertEquals(24, tektoDim);

        int thatmoDim = ClimateEngine.resolveDimensionId("thatmo");
        assertEquals(413025, thatmoDim);
    }

    @Test
    public void testDSurroundWeatherAdapter() {
        // Safe execution without crash
        DSurroundWeatherAdapter.INSTANCE.setIntensity(0.75F);
        DSurroundWeatherAdapter.INSTANCE.reset();
    }
}
