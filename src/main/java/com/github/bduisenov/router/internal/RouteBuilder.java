package com.github.bduisenov.router.internal;

import com.github.bduisenov.fn.State;
import com.github.bduisenov.router.RouteContext;
import io.vavr.control.Either;

import java.util.concurrent.Executor;
import java.util.function.Consumer;
import java.util.function.Function;

public abstract class RouteBuilder<T, P> {

    protected abstract State<InternalRouteContext<T, P>, InternalRouteContext<T, P>, Either<P, T>> getRoute();

    protected abstract Executor getAsyncExecutor();

    protected abstract Consumer<RouteContext<T, P>> getRouteContextConsumer();

    public abstract Function<T, Either<P, T>> build();
}
