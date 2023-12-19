package org.math.R;

import org.graalvm.polyglot.*;

import java.io.*;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.*;

public class R2jsSession extends AbstractR2jsSession {

    private static Engine ENGINE = Engine.create();
    private static Source MATH_SOURCE;
    private static Source RAND_SOURCE;
    private static Source R_SOURCE;
    private Context context;

    /**
     * Default constructor
     *
     * Initialize the Javascript js and load external js libraries
     *
     * @param console - console
     * @param properties - properties
     * @param environmentName - name of the environment
     */
    @SuppressWarnings({"removal","deprecation"})
    protected R2jsSession(RLog console, Properties properties, String environmentName) {
        super(console, properties, environmentName);
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
        return context.eval("js", toEval);
    }

    @Override
    protected void putVariable(String varname, Object var) {
        context.getBindings("js").putMember(varname, var);
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

    protected synchronized void loadJSLibraries() throws Exception {
        if(MATH_SOURCE == null) {
            Reader mathReader = new InputStreamReader(R2jsSession.class.getResourceAsStream(MATH_JS_FILE));
            Reader randReader = new InputStreamReader(R2jsSession.class.getResourceAsStream(RAND_JS_FILE));
            Reader rReader = new InputStreamReader(R2jsSession.class.getResourceAsStream(R_JS_FILE));
            
            MATH_SOURCE = Source.newBuilder("js",mathReader, "math.js").build();
            RAND_SOURCE = Source.newBuilder("js",randReader, "rand.js").build();
            R_SOURCE = Source.newBuilder("js",rReader, "R.js").build();
        }

        this.context.eval(MATH_SOURCE);
        this.context.eval(RAND_SOURCE);
        this.context.eval(R_SOURCE);

        this.simpleEval("__rand = rand()");
        this.simpleEval("__R = R()");

    }

    @Override
    protected void createScriptEngine() {
        context = Context.newBuilder().
                allowHostClassLookup(s -> true).
                allowHostAccess(HostAccess.ALL).
                engine(ENGINE).build();
    }

    @Override
    public double asDouble(Object o) throws ClassCastException {
        if(o instanceof Value) {
            return ((Value) o).asDouble();
        } else {
            return (double) o;
        }
    }

    @Override
    public double[] asArray(Object o) throws ClassCastException {
        if(o instanceof Double) {
            return new double[]{(double)o};
        }
        Value value = (Value) o;
        if(value.hasArrayElements()) {
            return value.as(double[].class);
        } else {
            return new double[]{value.asDouble()};
        }
    }

    @Override
    public double[][] asMatrix(Object o) throws ClassCastException {
        if(o instanceof Value) {
            return ((Value)o).as(double[][].class);
        } else {
            if (o == null) {
                return null;
            }
            if (o instanceof double[][]) {
                return (double[][]) o;
            } else if (o instanceof double[]) {
                return t(new double[][]{(double[]) o});
            } else if (o instanceof Double) {
                return new double[][]{{(double) o}};
            }
        }
        return null;
    }

    @Override
    public String asString(Object o) throws ClassCastException {
        if(o instanceof Value) {
            return ((Value) o).asString();
        } else {
            return (String) o;
        }
    }

    @Override
    public String[] asStrings(Object o) throws ClassCastException {
        if(o instanceof String) {
            return new String[]{o.toString()};
        }
        if(o instanceof String[]) {
            return (String[]) o;
        }
        Value value = (Value) o;
        if(value.hasArrayElements()) {
            try {
                return value.as(String[].class);
            } catch (Exception e) {
                Object[] objects = value.as(Object[].class);
                return Arrays.stream(objects).map(Object::toString).
                        toArray(String[]::new);
            }
        } else {
            return new String[]{asString(o)};
        }
    }

    @Override
    public boolean asLogical(Object o) throws ClassCastException {
        if(o instanceof Value) {
            return ((Value) o).asBoolean();
        } else {
            return (Boolean) o;
        }
    }

    @Override
    public boolean[] asLogicals(Object o) throws ClassCastException {
        if(o instanceof Value) {
            return ((Value)o).as(boolean[].class);
        } else {
            return new boolean[] {(boolean) o};
        }
    }

    @Override
    public Map asList(Object o) throws ClassCastException {
        if(o instanceof Value) {
            return ((Value)o).as(Map.class);
        } else {
            return (Map) o;
        }
    }

    @Override
    public Object cast(Object o) throws ClassCastException {
        Value value = (Value) o;
        if(value.isNumber()) {
            return asDouble(o);
        }
        if(value.isBoolean()) {
            return asLogical(o);
        }
        if(value.isString()) {
            return asString(o);
        }
        if(value.hasArrayElements()) {
            try{
                return asMatrix(o);
            } catch (Exception e) {
            }
            try{
                String[] stringArray = asStrings(o);
                try {
                    for (String string : stringArray) {
                        Double.valueOf(string);
                    }
                } catch (Exception e) {//e.printStackTrace();
                    // It can't be cast to double[] so we return String[]
                    return stringArray;
                }
                return asArray(o);
            } catch (Exception e) {
            }

            try{
                return asArray(o);
            } catch (Exception e) {
            }
        }
        try {
            return asList(o);
        } catch (Exception e) {
        }
        return o;
    }

    @Override
    public synchronized void end() {
        //js = null;
        // TODO
        super.end();
    }
}
