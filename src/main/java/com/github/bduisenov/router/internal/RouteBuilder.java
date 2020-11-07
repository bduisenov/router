package com.github.bduisenov.router.internal;

import com.github.bduisenov.fn.State;
import com.github.bduisenov.router.RouteContext;
import io.vavr.control.Either;

import java.util.concurrent.Executor;
import java.util.function.Consumer;
import java.util.function.Function;

public abstract class RouteBuilder<T, P> {

    abstract State<InternalRouteContext<T, P>, InternalRouteContext<T, P>, Either<P, T>> getRoute();

    abstract Executor getAsyncExecutor();

    abstract Consumer<RouteContext<T, P>> getRouteContextConsumer();

    abstract Function<T, Either<P, T>> build();

    public static <T, P> Function<T, Either<P, T>> router(Executor asyncExecutor, Function<DefaultRouteBuilder<T, P>, RouteBuilder<T, P>> routeConsumer,
                                                          Consumer<RouteContext<T, P>> routeContextConsumer) {
        DefaultRouteBuilder<T, P> builder = builder(asyncExecutor, routeContextConsumer);

        return routeConsumer.apply(builder).build();
    }

    private static <T, P> DefaultRouteBuilder<T, P> builder(Executor asyncExecutor, Consumer<RouteContext<T, P>> routeContextConsumer) {
        return new DefaultRouteBuilder<>(asyncExecutor, routeContextConsumer);
    }

    static abstract class NestedRouteBuilder<T, P> {

        abstract DefaultRouteBuilder<T, P> addNestedRoute();
    }
}
