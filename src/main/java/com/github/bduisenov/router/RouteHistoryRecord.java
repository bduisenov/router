package com.github.bduisenov.router;

import io.vavr.control.Either;
import lombok.Value;

@Value
public class RouteHistoryRecord<T, P> {

    T in;

    Either<P, T> out;

    int timeTakenNanos;

    String functionName;
}
