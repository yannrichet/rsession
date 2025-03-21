<<<<<<<< HEAD:src/main/java/org/math/R/executors/NashornExecutor.java
package org.math.R.executors;
========
package org.math.R;
>>>>>>>> master:src/main/java/org/math/R/R2jsSession.java

import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.Charset;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import javax.script.ScriptEngine;
import javax.script.ScriptEngineManager;
import javax.script.ScriptException;
import jdk.nashorn.api.scripting.ScriptObjectMirror;
import jdk.nashorn.api.scripting.ScriptUtils;
import org.math.R.Rsession;

import static org.math.R.AbstractR2jsSession.MATH_JS_FILE;
import static org.math.R.AbstractR2jsSession.RAND_JS_FILE;
import static org.math.R.AbstractR2jsSession.R_JS_FILE;

public class NashornExecutor extends JavaScriptExecutor {

    private ScriptEngine js;

    // Map containing js libraries already loaded (to not reload them at each instance of R2jsSession)
    private final static Map<String, Object> jsLibraries = Collections.synchronizedMap(new HashMap<>());

    @Override
    public Object execute(String script) throws ScriptException {
        return js.eval(script);
    }

    @Override
    public synchronized void loadJSLibraries() throws Exception {
        ScriptEngineManager manager = new ScriptEngineManager(null);
        js = manager.getEngineByName("JavaScript");
        if (js==null) js = manager.getEngineByName("js");
        if (js==null) js = manager.getEngineByExtension("js");
        if (js==null) js = manager.getEngineByName("nashorn");
        if (js==null) js = manager.getEngineByName("Nashorn");
        if (js==null) js = new jdk.nashorn.api.scripting.NashornScriptEngineFactory().getScriptEngine();
        if (js==null) throw new IllegalArgumentException("Could not load JavaScript ScriptEngine: "+manager.getEngineFactories());

        // Loading math.JS
        if (!jsLibraries.containsKey("math")) {
            InputStream mathInputStream = this.getClass().getResourceAsStream(MATH_JS_FILE);
            js.eval(new InputStreamReader(mathInputStream, Charset.forName("UTF-8")));
            jsLibraries.put("math", js.get("math"));
        } else {
            js.put("math", jsLibraries.get("math"));
        }

        js.eval("var parser = math.parser();");
        // Change 'Matrix' mathjs config by 'Array'
        js.eval("math.config({matrix: 'Array'})");
        js.eval("var str = String.prototype;");

        // suport T/F TRUE/FALSE shortcuts
        js.eval("var T = true;");
        js.eval("var F = false;");

        // Loading rand.js
        if (!jsLibraries.containsKey("rand")) {
            InputStream randInputStream = this.getClass().getResourceAsStream(RAND_JS_FILE);
            js.eval(new InputStreamReader(randInputStream, Charset.forName("UTF-8")));
            js.eval("__rand = rand()");
            jsLibraries.put("__rand", js.get("rand"));
        } else {
            js.put("__rand", jsLibraries.get("rand"));
        }

        // Loading R.js
        if (!jsLibraries.containsKey("R")) {
            InputStream RInputStream = this.getClass().getResourceAsStream(R_JS_FILE);
            js.eval(new InputStreamReader(RInputStream, Charset.forName("UTF-8")));
            js.eval("__R = R()");
            jsLibraries.put("__R", js.get("R"));
        } else {
            js.put("__R", jsLibraries.get("R"));
        }
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
            return (new double[][]{(double[]) o});
        } else if (o instanceof Double) {
            return new double[][]{{(double) o}};
        } else {
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
            return (double) (int) o;
        } else if (o instanceof ScriptObjectMirror) {
            try {
                return ((ScriptObjectMirror) o).to(double[][].class);
            } catch (Exception ignored) {
            }

            try {
                String[] stringArray = ((ScriptObjectMirror) o).to(String[].class);
                try {
                    for (String string : stringArray) {
                        Double.valueOf(string);
                    }
                } catch (Exception e) {//e.printStackTrace();
                    // It can't be cast to double[] so we return String[]
                    return stringArray;
                }
                return ((ScriptObjectMirror) o).to(double[].class);
            } catch (Exception ignored) {
            }

            try {
                return ((ScriptObjectMirror) o).to(double[].class);
            } catch (Exception ignored) {
            }

            try {
                Map m = ((ScriptObjectMirror) o).to(Map.class);
                try {
                    return asMatrix(m);
                } catch (ClassCastException c) {
                    return m;
                }
            } catch (Exception ignored) {
            }

            throw new IllegalArgumentException("Impossible to cast object: ScriptObjectMirror");
        } else {
            return o;
        }
    }

    @Override
    public void putVariable(String varname, Object var) {
        this.js.put(varname, var);
    }

    @Override
    public void close() {
        this.js = null;
    }
}