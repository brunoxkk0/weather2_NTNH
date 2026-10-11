package weather2.unit;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import java.io.IOException;
import java.io.InputStream;
import java.util.Scanner;

import org.junit.Test;

public class MixinConfigTest {

    @Test
    public void tornadoFlightProtectionMixinLoadsOnIntegratedServer() throws IOException {
        String config;
        try (InputStream stream = getClass().getClassLoader()
            .getResourceAsStream("mixins.weather2.json")) {
            if (stream == null) {
                throw new IOException("Missing mixins.weather2.json");
            }
            config = new Scanner(stream, "UTF-8").useDelimiter("\\A")
                .next();
        }

        int commonGroup = config.indexOf("\"mixins\"");
        int clientGroup = config.indexOf("\"client\"");
        int serverGroup = config.indexOf("\"server\"");
        assertTrue(commonGroup >= 0 && clientGroup > commonGroup && serverGroup > clientGroup);

        String commonMixins = config.substring(commonGroup, clientGroup);
        String serverMixins = config.substring(serverGroup);
        assertTrue(commonMixins.contains("\"NetHandlerPlayServerMixin\""));
        assertFalse(serverMixins.contains("\"NetHandlerPlayServerMixin\""));
    }
}
