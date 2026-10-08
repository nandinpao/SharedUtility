package com.agitg.sharedutility.database.core;

import java.util.List;
import java.util.ArrayList;
import java.util.Objects;
import java.util.function.Consumer;

/** Failure aggregation for pools OWNED by the library; caller decides ownership. */
public final class PoolCloseSupport {
    private PoolCloseSupport() { }

    /** Continue closing all pools; rethrow the first runtime exception, suppressing later errors. */
    public static <T> void closeOwned(List<? extends T> pools, Consumer<? super T> closer) {
        Objects.requireNonNull(pools, "pools");
        Objects.requireNonNull(closer, "closer");
        RuntimeException first = null;
        for (T pool : pools) {
            try { closer.accept(pool); }
            catch (RuntimeException failure) {
                if (first == null) first = failure;
                else first.addSuppressed(failure);
            }
        }
        if (first != null) throw first;
    }

    /** Cleanup for initialization failures; never replace the original failure. */
    public static <T> void closeAfterFailure(List<? extends T> pools,
                                              Consumer<? super T> closer, Throwable original) {
        Objects.requireNonNull(pools, "pools");
        Objects.requireNonNull(closer, "closer");
        Objects.requireNonNull(original, "original");
        for (T pool : new ArrayList<>(pools)) {
            try { closer.accept(pool); }
            catch (RuntimeException failure) {
                if (failure != original) original.addSuppressed(failure);
            }
        }
    }
}
