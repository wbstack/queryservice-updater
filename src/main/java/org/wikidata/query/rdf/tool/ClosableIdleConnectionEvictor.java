package org.wikidata.query.rdf.tool;

import java.io.Closeable;
import org.apache.http.impl.client.IdleConnectionEvictor;

final class ClosableIdleConnectionEvictor implements Closeable {
    private final IdleConnectionEvictor evictor;

    ClosableIdleConnectionEvictor(IdleConnectionEvictor evictor) {
        this.evictor = evictor;
    }

    @Override
    public void close() {
        evictor.shutdown();
    }
}
