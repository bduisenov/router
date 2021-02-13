package com.github.bduisenov.router.internal;

import com.github.bduisenov.router.RouteContext;
import com.github.bduisenov.router.internal.BuilderSteps.InitialSteps;
import com.github.bduisenov.router.internal.BuilderSteps.TerminatingStep;
import io.vavr.control.Either;

import java.util.concurrent.Executor;
import java.util.function.Consumer;
import java.util.function.Function;

public abstract class RouteFactory<T, P> {

    public static <T, P> Function<T, Either<P, T>> router(Function<InitialSteps<T, P>, TerminatingStep<T, P>> routeConsumer,
                                                          Consumer<RouteContext<T, P>> routeContextConsumer) {

        return routeConsumer.apply(new DefaultRouteBuilder<>(routeContextConsumer)).build();
    }

    public static <T, P> Function<T, Either<P, T>> router(Executor asyncExecutor, Function<InitialSteps<T, P>, TerminatingStep<T, P>> routeConsumer,
                                                          Consumer<RouteContext<T, P>> routeContextConsumer) {

        return routeConsumer.apply(new ParallelRouteBuilder<>(asyncExecutor, routeContextConsumer)).build();
    }
}
