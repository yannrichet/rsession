package org.math.R.executors;

import org.graalvm.polyglot.*;

import java.io.Reader;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;
import java.io.InputStreamReader;

import static org.math.R.AbstractR2jsSession.*;

public class GraalVMExecutor extends JavaScriptExecutor {
    private static final Engine ENGINE = Engine.create();
    private final Context context;
    private final Map<String, Source> libraries = new HashMap<>();

    private static Source MATH_SOURCE;
    private static Source RAND_SOURCE;
    private static Source R_SOURCE;

    public GraalVMExecutor() {
        this.context = Context.newBuilder()
                .allowHostClassLookup(s -> true)
                .allowHostAccess(HostAccess.ALL)
                .engine(ENGINE)
                .build();
    }

    @Override
    public Object execute(String script) {
        return context.eval("js", script);
    }

    @Override
    public synchronized void loadJSLibraries() throws Exception {
        if (MATH_SOURCE == null) {
            Reader mathReader = new InputStreamReader(GraalVMExecutor.class.getResourceAsStream(MATH_JS_FILE));
            Reader randReader = new InputStreamReader(GraalVMExecutor.class.getResourceAsStream(RAND_JS_FILE));
            Reader rReader = new InputStreamReader(GraalVMExecutor.class.getResourceAsStream(R_JS_FILE));

            MATH_SOURCE = Source.newBuilder("js", mathReader, "math.js").build();
            RAND_SOURCE = Source.newBuilder("js", randReader, "rand.js").build();
            R_SOURCE = Source.newBuilder("js", rReader, "R.js").build();
        }

        context.eval(MATH_SOURCE);
        context.eval(RAND_SOURCE);
        context.eval(R_SOURCE);

        execute("__rand = rand()");
        execute("__R = R()");
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
        if (o instanceof Value) {
            return ((Value) o).as(double[][].class);
        } else if (o == null) {
            return null;
        } else if (o instanceof double[][]) {
            return (double[][]) o;
        } else if (o instanceof double[]) {
            return new double[][]{(double[]) o};
        } else if (o instanceof Double) {
            return new double[][]{{(Double) o}};
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
            } catch (Exception ignored) {
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
            } catch (Exception ignored) {
            }

            try{
                return asArray(o);
            } catch (Exception ignored) {
            }
        }
        try {
            return asList(o);
        } catch (Exception ignored) {
        }
        return o;
    }

    @Override
    public void putVariable(String varname, Object var) {
        context.getBindings("js").putMember(varname, var);
    }

    @Override
    public void close() {
        context.close();
    }
}
