package org.math.R.executors;

import java.io.Reader;
import java.lang.reflect.Method;
import java.util.Map;
import java.util.logging.Level;
import java.util.logging.Logger;
import javax.script.ScriptEngine;
import javax.script.ScriptEngineManager;
import javax.script.ScriptException;
import jdk.nashorn.api.scripting.ScriptObjectMirror;
import jdk.nashorn.api.scripting.ScriptUtils;

public class NashornExecutor extends JavaScriptExecutor {
    private static final Logger LOGGER = Logger.getLogger(NashornExecutor.class.getName());

    @Override
    public Object execute(String script) {
        try {
            // Load Nashorn ScriptEngine using Reflection
            Class<?> scriptEngineManagerClass = Class.forName("javax.script.ScriptEngineManager");
            Object manager = scriptEngineManagerClass.getDeclaredConstructor().newInstance();
            Method getEngineByNameMethod = scriptEngineManagerClass.getMethod("getEngineByName", String.class);
            Object engine = getEngineByNameMethod.invoke(manager, "Nashorn");

            if (engine == null) {
                throw new RuntimeException("Nashorn engine not available");
            }

            Method evalMethod = engine.getClass().getMethod("eval", String.class);
            return evalMethod.invoke(engine, script);
        } catch (Exception e) {
            LOGGER.log(Level.SEVERE, "Error executing script", e);
            return null;
        }
    }

    @Override
    public void loadJSLibraries() throws Exception {

    }

    @Override
    public void loadLibrary(String name, Reader reader) {

    }

    @Override
    public void evalLibrary(String name) {

    }

    @Override
    public double asDouble(Object o) throws ClassCastException {
        if (o instanceof Double) {
            return (Double) o; // because already cast in Nashorn/jdk11 (but not in Nashorn/jdk8 !!)
        }
        return (double) ScriptUtils.convert(o, double.class);
    }

    @Override
    public double[] asArray(Object o) throws ClassCastException {
        if (o instanceof double[]) {
            return (double[]) o; // because already cast in Nashorn/jdk11 (but not in Nashorn/jdk8 !!)
        } else if (o instanceof Double) {
            return new double[]{(double)o};
        }
        Object co = ScriptUtils.convert(o, double[].class);
        if (co instanceof Double) {
            return new double[]{(double)co};
        }
        return (double[]) co;
    }

    @Override
    public double[][] asMatrix(Object o) throws ClassCastException {
        if (o == null) {
            return null;
        }
        if (o instanceof double[][]) {
            return (double[][]) o;
        } else if (o instanceof double[]) {
            return t(new double[][]{(double[]) o});
        } else if (o instanceof Double) {
            return new double[][]{{(double) o}};
        } else /*if (o instanceof Map)*/ {
            double[][] vals = null;
            int i = 0;
            try {
                for (Object k : ((Map) o).keySet()) {
                    double[] v = null;
                    if (o instanceof ScriptObjectMirror) {
                        try {
                            v = (double[]) ((ScriptObjectMirror) o).to(double[].class);
                        } catch (Exception ex) {
                            //ex.printStackTrace();
                            throw new ClassCastException("[asMatrix] Cannot cast ScriptObjectMirror list element to double[] " + ((Map) o).get(k) + " for key " + k + " in " + o);
                        }
                    } else {
                        try {
                            v = (double[]) ((Map) o).get(k);
                        } catch (Exception ex) {
                            //ex.printStackTrace();
                            throw new ClassCastException("[asMatrix] Cannot cast list element to double[] " + ((Map) o).get(k) + " for key " + k + " in " + o);
                        }
                    }
                    if (v == null) {
                        throw new ClassCastException("[asMatrix] Cannot get list element as double[] " + ((Map) o).get(k) + " for key " + k + " in " + o);
                    }
                    if (vals == null) {
                        vals = new double[v.length][((Map) o).size()];
                    }
                    for (int j = 0; j < v.length; j++) {
                        vals[j][i] = v[j];
                    }
                    i++;
                }
                return vals;
            } catch (Exception ex) {
                throw new ClassCastException("[asMatrix] Cannot cast Map to matrix: " + ex.getMessage());
            }
        }
    }

    @Override
    public String asString(Object o) throws ClassCastException {
        if (o instanceof String) {
            return (String) o; // because already cast in Nashorn/jdk11 (but not in Nashorn/jdk8 !!)
        }
        return (String) ScriptUtils.convert(o, String.class);
    }

    @Override
    public String[] asStrings(Object o) throws ClassCastException {
        if (o instanceof String[]) {
            return (String[]) o; // because already cast in Nashorn/jdk11 (but not in Nashorn/jdk8 !!)
        } else if (o instanceof String) {
            return new String[]{(String)o};
        }
        Object co = ScriptUtils.convert(o, String[].class);
        if (co instanceof String) {
            return new String[]{(String)co};
        }
        return (String[]) co;
    }

    @Override
    public boolean asLogical(Object o) throws ClassCastException {
        if (o instanceof Boolean) {
            return (Boolean) o; // because already cast in Nashorn/jdk11 (but not in Nashorn/jdk8 !!)
        }
        if (o instanceof Rsession.RException) {
            throw new IllegalArgumentException("[asLogical] Exception: " + ((Rsession.RException) o).getMessage());
        }
        return (boolean) ScriptUtils.convert(o, boolean.class);
    }

    @Override
    public boolean[] asLogicals(Object o) throws ClassCastException {
        if (o instanceof boolean[]) {
            return (boolean[]) o; // because already cast in Nashorn/jdk11 (but not in Nashorn/jdk8 !!)
        } else if (o instanceof Boolean) {
            return new boolean[]{(boolean)o};
        }
        Object co =  ScriptUtils.convert(o, boolean[].class);
        if (co instanceof Boolean) {
            return new boolean[]{(boolean)co};
        }
        return (boolean[]) co;
    }

    @Override
    public Map asList(Object o) throws ClassCastException {
        if (o instanceof Map) {
            return (Map) o; // because already cast in Nashorn/jdk11 (but not in Nashorn/jdk8 !!)
        }
        return (Map) ScriptUtils.convert(o, Map.class);
    }

    @Override
    public Object cast(Object o) throws ClassCastException {
        // If it's a ScriptObjectMirror, it can be an array or a matrix
        if (o instanceof Integer) {
            return Double.valueOf((int) o);
        } else if (o instanceof ScriptObjectMirror) {
            try {
//                System.err.println("// Casting of the ScriptObjectMirror to a double matrix");
                return ((ScriptObjectMirror) o).to(double[][].class);
            } catch (Exception e) {//e.printStackTrace();
            }

            try {
//                 System.err.println("// Casting of the ScriptObjectMirror to a string array");
                String[] stringArray = ((ScriptObjectMirror) o).to(String[].class);

//                 System.err.println("// Check if the String[] array can be cast to a double[] array");
                try {
                    for (String string : stringArray) {
                        Double.valueOf(string);
                    }
                } catch (Exception e) {//e.printStackTrace();
                    // It can't be cast to double[] so we return String[]
                    return stringArray;
                }

//                 System.err.println("// return double[] array");
                return ((ScriptObjectMirror) o).to(double[].class);

            } catch (Exception e) {//e.printStackTrace();
            }

            try {
//                 System.err.println("// Casting of the ScriptObjectMirror to a double array");
                return ((ScriptObjectMirror) o).to(double[].class);
            } catch (Exception e) {//e.printStackTrace();
            }

            try {
//                System.err.println(" // Casting of the ScriptObjectMirror to a list/map");
                Map m = ((ScriptObjectMirror) o).to(Map.class);
                try {
                    return asMatrix(m);
                } catch (ClassCastException c) {
                    //c.printStackTrace();
                    return m;
                }
            } catch (Exception e) {//e.printStackTrace();
            }

            throw new IllegalArgumentException("Impossible to cast object: ScriptObjectMirror");
        } else {
            return o;
        }
    }
}