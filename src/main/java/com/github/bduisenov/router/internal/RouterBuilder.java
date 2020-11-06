package com.github.bduisenov.router.internal;

import io.vavr.control.Either;

import java.util.function.Function;

import static io.vavr.API.TODO;

public interface RouterBuilder<T, P> {

    default Function<T, Either<P,T>> build() {
        return TODO();
    };
}
