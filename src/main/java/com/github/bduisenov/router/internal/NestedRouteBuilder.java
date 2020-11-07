package com.github.bduisenov.router.internal;

public abstract class NestedRouteBuilder<T, P> {

    protected abstract DefaultRouteBuilder<T, P> addNestedRoute();
}
