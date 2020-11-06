package com.github.bduisenov.router.internal;

import io.vavr.control.Either;

import java.util.function.Function;

public interface RouterBuilder<T, P> {

    Function<T, Either<P,T>> build();
}
