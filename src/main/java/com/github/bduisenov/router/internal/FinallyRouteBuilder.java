package com.github.bduisenov.router.internal;

import io.vavr.control.Either;
import lombok.AccessLevel;
import lombok.RequiredArgsConstructor;

import java.util.function.Function;

@RequiredArgsConstructor(access = AccessLevel.PACKAGE)
public final class FinallyRouteBuilder<T, P> implements RouterBuilder<T, P> {

    private final RouterBuilder<T, P> parentRoute;

    @Override
    public Function<T, Either<P, T>> build() {
        return parentRoute.build();
    }
}
