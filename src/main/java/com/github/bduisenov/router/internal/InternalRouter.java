package com.github.bduisenov.router.internal;

import com.github.bduisenov.router.RouteContext;
import com.github.bduisenov.router.RouteHistoryRecord;
import io.vavr.Tuple2;
import io.vavr.collection.List;
import io.vavr.control.Either;
import lombok.AccessLevel;
import lombok.NonNull;
import lombok.RequiredArgsConstructor;

import java.util.concurrent.CompletableFuture;
import java.util.function.Consumer;
import java.util.function.Function;

import static io.vavr.API.Try;
import static java.util.concurrent.CompletableFuture.allOf;
import static java.util.concurrent.TimeUnit.NANOSECONDS;
import static java.util.function.Function.identity;

@RequiredArgsConstructor(access = AccessLevel.PACKAGE)
final class InternalRouter<T, P> implements Function<T, Either<P, T>> {

    @NonNull
    private final Function<T, Tuple2<InternalRouteContext<T, P>, Either<P, T>>> route;

    @NonNull
    private final Consumer<RouteContext<T, P>> routeContextConsumer;

    @Override
    public Either<P, T> apply(T initialState) {
        Tuple2<InternalRouteContext<T, P>, Either<P, T>> executionResult = route.apply(initialState);

        InternalRouteContext<T, P> internalRouteContext = executionResult._1;
        Either<P, T> result = executionResult._2;

        deepWait(internalRouteContext.getNestedRouterContexts())
                .whenComplete(($1, $2) -> routeContextConsumer.accept(toJavaView(internalRouteContext)));

        return result;
    }

    private CompletableFuture<Void> deepWait(List<CompletableFuture<Tuple2<InternalRouteContext<T, P>, Either<P, T>>>> promises) {
        List<CompletableFuture<Void>> deeplyChained = promises.map(promise -> promise.thenCompose(tuple -> deepWait(tuple._1.getNestedRouterContexts())));

        return allOf(deeplyChained.toJavaList().toArray(new CompletableFuture[0]));
    }

    private RouteContext<T, P> toJavaView(InternalRouteContext<T, P> internalRouteContext) {
        return new RouteContext<T, P>() {
            @Override
            public T getState() {
                return internalRouteContext.getState();
            }

            @Override
            public java.util.List<RouteHistoryRecord<T, P>> getHistoryRecords() {
                return internalRouteContext.getHistoryRecords().toJavaList();
            }

            @Override
            public java.util.List<Tuple2<RouteContext<T, P>, Either<P, T>>> getNestedRouterContexts() {
                return internalRouteContext.getNestedRouterContexts().map(promise -> Try(() -> promise.get(0, NANOSECONDS)))
                        .flatMap(identity())
                        .map(tuple -> tuple.map1(InternalRouter.this::toJavaView))
                        .toJavaList();
            }
        };
    }
}
