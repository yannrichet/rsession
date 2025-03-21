package org.math.R;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.util.*;

import org.math.R.executors.JavaScriptExecutor;

public class R2jsSession extends AbstractR2jsSession {

    private JavaScriptExecutor executor;

    /**
     * Default constructor
     * <p>
     * Initialize the Javascript js and load external js libraries
     *
     * @param console - console
     * @param properties - properties
     * @param environmentName - name of the environment
     */
    protected R2jsSession(RLog console, Properties properties, String environmentName) {
        super(console, properties, environmentName);
    }

    @Override
    protected void loadJSLibraries() throws Exception {
        this.executor.loadJSLibraries();
    }

    public R2jsSession(RLog console, Properties properties) {
        this(console, properties, null);
    }

    public R2jsSession(final PrintStream p, Properties properties) {
        this(p, properties, null);
    }

    public R2jsSession(final PrintStream p, Properties properties, String environmentName) {
        this(new RLogPrintStream(p), properties, environmentName);
    }

    public static R2jsSession newInstance(final RLog console, Properties properties) {
        return new R2jsSession(console, properties);
    }


    @Override
    protected Object simpleEval(String toEval) throws Exception {
        return executor.execute(toEval);
    }

    @Override
    protected void putVariable(String varname, Object var) {
        executor.putVariable(varname, var);
    }

    private static StringBuilder resourceToStringBuilder(String resource) {
        InputStream inputStream = R2jsSession.class.getResourceAsStream(MATH_JS_FILE);
        StringBuilder textBuilder = new StringBuilder();
        try (Reader reader = new BufferedReader(new InputStreamReader(inputStream, StandardCharsets.UTF_8))) {
            int c = 0;
            while ((c = reader.read()) != -1) {
                textBuilder.append((char) c);
            }
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
        return textBuilder;
    }

    @Override
    protected void createScriptEngine() {
        executor = JavaScriptExecutor.getInstance();
    }

    @Override
    public double asDouble(Object o) throws ClassCastException {
        return executor.asDouble(o);
    }

    @Override
    public double[] asArray(Object o) throws ClassCastException {
        return executor.asArray(o);
    }

    @Override
    public double[][] asMatrix(Object o) throws ClassCastException {
        return executor.asMatrix(o);
    }

    @Override
    public String asString(Object o) throws ClassCastException {
        return executor.asString(o);
    }

    @Override
    public String[] asStrings(Object o) throws ClassCastException {
        return executor.asStrings(o);
    }

    @Override
    public boolean asLogical(Object o) throws ClassCastException {
        return executor.asLogical(o);
    }

    @Override
    public boolean[] asLogicals(Object o) throws ClassCastException {
        return executor.asLogicals(o);
    }

    @Override
    public Map asList(Object o) throws ClassCastException {
        return executor.asList(o);
    }

    @Override
    public Object cast(Object o) throws ClassCastException {
        return executor.cast(o);
    }

    @Override
    public synchronized void end() {
        this.executor.close();
        super.end();
    }
}
