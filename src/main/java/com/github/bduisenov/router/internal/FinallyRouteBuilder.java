package com.github.bduisenov.router.internal;

import lombok.AccessLevel;
import lombok.RequiredArgsConstructor;

@RequiredArgsConstructor(access = AccessLevel.PACKAGE)
public final class FinallyRouteBuilder<T, P> implements RouterBuilder {

    private final DefaultRouterBuilder<T, P> parentRouter;
}
