package com.github.bduisenov.router.internal;

import com.github.bduisenov.fn.State;
import io.vavr.API.Match.Case;
import io.vavr.API.Match.Pattern0;
import io.vavr.API.Match.Pattern1;
import io.vavr.collection.List;
import io.vavr.control.Either;
import lombok.AccessLevel;
import lombok.RequiredArgsConstructor;

import java.util.concurrent.Executor;
import java.util.function.Consumer;

import static com.github.bduisenov.router.internal.RouterFunctions.noopRouteContextConsumer;
import static io.vavr.API.$;
import static io.vavr.API.Case;
import static io.vavr.API.List;

@RequiredArgsConstructor(access = AccessLevel.PACKAGE)
public final class MatchRouteBuilder<T, P> implements RouterBuilder {

    private final DefaultRouterBuilder<T, P> parentRouter;

    List<Case<? extends Either<P, T>, State<InternalRouteContext<T, P>, InternalRouteContext<T, P>, Either<P, T>>>> cases = List();

    public MatchRouteBuilder<T, P> when(Pattern0<? extends Either<P, T>> pattern, Consumer<DefaultRouterBuilder<T, P>> routerConsumer) {
        Executor asyncExecutor = parentRouter.asyncExecutor;
        DefaultRouterBuilder<T, P> matchRouteBuilder = new DefaultRouterBuilder<>(asyncExecutor, noopRouteContextConsumer());

        routerConsumer.accept(matchRouteBuilder);

        cases = cases.append(Case(pattern, matchRouteBuilder.route));

        return this;
    }

    public MatchRouteBuilder<T, P> when(Pattern1<? extends Either<P, T>, ?> pattern, Consumer<DefaultRouterBuilder<T, P>> routerConsumer) {
        Executor asyncExecutor = parentRouter.asyncExecutor;
        DefaultRouterBuilder<T, P> matchRouteBuilder = new DefaultRouterBuilder<>(asyncExecutor, noopRouteContextConsumer());

        routerConsumer.accept(matchRouteBuilder);

        cases = cases.append(Case(pattern, matchRouteBuilder.route));

        return this;
    }

    DefaultRouterBuilder<T, P> addMatchRoute() {
        // default noop matcher
        cases = cases.append(Case($(), State::pure));

        return parentRouter.addMatchRoute(this);
    }
}
