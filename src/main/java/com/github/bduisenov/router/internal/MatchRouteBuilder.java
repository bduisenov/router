package com.github.bduisenov.router.internal;

import com.github.bduisenov.fn.State;
import com.github.bduisenov.router.RouteContext;
import com.github.bduisenov.router.internal.BuilderSteps.MatchWhenStep;
import com.github.bduisenov.router.internal.BuilderSteps.Steps;
import io.vavr.API.Match.Case;
import io.vavr.API.Match.Pattern0;
import io.vavr.API.Match.Pattern1;
import io.vavr.collection.List;
import io.vavr.control.Either;
import lombok.RequiredArgsConstructor;
import lombok.val;

import java.util.concurrent.Executor;
import java.util.function.Consumer;
import java.util.function.Function;

import static io.vavr.API.Case;
import static io.vavr.API.List;
import static lombok.AccessLevel.PACKAGE;

@RequiredArgsConstructor(access = PACKAGE)
final class MatchRouteBuilder<T, P> implements MatchWhenStep<T, P> {

    private final Consumer<RouteContext<T, P>> routeContextConsumer;

    final List<Case<? extends Either<P, T>, State<InternalRouteContext<T, P>, InternalRouteContext<T, P>, Either<P, T>>>> cases;

    MatchRouteBuilder(Consumer<RouteContext<T, P>> routeContextConsumer) {
        this(routeContextConsumer, List());
    }

    @Override
    public MatchWhenStep<T, P> when(Pattern1<? extends Either<P, T>, ?> pattern, Function<Steps<T, P>, Steps<T, P>> routeConsumer) {
        val newBuilder = new DefaultRouteBuilder<>(routeContextConsumer);
        val childRoute = routeConsumer.apply(newBuilder).route();

        val _cases = cases.append(Case(pattern, childRoute));

        return new MatchRouteBuilder<>(routeContextConsumer, _cases);
    }

    @Override
    public MatchWhenStep<T, P> when(Executor parentAsyncExecutor, Pattern1<? extends Either<P, T>, ?> pattern, Function<Steps<T, P>, Steps<T, P>> routeConsumer) {
        val newBuilder = new ParallelRouteBuilder<>(parentAsyncExecutor, routeContextConsumer);
        val childRoute = routeConsumer.apply(newBuilder).route();

        val _cases = cases.append(Case(pattern, childRoute));

        return new MatchRouteBuilder<>(routeContextConsumer, _cases);
    }

    @Override
    public MatchWhenStep<T, P> when(Pattern0<? extends Either<P, T>> pattern, Function<Steps<T, P>, Steps<T, P>> routeConsumer) {
        val newBuilder = new DefaultRouteBuilder<>(routeContextConsumer);
        val childRoute = routeConsumer.apply(newBuilder).route();

        val _cases = cases.append(Case(pattern, childRoute));

        return new MatchRouteBuilder<>(routeContextConsumer, _cases);
    }

    @Override
    public MatchWhenStep<T, P> when(Executor parentAsyncExecutor, Pattern0<? extends Either<P, T>> pattern, Function<Steps<T, P>, Steps<T, P>> routeConsumer) {
        val newBuilder = new ParallelRouteBuilder<>(parentAsyncExecutor, routeContextConsumer);
        val childRoute = routeConsumer.apply(newBuilder).route();

        val _cases = cases.append(Case(pattern, childRoute));

        return new MatchRouteBuilder<>(routeContextConsumer, _cases);
    }
}

