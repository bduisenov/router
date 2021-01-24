package com.github.bduisenov.router;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

import java.util.function.Function;
import java.util.function.Predicate;

import static lombok.AccessLevel.PRIVATE;

@Getter
@RequiredArgsConstructor(access = PRIVATE)
public final class RetryableOperation<T, R, P> {

    private final Function<T, R> function;

    private final int numberOfTries;

    private final Predicate<P> shouldApply;

    public static <T, R, P> RetryableOperation<T, R, P> retryable(Function<T, R> function, int numberOfTries) {
        return retryable(function, numberOfTries, val -> true);
    }

    public static <T, R, P> RetryableOperation<T, R, P> retryable(Function<T, R> function, int numberOfTries, Predicate<P> shouldApply) {
        return new RetryableOperation<>(function, numberOfTries, shouldApply);
    }
}
