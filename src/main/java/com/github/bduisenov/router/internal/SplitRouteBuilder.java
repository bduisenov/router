package com.github.bduisenov.router.internal;

import io.vavr.Function2;
import io.vavr.control.Either;

import java.util.List;
import java.util.concurrent.Executor;
import java.util.function.Function;

import static com.github.bduisenov.router.internal.RouterFunctions.noopRouteContextConsumer;

public final class SplitRouteBuilder<T, P> extends DefaultRouterBuilder<T, P> {

    private final DefaultRouterBuilder<T, P> parentRouter;

    final Function<T, List<T>> splitter;

    final Function2<T, List<Either<P, T>>, Either<P, T>> aggregator;

    SplitRouteBuilder(Executor asyncExecutor, DefaultRouterBuilder<T, P> parentRouter,
                             Function<T, java.util.List<T>> splitter,
                             Function2<T, java.util.List<Either<P, T>>, Either<P, T>> aggregator) {
        super(asyncExecutor, noopRouteContextConsumer());
        this.parentRouter = parentRouter;
        this.splitter = splitter;
        this.aggregator = aggregator;
    }

    DefaultRouterBuilder<T, P> addSplitRoute() {
        return parentRouter.addSplitRoute(this);
    }
}
