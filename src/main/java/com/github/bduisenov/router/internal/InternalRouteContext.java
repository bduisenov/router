package com.github.bduisenov.router.internal;

import com.github.bduisenov.router.RouteHistoryRecord;
import io.vavr.Tuple2;
import io.vavr.collection.List;
import io.vavr.control.Either;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.RequiredArgsConstructor;

import java.util.concurrent.CompletableFuture;

import static io.vavr.API.List;

@Getter
@RequiredArgsConstructor(access = AccessLevel.PACKAGE)
final class InternalRouteContext<T, P> {

    // Last successful state
    private final T state;

    private final List<RouteHistoryRecord<T, P>> historyRecords;

    /**
     * Centralized map containing the async child route number and it's result context of execution.
     */
    final List<CompletableFuture<Tuple2<InternalRouteContext<T, P>, Either<P, T>>>> nestedRouterContexts;

    InternalRouteContext(T state, List<RouteHistoryRecord<T, P>> historyRecords) {
        this(state, historyRecords, List());
    }

    InternalRouteContext(T state) {
        this(state, List());
    }
}

