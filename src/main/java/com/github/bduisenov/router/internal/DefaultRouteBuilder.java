package com.github.bduisenov.router.internal;

import com.github.bduisenov.fn.State;
import com.github.bduisenov.router.RetryableOperation;
import com.github.bduisenov.router.RouteContext;
import com.github.bduisenov.router.RouteHistoryRecord;
import io.vavr.API;
import io.vavr.Function1;
import io.vavr.Function2;
import io.vavr.collection.List;
import io.vavr.control.Either;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.val;

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
import static io.vavr.API.Right;
import static io.vavr.API.Tuple;

@Getter(value = AccessLevel.PACKAGE)
public final class DefaultRouteBuilder<T, P> extends RouteBuilder<T, P> {

    /**
     * Initial route prepares {@link State} with {@link InternalRouteContext} and passed {@code state}.
     */
    private final State<InternalRouteContext<T, P>, InternalRouteContext<T, P>, Either<P, T>> route;

    /**
     * Default async executor is used by async routes in case if no explicit executor is given to the async route.
     * By default, {@code directExecutor} is used which runs the execution on callers thread.
     */
    private final Executor asyncExecutor;

    /**
     * To be called when root route and all it's branches are complete.
     */
    private final Consumer<RouteContext<T, P>> routeContextConsumer;

    private final State<InternalRouteContext<T, P>, InternalRouteContext<T, P>, T> getState = gets(InternalRouteContext::getState);

    DefaultRouteBuilder(RouteBuilder<T, P> parentRouteBuilder) {
        this(parentRouteBuilder.getAsyncExecutor(), parentRouteBuilder.getRouteContextConsumer());
    }

    DefaultRouteBuilder(RouteBuilder<T, P> parentRouteBuilder,
                               State<InternalRouteContext<T, P>, InternalRouteContext<T, P>, Either<P, T>> route) {
        this(parentRouteBuilder.getAsyncExecutor(), parentRouteBuilder.getRouteContextConsumer(), route);
    }

    DefaultRouteBuilder(Executor asyncExecutor, Consumer<RouteContext<T, P>> routeContextConsumer) {
        this(asyncExecutor, routeContextConsumer, state(context -> Tuple(context, Right(context.getState()))));
    }

    DefaultRouteBuilder(Executor asyncExecutor, Consumer<RouteContext<T, P>> routeContextConsumer,
                               State<InternalRouteContext<T, P>, InternalRouteContext<T, P>, Either<P, T>> route) {
        this.asyncExecutor = asyncExecutor;
        this.routeContextConsumer = routeContextConsumer;
        this.route = route;
    }

    public DefaultRouteBuilder<T, P> flatMap(Function<T, Either<P, T>> fun) {
        String name = fun.getClass().getSimpleName();

        val _route = route.flatMap(thunk(pure(simple(either -> either.flatMap(fun), name))));

        return new DefaultRouteBuilder<>(asyncExecutor, routeContextConsumer, _route);
    }

    public DefaultRouteBuilder<T, P> flatMap(RetryableOperation<T, Either<P, T>, P> retryableOperation) {
        int numberOfTries = retryableOperation.getNumberOfTries();
        if (numberOfTries > 100) {
            throw new IllegalArgumentException("Too many retries specified");
        }

        Function<T, Either<P, T>> fun = retryableOperation.getFunction();
        String name = fun.getClass().getSimpleName();

        Predicate<P> predicate = retryableOperation.getShouldApply();

        val _route = route.flatMap(thunk(pure(retryable(either -> either.flatMap(fun), name, numberOfTries, predicate))));

        return new DefaultRouteBuilder<>(asyncExecutor, routeContextConsumer, _route);
    }

    public DefaultRouteBuilder<T, P> recover(Function2<T, P, Either<P, T>> recoverFun) {
        val _route = route.flatMap(either -> state(context -> {
            T state = context.getState();

            Either<P, T> recovered = either.fold(problem -> recoverFun.apply(state, problem), API::Right);

            return Tuple(context, recovered);
        }));

        return new DefaultRouteBuilder<>(asyncExecutor, routeContextConsumer, _route);
    }

    public FinallyRouteBuilder<T, P> doFinally(Function2<T, Either<P, T>, Either<P, T>> fun) {
        String name = fun.getClass().getSimpleName();

        Function1<State<InternalRouteContext<T, P>, InternalRouteContext<T, P>, T>, State<InternalRouteContext<T, P>, InternalRouteContext<T, P>, Function1<Either<P, T>, Either<P, T>>>> lifted =
                liftM(fun.curried());

        val _route = route.flatMap(thunk(lifted.apply(getState).map(f -> alwaysReportable(f, name))));

        return new FinallyRouteBuilder<>(new DefaultRouteBuilder<>(asyncExecutor, routeContextConsumer, _route));
    }

    public DefaultRouteBuilder<T, P> match(Function<MatchRouteBuilder<T, P>, MatchRouteBuilder<T, P>> matchRoute) {
        MatchRouteBuilder<T, P> matchRouteBuilder = new MatchRouteBuilder<>(this);

        return matchRoute.apply(matchRouteBuilder).addNestedRoute();
    }

    public DefaultRouteBuilder<T, P> split(Function<T, java.util.List<T>> splitter, Function2<T, java.util.List<Either<P, T>>, Either<P, T>> aggregator, Function<DefaultRouteBuilder<T, P>, DefaultRouteBuilder<T, P>> splitRoute) {
        DefaultRouteBuilder<T, P> splitRouteBuilder = splitRoute.apply(new DefaultRouteBuilder<>(this));

        return new SplitRouteBuilder<>(this, splitRouteBuilder, splitter, aggregator).addNestedRoute();
    }

    public DefaultRouteBuilder<T, P> async(Function<DefaultRouteBuilder<T, P>, DefaultRouteBuilder<T, P>> asyncRoute) {
        return async(asyncExecutor, asyncRoute);
    }

    public DefaultRouteBuilder<T, P> async(Executor asyncExecutor, Function<DefaultRouteBuilder<T, P>, DefaultRouteBuilder<T, P>> asyncRoute) {
        DefaultRouteBuilder<T, P> asyncRouteBuilder = asyncRoute.apply(new DefaultRouteBuilder<>(asyncExecutor, routeContextConsumer));

        return new AsyncRouteBuilder<>(this, asyncRouteBuilder).addNestedRoute();
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

    Function<T, Either<P, T>> build() {
        return new InternalRouter<>(initialState -> route.run(new InternalRouteContext<>(initialState)), routeContextConsumer);
    }
}

