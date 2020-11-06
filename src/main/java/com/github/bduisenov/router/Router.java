package com.github.bduisenov.router;

import com.github.bduisenov.router.internal.DefaultRouteBuilder;
import com.github.bduisenov.router.internal.RouterBuilder;
import io.vavr.control.Either;

import java.util.concurrent.Executor;
import java.util.function.Consumer;
import java.util.function.Function;

import static com.github.bduisenov.router.internal.RouterFunctions.noopRouteContextConsumer;

public final class Router {

    private Router() {
        // NOOP
    }

    public static <T, P> Function<T, Either<P, T>> router(Function<DefaultRouteBuilder<T, P>, RouterBuilder<T, P>> routeConsumer) {
        return router(Runnable::run, routeConsumer);
    }

    public static <T, P> Function<T, Either<P, T>> router(Function<DefaultRouteBuilder<T, P>, RouterBuilder<T, P>> routeConsumer,
                                                          Consumer<RouteContext<T, P>> routeContextConsumer) {
        return router(Runnable::run, routeConsumer, routeContextConsumer);
    }

    public static <T, P> Function<T, Either<P, T>> router(Executor asyncExecutor, Function<DefaultRouteBuilder<T, P>, RouterBuilder<T, P>> routeConsumer) {
        return router(asyncExecutor, routeConsumer, noopRouteContextConsumer());
    }

    public static <T, P> Function<T, Either<P, T>> router(Executor asyncExecutor, Function<DefaultRouteBuilder<T, P>, RouterBuilder<T, P>> routeConsumer,
                                                          Consumer<RouteContext<T, P>> routeContextConsumer) {
        DefaultRouteBuilder<T, P> builder = builder(asyncExecutor, routeContextConsumer);

        return routeConsumer.apply(builder).build();
    }

    private static <T, P> DefaultRouteBuilder<T, P> builder(Executor asyncExecutor, Consumer<RouteContext<T, P>> routeContextConsumer) {
        return new DefaultRouteBuilder<>(asyncExecutor, routeContextConsumer);
    }
}
