package org.math.R.R2js;

import org.graalvm.polyglot.*;
import org.math.R.RLog;
import org.math.R.RLogPrintStream;

import java.io.File;
import java.io.PrintStream;
import java.net.URL;
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


    protected synchronized void loadJSLibraries() throws Exception {
        if(MATH_SOURCE == null) {
            URL math_url = getClass().getResource(MATH_JS_FILE);
            URL rand_url = getClass().getResource(RAND_JS_FILE);
            URL r_url = getClass().getResource(R_JS_FILE);
            if (math_url == null || rand_url == null || r_url == null) {
                throw new IllegalArgumentException("file not found!");
            } else {
                MATH_SOURCE = Source.newBuilder("js",new File(math_url.toURI())).build();
                RAND_SOURCE = Source.newBuilder("js",new File(rand_url.toURI())).build();
                R_SOURCE = Source.newBuilder("js",new File(r_url.toURI())).build();
            }
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


//        return o;
////        // If it's a ScriptObjectMirror, it can be an array or a matrix
//        if (o instanceof Integer) {
//            return Double.valueOf((int) o);
//        } else if (o instanceof ScriptObjectMirror) {
//            try {
////                System.err.println("// Casting of the ScriptObjectMirror to a double matrix");
//                return ((ScriptObjectMirror) o).to(double[][].class);
//            } catch (Exception e) {//e.printStackTrace();
//            }
//
//            try {
////                 System.err.println("// Casting of the ScriptObjectMirror to a string array");
//                String[] stringArray = ((ScriptObjectMirror) o).to(String[].class);
//
////                 System.err.println("// Check if the String[] array can be cast to a double[] array");
//                try {
//                    for (String string : stringArray) {
//                        Double.valueOf(string);
//                    }
//                } catch (Exception e) {//e.printStackTrace();
//                    // It can't be cast to double[] so we return String[]
//                    return stringArray;
//                }
//
////                 System.err.println("// return double[] array");
//                return ((ScriptObjectMirror) o).to(double[].class);
//
//            } catch (Exception e) {//e.printStackTrace();
//            }
//
//            try {
////                 System.err.println("// Casting of the ScriptObjectMirror to a double array");
//                return ((ScriptObjectMirror) o).to(double[].class);
//            } catch (Exception e) {//e.printStackTrace();
//            }
//
//            try {
////                System.err.println(" // Casting of the ScriptObjectMirror to a list/map");
//                Map m = ((ScriptObjectMirror) o).to(Map.class);
//                try {
//                    return asMatrix(m);
//                } catch (ClassCastException c) {
//                    //c.printStackTrace();
//                    return m;
//                }
//            } catch (Exception e) {//e.printStackTrace();
//            }
//
//            throw new IllegalArgumentException("Impossible to cast object: ScriptObjectMirror");
//        } else {
//            return o;
//        }
    }

    @Override
    public synchronized void end() {
        //js = null;
        // TODO
        super.end();
    }
}
