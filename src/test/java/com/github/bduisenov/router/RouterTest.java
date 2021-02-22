package com.github.bduisenov.router;

import io.vavr.Function1;
import io.vavr.Function2;
import io.vavr.control.Either;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.mockito.InOrder;
import org.mockito.Mockito;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.Executor;
import java.util.function.Consumer;
import java.util.function.Function;

import static com.github.bduisenov.router.RetryableOperation.retryable;
import static com.github.bduisenov.router.Router.router;
import static io.vavr.API.$;
import static io.vavr.API.Left;
import static io.vavr.API.Right;
import static io.vavr.API.TODO;
import static io.vavr.Patterns.$Left;
import static io.vavr.Patterns.$Right;
import static java.util.Arrays.asList;
import static java.util.Collections.emptyList;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.RETURNS_DEEP_STUBS;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
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
    Function<Doc, Either<Problem, Doc>> fun4 = mock(Function.class);

    @BeforeEach
    void setUp() {
        Mockito.reset();

        doAnswer(answer -> Right(answer.getArgument(0))).when(fun1).apply(any());
        doAnswer(answer -> Right(answer.getArgument(0))).when(fun2).apply(any());
        doAnswer(answer -> Right(answer.getArgument(0))).when(fun3).apply(any());
        doAnswer(answer -> Right(answer.getArgument(0))).when(fun4).apply(any());
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
            InOrder inOrder = inOrder(fun1, fun2, fun3);
            inOrder.verify(fun1).apply(any());
            inOrder.verify(fun2).apply(any());
            inOrder.verify(fun3).apply(any());
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
            InOrder inOrder = inOrder(fun1, fun2, fun3);
            inOrder.verify(fun1).apply(any());
            inOrder.verify(fun2).apply(any());
            inOrder.verify(fun3, never()).apply(any());
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
                    .recover(recover)
                    .flatMap(fun3));

            Either<Problem, Doc> result = fun.apply(new Doc());

            assertThat(result.isRight()).isTrue();
            InOrder inOrder = inOrder(fun1, fun2, fun3);
            inOrder.verify(fun1).apply(any());
            inOrder.verify(fun2).apply(any());
            inOrder.verify(fun3).apply(any());
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
            InOrder inOrder = inOrder(fun1, fun2, fun3);
            inOrder.verify(fun1).apply(any());
            inOrder.verify(fun2).apply(any());
            inOrder.verify(fun3).apply(any());
            verify(recover).apply(any(), any());
        }

        @Test
        public void whenRecovered_recoveredStateIsUsedInSubRoute() {
            Function<Integer, Either<Integer, Integer>> fun = mock(Function.class);
            doAnswer(answer -> Right(answer.getArgument(0, Integer.class) + 1)).when(fun).apply(any());

            Function<Integer, Either<Integer, Integer>> router = router(route -> route
                    .flatMap(fun) // 1 -> 2
                    .flatMap(fun) // 2 -> 3
                    .flatMap(in -> Left(0))
                    .recover((in, either) -> Right(4)) // 3 -> 4
                    .match(mr -> mr
                            .when($Right($(in -> in == 4)), wr -> wr
                                    .flatMap(fun))) // 4 -> 5
                    .flatMap(fun) // 5 -> 6
            );

            final Either<Integer, Integer> result = router.apply(1);

            assertThat(result).isEqualTo(Right(6));
            InOrder inOrder = inOrder(fun, fun, fun, fun);
            inOrder.verify(fun).apply(1);
            inOrder.verify(fun).apply(2);
            inOrder.verify(fun).apply(4);
            inOrder.verify(fun).apply(5);
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
                                    .flatMap(fun3)))
                    .flatMap(fun4)
            );

            Either<Problem, Doc> result = fun.apply(new Doc());

            assertThat(result.isRight()).isTrue();
            InOrder inOrder = inOrder(fun1, fun2, fun3, fun4);
            inOrder.verify(fun1).apply(any());
            inOrder.verify(fun2).apply(any());
            inOrder.verify(fun4).apply(any());
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
                                    .flatMap(fun3)))
                    .flatMap(fun4));

            Either<Problem, Doc> result = fun.apply(new Doc());

            assertThat(result.isRight()).isTrue();
            InOrder inOrder = inOrder(fun1, fun4);
            inOrder.verify(fun1).apply(any());
            inOrder.verify(fun4).apply(any());
            verifyNoInteractions(fun2);
            verifyNoInteractions(fun3);
        }

        @Test
        void whenLeftCaseMatched_skipNestedRoute() {
            Function<Integer, Either<Integer, Integer>> fun = mock(Function.class);
            doAnswer(answer -> Right(answer.getArgument(0, Integer.class) + 1)).when(fun).apply(any());

            Function<Integer, Either<Integer, Integer>> router = router(route -> route
                    .flatMap(fun) // 1 -> 2
                    .flatMap(fun) // 2 -> 3
                    .flatMap(in -> Left(0))
                    .match(mr -> mr
                            .when($Left($()), lr -> lr
                                    .flatMap(x -> TODO("must not be called"))))
                    .flatMap(fun)
            );

            final Either<Integer, Integer> result = router.apply(1);

            InOrder inOrder = inOrder(fun);
            inOrder.verify(fun).apply(1);
            inOrder.verify(fun).apply(2);
            verify(fun, times(2)).apply(any());
            assertThat(result).isEqualTo(Left(0));
        }

        @Test
        void whenLeftCaseMatchedAndRecovered_skipNestedRouteBeginningAndContinueAfterRecover() {
            Function<Integer, Either<Integer, Integer>> fun = mock(Function.class);
            doAnswer(answer -> Right(answer.getArgument(0, Integer.class) + 1)).when(fun).apply(any());

            Function<Integer, Either<Integer, Integer>> router = router(route -> route
                    .flatMap(fun) // 1 -> 2
                    .flatMap(fun) // 2 -> 3
                    .flatMap(in -> Left(0))
                    .match(mr -> mr
                            .when($Left($()), lr -> lr
                                    .flatMap(x -> TODO("must not be called"))
                                    .recover((last, either) -> Right(4))
                                    .flatMap(fun)))
                    .flatMap(fun)
            );

            final Either<Integer, Integer> result = router.apply(1);

            InOrder inOrder = inOrder(fun);
            inOrder.verify(fun).apply(1);
            inOrder.verify(fun).apply(2);
            inOrder.verify(fun).apply(4);
            inOrder.verify(fun).apply(5);
            verify(fun, times(4)).apply(any());
            assertThat(result).isEqualTo(Right(6));
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
                    .peekAsync(asyncRoute -> asyncRoute
                            .flatMap(fun1)
                            .flatMap(fun2))
                    .flatMap(fun3)
                    .flatMap(fun4));

            Either<Problem, Doc> result = fun.apply(new Doc());

            assertThat(result.isRight()).isTrue();
            verify(executor).execute(any());
            InOrder inOrder = inOrder(fun1, fun2, fun3, fun4);
            inOrder.verify(fun1).apply(any());
            inOrder.verify(fun2).apply(any());
            inOrder.verify(fun3).apply(any());
            inOrder.verify(fun4).apply(any());
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
                    .peekAsync(asyncExecutor, asyncRoute -> asyncRoute
                            .flatMap(fun1)
                            .flatMap(fun2))
                    .flatMap(fun3)
                    .flatMap(fun4));

            Either<Problem, Doc> result = fun.apply(new Doc());

            assertThat(result.isRight()).isTrue();
            verifyNoInteractions(executor);
            verify(asyncExecutor).execute(any());
            InOrder inOrder = inOrder(fun1, fun2, fun3, fun4);
            inOrder.verify(fun1).apply(any());
            inOrder.verify(fun2).apply(any());
            inOrder.verify(fun3).apply(any());
            inOrder.verify(fun4).apply(any());
        }
    }

    @Nested
    @DisplayName("split")
    class Split {

        Executor executor = spy(new Executor() {
            @Override
            public void execute(Runnable command) {
                command.run();
            }
        });

        @Test
        void whenSplitterReturnsNonEmptyList_runsNestedRouteForEachElement() {
            Function<Doc, List<Doc>> splitter = doc -> asList(doc, doc, doc);
            Function2<Doc, List<Either<Problem, Doc>>, Either<Problem, Doc>> aggregator = (x, xs) -> Right(x);

            Function<Doc, Either<Problem, Doc>> fun = router(route -> route
                    .split(splitter, aggregator, splitRoute -> splitRoute
                            .flatMap(fun1)
                            .flatMap(fun2))
                    .flatMap(fun3)
                    .flatMap(fun4));

            Either<Problem, Doc> result = fun.apply(new Doc());

            assertThat(result.isRight()).isTrue();
            InOrder inOrder = inOrder(fun1, fun2, fun3, fun4);
            inOrder.verify(fun1).apply(any());
            inOrder.verify(fun2).apply(any());
            inOrder.verify(fun1).apply(any());
            inOrder.verify(fun2).apply(any());
            inOrder.verify(fun1).apply(any());
            inOrder.verify(fun2).apply(any());
            inOrder.verify(fun3).apply(any());
            inOrder.verify(fun4).apply(any());
        }

        @Test
        void whenSplitterReturnsEmptyList_skipsNestedRoute() {
            Function<Doc, List<Doc>> splitter = doc -> emptyList();
            Function2<Doc, List<Either<Problem, Doc>>, Either<Problem, Doc>> aggregator = (x, xs) -> Right(x);

            Function<Doc, Either<Problem, Doc>> fun = router(route -> route
                    .split(splitter, aggregator, splitRoute -> splitRoute
                            .flatMap(fun1)
                            .flatMap(fun2))
                    .flatMap(fun3)
                    .flatMap(fun4));

            Either<Problem, Doc> result = fun.apply(new Doc());

            assertThat(result.isRight()).isTrue();
            verifyNoInteractions(fun1);
            verifyNoInteractions(fun2);
            InOrder inOrder = inOrder(fun3, fun4);
            inOrder.verify(fun3).apply(any());
            inOrder.verify(fun4).apply(any());
        }

        @Test
        void whenLeftSideIsGivenToSplitRoute_skipsNestedRoute() {
            doReturn(Left(new Problem())).when(fun1).apply(any());

            Function<Doc, List<Doc>> splitter = doc -> asList(doc, doc, doc);
            Function2<Doc, List<Either<Problem, Doc>>, Either<Problem, Doc>> aggregator = (x, xs) -> Right(x);

            Function<Doc, Either<Problem, Doc>> fun = router(route -> route
                    .flatMap(fun1)
                    .split(splitter, aggregator, splitRoute -> splitRoute
                            .flatMap(fun2))
                    .flatMap(fun3));

            Either<Problem, Doc> result = fun.apply(new Doc());

            assertThat(result.isLeft()).isTrue();
            verify(fun1).apply(any());
            verifyNoInteractions(fun2);
            verifyNoInteractions(fun3);
        }

        @Test
        void initialStateAndSplitRouteResultListAreGivenToAggregate() {
            Function<Doc, List<Doc>> splitter = doc -> asList(doc, doc, doc);
            Function2<Doc, List<Either<Problem, Doc>>, Either<Problem, Doc>> aggregator = (x, xs) -> {
                assertThat(x).isNotNull();
                assertThat(xs).allMatch(Either::isLeft);
                return Right(x);
            };

            doReturn(Left(new Problem())).when(fun1).apply(any());

            Function<Doc, Either<Problem, Doc>> fun = router(route -> route
                    .split(splitter, aggregator, splitRoute -> splitRoute
                            .flatMap(fun1)));

            Either<Problem, Doc> result = fun.apply(new Doc());

            assertThat(result.isRight()).isTrue();
            verify(fun1, times(3)).apply(any());
        }

        @Test
        void whenSplitterReturnsNonEmptyList_runsNestedRouteForEachElementAndAggregate() {
            Function<Doc, List<Doc>> splitter = doc -> asList(doc, doc, doc);
            Function2<Doc, List<Either<Problem, Doc>>, Either<Problem, Doc>> aggregator = (x, xs) -> Right(x);

            Function<Doc, Either<Problem, Doc>> fun = router(route -> route
                    .split(splitter, splitRoute -> splitRoute
                            .flatMap(fun1)
                            .flatMap(fun2))
                    .aggregate(aggregator)
                    .flatMap(fun3)
                    .flatMap(fun4));

            Either<Problem, Doc> result = fun.apply(new Doc());

            assertThat(result.isRight()).isTrue();
            InOrder inOrder = inOrder(fun1, fun2, fun3, fun4);
            inOrder.verify(fun1).apply(any());
            inOrder.verify(fun2).apply(any());
            inOrder.verify(fun1).apply(any());
            inOrder.verify(fun2).apply(any());
            inOrder.verify(fun1).apply(any());
            inOrder.verify(fun2).apply(any());
            inOrder.verify(fun3).apply(any());
            inOrder.verify(fun4).apply(any());
        }

        @Test
        void whenSplitterReturnsEmptyList_skipsNestedRouteAndAggregate() {
            Function<Doc, List<Doc>> splitter = doc -> emptyList();
            Function2<Doc, List<Either<Problem, Doc>>, Either<Problem, Doc>> aggregator = (x, xs) -> Right(x);

            Function<Doc, Either<Problem, Doc>> fun = router(route -> route
                    .split(splitter, splitRoute -> splitRoute
                            .flatMap(fun1)
                            .flatMap(fun2))
                    .aggregate(aggregator)
                    .flatMap(fun3)
                    .flatMap(fun4));

            Either<Problem, Doc> result = fun.apply(new Doc());

            assertThat(result.isRight()).isTrue();
            verifyNoInteractions(fun1);
            verifyNoInteractions(fun2);
            InOrder inOrder = inOrder(fun3, fun4);
            inOrder.verify(fun3).apply(any());
            inOrder.verify(fun4).apply(any());
        }

        @Test
        void whenLeftSideIsGivenToSplitRoute_skipsNestedRouteAndAggregate() {
            doReturn(Left(new Problem())).when(fun1).apply(any());

            Function<Doc, List<Doc>> splitter = doc -> asList(doc, doc, doc);
            Function2<Doc, List<Either<Problem, Doc>>, Either<Problem, Doc>> aggregator = (x, xs) -> Right(x);

            Function<Doc, Either<Problem, Doc>> fun = router(route -> route
                    .flatMap(fun1)
                    .split(splitter, splitRoute -> splitRoute
                            .flatMap(fun2))
                    .aggregate(aggregator)
                    .flatMap(fun3));

            Either<Problem, Doc> result = fun.apply(new Doc());

            assertThat(result.isLeft()).isTrue();
            verify(fun1).apply(any());
            verifyNoInteractions(fun2);
            verifyNoInteractions(fun3);
        }

        @Test
        void initialStateAndSplitRouteResultListAreGivenToAggregator() {
            Function<Doc, List<Doc>> splitter = doc -> asList(doc, doc, doc);
            Function2<Doc, List<Either<Problem, Doc>>, Either<Problem, Doc>> aggregator = (x, xs) -> {
                assertThat(x).isNotNull();
                assertThat(xs).allMatch(Either::isLeft);
                return Right(x);
            };

            doReturn(Left(new Problem())).when(fun1).apply(any());

            Function<Doc, Either<Problem, Doc>> fun = router(route -> route
                    .split(splitter, splitRoute -> splitRoute
                            .flatMap(fun1))
                    .aggregate(aggregator));

            Either<Problem, Doc> result = fun.apply(new Doc());

            assertThat(result.isRight()).isTrue();
            verify(fun1, times(3)).apply(any());
        }

        @Test
        void whenSplitterReturnsNonEmptyList_runsInParallelAndAggregate() {
            Function<Doc, List<Doc>> splitter = doc -> asList(doc, doc, doc);
            Function2<Doc, List<Either<Problem, Doc>>, Either<Problem, Doc>> aggregator = (x, xs) -> Right(x);

            Function<Doc, Either<Problem, Doc>> fun = router(executor, route -> route
                    .split(splitter, splitRoute -> splitRoute
                            .flatMap(fun1)
                            .flatMap(fun2))
                    .parallel()
                    .aggregate(aggregator)
                    .flatMap(fun3)
                    .flatMap(fun4));

            Either<Problem, Doc> result = fun.apply(new Doc());

            assertThat(result.isRight()).isTrue();
            verify(executor, times(3)).execute(any());
            InOrder inOrder = inOrder(fun1, fun2, fun3, fun4);
            inOrder.verify(fun1).apply(any());
            inOrder.verify(fun2).apply(any());
            inOrder.verify(fun1).apply(any());
            inOrder.verify(fun2).apply(any());
            inOrder.verify(fun1).apply(any());
            inOrder.verify(fun2).apply(any());
            inOrder.verify(fun3).apply(any());
            inOrder.verify(fun4).apply(any());
        }

        /**
         * Current implementation requires developer to specify both the executor and the parallel method invocation.
         * It is confusing.
         */
        @Test
        void whenSplitterReturnsNonEmptyListAndNoExecutorPassed_runsInParallelAndAggregate() {
            Function<Doc, List<Doc>> splitter = doc -> asList(doc, doc, doc);
            Function2<Doc, List<Either<Problem, Doc>>, Either<Problem, Doc>> aggregator = (x, xs) -> Right(x);

//            Function<Doc, Either<Problem, Doc>> fun = router(executor, route -> route
            Function<Doc, Either<Problem, Doc>> fun = router(route -> route
                    .split(splitter, splitRoute -> splitRoute
                            .flatMap(fun1)
                            .flatMap(fun2))
                    .parallel()
                    .aggregate(aggregator)
                    .flatMap(fun3)
                    .flatMap(fun4));

            Either<Problem, Doc> result = fun.apply(new Doc());

            assertThat(result.isRight()).isTrue();
//            verify(executor, times(3)).execute(any());
            InOrder inOrder = inOrder(fun1, fun2, fun3, fun4);
            inOrder.verify(fun1).apply(any());
            inOrder.verify(fun2).apply(any());
            inOrder.verify(fun1).apply(any());
            inOrder.verify(fun2).apply(any());
            inOrder.verify(fun1).apply(any());
            inOrder.verify(fun2).apply(any());
            inOrder.verify(fun3).apply(any());
            inOrder.verify(fun4).apply(any());
        }

        @Test
        void whenSplitterReturnsNonEmptyListAndMethodParallelNotInvoked_runsInParallelAndAggregate() {
            Function<Doc, List<Doc>> splitter = doc -> asList(doc, doc, doc);
            Function2<Doc, List<Either<Problem, Doc>>, Either<Problem, Doc>> aggregator = (x, xs) -> Right(x);

            Function<Doc, Either<Problem, Doc>> fun = router(executor, route -> route
                    .split(splitter, splitRoute -> splitRoute
                            .flatMap(fun1)
                            .flatMap(fun2))
//                    .parallel()
                    .aggregate(aggregator)
                    .flatMap(fun3)
                    .flatMap(fun4));

            Either<Problem, Doc> result = fun.apply(new Doc());

            assertThat(result.isRight()).isTrue();
//            verify(executor, times(3)).execute(any());
            InOrder inOrder = inOrder(fun1, fun2, fun3, fun4);
            inOrder.verify(fun1).apply(any());
            inOrder.verify(fun2).apply(any());
            inOrder.verify(fun1).apply(any());
            inOrder.verify(fun2).apply(any());
            inOrder.verify(fun1).apply(any());
            inOrder.verify(fun2).apply(any());
            inOrder.verify(fun3).apply(any());
            inOrder.verify(fun4).apply(any());
        }

        @Test
        void whenSplitterReturnsEmptyList_skipsParallelAndAggregate() {
            Function<Doc, List<Doc>> splitter = doc -> emptyList();
            Function2<Doc, List<Either<Problem, Doc>>, Either<Problem, Doc>> aggregator = (x, xs) -> Right(x);

            Function<Doc, Either<Problem, Doc>> fun = router(executor, route -> route
                    .split(splitter, splitRoute -> splitRoute
                            .flatMap(fun1)
                            .flatMap(fun2))
                    .parallel()
                    .aggregate(aggregator)
                    .flatMap(fun3)
                    .flatMap(fun4));

            Either<Problem, Doc> result = fun.apply(new Doc());

            assertThat(result.isRight()).isTrue();
            verifyNoInteractions(executor);
            verifyNoInteractions(fun1);
            verifyNoInteractions(fun2);
            InOrder inOrder = inOrder(fun3, fun4);
            inOrder.verify(fun3).apply(any());
            inOrder.verify(fun4).apply(any());
        }

        @Test
        void whenLeftSideIsGivenToSplitRoute_skipsParallelAndAggregate() {
            doReturn(Left(new Problem())).when(fun1).apply(any());

            Function<Doc, List<Doc>> splitter = doc -> asList(doc, doc, doc);
            Function2<Doc, List<Either<Problem, Doc>>, Either<Problem, Doc>> aggregator = (x, xs) -> Right(x);

            Function<Doc, Either<Problem, Doc>> fun = router(executor, route -> route
                    .flatMap(fun1)
                    .split(splitter, splitRoute -> splitRoute
                            .flatMap(fun2))
                    .parallel()
                    .aggregate(aggregator)
                    .flatMap(fun3));

            Either<Problem, Doc> result = fun.apply(new Doc());

            assertThat(result.isLeft()).isTrue();
            verifyNoInteractions(executor);
            verify(fun1).apply(any());
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
            Consumer<RouteContext<Doc, Problem>> consumer = rc -> {
                historyRecords.addAll(rc.getHistoryRecords());

                rc.getNestedRouteContexts().forEach(nrc -> historyRecords.addAll(nrc._1.getHistoryRecords()));
            };

            Function<Doc, Either<Problem, Doc>> fun = router(route -> route
                    .flatMap(fun1)
                    .peekAsync(asyncRoute -> asyncRoute
                            .flatMap(fun1))
                    .split(Collections::singletonList, splitRoute -> splitRoute
                            .flatMap(fun1))
                    .parallel()
                    .aggregate((doc, xs) -> xs.get(0))
                    .flatMap(fun1), consumer);

            Either<Problem, Doc> result = fun.apply(new Doc());

            assertThat(result.isRight()).isTrue();
            assertThat(historyRecords).hasSize(4);
        }
    }
}