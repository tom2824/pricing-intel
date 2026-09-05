package io.github.tom2824.pricingintel.collector;

import io.github.tom2824.pricingintel.domain.ListingId;
import java.time.Instant;
import java.util.List;

/**
 * Archive dans plusieurs stores (par exemple HTML complet à courte rétention et version distillée à longue
 * rétention). Chaque store est tenté même si un autre échoue ; la première erreur est relancée à la fin.
 */
public final class CompositeRawSnapshotStore implements RawSnapshotStore {

    private final List<RawSnapshotStore> stores;

    public CompositeRawSnapshotStore(List<RawSnapshotStore> stores) {
        this.stores = List.copyOf(stores);
    }

    public static RawSnapshotStore of(List<RawSnapshotStore> stores) {
        if (stores.isEmpty()) {
            return RawSnapshotStore.none();
        }
        return stores.size() == 1 ? stores.get(0) : new CompositeRawSnapshotStore(stores);
    }

    @Override
    public void store(ListingId listingId, FetchResult result) {
        RuntimeException first = null;
        for (RawSnapshotStore store : stores) {
            try {
                store.store(listingId, result);
            } catch (RuntimeException e) {
                if (first == null) {
                    first = e;
                } else {
                    first.addSuppressed(e);
                }
            }
        }
        if (first != null) {
            throw first;
        }
    }

    @Override
    public int purgeExpired(Instant now) {
        int purged = 0;
        for (RawSnapshotStore store : stores) {
            purged += store.purgeExpired(now);
        }
        return purged;
    }
}
