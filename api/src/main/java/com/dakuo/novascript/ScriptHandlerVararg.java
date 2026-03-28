package com.dakuo.novascript;

@FunctionalInterface
public interface ScriptHandlerVararg {
    Object handle(Object[] args);
}