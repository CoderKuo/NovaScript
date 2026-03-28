package com.dakuo.novascript;

@FunctionalInterface
public interface ScriptConfigurer {
    void configure(ScriptSetup setup);
}