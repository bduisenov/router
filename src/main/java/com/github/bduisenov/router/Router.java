package com.github.bduisenov.router;

import com.github.bduisenov.fn.State;
import io.vavr.API;
import io.vavr.API.Match.Case;
import io.vavr.API.Match.Pattern0;
import io.vavr.API.Match.Pattern1;
import io.vavr.Function1;
import io.vavr.Function2;
import io.vavr.Tuple2;
import io.vavr.collection.List;
import io.vavr.control.Either;
import lombok.Getter;
import lombok.NonNull;
import lombok.RequiredArgsConstructor;
import lombok.Value;
import lombok.val;

import java.time.Duration;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;
import java.util.function.Consumer;
import java.util.function.Function;

import static com.github.bduisenov.fn.State.gets;
import static com.github.bduisenov.fn.State.liftM;
import static com.github.bduisenov.fn.State.pure;
import static com.github.bduisenov.fn.State.state;
import static io.vavr.API.$;
import static io.vavr.API.Case;
import static io.vavr.API.List;
import static io.vavr.API.Match;
import static io.vavr.API.Right;
import static io.vavr.API.TODO;
import static io.vavr.API.Try;
import static io.vavr.API.Tuple;
import static java.util.concurrent.CompletableFuture.allOf;
import static java.util.concurrent.CompletableFuture.runAsync;
import static java.util.concurrent.TimeUnit.NANOSECONDS;
import static java.util.function.Function.identity;
import static lombok.AccessLevel.PRIVATE;

@RequiredArgsConstructor
public class Router<T, P> implements Function<T, Either<P, T>> {

    @NonNull
    private final Function<T, Tuple2<InternalRouteContext<T, P>, Either<P, T>>> route;

    @NonNull
    private final Consumer<RouteContext<T, P>> routeContextConsumer;

    @Override
    public Either<P, T> apply(T initialState) {
        Tuple2<InternalRouteContext<T, P>, Either<P, T>> executionResult = route.apply(initialState);

        InternalRouteContext<T, P> internalRouteContext = executionResult._1;
        Either<P, T> result = executionResult._2;

        deepWait(internalRouteContext.asyncRouterContexts)
                .whenComplete(($1, $2) -> routeContextConsumer.accept(toJavaView(internalRouteContext)));

        return result;
    }

    private CompletableFuture<Void> deepWait(List<CompletableFuture<Tuple2<InternalRouteContext<T, P>, Either<P, T>>>> promises) {
        List<CompletableFuture<Void>> deeplyChained = promises.map(promise -> promise.thenCompose(tuple -> deepWait(tuple._1.asyncRouterContexts)));

        return allOf(deeplyChained.toJavaList().toArray(new CompletableFuture[0]));
    }

    private RouteContext<T, P> toJavaView(InternalRouteContext<T, P> internalRouteContext) {
        return new RouteContext<T, P>() {
            @Override
            public T getState() {
                return internalRouteContext.state;
            }

            @Override
            public java.util.List<RouteHistoryRecord<T, P>> getHistoryRecords() {
                return internalRouteContext.historyRecords.toJavaList();
            }

            @Override
            public java.util.List<Tuple2<RouteContext<T, P>, Either<P, T>>> getAsyncRouterContexts() {
                return internalRouteContext.asyncRouterContexts.map(promise -> Try(() -> promise.get(0, NANOSECONDS)))
                        .flatMap(identity())
                        .map(tuple -> tuple.map1(Router.this::toJavaView))
                        .toJavaList();
            }
        };
    }

    public static <T, P> Router<T, P> router(Consumer<RouterBuilder<T, P>> route) {
        return router(Runnable::run, route);
    }

    public static <T, P> Router<T, P> router(Consumer<RouterBuilder<T, P>> route, Consumer<RouteContext<T, P>> routeContextConsumer) {
        return router(Runnable::run, route, routeContextConsumer);
    }

    public static <T, P> Router<T, P> router(Executor asyncExecutor, Consumer<RouterBuilder<T, P>> route) {
        return router(asyncExecutor, route, noopRouteContextConsumer());
    }

    public static <T, P> Router<T, P> router(Executor asyncExecutor, Consumer<RouterBuilder<T, P>> route, Consumer<RouteContext<T, P>> routeContextConsumer) {
        RouterBuilder<T, P> builder = builder(asyncExecutor, routeContextConsumer);

        route.accept(builder);

        return builder.build();
    }

    private static <T, P> RouterBuilder<T, P> builder(Executor asyncExecutor, Consumer<RouteContext<T, P>> routeContextConsumer) {
        return new RouterBuilder<>(asyncExecutor, routeContextConsumer);
    }

    // MARK: route builder

    @RequiredArgsConstructor
    public static class RouterBuilder<T, P> {

        /**
         * Initial route prepares {@link State} with {@link InternalRouteContext} and passed {@code state}.
         */
        State<InternalRouteContext<T, P>, Either<P, T>> route = gets(context -> Right(context.state));

        /**
         * Default async executor is used by async routes in case if no explicit executor is given to the async route.
         * By default, {@code directExecutor} is used which runs the execution on callers thread.
         */
        @NonNull
        final Executor asyncExecutor;

        /**
         * To be called when root route and all it's branches are complete.
         */
        @NonNull
        private final Consumer<RouteContext<T, P>> routeContextConsumer;

        private final State<InternalRouteContext<T, P>, T> getState = gets(InternalRouteContext::getState);

        public RouterBuilder<T, P> flatMap(Function<T, Either<P, T>> fun) {
            String name = fun.getClass().getSimpleName();

            route = route.flatMap(tracked(pure(simple(either -> either.flatMap(fun), name))));

            return this;
        }

        public RouterBuilder<T, P> recover(Function2<T, P, Either<P, T>> recoverFun) {
            this.route = this.route.flatMap(either -> state(context -> {
                T state = context.getState();

                Either<P, T> recovered = either.fold(problem -> recoverFun.apply(state, problem), API::Right);

                return Tuple(context, recovered);
            }));

            return this;
        }

        public FinallyRouteBuilder<T, P> doFinally(Function2<T, Either<P, T>, Either<P, T>> fun) {
            String name = fun.getClass().getSimpleName();

            Function1<State<InternalRouteContext<T, P>, T>, State<InternalRouteContext<T, P>, Function1<Either<P, T>, Either<P, T>>>> lifted =
                    liftM(fun.curried());

            route = route.flatMap(tracked(lifted.apply(getState).map(f -> alwaysReportable(f, name))));

            return new FinallyRouteBuilder<>(this);
        }

        public RouterBuilder<T, P> match(Consumer<MatchRouteBuilder<T, P>> matchRoute) {
            MatchRouteBuilder<T, P> matchBuilder = new MatchRouteBuilder<>(this);

            matchRoute.accept(matchBuilder);

            return matchBuilder.addMatchRoute();
        }

        RouterBuilder<T, P> addMatchRoute(MatchRouteBuilder<T, P> matchRouteBuilder) {
            @SuppressWarnings("unchecked")
            Case<? extends Either<P, T>, State<InternalRouteContext<T, P>, Either<P, T>>>[] cases = matchRouteBuilder.cases.toJavaList().toArray(new Case[0]);

            route = route.flatMap(either -> Match(either).of(cases));

            return this;
        }

        public RouterBuilder<T, P> async(Consumer<AsyncRouteBuilder<T, P>> asyncRoute) {
            return async(asyncExecutor, asyncRoute);
        }

        public RouterBuilder<T, P> async(Executor asyncExecutor, Consumer<AsyncRouteBuilder<T, P>> asyncRoute) {
            AsyncRouteBuilder<T, P> asyncBuilder = new AsyncRouteBuilder<>(asyncExecutor, this);

            asyncRoute.accept(asyncBuilder);

            return asyncBuilder.addAsyncRoute();
        }

        private RouterBuilder<T, P> addAsyncRoute(AsyncRouteBuilder<T, P> asyncRouteBuilder) {
            State<InternalRouteContext<T, P>, Either<P, T>> asyncRoute = asyncRouteBuilder.route;
            Executor asyncExecutor = asyncRouteBuilder.asyncExecutor;

            route = route.flatMap(either -> state(context -> {
                CompletableFuture<Tuple2<InternalRouteContext<T, P>, Either<P, T>>> promise = new CompletableFuture<>();

                either.peekLeft(problem -> promise.cancel(true));
                either.peek(branchedOffState -> runAsync(() -> Try(() -> asyncRoute.run(new InternalRouteContext<>(branchedOffState)))
                        .onSuccess(promise::complete)
                        .onFailure(promise::completeExceptionally), asyncExecutor));

                InternalRouteContext<T, P> updatedContext = new InternalRouteContext<>(context.state, context.historyRecords, context.asyncRouterContexts.append(promise));

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

        private Router<T, P> build() {
            return new Router<>(initialState -> route.run(new InternalRouteContext<>(initialState)), routeContextConsumer);
        }
    }

    // MARK: match route builder

    public static class MatchRouteBuilder<T, P> {

        private final RouterBuilder<T, P> parentRouter;

        private List<Case<? extends Either<P, T>, State<InternalRouteContext<T, P>, Either<P, T>>>> cases = List();

        private MatchRouteBuilder(RouterBuilder<T, P> parentRouter) {
            this.parentRouter = parentRouter;
        }

        public MatchRouteBuilder<T, P> when(Pattern0<? extends Either<P, T>> pattern, Consumer<RouterBuilder<T, P>> routerConsumer) {
            Executor asyncExecutor = parentRouter.asyncExecutor;
            RouterBuilder<T, P> matchRouteBuilder = new RouterBuilder<>(asyncExecutor, noopRouteContextConsumer());

            routerConsumer.accept(matchRouteBuilder);

            cases = cases.append(Case(pattern, matchRouteBuilder.route));

            return this;
        }

        public MatchRouteBuilder<T, P> when(Pattern1<? extends Either<P, T>, ?> pattern, Consumer<RouterBuilder<T, P>> routerConsumer) {
            Executor asyncExecutor = parentRouter.asyncExecutor;
            RouterBuilder<T, P> matchRouteBuilder = new RouterBuilder<>(asyncExecutor, noopRouteContextConsumer());

            routerConsumer.accept(matchRouteBuilder);

            cases = cases.append(Case(pattern, matchRouteBuilder.route));

            return this;
        }

        private RouterBuilder<T, P> addMatchRoute() {
            // default noop matcher
            cases = cases.append(Case($(), State::pure));

            return parentRouter.addMatchRoute(this);
        }
    }

    // MARK: async route builder

    public static class AsyncRouteBuilder<T, P> extends RouterBuilder<T, P> {

        private final RouterBuilder<T, P> parentRouter;

        private AsyncRouteBuilder(Executor asyncExecutor, RouterBuilder<T, P> parentRouter) {
            super(asyncExecutor, noopRouteContextConsumer());
            this.parentRouter = parentRouter;
        }

        private RouterBuilder<T, P> addAsyncRoute() {
            return parentRouter.addAsyncRoute(this);
        }
    }

    // MARK: finally route builder

    @RequiredArgsConstructor
    public static class FinallyRouteBuilder<T, P> {

        private final RouterBuilder<T, P> parentRouter;

        private Router<T, P> build() {
            return parentRouter.build();
        }
    }

    // MARK: helper funcs

    // @formatter:off
    private static <T, P> Consumer<RouteContext<T, P>> noopRouteContextConsumer() {
        return rc -> {};
    }
    // @formatter:on

    // TODO rename method
    public static <T, P> Function1<Either<P, T>, State<InternalRouteContext<T, P>, Either<P, T>>> tracked(
            State<InternalRouteContext<T, P>, RouteFunction<T, P>> stateM) {
        return either -> stateM.flatMap(fun -> state(context -> {
            ExecutionContext<T, P> execContext = fun.apply(context.state, either);

            List<RouteHistoryRecord<T, P>> historyRecords = execContext.getHistoryRecords();
            Either<P, T> result = execContext.getResult();

            List<RouteHistoryRecord<T, P>> updatedHistoryRecords = context.getHistoryRecords().appendAll(historyRecords);
            InternalRouteContext<T, P> updatedContext = new InternalRouteContext<>(result.getOrElse(context.state), updatedHistoryRecords,
                    context.asyncRouterContexts);

            return Tuple(updatedContext, result);
        }));
    }

    @Value
    @RequiredArgsConstructor
    private static class InternalRouteContext<T, P> {

        // Last successful state
        @NonNull
        private final T state;

        @NonNull
        private final List<RouteHistoryRecord<T, P>> historyRecords;

        /**
         * Centralized map containing the async child route number and it's result context of execution.
         */
        @NonNull
        private final List<CompletableFuture<Tuple2<InternalRouteContext<T, P>, Either<P, T>>>> asyncRouterContexts;

        private InternalRouteContext(T state, List<RouteHistoryRecord<T, P>> historyRecords) {
            this(state, historyRecords, List());
        }

        private InternalRouteContext(T state) {
            this(state, List());
        }
    }

    public interface RouteContext<T, P> {

        T getState();

        java.util.List<RouteHistoryRecord<T, P>> getHistoryRecords();

        java.util.List<Tuple2<RouteContext<T, P>, Either<P, T>>> getAsyncRouterContexts();
    }

    @Value
    public static class RouteHistoryRecord<T, P> {

        @NonNull
        private final T in;

        @NonNull
        private final Either<P, T> out;

        private final int timeTakenNanos;

        @NonNull
        private final String functionName;
    }

    @Value
    private static class ExecutionContext<T, P> {

        private List<RouteHistoryRecord<T, P>> historyRecords;

        private Either<P, T> result;
    }

    private interface RouteFunction<T, P> {

        default ExecutionContext<T, P> apply(T state, Either<P, T> either) {
            return either.isRight() ? internalApply(state, either) : new ExecutionContext<>(List(), either);
        }

        default ExecutionContext<T, P> internalApply(T state, Either<P, T> either) {
            return TODO();
        }

        default Tuple2<Either<P, T>, Duration> execute(Function1<Either<P, T>, Either<P, T>> function,
                                                       Either<P, T> either) {
            long startTime = System.nanoTime();
            Either<P, T> result = function.apply(either);
            Duration elapsed = Duration.ofNanos(System.nanoTime() - startTime);

            return Tuple(result, elapsed);
        }

        default RouteHistoryRecord<T, P> createRouteHistoryRecord(T inArg, Tuple2<Either<P, T>, Duration> executionResult, String name) {
            return new RouteHistoryRecord<>(inArg, executionResult._1, executionResult._2.getNano(), name);
        }
    }

    @Getter
    @RequiredArgsConstructor(access = PRIVATE)
    public static final class RetryableOperation<T, R> {

        private final Function<T, R> function;

        private final int numberOfRetries;

        public static <T, R> RetryableOperation<T, R> retryable(Function<T, R> function, int numberOfRetries) {
            return new RetryableOperation<>(function, numberOfRetries);
        }
    }
}