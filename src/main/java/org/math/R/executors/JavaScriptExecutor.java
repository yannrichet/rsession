package org.math.R.executors;

import java.util.Map;

public abstract class JavaScriptExecutor {
    public static JavaScriptExecutor getInstance() {
        String version = System.getProperty("java.version");
        if (version.startsWith("11")) {
            return new NashornExecutor();
        } else {
            try {
                // Dynamically load GraalVMExecutor to avoid compile-time dependency
                Class<?> clazz = Class.forName("org.math.R.executors.GraalVMExecutor");
                return (JavaScriptExecutor) clazz.getDeclaredConstructor().newInstance();
            } catch (Exception e) {
                throw new RuntimeException("Failed to load GraalVMExecutor. Ensure you are running with a compatible Java version.", e);
            }
        }
    }

    public abstract Object execute(String script) throws Exception;

    public abstract void loadJSLibraries() throws Exception;

    public abstract double asDouble(Object o) throws ClassCastException;

    public abstract double[] asArray(Object o) throws ClassCastException;

    public abstract double[][] asMatrix(Object o) throws ClassCastException;

    public abstract String asString(Object o) throws ClassCastException;

    public abstract String[] asStrings(Object o) throws ClassCastException;

    public abstract boolean asLogical(Object o) throws ClassCastException;

    public abstract boolean[] asLogicals(Object o) throws ClassCastException;

    public abstract Map asList(Object o) throws ClassCastException;

    public abstract Object cast(Object o) throws ClassCastException;

    public abstract void putVariable(String varname, Object var);

    public abstract void close();
}
