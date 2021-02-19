package com.github.bduisenov.router.internal;

import com.github.bduisenov.fn.State;
import com.github.bduisenov.router.RouteContext;
import com.github.bduisenov.router.internal.BuilderSteps.MatchWhenStep;
import com.github.bduisenov.router.internal.BuilderSteps.Steps;
import io.vavr.API.Match.Case;
import io.vavr.API.Match.Pattern;
import io.vavr.API.Match.Pattern0;
import io.vavr.API.Match.Pattern1;
import io.vavr.collection.List;
import io.vavr.control.Either;
import lombok.RequiredArgsConstructor;
import lombok.val;

import java.util.concurrent.Executor;
import java.util.function.Consumer;
import java.util.function.Function;

import static com.github.bduisenov.fn.State.state;
import static io.vavr.API.List;
import static io.vavr.API.Right;
import static io.vavr.API.Tuple;
import static lombok.AccessLevel.PACKAGE;

@RequiredArgsConstructor(access = PACKAGE)
final class MatchRouteBuilder<T, P> implements MatchWhenStep<T, P> {

    private final Executor parentAsyncExecutor;

    private final Consumer<RouteContext<T, P>> routeContextConsumer;

    final List<Case<? extends Either<P, T>, State<InternalRouteContext<T, P>, InternalRouteContext<T, P>, Either<P, T>>>> cases;

    MatchRouteBuilder(Executor parentAsyncExecutor, Consumer<RouteContext<T, P>> routeContextConsumer) {
        this(parentAsyncExecutor, routeContextConsumer, List());
    }

    @Override
    public MatchWhenStep<T, P> when(Pattern1<? extends Either<P, T>, ?> pattern, Function<Steps<T, P>, Steps<T, P>> routeConsumer) {
        val _cases = cases.append(new WhenCase<>(pattern, either -> {
            val newBuilder = new DefaultRouteBuilder<>(parentAsyncExecutor, routeContextConsumer, state(context -> Tuple(context, either)));
            return routeConsumer.apply(newBuilder).route();
        }));

        return new MatchRouteBuilder<>(parentAsyncExecutor, routeContextConsumer, _cases);
    }

    @Override
    public MatchWhenStep<T, P> when(Pattern0<? extends Either<P, T>> pattern, Function<Steps<T, P>, Steps<T, P>> routeConsumer) {
        val _cases = cases.append(new WhenCase<>(pattern, either -> {
            val newBuilder = new DefaultRouteBuilder<>(parentAsyncExecutor, routeContextConsumer, state(context -> Tuple(context, Right(context.getState()))));
            return routeConsumer.apply(newBuilder).route();
        }));

        return new MatchRouteBuilder<>(parentAsyncExecutor, routeContextConsumer, _cases);
    }

    @RequiredArgsConstructor
    public static final class WhenCase<T, R> implements Case<T, R> {

        private static final long serialVersionUID = 1L;

        private final Pattern<T, ?> pattern;
        private final Function<T, R> f;

        @Override
        public R apply(T obj) {
            return f.apply(obj);
        }

        @Override
        public boolean isDefinedAt(T obj) {
            return pattern.isDefinedAt(obj);
        }
    }
}

