package weather2.integration;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import java.util.List;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import weather2.harness.TestEnvironment;

public class IntegrationManagerTest {

    @Before
    public void setUp() {
        TestEnvironment.resetAll();
    }

    @After
    public void tearDown() {
        TestEnvironment.resetAll();
    }

    @Test
    public void testAdapterReportModel() {
        AdapterReport repActive = AdapterReport.active("TestAdapter", "testmod", "Everything OK");
        assertEquals(AdapterStatus.ACTIVE, repActive.getStatus());
        assertFalse("Fallback must not be active", repActive.isFallbackActive());

        AdapterReport repDegraded = AdapterReport
            .degraded("TestAdapter", "testmod", "Field missing", "Static defaults");
        assertEquals(AdapterStatus.DEGRADED, repDegraded.getStatus());
        assertTrue("Fallback must be active", repDegraded.isFallbackActive());
        assertEquals("Static defaults", repDegraded.getFallbackDescription());

        AdapterReport repFailed = AdapterReport.failed("TestAdapter", "testmod", "NPE on link");
        assertEquals(AdapterStatus.FAILED, repFailed.getStatus());

        AdapterReport repNotInstalled = AdapterReport.notInstalled("TestAdapter", "testmod");
        assertEquals(AdapterStatus.NOT_INSTALLED, repNotInstalled.getStatus());
    }

    @Test
    public void testNotInstalledState() {
        TestEnvironment.setupVanilla();

        List<AdapterReport> reports = IntegrationManager.diagnoseAll();
        assertEquals("Dynamic Surroundings adapter must be registered", 1, reports.size());

        for (AdapterReport report : reports) {
            assertEquals(
                "Adapter " + report.getAdapterName() + " must be NOT_INSTALLED when mod absent",
                AdapterStatus.NOT_INSTALLED,
                report.getStatus());
        }
    }

    @Test
    public void testCircuitBreakerTripping() {
        IntegrationManager.resetCache();
        assertFalse(
            "Circuit breaker must be closed initially",
            IntegrationManager.isCircuitBreakerTripped("DSurround Weather Adapter"));

        // Trip circuit breaker
        IntegrationManager
            .tripCircuitBreaker("DSurround Weather Adapter", new RuntimeException("Simulated fatal corruption"));
        assertTrue(
            "Circuit breaker must be tripped",
            IntegrationManager.isCircuitBreakerTripped("DSurround Weather Adapter"));

        // When diagnoseAll is called, the tripped adapter must report FAILED
        List<AdapterReport> reports = IntegrationManager.diagnoseAll();
        boolean foundFailed = false;
        for (AdapterReport r : reports) {
            if ("DSurround Weather Adapter".equals(r.getAdapterName())) {
                assertEquals(AdapterStatus.FAILED, r.getStatus());
                assertTrue(
                    r.getDetails()
                        .contains("Simulated fatal corruption"));
                foundFailed = true;
            }
        }
        assertTrue("Tripped adapter must be reported in diagnoseAll", foundFailed);
    }

    @Test
    public void testDiagnosticSummaryLogging() {
        IntegrationManager.resetCache();
        IntegrationManager.setDSurroundLoaded(false);

        List<AdapterReport> reports = IntegrationManager.diagnoseAll();
        for (AdapterReport r : reports) {
            String line = String.format(
                "  [%-13s] %-25s (%-10s) : %s",
                r.getStatus(),
                r.getAdapterName(),
                r.getTargetModId(),
                r.getDetails());
            assertTrue(line.length() > 0);
        }
    }
}
