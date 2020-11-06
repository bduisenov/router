package com.github.bduisenov.router.internal;

import io.vavr.Tuple2;
import io.vavr.control.Either;
import lombok.AccessLevel;
import lombok.RequiredArgsConstructor;
import lombok.val;

import java.util.concurrent.CompletableFuture;

import static com.github.bduisenov.fn.State.state;
import static io.vavr.API.Try;
import static io.vavr.API.Tuple;
import static java.util.concurrent.CompletableFuture.runAsync;

@RequiredArgsConstructor(access = AccessLevel.PACKAGE)
public final class AsyncRouteBuilder<T, P> implements RouterBuilder<T, P> {

    private final DefaultRouteBuilder<T, P> parentRouteBuilder;

    private final DefaultRouteBuilder<T, P> asyncRouteBuilder;

    DefaultRouteBuilder<T, P> addAsyncRoute() {
        val _route = parentRouteBuilder.route.flatMap(either -> state(context -> {
            CompletableFuture<Tuple2<InternalRouteContext<T, P>, Either<P, T>>> promise = new CompletableFuture<>();

            either.peekLeft(problem -> promise.cancel(true));
            either.peek(branchedOffState -> runAsync(() -> Try(() -> asyncRouteBuilder.route.run(new InternalRouteContext<>(branchedOffState)))
                    .onSuccess(promise::complete)
                    .onFailure(promise::completeExceptionally), asyncRouteBuilder.asyncExecutor));

            InternalRouteContext<T, P> updatedContext = new InternalRouteContext<>(context.getState(), context.getHistoryRecords(), context.nestedRouterContexts.append(promise));

            return Tuple(updatedContext, either);
        }));

        return new DefaultRouteBuilder<>(parentRouteBuilder, _route);
    }
}
