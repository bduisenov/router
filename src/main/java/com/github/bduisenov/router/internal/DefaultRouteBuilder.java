package com.github.bduisenov.router.internal;

import com.github.bduisenov.fn.State;
import com.github.bduisenov.router.RetryableOperation;
import com.github.bduisenov.router.RouteContext;
import com.github.bduisenov.router.RouteHistoryRecord;
import com.github.bduisenov.router.internal.BuilderSteps.MatchWhenStep;
import com.github.bduisenov.router.internal.BuilderSteps.Steps;
import com.github.bduisenov.router.internal.BuilderSteps.TerminatingStep;
import io.vavr.API;
import io.vavr.API.Match.Case;
import io.vavr.Function1;
import io.vavr.Function2;
import io.vavr.collection.List;
import io.vavr.control.Either;
import lombok.val;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.Predicate;

import static com.github.bduisenov.fn.State.*;
import static com.github.bduisenov.router.internal.RouterFunctions.thunk;
import static io.vavr.API.*;
import static io.vavr.Predicates.not;
import static java.util.function.Function.identity;

final class DefaultRouteBuilder<T, P> implements Steps<T, P> {

    /**
     * Initial route prepares {@link State} with {@link InternalRouteContext} and passed {@code state}.
     */
    private final State<InternalRouteContext<T, P>, InternalRouteContext<T, P>, Either<P, T>> route;

    /**
     * To be called when root route and all it's branches are complete.
     */
    private final Consumer<RouteContext<T, P>> routeContextConsumer;

    private final State<InternalRouteContext<T, P>, InternalRouteContext<T, P>, T> getState = gets(InternalRouteContext::getState);

    DefaultRouteBuilder(Consumer<RouteContext<T, P>> routeContextConsumer) {
        this(routeContextConsumer, state(context -> Tuple(context, Right(context.getState()))));
    }

    DefaultRouteBuilder(Consumer<RouteContext<T, P>> routeContextConsumer, State<InternalRouteContext<T, P>, InternalRouteContext<T, P>, Either<P, T>> route) {
        this.route = route;
        this.routeContextConsumer = routeContextConsumer;
    }

    @Override
    public Steps<T, P> flatMap(Function<T, Either<P, T>> fun) {
        val name = fun.getClass().getSimpleName();

        val _route = route.flatMap(thunk(pure(simple(either -> either.flatMap(fun), name))));

        return new DefaultRouteBuilder<>(routeContextConsumer, _route);
    }

    @Override
    public Steps<T, P> flatMap(RetryableOperation<T, Either<P, T>, P> retryableOperation) {
        int numberOfTries = retryableOperation.getNumberOfTries();
        if (numberOfTries > 100) {
            throw new IllegalArgumentException("Too many retries specified");
        }

        Function<T, Either<P, T>> fun = retryableOperation.getFunction();
        String name = fun.getClass().getSimpleName();

        Predicate<P> predicate = retryableOperation.getShouldApply();

        val _route = route.flatMap(thunk(pure(retryable(either -> either.flatMap(fun), name, numberOfTries, predicate))));

        return new DefaultRouteBuilder<>(routeContextConsumer, _route);
    }

    @Override
    public Steps<T, P> aggregate(Function<T, java.util.List<T>> splitter,
                                 Function<Steps<T, P>, Steps<T, P>> routeConsumer,
                                 Function2<T, java.util.List<Either<P, T>>, Either<P, T>> aggregator) {

        val newBuilder = new DefaultRouteBuilder<>(routeContextConsumer);
        val childRoute = routeConsumer.apply(newBuilder).route();

        val _route = route.flatMap(either -> state(context -> either.map(x -> Tuple(x, List.ofAll(splitter.apply(x))))
                .filter(not(tuple -> tuple._2.isEmpty()))
                .fold(() -> Tuple(context, either), el -> el.fold(__ -> Tuple(context, either), tuple -> {
                    val results = tuple._2.map(InternalRouteContext<T, P>::new).map(childRoute::run);

                    val nestedRouterContexts = results.map(CompletableFuture::completedFuture);
                    val updatedNestedRouterContexts = context.getNestedRouterContexts().appendAll(nestedRouterContexts);
                    val updatedContext = new InternalRouteContext<>(context.getState(), context.getHistoryRecords(), updatedNestedRouterContexts);

                    return Tuple(updatedContext, aggregator.apply(tuple._1, results.unzip(identity())._2.asJava()));
                }))));

        // this is a circular dependency
        return new DefaultRouteBuilder<>(routeContextConsumer, _route);
    }

    @Override
    public Steps<T, P> aggregate(Executor parentAsyncExecutor,
                                 Executor childAsyncExecutor,
                                 Function<T, java.util.List<T>> splitter,
                                 Function<Steps<T, P>, Steps<T, P>> routeConsumer,
                                 Function2<T, java.util.List<Either<P, T>>, Either<P, T>> aggregator) {
        throw new UnsupportedOperationException("This method is not supported for single threaded route builder");
    }

    @Override
    public Steps<T, P> peekAsync(Function<Steps<T, P>, Steps<T, P>> routeConsumer) {
        throw new UnsupportedOperationException("This method is not supported for single threaded route builder");
    }

    @Override
    public Steps<T, P> peekAsync(Executor childAsyncExecutor, Function<Steps<T, P>, Steps<T, P>> routeConsumer) {
        throw new UnsupportedOperationException("This method is not supported for single threaded route builder");
    }

    @Override
    public Steps<T, P> match(Function<MatchWhenStep<T, P>, MatchWhenStep<T, P>> whenSupplier) {
        val matchRouteBuilder = (MatchRouteBuilder<T, P>) whenSupplier.apply(new MatchRouteBuilder<>(routeContextConsumer));

        // default noop matcher
        val _cases = matchRouteBuilder.cases.append(Case($(), State::pure));
        @SuppressWarnings("unchecked")
        Case<? extends Either<P, T>, State<InternalRouteContext<T, P>, InternalRouteContext<T, P>, Either<P, T>>>[] casesArr = _cases.toJavaList().toArray(new Case[0]);

        val _route = route.flatMap(either -> Match(either).of(casesArr));

        return new DefaultRouteBuilder<>(routeContextConsumer, _route);
    }

    @Override
    public TerminatingStep<T, P> doFinally(Function2<T, Either<P, T>, Either<P, T>> fun) {
        String name = fun.getClass().getSimpleName();

        Function1<State<InternalRouteContext<T, P>, InternalRouteContext<T, P>, T>, State<InternalRouteContext<T, P>, InternalRouteContext<T, P>, Function1<Either<P, T>, Either<P, T>>>> lifted =
                liftM(fun.curried());

        val _route = route.flatMap(thunk(lifted.apply(getState).map(f -> alwaysReportable(f, name))));

        return new DefaultRouteBuilder<>(routeContextConsumer, _route);
    }

    @Override
    public Steps<T, P> recover(Function2<T, P, Either<P, T>> recoverFun) {
        val _route = route.flatMap(either -> state(context -> {
            T state = context.getState();

            Either<P, T> recovered = either.fold(problem -> recoverFun.apply(state, problem), API::Right);

            return Tuple(context, recovered);
        }));

        return new DefaultRouteBuilder<>(routeContextConsumer, _route);
    }

    @Override
    public Function<T, Either<P, T>> build() {
        return new InternalRouter<>(initialState -> route.run(new InternalRouteContext<>(initialState)), routeContextConsumer);
    }

    @Override
    public State<InternalRouteContext<T, P>, InternalRouteContext<T, P>, Either<P, T>> route() {
        return route;
    }

    private RouteFunction<T, P> simple(Function1<Either<P, T>, Either<P, T>> function, String name) {
        return new RouteFunction<T, P>() {
            @Override
            public ExecutionContext<T, P> internalApply(T state, Either<P, T> either) {
                val executionResult = execute(function, either);
                val rhr = createRouteHistoryRecord(either.get(), executionResult, name);

                return new ExecutionContext<>(List(rhr), executionResult._1);
            }
        };
    }

    private RouteFunction<T, P> alwaysReportable(Function1<Either<P, T>, Either<P, T>> function, String name) {
        return new RouteFunction<T, P>() {
            @Override
            public ExecutionContext<T, P> apply(T state, Either<P, T> either) {
                val executionResult = execute(function, either);
                val rhr = createRouteHistoryRecord(either.getOrElse(state), executionResult, name);

                return new ExecutionContext<>(List(rhr), executionResult._1);
            }
        };
    }

    private RouteFunction<T, P> retryable(Function1<Either<P, T>, Either<P, T>> function, String name, int numberOfTries, Predicate<P> shouldApply) {
        return new RouteFunction<T, P>() {
            @Override
            public ExecutionContext<T, P> internalApply(T state, Either<P, T> either) {
                return internalApply(either, numberOfTries, List());
            }

            private ExecutionContext<T, P> internalApply(Either<P, T> either, int numberOfTries, io.vavr.collection.List<RouteHistoryRecord<T, P>> acc) {
                val executionResult = execute(function, either);
                val rhr = createRouteHistoryRecord(either.get(), executionResult, name);

                Either<P, T> result = executionResult._1;

                return numberOfTries > 1 && result.isLeft() && shouldApply.test(result.getLeft())
                        ? internalApply(either, numberOfTries - 1, acc.append(rhr))
                        : new ExecutionContext<>(acc.append(rhr), result);
            }
        };
    }
}

