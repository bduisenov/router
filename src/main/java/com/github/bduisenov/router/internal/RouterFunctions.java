package com.github.bduisenov.router.internal;

import com.github.bduisenov.fn.State;
import com.github.bduisenov.router.RouteContext;
import com.github.bduisenov.router.RouteHistoryRecord;
import com.github.bduisenov.router.internal.RouteFunction.ExecutionContext;
import io.vavr.Function1;
import io.vavr.collection.List;
import io.vavr.control.Either;

import java.util.function.Consumer;

import static com.github.bduisenov.fn.State.state;
import static io.vavr.API.Tuple;

public final class RouterFunctions {

    private RouterFunctions() {
        // NOOP
    }

    // @formatter:off
    public static <T, P> Consumer<RouteContext<T, P>> noopRouteContextConsumer() {
        return rc -> {};
    }
    // @formatter:on

    static <T, P> Function1<Either<P, T>, State<InternalRouteContext<T, P>, InternalRouteContext<T, P>, Either<P, T>>> thunk(
            State<InternalRouteContext<T, P>, InternalRouteContext<T, P>, RouteFunction<T, P>> stateM) {
        return either -> stateM.flatMap(fun -> state(context -> {
            ExecutionContext<T, P> execContext = fun.apply(context.getState(), either);

            List<RouteHistoryRecord<T, P>> historyRecords = execContext.historyRecords;
            Either<P, T> result = execContext.result;

            List<RouteHistoryRecord<T, P>> updatedHistoryRecords = context.getHistoryRecords().appendAll(historyRecords);
            InternalRouteContext<T, P> updatedContext = new InternalRouteContext<>(result.getOrElse(context.getState()), updatedHistoryRecords,
                    context.nestedRouterContexts);

            return Tuple(updatedContext, result);
        }));
    }
}
