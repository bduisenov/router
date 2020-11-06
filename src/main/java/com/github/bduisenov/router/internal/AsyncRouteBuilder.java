package com.github.bduisenov.router.internal;

import java.util.concurrent.Executor;

import static com.github.bduisenov.router.internal.RouterFunctions.noopRouteContextConsumer;

public final class AsyncRouteBuilder<T, P> extends DefaultRouterBuilder<T, P> {

    private final DefaultRouterBuilder<T, P> parentRouter;

    AsyncRouteBuilder(Executor asyncExecutor, DefaultRouterBuilder<T, P> parentRouter) {
        super(asyncExecutor, noopRouteContextConsumer());
        this.parentRouter = parentRouter;
    }

    DefaultRouterBuilder<T, P> addAsyncRoute() {
        return parentRouter.addAsyncRoute(this);
    }
}
