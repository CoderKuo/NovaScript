package com.dakuo.novascript.module;

import java.lang.annotation.*;

/**
 * 标记 NovaScript 模块入口类。
 * <p>
 * 被标记的类必须实现 {@link NovaScriptModule} 接口，并提供无参构造器。
 * 将 jar 放入 {@code plugins/NovaScript/modules/} 目录即可自动加载。
 * <p>
 * 示例:
 * <pre>{@code
 * @NovaModule(name = "MyModule", version = "1.0.0", description = "自定义扩展")
 * public class MyModule implements NovaScriptModule {
 *     @Override
 *     public void onEnable(NovaScriptAPI api, ScriptSetup globalSetup) {
 *         globalSetup.defineFunction("hello", (ScriptHandler1) name -> "Hello, " + name);
 *     }
 * }
 * }</pre>
 */
@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.TYPE)
public @interface NovaModule {
    /** 模块名称 */
    String name();

    /** 模块版本 */
    String version() default "1.0.0";

    /** 模块描述 */
    String description() default "";
}
