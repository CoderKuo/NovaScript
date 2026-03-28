package com.dakuo.novascript;

public interface ScriptSetup {
    void defineFunction(String name, ScriptHandler0 handler);

    void defineFunction(String name, ScriptHandler1 handler);

    void defineFunction(String name, ScriptHandler2 handler);

    void defineFunction(String name, ScriptHandler3 handler);

    void defineFunction(String name, ScriptHandler4 handler);

    void defineFunctionVararg(String name, ScriptHandlerVararg handler);

    void set(String name, Object value);
}