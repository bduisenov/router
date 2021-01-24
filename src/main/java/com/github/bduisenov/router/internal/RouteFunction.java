package com.github.bduisenov.router.internal;

import com.github.bduisenov.router.RouteHistoryRecord;
import io.vavr.Function1;
import io.vavr.Tuple2;
import io.vavr.collection.List;
import io.vavr.control.Either;
import lombok.RequiredArgsConstructor;

import java.time.Duration;

import static io.vavr.API.List;
import static io.vavr.API.TODO;
import static io.vavr.API.Tuple;
import static lombok.AccessLevel.PACKAGE;

interface RouteFunction<T, P> {

    @RequiredArgsConstructor(access = PACKAGE)
    final class ExecutionContext<T, P> {

        final List<RouteHistoryRecord<T, P>> historyRecords;

        final Either<P, T> result;
    }

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
