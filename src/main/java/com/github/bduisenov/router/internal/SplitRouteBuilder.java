package com.github.bduisenov.router.internal;

import io.vavr.Function2;
import io.vavr.collection.List;
import io.vavr.control.Either;
import lombok.AccessLevel;
import lombok.RequiredArgsConstructor;
import lombok.val;

import java.util.concurrent.CompletableFuture;
import java.util.function.Function;

import static com.github.bduisenov.fn.State.state;
import static io.vavr.API.Tuple;
import static io.vavr.Predicates.not;
import static java.util.function.Function.identity;
import static java.util.stream.Collectors.toList;

@RequiredArgsConstructor(access = AccessLevel.PROTECTED)
public class SplitRouteBuilder<T, P> extends NestedRouteBuilder<T, P> {

    private final RouteBuilder<T, P> parentRouteBuilder;

    private final RouteBuilder<T, P> splitRouteBuilder;

    private final Function<T, java.util.List<T>> splitter;

    private final Function2<T, java.util.List<Either<P, T>>, Either<P, T>> aggregator;

    @Override
    protected DefaultRouteBuilder<T, P> addNestedRoute() {
        val _route = parentRouteBuilder.getRoute().flatMap(either -> state(context -> either.map(x -> Tuple(x, List.ofAll(splitter.apply(x))))
                .filter(not(tuple -> tuple._2.isEmpty()))
                .fold(() -> Tuple(context, either), el -> el.fold($_ -> Tuple(context, either), tuple -> {
                    val results = tuple._2.map(InternalRouteContext<T, P>::new).map(splitRouteBuilder.getRoute()::run);
                    val nestedRouterContexts = results.map(CompletableFuture::completedFuture).collect(toList());
                    val updatedNestedRouterContexts = context.nestedRouterContexts.appendAll(nestedRouterContexts);
                    val updatedContext = new InternalRouteContext<>(context.getState(), context.getHistoryRecords(), updatedNestedRouterContexts);

                    return Tuple(updatedContext, aggregator.apply(tuple._1, results.unzip(identity())._2.asJava()));
                }))));

        return new DefaultRouteBuilder<>(parentRouteBuilder, _route);
    }
}
