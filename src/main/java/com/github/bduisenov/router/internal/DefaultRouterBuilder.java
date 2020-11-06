package com.github.bduisenov.router.internal;

import com.github.bduisenov.fn.State;
import com.github.bduisenov.router.RetryableOperation;
import com.github.bduisenov.router.RouteContext;
import com.github.bduisenov.router.RouteHistoryRecord;
import io.vavr.API;
import io.vavr.Function1;
import io.vavr.Function2;
import io.vavr.Tuple2;
import io.vavr.collection.List;
import io.vavr.control.Either;
import lombok.RequiredArgsConstructor;
import lombok.val;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.Predicate;

import static com.github.bduisenov.fn.State.gets;
import static com.github.bduisenov.fn.State.liftM;
import static com.github.bduisenov.fn.State.pure;
import static com.github.bduisenov.fn.State.state;
import static com.github.bduisenov.router.internal.RouterFunctions.thunk;
import static io.vavr.API.List;
import static io.vavr.API.Match;
import static io.vavr.API.Right;
import static io.vavr.API.Try;
import static io.vavr.API.Tuple;
import static io.vavr.Predicates.not;
import static java.util.concurrent.CompletableFuture.runAsync;
import static java.util.function.Function.identity;
import static java.util.stream.Collectors.toList;

@RequiredArgsConstructor
public class DefaultRouterBuilder<T, P> implements RouterBuilder {

    /**
     * Initial route prepares {@link State} with {@link InternalRouteContext} and passed {@code state}.
     */
    State<InternalRouteContext<T, P>, InternalRouteContext<T, P>, Either<P, T>> route = state(context -> Tuple(context, Right(context.getState())));

    /**
     * Default async executor is used by async routes in case if no explicit executor is given to the async route.
     * By default, {@code directExecutor} is used which runs the execution on callers thread.
     */
    final Executor asyncExecutor;

    /**
     * To be called when root route and all it's branches are complete.
     */
    private final Consumer<RouteContext<T, P>> routeContextConsumer;

    private final State<InternalRouteContext<T, P>, InternalRouteContext<T, P>, T> getState = gets(InternalRouteContext::getState);

    public DefaultRouterBuilder<T, P> flatMap(Function<T, Either<P, T>> fun) {
        String name = fun.getClass().getSimpleName();

        route = route.flatMap(thunk(pure(simple(either -> either.flatMap(fun), name))));

        return this;
    }

    public DefaultRouterBuilder<T, P> flatMap(RetryableOperation<T, Either<P, T>, P> retryableOperation) {
        int numberOfTries = retryableOperation.getNumberOfTries();
        if (numberOfTries > 100) {
            throw new IllegalArgumentException("Too many retries specified");
        }

        Function<T, Either<P, T>> fun = retryableOperation.getFunction();
        String name = fun.getClass().getSimpleName();

        Predicate<P> predicate = retryableOperation.getShouldApply();

        route = route.flatMap(thunk(pure(retryable(either -> either.flatMap(fun), name, numberOfTries, predicate))));

        return this;
    }

    public DefaultRouterBuilder<T, P> recover(Function2<T, P, Either<P, T>> recoverFun) {
        this.route = this.route.flatMap(either -> state(context -> {
            T state = context.getState();

            Either<P, T> recovered = either.fold(problem -> recoverFun.apply(state, problem), API::Right);

            return Tuple(context, recovered);
        }));

        return this;
    }

    public FinallyRouteBuilder<T, P> doFinally(Function2<T, Either<P, T>, Either<P, T>> fun) {
        String name = fun.getClass().getSimpleName();

        Function1<State<InternalRouteContext<T, P>, InternalRouteContext<T, P>, T>, State<InternalRouteContext<T, P>, InternalRouteContext<T, P>, Function1<Either<P, T>, Either<P, T>>>> lifted =
                liftM(fun.curried());

        route = route.flatMap(thunk(lifted.apply(getState).map(f -> alwaysReportable(f, name))));

        return new FinallyRouteBuilder<>(this);
    }

    public DefaultRouterBuilder<T, P> match(Consumer<MatchRouteBuilder<T, P>> matchRoute) {
        MatchRouteBuilder<T, P> matchBuilder = new MatchRouteBuilder<>(this);

        matchRoute.accept(matchBuilder);

        return matchBuilder.addMatchRoute();
    }

    DefaultRouterBuilder<T, P> addMatchRoute(MatchRouteBuilder<T, P> matchRouteBuilder) {
        @SuppressWarnings("unchecked")
        API.Match.Case<? extends Either<P, T>, State<InternalRouteContext<T, P>, InternalRouteContext<T, P>, Either<P, T>>>[] cases = matchRouteBuilder.cases.toJavaList().toArray(new API.Match.Case[0]);

        route = route.flatMap(either -> Match(either).of(cases));

        return this;
    }

    public DefaultRouterBuilder<T, P> split(Function<T, java.util.List<T>> splitter, Function2<T, java.util.List<Either<P, T>>, Either<P, T>> aggregator, Consumer<SplitRouteBuilder<T, P>> splitRoute) {
        SplitRouteBuilder<T, P> splitBuilder = new SplitRouteBuilder<>(asyncExecutor, this, splitter, aggregator);

        splitRoute.accept(splitBuilder);

        return splitBuilder.addSplitRoute();
    }

    protected DefaultRouterBuilder<T, P> addSplitRoute(SplitRouteBuilder<T, P> splitRouteBuilder) {
        State<InternalRouteContext<T, P>, InternalRouteContext<T, P>, Either<P, T>> splitRoute = splitRouteBuilder.route;
        Function<T, java.util.List<T>> splitter = splitRouteBuilder.splitter;
        Function2<T, java.util.List<Either<P, T>>, Either<P, T>> aggregator = splitRouteBuilder.aggregator;

        route = route.flatMap(either -> state(context -> either.map(x -> Tuple(x, List.ofAll(splitter.apply(x))))
                .filter(not(tuple -> tuple._2.isEmpty()))
                .fold(() -> Tuple(context, either), el -> el.fold($_ -> Tuple(context, either), tuple -> {
                    val results = tuple._2.map(InternalRouteContext<T, P>::new).map(splitRoute::run);
                    val nestedRouterContexts = results.map(CompletableFuture::completedFuture).collect(toList());
                    val updatedNestedRouterContexts = context.nestedRouterContexts.appendAll(nestedRouterContexts);
                    val updatedContext = new InternalRouteContext<>(context.getState(), context.getHistoryRecords(), updatedNestedRouterContexts);

                    return Tuple(updatedContext, aggregator.apply(tuple._1, results.unzip(identity())._2.asJava()));
                }))));

        return this;
    }

    public DefaultRouterBuilder<T, P> async(Consumer<AsyncRouteBuilder<T, P>> asyncRoute) {
        return async(asyncExecutor, asyncRoute);
    }

    public DefaultRouterBuilder<T, P> async(Executor asyncExecutor, Consumer<AsyncRouteBuilder<T, P>> asyncRoute) {
        AsyncRouteBuilder<T, P> asyncBuilder = new AsyncRouteBuilder<>(asyncExecutor, this);

        asyncRoute.accept(asyncBuilder);

        return asyncBuilder.addAsyncRoute();
    }

    DefaultRouterBuilder<T, P> addAsyncRoute(AsyncRouteBuilder<T, P> asyncRouteBuilder) {
        State<InternalRouteContext<T, P>, InternalRouteContext<T, P>, Either<P, T>> asyncRoute = asyncRouteBuilder.route;
        Executor asyncExecutor = asyncRouteBuilder.asyncExecutor;

        route = route.flatMap(either -> state(context -> {
            CompletableFuture<Tuple2<InternalRouteContext<T, P>, Either<P, T>>> promise = new CompletableFuture<>();

            either.peekLeft(problem -> promise.cancel(true));
            either.peek(branchedOffState -> runAsync(() -> Try(() -> asyncRoute.run(new InternalRouteContext<>(branchedOffState)))
                    .onSuccess(promise::complete)
                    .onFailure(promise::completeExceptionally), asyncExecutor));

            InternalRouteContext<T, P> updatedContext = new InternalRouteContext<>(context.getState(), context.getHistoryRecords(), context.nestedRouterContexts.append(promise));

            return Tuple(updatedContext, either);
        }));

        return this;
    }

    private static <T, P> RouteFunction<T, P> simple(Function1<Either<P, T>, Either<P, T>> function, String name) {
        return new RouteFunction<T, P>() {
            @Override
            public ExecutionContext<T, P> internalApply(T state, Either<P, T> either) {
                val executionResult = execute(function, either);
                val rhr = createRouteHistoryRecord(either.get(), executionResult, name);

                return new ExecutionContext<>(List(rhr), executionResult._1);
            }
        };
    }

    private static <T, P> RouteFunction<T, P> alwaysReportable(Function1<Either<P, T>, Either<P, T>> function, String name) {
        return new RouteFunction<T, P>() {
            @Override
            public ExecutionContext<T, P> apply(T state, Either<P, T> either) {
                val executionResult = execute(function, either);
                val rhr = createRouteHistoryRecord(either.getOrElse(state), executionResult, name);

                return new ExecutionContext<>(List(rhr), executionResult._1);
            }
        };
    }

    private static <T, P> RouteFunction<T, P> retryable(Function1<Either<P, T>, Either<P, T>> function, String name, int numberOfTries, Predicate<P> shouldApply) {
        return new RouteFunction<T, P>() {
            @Override
            public ExecutionContext<T, P> internalApply(T state, Either<P, T> either) {
                return internalApply(either, numberOfTries, List());
            }

            private ExecutionContext<T, P> internalApply(Either<P, T> either, int numberOfTries, List<RouteHistoryRecord<T, P>> acc) {
                val executionResult = execute(function, either);
                val rhr = createRouteHistoryRecord(either.get(), executionResult, name);

                Either<P, T> result = executionResult._1;

                return numberOfTries > 1 && result.isLeft() && shouldApply.test(result.getLeft())
                        ? internalApply(either, numberOfTries - 1, acc.append(rhr))
                        : new ExecutionContext<>(acc.append(rhr), result);
            }
        };
    }

    public Function<T, Either<P, T>> build() {
        return new InternalRouter<>(initialState -> route.run(new InternalRouteContext<>(initialState)), routeContextConsumer);
    }
}

