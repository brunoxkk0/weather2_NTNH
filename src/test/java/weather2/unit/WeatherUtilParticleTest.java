package weather2.unit;

import static org.junit.Assert.assertEquals;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import CoroUtil.OldUtil;
import extendedrenderer.particle.entity.EntityRotFX;
import weather2.harness.RecordingWorld;
import weather2.harness.TestEnvironment;
import weather2.util.WeatherUtilParticle;

public class WeatherUtilParticleTest {

    private boolean runningMCP;
    private boolean checkforMCP;

    @Before
    public void setUp() {
        TestEnvironment.setupVanilla();
        runningMCP = OldUtil.runningMCP;
        checkforMCP = OldUtil.checkforMCP;
        OldUtil.runningMCP = false;
        OldUtil.checkforMCP = false;
    }

    @After
    public void tearDown() {
        OldUtil.runningMCP = runningMCP;
        OldUtil.checkforMCP = checkforMCP;
        TestEnvironment.resetAll();
    }

    @Test
    public void particleAgeCanBeReadAndWrittenOutsideMcpRuntime() {
        EntityRotFX particle = new EntityRotFX(RecordingWorld.create(), 0.0D, 64.0D, 0.0D, 0.0D, 0.0D, 0.0D);

        WeatherUtilParticle.setParticleAge(particle, 37);

        assertEquals(37, WeatherUtilParticle.getParticleAge(particle));
    }
}
