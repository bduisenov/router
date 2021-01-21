package com.github.bduisenov.router;

import com.github.bduisenov.router.internal.BuilderSteps.InitialSteps;
import com.github.bduisenov.router.internal.BuilderSteps.TerminatingStep;
import com.github.bduisenov.router.internal.RouteFactory;
import io.vavr.control.Either;

import java.util.concurrent.Executor;
import java.util.function.Consumer;
import java.util.function.Function;

import static com.github.bduisenov.router.internal.RouterFunctions.noopRouteContextConsumer;

public final class Router {

    private Router() {
        // NOOP
    }

    public static <T, P> Function<T, Either<P, T>> router(Function<InitialSteps<T, P>, TerminatingStep<T, P>> routeConsumer) {
        return router(Runnable::run, routeConsumer);
    }

    public static <T, P> Function<T, Either<P, T>> router(Function<InitialSteps<T, P>, TerminatingStep<T, P>> routeConsumer,
                                                          Consumer<RouteContext<T, P>> routeContextConsumer) {
        return router(Runnable::run, routeConsumer, routeContextConsumer);
    }

    public static <T, P> Function<T, Either<P, T>> router(Executor asyncExecutor, Function<InitialSteps<T, P>, TerminatingStep<T, P>> routeConsumer) {
        return router(asyncExecutor, routeConsumer, noopRouteContextConsumer());
    }

    public static <T, P> Function<T, Either<P, T>> router(Executor asyncExecutor, Function<InitialSteps<T, P>, TerminatingStep<T, P>> routeConsumer,
                                                          Consumer<RouteContext<T, P>> routeContextConsumer) {
        return RouteFactory.router(asyncExecutor, routeConsumer, routeContextConsumer);
    }
}
