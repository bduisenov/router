package com.github.bduisenov.router.internal;

import com.github.bduisenov.fn.State;
import io.vavr.API;
import io.vavr.API.Match.Case;
import io.vavr.API.Match.Pattern0;
import io.vavr.API.Match.Pattern1;
import io.vavr.collection.List;
import io.vavr.control.Either;
import lombok.val;

import java.util.function.Function;

import static io.vavr.API.$;
import static io.vavr.API.Case;
import static io.vavr.API.List;
import static io.vavr.API.Match;

public final class MatchRouteBuilder<T, P> implements RouterBuilder<T, P> {

    private final DefaultRouteBuilder<T, P> parentRouter;

    private final List<Case<? extends Either<P, T>, State<InternalRouteContext<T, P>, InternalRouteContext<T, P>, Either<P, T>>>> cases;

    MatchRouteBuilder(DefaultRouteBuilder<T, P> parentRouter) {
        this(parentRouter, List());
    }

    public MatchRouteBuilder(DefaultRouteBuilder<T, P> parentRouter, List<Case<? extends Either<P, T>, State<InternalRouteContext<T, P>, InternalRouteContext<T, P>, Either<P, T>>>> cases) {
        this.parentRouter = parentRouter;
        this.cases = cases;
    }

    public MatchRouteBuilder<T, P> when(Pattern0<? extends Either<P, T>> pattern, Function<DefaultRouteBuilder<T, P>, DefaultRouteBuilder<T, P>> routerConsumer) {
        DefaultRouteBuilder<T, P> matchRouteBuilder = routerConsumer.apply(new DefaultRouteBuilder<>(parentRouter));

        val _cases = cases.append(Case(pattern, matchRouteBuilder.route));

        return new MatchRouteBuilder<>(parentRouter, _cases);
    }

    public MatchRouteBuilder<T, P> when(Pattern1<? extends Either<P, T>, ?> pattern, Function<DefaultRouteBuilder<T, P>, DefaultRouteBuilder<T, P>> routerConsumer) {
        DefaultRouteBuilder<T, P> matchRouteBuilder = routerConsumer.apply(new DefaultRouteBuilder<>(parentRouter));

        val _cases = cases.append(Case(pattern, matchRouteBuilder.route));

        return new MatchRouteBuilder<>(parentRouter, _cases);
    }

    DefaultRouteBuilder<T, P> addMatchRoute() {
        // default noop matcher
        val _cases = cases.append(Case($(), State::pure));
        @SuppressWarnings("unchecked")
        API.Match.Case<? extends Either<P, T>, State<InternalRouteContext<T, P>, InternalRouteContext<T, P>, Either<P, T>>>[] casesArr = _cases.toJavaList().toArray(new API.Match.Case[0]);

        val _route = parentRouter.route.flatMap(either -> Match(either).of(casesArr));

        return new DefaultRouteBuilder<>(parentRouter, _route);
    }
}
