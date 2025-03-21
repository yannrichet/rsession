package org.math.R.executors;

import java.io.Reader;
import java.util.Map;

public abstract class JavaScriptExecutor {
    public static JavaScriptExecutor getInstance() {
        String version = System.getProperty("java.version");
        if (version.startsWith("11")) {
            return new NashornExecutor();
        } else {
            return new GraalVMExecutor();
        }
    }

    public abstract Object execute(String script);

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
}
