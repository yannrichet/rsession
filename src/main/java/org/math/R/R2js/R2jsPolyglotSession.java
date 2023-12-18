package org.math.R.R2js;

import org.graalvm.polyglot.*;
import org.math.R.RLog;

import java.io.File;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.Charset;
import java.util.*;

public class R2jsPolyglotSession extends R2jsSession {

    private Engine engine;
    private Context context;

    // Map containing js libraries already loaded (to not reload them at each instance of R2jsSession)
    private final static Map<String, Object> jsLibraries = Collections.synchronizedMap(new HashMap<>());

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
    protected R2jsPolyglotSession(RLog console, Properties properties, String environmentName) {
        super(console, properties, environmentName);
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
        Source mathSource = Source.newBuilder("js",new File("/home/chabs/Documents/workspaces/rsession/src/main/resources/org/math/R/math.js")).build();
        Source randSource = Source.newBuilder("js",new File("/home/chabs/Documents/workspaces/rsession/src/main/resources/org/math/R/rand.js")).build();
        Source rSource = Source.newBuilder("js",new File("/home/chabs/Documents/workspaces/rsession/src/main/resources/org/math/R/R.js")).build();
        this.context.eval(mathSource);
        this.context.eval(randSource);
        this.context.eval(rSource);

        this.simpleEval("__rand = rand()");
        this.simpleEval("__R = R()");

    }

    @Override
    protected void createScriptEngine() {
        engine = Engine.create();
        context = Context.newBuilder().
                allowHostClassLookup(s -> true).
                allowHostAccess(HostAccess.ALL).
                engine(engine).build();
    }

    @Override
    public double asDouble(Object o) throws ClassCastException {
        return ((Value) o).asDouble();
    }

    @Override
    public double[] asArray(Object o) throws ClassCastException {
        return ((Value)o).as(double[].class);
//        if (o instanceof double[]) {
//            return (double[]) o; // because already cast in Nashorn/jdk11 (but not in Nashorn/jdk8 !!)
//        } else if (o instanceof Double) {
//            return new double[]{(double)o};
//        }
//        Object co = ScriptUtils.convert(o, double[].class);
//        if (co instanceof Double) {
//            return new double[]{(double)co};
//        }
//        return (double[]) co;
    }

    @Override
    public double[][] asMatrix(Object o) throws ClassCastException {
        return ((Value)o).as(double[][].class);
//        if (o == null) {
//            return null;
//        }
//        if (o instanceof double[][]) {
//            return (double[][]) o;
//        } else if (o instanceof double[]) {
//            return t(new double[][]{(double[]) o});
//        } else if (o instanceof Double) {
//            return new double[][]{{(double) o}};
//        } else /*if (o instanceof Map)*/ {
//            double[][] vals = null;
//            int i = 0;
//            try {
//                for (Object k : ((Map) o).keySet()) {
//                    double[] v = null;
//                    if (o instanceof ScriptObjectMirror) {
//                        try {
//                            v = (double[]) ((ScriptObjectMirror) o).to(double[].class);
//                        } catch (Exception ex) {
//                            //ex.printStackTrace();
//                            throw new ClassCastException("[asMatrix] Cannot cast ScriptObjectMirror list element to double[] " + ((Map) o).get(k) + " for key " + k + " in " + o);
//                        }
//                    } else {
//                        try {
//                            v = (double[]) ((Map) o).get(k);
//                        } catch (Exception ex) {
//                            //ex.printStackTrace();
//                            throw new ClassCastException("[asMatrix] Cannot cast list element to double[] " + ((Map) o).get(k) + " for key " + k + " in " + o);
//                        }
//                    }
//                    if (v == null) {
//                        throw new ClassCastException("[asMatrix] Cannot get list element as double[] " + ((Map) o).get(k) + " for key " + k + " in " + o);
//                    }
//                    if (vals == null) {
//                        vals = new double[v.length][((Map) o).size()];
//                    }
//                    for (int j = 0; j < v.length; j++) {
//                        vals[j][i] = v[j];
//                    }
//                    i++;
//                }
//                return vals;
//            } catch (Exception ex) {
//                throw new ClassCastException("[asMatrix] Cannot cast Map to matrix: " + ex.getMessage());
//            }
//        }
    }

    @Override
    public String asString(Object o) throws ClassCastException {
        return ((Value) o).asString();
    }

    @Override
    public String[] asStrings(Object o) throws ClassCastException {
        try {
            return ((Value)o).as(String[].class);
        } catch (Exception e) {
            Object[] objects = ((Value)o).as(Object[].class);
            return Arrays.stream(objects).map(Object::toString).
                    toArray(String[]::new);
        }
//        if (o instanceof String[]) {
//            return (String[]) o; // because already cast in Nashorn/jdk11 (but not in Nashorn/jdk8 !!)
//        } else if (o instanceof String) {
//            return new String[]{(String)o};
//        }
//        Object co = ScriptUtils.convert(o, String[].class);
//        if (co instanceof String) {
//            return new String[]{(String)co};
//        }
//        return (String[]) co;
    }

    @Override
    public boolean asLogical(Object o) throws ClassCastException {
        return ((Value) o).asBoolean();
    }

    @Override
    public boolean[] asLogicals(Object o) throws ClassCastException {
        return ((Value)o).as(boolean[].class);
    }

    @Override
    public Map asList(Object o) throws ClassCastException {
        return ((Value)o).as(Map.class);
//        if (o instanceof Map) {
//            return (Map) o; // because already cast in Nashorn/jdk11 (but not in Nashorn/jdk8 !!)
//        }
//        return (Map) ScriptUtils.convert(o, Map.class);
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
