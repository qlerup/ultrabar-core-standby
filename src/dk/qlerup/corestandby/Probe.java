package dk.qlerup.corestandby;

import java.io.IOException;

interface Probe {
    boolean ping() throws IOException;
}
