package com.github.bduisenov.router;

import io.vavr.Tuple2;
import io.vavr.control.Either;

public interface RouteContext<T, P> {

    T getState();

    java.util.List<RouteHistoryRecord<T, P>> getHistoryRecords();

    java.util.List<Tuple2<RouteContext<T, P>, Either<P, T>>> getNestedRouteContexts();
}
