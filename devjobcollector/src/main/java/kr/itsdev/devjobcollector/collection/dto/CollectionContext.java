package kr.itsdev.devjobcollector.collection.dto;

import java.time.Instant;

public record CollectionContext(Instant requestedAt) {

    public CollectionContext {
        if (requestedAt == null) {
            requestedAt = Instant.now();
        }
    }

    public static CollectionContext now() {
        return new CollectionContext(Instant.now());
    }
}
