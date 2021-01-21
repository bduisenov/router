package com.github.bduisenov.router.internal;

import com.github.bduisenov.fn.State;
import com.github.bduisenov.router.RouteContext;
import io.vavr.control.Either;
import lombok.AccessLevel;
import lombok.RequiredArgsConstructor;

import java.util.concurrent.Executor;
import java.util.function.Consumer;
import java.util.function.Function;

@RequiredArgsConstructor(access = AccessLevel.PACKAGE)
public final class FinallyRouteBuilder<T, P> extends RouteBuilder<T, P> {

    private final RouteBuilder<T, P> parentRoute;

    @Override
    protected State<InternalRouteContext<T, P>, InternalRouteContext<T, P>, Either<P, T>> getRoute() {
        return parentRoute.getRoute();
    }

    @Override
    protected Executor getAsyncExecutor() {
        return parentRoute.getAsyncExecutor();
    }

    @Override
    protected Consumer<RouteContext<T, P>> getRouteContextConsumer() {
        return parentRoute.getRouteContextConsumer();
    }

    @Override
    public Function<T, Either<P, T>> build() {
        return parentRoute.build();
    }
}
