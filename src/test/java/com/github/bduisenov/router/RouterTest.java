package com.github.bduisenov.router;

import com.github.bduisenov.router.Router.RouteContext;
import com.github.bduisenov.router.Router.RouteHistoryRecord;
import io.vavr.Function1;
import io.vavr.Function2;
import io.vavr.control.Either;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Executor;
import java.util.function.Consumer;
import java.util.function.Function;

import static com.github.bduisenov.router.Router.RetryableOperation.retryable;
import static com.github.bduisenov.router.Router.router;
import static io.vavr.API.$;
import static io.vavr.API.Left;
import static io.vavr.API.Right;
import static io.vavr.Patterns.$Left;
import static io.vavr.Patterns.$Right;
import static java.util.Arrays.asList;
import static java.util.Collections.emptyList;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.RETURNS_DEEP_STUBS;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

@SuppressWarnings("unchecked")
class RouterTest {

    static class Problem {
    }

    static class Doc {
    }

    Function<Doc, Either<Problem, Doc>> fun1 = mock(Function.class);
    Function<Doc, Either<Problem, Doc>> fun2 = mock(Function.class);
    Function<Doc, Either<Problem, Doc>> fun3 = mock(Function.class);

    @BeforeEach
    void setUp() {
        doAnswer(answer -> Right(answer.getArgument(0))).when(fun1).apply(any());
        doAnswer(answer -> Right(answer.getArgument(0))).when(fun2).apply(any());
        doAnswer(answer -> Right(answer.getArgument(0))).when(fun3).apply(any());
    }

    @Nested
    @DisplayName("flatMap")
    class FlatMap {

        @Test
        void whenAllFunctionsReturnRight_flatMapsOverAllFunctions() {
            Function<Doc, Either<Problem, Doc>> fun = router(route -> route
                    .flatMap(fun1)
                    .flatMap(fun2)
                    .flatMap(fun3));

            Either<Problem, Doc> result = fun.apply(new Doc());

            assertThat(result.isRight()).isTrue();
            verify(fun1).apply(any());
            verify(fun2).apply(any());
            verify(fun3).apply(any());
        }

        @Test
        void whenAllFunctionsReturnLeftExceptFirstOne_skipsFlatMapsAfterFailure() {
            doReturn(Left(new Problem())).when(fun2).apply(any());
            doReturn(Left(new Problem())).when(fun3).apply(any());

            Function<Doc, Either<Problem, Doc>> fun = router(route -> route
                    .flatMap(fun1)
                    .flatMap(fun2)
                    .flatMap(fun3));

            Either<Problem, Doc> result = fun.apply(new Doc());

            assertThat(result.isLeft()).isTrue();
            verify(fun1).apply(any());
            verify(fun2).apply(any());
            verifyNoInteractions(fun3);
        }
    }

    @Nested
    @DisplayName("retryable")
    class Retryable {

        @Test
        void whenFunctionReturnsLeft_retries() {
            doReturn(Left(new Problem())).doReturn(Right(new Doc()))
                    .when(fun2).apply(any());

            Function<Doc, Either<Problem, Doc>> fun = router(route -> route
                    .flatMap(fun1)
                    .flatMap(retryable(fun2, 2))
                    .flatMap(fun3));

            Either<Problem, Doc> result = fun.apply(new Doc());

            assertThat(result.isRight()).isTrue();
            verify(fun1).apply(any());
            verify(fun2, times(2)).apply(any());
            verify(fun3).apply(any());
        }

        @Test
        void whenFunctionReturnsRight_skipRetries() {
            Function<Doc, Either<Problem, Doc>> fun = router(route -> route
                    .flatMap(fun1)
                    .flatMap(retryable(fun2, 2))
                    .flatMap(fun3));

            Either<Problem, Doc> result = fun.apply(new Doc());

            assertThat(result.isRight()).isTrue();
            verify(fun1).apply(any());
            verify(fun2).apply(any());
            verify(fun3).apply(any());
        }
    }

    @Nested
    @DisplayName("recover")
    class Recover {

        Function2<Doc, Problem, Either<Problem, Doc>> recover = mock(Function2.class);

        @BeforeEach
        void setUp() {
            doAnswer(answer -> Right(answer.getArgument(0)))
                    .when(recover).apply(any(), any());
        }

        @Test
        void whenAllFunctionsReturnRight_recoverIsNotCalled() {
            Function<Doc, Either<Problem, Doc>> fun = router(route -> route
                    .flatMap(fun1)
                    .flatMap(fun2)
                    .flatMap(fun3)
                    .recover(recover));

            Either<Problem, Doc> result = fun.apply(new Doc());

            assertThat(result.isRight()).isTrue();
            verify(fun1).apply(any());
            verify(fun2).apply(any());
            verify(fun3).apply(any());
            verifyNoInteractions(recover);
        }

        @Test
        void whenFirstFunctionReturnsLeft_recoversTheStateAndCallsSubsequentFunctions() {
            doReturn(Left(new Problem())).when(fun1).apply(any());

            Function<Doc, Either<Problem, Doc>> fun = router(route -> route
                    .flatMap(fun1)
                    .recover(recover)
                    .flatMap(fun2)
                    .flatMap(fun3));

            Either<Problem, Doc> result = fun.apply(new Doc());

            assertThat(result.isRight()).isTrue();
            verify(fun1).apply(any());
            verify(fun2).apply(any());
            verify(fun3).apply(any());
            verify(recover).apply(any(), any());
        }
    }

    @Nested
    @DisplayName("doFinally")
    class DoFinally {

        Function1<Doc, Function1<Either<Problem, Doc>, Either<Problem, Doc>>> arg1curried = mock(Function1.class);
        Function1<Either<Problem, Doc>, Either<Problem, Doc>> arg2curried = mock(Function1.class);
        Function2<Doc, Either<Problem, Doc>, Either<Problem, Doc>> doFinally = mock(Function2.class, RETURNS_DEEP_STUBS);

        @BeforeEach
        void setUp() {
            doReturn(arg1curried).when(doFinally).curried();
            doReturn(arg2curried).when(arg1curried).apply(any());
            doAnswer(answer -> answer.getArgument(0)).when(arg2curried).apply(any());
        }

        @Test
        void whenAllFunctionsReturnRight_callsDoFinally() {
            Function<Doc, Either<Problem, Doc>> fun = router(route -> route
                    .flatMap(fun1)
                    .flatMap(fun2)
                    .flatMap(fun3)
                    .doFinally(doFinally));

            Either<Problem, Doc> result = fun.apply(new Doc());

            assertThat(result.isRight()).isTrue();
            verify(fun1).apply(any());
            verify(fun2).apply(any());
            verify(fun3).apply(any());
            verify(arg2curried).apply(any());
        }

        @Test
        void whenAllFunctionsReturnLeft_callsDoFinally() {
            doReturn(Left(new Problem())).when(fun1).apply(any());
            doReturn(Left(new Problem())).when(fun2).apply(any());
            doReturn(Left(new Problem())).when(fun3).apply(any());

            Function<Doc, Either<Problem, Doc>> fun = router(route -> route
                    .flatMap(fun1)
                    .flatMap(fun2)
                    .flatMap(fun3)
                    .doFinally(doFinally));

            Either<Problem, Doc> result = fun.apply(new Doc());

            assertThat(result.isLeft()).isTrue();
            verify(fun1).apply(any());
            verifyNoInteractions(fun2);
            verifyNoInteractions(fun3);
            verify(arg2curried).apply(any());
        }
    }

    @Nested
    @DisplayName("match")
    class Match {

        @Test
        void whenFirstCaseIsMatched_skipOtherCases() {
            Function<Doc, Either<Problem, Doc>> fun = router(route -> route
                    .flatMap(fun1)
                    .match(matchRoute -> matchRoute
                            .when($(), firstRoute -> firstRoute
                                    .flatMap(fun2))
                            .when($Right($()), secondRoute -> secondRoute
                                    .flatMap(fun3))));

            Either<Problem, Doc> result = fun.apply(new Doc());

            assertThat(result.isRight()).isTrue();
            verify(fun1).apply(any());
            verify(fun2).apply(any());
            verifyNoInteractions(fun3);
        }

        @Test
        void whenNoCasesMatched_skipNestedRoutes() {
            Function<Doc, Either<Problem, Doc>> fun = router(route -> route
                    .flatMap(fun1)
                    .match(matchRoute -> matchRoute
                            .when($Left($()), firstRoute -> firstRoute
                                    .recover((doc, result) -> Right(doc))
                                    .flatMap(fun2))
                            .when($Left($()), secondRoute -> secondRoute
                                    .recover((doc, result) -> Right(doc))
                                    .flatMap(fun3))));

            Either<Problem, Doc> result = fun.apply(new Doc());

            assertThat(result.isRight()).isTrue();
            verify(fun1).apply(any());
            verifyNoInteractions(fun2);
            verifyNoInteractions(fun3);
        }
    }

    @Nested
    @DisplayName("async")
    class Async {

        Executor executor = spy(new Executor() {
            @Override
            public void execute(Runnable command) {
                command.run();
            }
        });

        @Test
        void whenImplicitExecutorIsGiven_usesImplicitExecutor() {
            Function<Doc, Either<Problem, Doc>> fun = router(executor, route -> route
                    .async(asyncRoute -> asyncRoute
                            .flatMap(fun1)
                            .flatMap(fun2)
                            .flatMap(fun3)));

            Either<Problem, Doc> result = fun.apply(new Doc());

            assertThat(result.isRight()).isTrue();
            verify(executor).execute(any());
            verify(fun1).apply(any());
            verify(fun2).apply(any());
            verify(fun3).apply(any());
        }

        @Test
        void whenExplicitExecutorIsGiven_usesExplicitExecutor() {
            Executor asyncExecutor = spy(new Executor() {
                @Override
                public void execute(Runnable command) {
                    command.run();
                }
            });

            Function<Doc, Either<Problem, Doc>> fun = router(executor, route -> route
                    .async(asyncExecutor, asyncRoute -> asyncRoute
                            .flatMap(fun1)
                            .flatMap(fun2)
                            .flatMap(fun3)));

            Either<Problem, Doc> result = fun.apply(new Doc());

            assertThat(result.isRight()).isTrue();
            verifyNoInteractions(executor);
            verify(asyncExecutor).execute(any());
            verify(fun1).apply(any());
            verify(fun2).apply(any());
            verify(fun3).apply(any());
        }
    }

    @Nested
    @DisplayName("split")
    class Split {

        @Test
        void whenSplitterReturnsNonEmptyList_runsNestedRouteForEachElement() {
            Function<Doc, List<Doc>> splitter = doc -> asList(doc, doc, doc);
            Function<List<Either<Problem, Doc>>, Either<Problem, Doc>> aggregator = xs -> xs.get(0);

            Function<Doc, Either<Problem, Doc>> fun = router(route -> route
                    .split(splitter, aggregator, splitRoute -> splitRoute
                            .flatMap(fun1)
                            .flatMap(fun2)
                            .flatMap(fun3)));

            Either<Problem, Doc> result = fun.apply(new Doc());

            assertThat(result.isRight()).isTrue();
            verify(fun1, times(3)).apply(any());
            verify(fun2, times(3)).apply(any());
            verify(fun3, times(3)).apply(any());
        }

        @Test
        void whenSplitterReturnsEmptyList_skipsNestedRoute() {
            Function<Doc, List<Doc>> splitter = doc -> emptyList();
            Function<List<Either<Problem, Doc>>, Either<Problem, Doc>> aggregator = xs -> xs.get(0);

            Function<Doc, Either<Problem, Doc>> fun = router(route -> route
                    .split(splitter, aggregator, splitRoute -> splitRoute
                            .flatMap(fun1)
                            .flatMap(fun2)
                            .flatMap(fun3)));

            Either<Problem, Doc> result = fun.apply(new Doc());

            assertThat(result.isRight()).isTrue();
            verifyNoInteractions(fun1);
            verifyNoInteractions(fun2);
            verifyNoInteractions(fun3);
        }
    }

    @Nested
    @DisplayName("routeContext")
    class RouteContextConsumer {

        @Test
        void whenRouteIsExecuted_containsHistoryRecordForEachFunction() {
            List<RouteHistoryRecord<Doc, Problem>> historyRecords = new ArrayList<>();
            Consumer<RouteContext<Doc, Problem>> consumer = rc -> historyRecords.addAll(rc.getHistoryRecords());

            Function<Doc, Either<Problem, Doc>> fun = router(route -> route
                    .flatMap(fun1)
                    .flatMap(fun2)
                    .flatMap(fun3), consumer);

            Either<Problem, Doc> result = fun.apply(new Doc());

            assertThat(result.isRight()).isTrue();
            assertThat(historyRecords).hasSize(3);
        }
    }
}