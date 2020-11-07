package com.github.bduisenov.router.internal;

import com.github.bduisenov.router.internal.RouteBuilder.NestedRouteBuilder;
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
final class AsyncRouteBuilder<T, P> extends NestedRouteBuilder<T, P> {

    private final RouteBuilder<T, P> parentRouteBuilder;

    private final RouteBuilder<T, P> asyncRouteBuilder;

    @Override
    DefaultRouteBuilder<T, P> addNestedRoute() {
        val _route = parentRouteBuilder.getRoute().flatMap(either -> state(context -> {
            CompletableFuture<Tuple2<InternalRouteContext<T, P>, Either<P, T>>> promise = new CompletableFuture<>();

            either.peekLeft(problem -> promise.cancel(true));
            either.peek(branchedOffState -> runAsync(() -> Try(() -> asyncRouteBuilder.getRoute().run(new InternalRouteContext<>(branchedOffState)))
                    .onSuccess(promise::complete)
                    .onFailure(promise::completeExceptionally), asyncRouteBuilder.getAsyncExecutor()));

            InternalRouteContext<T, P> updatedContext = new InternalRouteContext<>(context.getState(), context.getHistoryRecords(), context.getNestedRouterContexts().append(promise));

            return Tuple(updatedContext, either);
        }));

        return new DefaultRouteBuilder<>(parentRouteBuilder, _route);
    }
}
