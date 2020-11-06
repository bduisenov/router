package com.github.bduisenov.router;

import com.github.bduisenov.router.internal.DefaultRouterBuilder;
import io.vavr.control.Either;

import java.util.concurrent.Executor;
import java.util.function.Consumer;
import java.util.function.Function;

import static com.github.bduisenov.router.internal.RouterFunctions.noopRouteContextConsumer;

public final class Router {

    private Router() {
        // NOOP
    }

    public static <T, P> Function<T, Either<P, T>> router(Consumer<DefaultRouterBuilder<T, P>> route) {
        return router(Runnable::run, route);
    }

    public static <T, P> Function<T, Either<P, T>> router(Consumer<DefaultRouterBuilder<T, P>> route, Consumer<RouteContext<T, P>> routeContextConsumer) {
        return router(Runnable::run, route, routeContextConsumer);
    }

    public static <T, P> Function<T, Either<P, T>> router(Executor asyncExecutor, Consumer<DefaultRouterBuilder<T, P>> route) {
        return router(asyncExecutor, route, noopRouteContextConsumer());
    }

    public static <T, P> Function<T, Either<P, T>> router(Executor asyncExecutor, Consumer<DefaultRouterBuilder<T, P>> route, Consumer<RouteContext<T, P>> routeContextConsumer) {
        DefaultRouterBuilder<T, P> builder = builder(asyncExecutor, routeContextConsumer);

        route.accept(builder);

        return builder.build();
    }

    private static <T, P> DefaultRouterBuilder<T, P> builder(Executor asyncExecutor, Consumer<RouteContext<T, P>> routeContextConsumer) {
        return new DefaultRouterBuilder<>(asyncExecutor, routeContextConsumer);
    }
}
