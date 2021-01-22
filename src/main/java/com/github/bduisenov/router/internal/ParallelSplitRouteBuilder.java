package com.github.bduisenov.router.internal;

import com.github.bduisenov.fn.State;
import com.github.bduisenov.router.RouteContext;
import com.github.bduisenov.router.internal.BuilderSteps.AggregateStep;
import com.github.bduisenov.router.internal.BuilderSteps.Steps;
import io.vavr.Function2;
import io.vavr.collection.List;
import io.vavr.control.Either;
import lombok.RequiredArgsConstructor;
import lombok.val;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;
import java.util.function.Consumer;
import java.util.function.Function;

import static com.github.bduisenov.fn.State.state;
import static io.vavr.API.Tuple;
import static io.vavr.Predicates.not;
import static java.util.concurrent.CompletableFuture.supplyAsync;
import static java.util.function.Function.identity;

@RequiredArgsConstructor
public class ParallelSplitRouteBuilder<T, P> implements AggregateStep<T, P> {

    private final Executor parentAsyncExecutor;

    private final Executor childAsyncExecutor;

    private final Consumer<RouteContext<T, P>> routeContextConsumer;

    private final State<InternalRouteContext<T, P>, InternalRouteContext<T, P>, Either<P, T>> parentRoute;

    private final State<InternalRouteContext<T, P>, InternalRouteContext<T, P>, Either<P, T>> childRoute;

    private final Function<T, java.util.List<T>> splitter;

    @Override
    public Steps<T, P> aggregate(Function2<T, java.util.List<Either<P, T>>, Either<P, T>> aggregator) {
        val _route = parentRoute.flatMap(either -> state(context -> either.map(x -> Tuple(x, List.ofAll(splitter.apply(x))))
                .filter(not(tuple -> tuple._2.isEmpty()))
                .fold(() -> Tuple(context, either), el -> el.fold(__ -> Tuple(context, either), tuple -> {
                    val promises = tuple._2.map(branchedOffState -> supplyAsync(() ->
                            childRoute.run(new InternalRouteContext<>(branchedOffState)), childAsyncExecutor));

                    val results = promises.map(CompletableFuture::join);

                    val updatedNestedRouterContexts = context.getNestedRouterContexts().appendAll(promises);
                    val updatedContext = new InternalRouteContext<>(context.getState(), context.getHistoryRecords(), updatedNestedRouterContexts);

                    return Tuple(updatedContext, aggregator.apply(tuple._1, results.unzip(identity())._2.asJava()));
                }))));


        return new DefaultRouteBuilder<>(parentAsyncExecutor, routeContextConsumer, _route);
    }
}
