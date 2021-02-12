package com.github.bduisenov.router.internal;

import com.github.bduisenov.fn.State;
import com.github.bduisenov.router.RetryableOperation;
import io.vavr.API.Match.Pattern0;
import io.vavr.API.Match.Pattern1;
import io.vavr.Function2;
import io.vavr.control.Either;

import java.util.List;
import java.util.concurrent.Executor;
import java.util.function.Function;

public abstract class BuilderSteps {

    private BuilderSteps() {
        //NOOP
    }

    public interface FlatMapStep<T, P> {
        Steps<T, P> flatMap(Function<T, Either<P, T>> fun);

        Steps<T, P> flatMap(RetryableOperation<T, Either<P, T>, P> retryableOperation);
    }

    public interface SplitStep<T, P> {
        ParallelStep<T, P> split(Function<T, java.util.List<T>> splitter, Function<Steps<T, P>, Steps<T, P>> routeConsumer);

        Steps<T, P> split(Function<T, java.util.List<T>> splitter, Function2<T, List<Either<P, T>>, Either<P, T>> aggregator,
                          Function<Steps<T, P>, Steps<T, P>> routeConsumer);
    }

    public interface AggregateStep<T, P> {
        Steps<T, P> aggregate(Function2<T, java.util.List<Either<P, T>>, Either<P, T>> aggregator);
    }

    public interface ParallelStep<T, P> extends AggregateStep<T, P> {
        AggregateStep<T, P> parallel();

        AggregateStep<T, P> parallel(Executor childAsyncExecutor);
    }

    public interface PeekAsyncStep<T, P> {
        Steps<T, P> peekAsync(Function<Steps<T, P>, Steps<T, P>> routeConsumer);

        Steps<T, P> peekAsync(Executor childAsyncExecutor, Function<Steps<T, P>, Steps<T, P>> routeConsumer);
    }

    public interface MatchStep<T, P> {
        Steps<T, P> match(Function<MatchWhenStep<T, P>, MatchWhenStep<T, P>> whenSupplier);
    }

    public interface MatchWhenStep<T, P> {
        MatchWhenStep<T, P> when(Pattern0<? extends Either<P, T>> pattern, Function<Steps<T, P>, Steps<T, P>> routeConsumer);

        MatchWhenStep<T, P> when(Pattern1<? extends Either<P, T>, ?> pattern, Function<Steps<T, P>, Steps<T, P>> routeConsumer);
    }

    public interface RecoverStep<T, P> {
        Steps<T, P> recover(Function2<T, P, Either<P, T>> recoverFun);
    }

    public interface FinalStep<T, P> {
        TerminatingStep<T, P> doFinally(Function2<T, Either<P, T>, Either<P, T>> fun);
    }

    // MARK: step combinations

    public interface InitialSteps<T, P> extends
            FlatMapStep<T, P>,
            SplitStep<T, P>,
            PeekAsyncStep<T, P>,
            MatchStep<T, P> {
    }

    public interface BasicSteps<T, P> extends
            InitialSteps<T, P>,
            RecoverStep<T, P>,
            FinalStep<T, P> {
    }

    public interface TerminatingStep<T, P> {
        Function<T, Either<P, T>> build();

        State<InternalRouteContext<T, P>, InternalRouteContext<T, P>, Either<P, T>> route();
    }

    public interface Steps<T, P> extends TerminatingStep<T, P>, BasicSteps<T, P> {
    }
}
