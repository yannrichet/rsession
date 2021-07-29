package org.math.R;

import java.io.File;
import java.io.IOException;
import java.io.PrintStream;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.util.Arrays;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import javax.script.ScriptException;
import org.apache.commons.io.FileUtils;
import static org.math.R.Rsession.HEAD_EXCEPTION;
import org.math.array.DoubleArray;
import org.renjin.eval.Session;
import org.renjin.eval.SessionBuilder;
import org.renjin.primitives.matrix.Matrix;
import org.renjin.primitives.packaging.PackageLoader;
import org.renjin.script.RenjinScriptEngine;
import org.renjin.script.RenjinScriptEngineFactory;
import org.renjin.sexp.DoubleArrayVector;
import org.renjin.sexp.DoubleVector;
import org.renjin.sexp.IntVector;
import org.renjin.sexp.ListVector;
import org.renjin.sexp.Logical;
import static org.renjin.sexp.Logical.TRUE;
import org.renjin.sexp.LogicalVector;
import org.renjin.sexp.Null;
import org.renjin.sexp.SEXP;
import org.renjin.sexp.StringArrayVector;
import org.renjin.sexp.StringVector;

/**
 *
 * @author richet
 */
public class RenjinSession extends Rsession implements RLog {

    protected RenjinScriptEngine R = null;
    File wdir;
    
    private static final String ENVIRONMENT_DEFAULT = "..renjin..";

    public static RenjinSession newInstance(final RLog console, Properties properties) {
        return new RenjinSession(console, properties);
    }

    public RenjinSession(RLog console, Properties properties) {
        super(console);
        envName = ENVIRONMENT_DEFAULT;

        try {
            if (Class.forName("org.renjin.aether.AetherPackageLoader", false, this.getClass().getClassLoader()) != null) {
                Session session = new SessionBuilder().bind(PackageLoader.class, new org.renjin.aether.AetherPackageLoader()).withDefaultPackages().build();
                R = new RenjinScriptEngineFactory().getScriptEngine(session);
            } else {
                throw new ClassNotFoundException("org.renjin.aether.AetherPackageLoader missing");
            }
        } catch (ClassNotFoundException e) {
            Log.Err.println("Could not access some Renjin dependency: " + e);
            Log.Err.println("Using Renjin without packages repository");
            R = new RenjinScriptEngineFactory().getScriptEngine();
        }

        if (R == null) {
            throw new RuntimeException("Renjin Script Engine not found in the classpath.");
        }

        try {
            int rand = Math.round((float) Math.random() * 10000);
            wdir = new File(new File(System.getProperty("RSESSION_HOME",FileUtils.getTempDirectoryPath()), ".Renjin"), "" + rand);
            if (!wdir.mkdirs()) {
                wdir = new File(new File(FileUtils.getUserDirectory(), ".Renjin"), "" + rand);
                if (!wdir.mkdirs()) {
                    throw new IOException("Could not create directory " + 
                    new File(new File(System.getProperty("RSESSION_HOME",FileUtils.getTempDirectoryPath()), ".Renjin"), "" + rand) +
                    "\n or " +
                    new File(new File(FileUtils.getUserDirectory(), ".Renjin"), "" + rand));
                }
            }
            setwd(wdir);
            wdir.deleteOnExit();
        } catch (Exception ex) {
            log("Could not use directory: " + wdir + "\n" + ex.getMessage(), Level.ERROR);
        }

        SINK_FILE = SINK_FILE_BASE + "-renjin" + this.hashCode();

        setenv(properties);

        repos = "org.renjin.cran";
    }

    public RenjinSession(final PrintStream p, Properties properties) {
        this(new RLogPrintStream(p), properties);
    }

    public String gethomedir() {
        return System.getProperty("user.home");
    }  
    
    @Override
    boolean isWindows() {
        return RserveDaemon.isWindows();
    }

    @Override
    boolean isMacOSX() {
        return RserveDaemon.isMacOSX();
    }

    @Override
    boolean isLinux() {
        return RserveDaemon.isLinux();
    }

    @Override
    public synchronized boolean silentlyVoidEval(String expression, boolean tryEval) {
        if (R == null) {
            log(HEAD_EXCEPTION + "R environment not initialized.", Level.ERROR);
            return false;
        }
        if (expression == null) {
            return false;
        }
        if (expression.trim().length() == 0) {
            return true;
        }
        for (EvalListener b : eval) {
            b.eval(expression);
        }
        SEXP e = null;
        //synchronized (R) {
            try {
                if (SINK_OUTPUT) {
                    R.eval(".f <- file('" + toRpath(SINK_FILE) + "',open='wt')");
                    R.eval("sink(.f,type='output')");
                }
                if (SINK_MESSAGE) {
                    R.eval(".fm <- file('" + toRpath(SINK_FILE) + ".m',open='wt')");
                    R.eval("sink(.fm,type='message')");
                }
                if (tryEval) {
                    e = ((SEXP) R.eval("try(eval(parse(text='" + expression.replace("'", "\\'") + "')),silent=FALSE)"));
                } else {
                    e = ((SEXP) R.eval(expression));
                }
            } catch (Exception ex) {
                log(HEAD_EXCEPTION + ex.getMessage() + "\n  " + expression, Level.ERROR);
            } finally {
                if (SINK_OUTPUT) {
                    try {
                        R.eval("sink(type='output')");
                        lastOuput = asString(R.eval("paste(collapse='\n',readLines('" + toRpath(SINK_FILE) + "'))"));
                        log(lastOuput, Level.OUTPUT);
                    } catch (Exception ex) {
                        lastOuput = ex.getMessage();
                        log(lastOuput, Level.WARNING);
                    } finally {
                        try {
                            R.eval("close(.f)");
                            R.eval("unlink('" + toRpath(SINK_FILE) + "')");
                        } catch (Exception ex) {
                            log(HEAD_EXCEPTION + ex.getMessage(), Level.ERROR);
                        }
                    }
                }
                if (SINK_MESSAGE) {
                    try {
                        R.eval("sink(type='message')");
                        lastMessage = asString(R.eval("paste(collapse='\n',readLines('" + toRpath(SINK_FILE) + ".m'))"));
                        log(lastMessage, Level.INFO);
                    } catch (Exception ex) {
                        lastMessage = ex.getMessage();
                        log(lastMessage, Level.WARNING);
                    } finally {
                        try {
                            R.eval("close(.fm)");
                            R.eval("unlink('" + toRpath(SINK_FILE) + ".m')");
                        } catch (Exception ex) {
                            log(HEAD_EXCEPTION + ex.getMessage(), Level.ERROR);
                        }
                    }
                }
            }
        //}

        if (tryEval && e != null) {
            try {
                if (e.inherits("try-error")/*e.isString() && e.asStrings().length > 0 && e.asString().toLowerCase().startsWith("error")*/) {
                    log(HEAD_EXCEPTION + e.asString() + "\n  " + expression, Level.WARNING);
                    return false;
                }
            } catch (Exception ex) {
                log(HEAD_ERROR + ex.getMessage() + "\n  " + expression, Level.ERROR);
                return false;
            }
        }
        return true;
    }

    @Override
    public synchronized Object silentlyRawEval(String expression, boolean tryEval) {
        if (R == null) {
            log(HEAD_EXCEPTION + "R environment not initialized.", Level.ERROR);
            return new RException(HEAD_EXCEPTION + "R environment not initialized.");
        }
        if (expression == null) {
            return null;
        }
        if (expression.trim().length() == 0) {
            return null;
        }
        for (EvalListener b : eval) {
            b.eval(expression);
        }
        Object e = null;
        //synchronized (R) {
            try {
                if (SINK_OUTPUT) {
                    R.eval(".f <- file('" + toRpath(SINK_FILE) + "',open='wt')");
                    R.eval("sink(.f,type='output')");
                }
                if (SINK_MESSAGE) {
                    R.eval(".fm <- file('" + toRpath(SINK_FILE) + ".m',open='wt')");
                    R.eval("sink(.fm,type='message')");
                }
                if (tryEval) {
                    e = (SEXP) (R.eval("try(eval(parse(text='" + expression.replace("'", "\\'") + "')),silent=FALSE)"));
                } else {
                    e = (SEXP) (R.eval(expression));
                }
            } catch (Exception ex) {
                log(HEAD_EXCEPTION + ex.getMessage() + "\n  " + expression, Level.ERROR);
                return new RException(HEAD_EXCEPTION + ex.getMessage() + "\n  " + expression);
            } finally {
                if (SINK_OUTPUT) {
                    try {
                        R.eval("sink(type='output')");
                        lastOuput = asString(R.eval("paste(collapse='\n',readLines('" + toRpath(SINK_FILE) + "'))"));
                        log(lastOuput, Level.OUTPUT);
                    } catch (Exception ex) {
                        lastOuput = ex.getMessage();
                        log(lastOuput, Level.WARNING);
                    } finally {
                        try {
                            R.eval("close(.f)"); // because Renjin.sink() do not properly closeLog connection, so calling it explicitely
                            R.eval("unlink('" + toRpath(SINK_FILE) + "')");
                        } catch (Exception ex) {
                            log(HEAD_EXCEPTION + ex.getMessage(), Level.ERROR);
                        }
                    }
                }
                if (SINK_MESSAGE) {
                    try {
                        R.eval("sink(type='message')");
                        lastMessage = asString(R.eval("paste(collapse='\n',readLines('" + toRpath(SINK_FILE) + ".m'))"));
                        log(lastMessage, Level.INFO);
                    } catch (Exception ex) {
                        lastMessage = ex.getMessage();
                        log(lastMessage, Level.WARNING);
                    } finally {
                        try {
                            R.eval("close(.fm)");
                            R.eval("unlink('" + toRpath(SINK_FILE) + ".m')");
                        } catch (Exception ex) {
                            log(HEAD_EXCEPTION + ex.getMessage(), Level.ERROR);
                        }
                    }
                }
            //}
        }

        if (tryEval && e != null) {
            try {
                if (((SEXP) e).inherits("try-error")/*e.isString() && e.asStrings().length > 0 && e.asString().toLowerCase().startsWith("error")*/) {
                    log(HEAD_EXCEPTION + ((SEXP) e).asString() + "\n  " + expression, Level.WARNING);
                    e = new RException(HEAD_EXCEPTION + ((SEXP) e).asString() + "\n  " + expression);
                }
            } catch (Exception ex) {
                log(HEAD_ERROR + ex.getMessage() + "\n  " + expression, Level.ERROR);
                return new RException(HEAD_ERROR + ex.getMessage() + "\n  " + expression);
            }
        }
        return e;
    }

    @Override
    public synchronized boolean set(String varname, double[][] data, String... names) {
        note_code("`" + varname + "` <- " + (data == null ? "list()" : toRcode(data)));
        note_code("names(" + varname + ") <- " + toRcode(names));
        note_code("`" + varname + "` <- data.frame(" + varname + ")");

        if (data == null || data[0].length==0) {
            if (names == null) {
                return false;
            }
            List<SEXP> nulls = new LinkedList<>();
            for (int i = 0; i < names.length; i++) {
                nulls.add(Null.INSTANCE.clone());
            }

            ListVector l = new ListVector(nulls);
            //synchronized (R) {
                R.put(varname, l);
                R.put(varname + ".names", new StringArrayVector(names));
                try {
                    R.eval("names(" + varname + ") <- " + varname + ".names");
                    //R.eval(varname + " <- data.frame(" + varname + ")");
                } catch (ScriptException ex) {
                    log(HEAD_ERROR + ex.getMessage(), Level.ERROR);
                    return false;
                }
            //}
            return true;

        } else {
            DoubleVector[] d = new DoubleVector[data[0].length];
            for (int i = 0; i < d.length; i++) {
                d[i] = new DoubleArrayVector(DoubleArray.getColumnCopy(data, i));
            }
            ListVector l = new ListVector(d);
            //l.setAttribute(Symbols.NAMES, new StringArrayVector(names)); 
            //synchronized (R) {
                R.put(varname, l);
                //R.put("names("+varname+")",new StringArrayVector(names));
                R.put(varname + ".names", new StringArrayVector(names));
                try {
                    R.eval("names(" + varname + ") <- " + varname + ".names");
                    R.eval(varname + " <- data.frame(" + varname + ")");
                } catch (ScriptException ex) {
                    log(HEAD_ERROR + ex.getMessage(), Level.ERROR);
                    return false;
                }
            //}
            return true;
        }
    }

    @Override
    public synchronized boolean set(String varname, Object var) {
        note_code("`" + varname + "` <- " + toRcode(var));

        if (var instanceof double[][]) {
            double[][] dd = (double[][]) var;
            double[] d = reshapeAsRow(dd);
            //synchronized (R) {
                R.put(varname, d);
                try {
                    R.eval(varname + " <- matrix(" + varname + ",nrow=" + dd.length + ")");
                } catch (ScriptException ex) {
                    log(HEAD_ERROR + ex.getMessage(), Level.ERROR);
                    return false;
                }
            //}
        } else {
            //synchronized (R) {
                R.put(varname, var);
            //}
        }
        return true;
    }

    // http://docs.renjin.org/en/latest/library/moving-data-between-java-and-r-code.html
    @Override
    public double asDouble(Object o) throws ClassCastException {
        if (o == null) {
            return (Double) null;
        }
        if (o instanceof Double) {
            return (double) o;
        }
        if (!(o instanceof SEXP)) {
            throw new IllegalArgumentException("[asDouble] Not an SEXP object: " + o);
        }
        try {
            return ((SEXP) o).asReal();
        } catch (Exception ex) {
            throw new ClassCastException("[asDouble] Cannot cast to double " + o);
        }
    }

    @Override
    public double[] asArray(Object o) throws ClassCastException {
        if (o == null) {
            return null;
        }
        if (o instanceof double[]) {
            return (double[]) o;
        }
        if (o instanceof Double) {
            return new double[]{(double) o};
        }
        if (!(o instanceof SEXP)) {
            throw new IllegalArgumentException("[asArray] Not an SEXP object: " + o);
        }
        if (!(o instanceof DoubleVector)) {
            throw new IllegalArgumentException("[asArray] Not a DoubleVector object: " + o);
        }
        try {
            return ((DoubleVector) o).toDoubleArray();
        } catch (Exception ex) {
            throw new ClassCastException("[asArray] Cannot cast to double[] " + o);
        }
    }

    @Override
    public double[][] asMatrix(Object o) throws ClassCastException {
        if (o == null) {
            return null;
        }
        if (o instanceof double[][]) {
            return (double[][]) o;
        }
        if (o instanceof Map) {
            double[][] vals = null;
            int i = 0;
            try {
                for (Object k : ((Map) o).keySet()) {
                    double[] v = null;
                    try {
                        v = (double[]) ((Map) o).get(k);
                    } catch (Exception ex) {
                        throw new ClassCastException("[asMatrix] Cannot cast list element to double[] " + ((Map) o).get(k) + " for key " + k + " in " + o);
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
                throw new ClassCastException("[asMatrix] Cannot cast Map to matrix: "+ex.getMessage());
            }
        }
        if (o instanceof double[]) {
            return t(new double[][]{(double[]) o});
        }
        if (o instanceof Double) {
            return new double[][]{{(double) o}};
        }
        if (!(o instanceof SEXP)) {
            throw new IllegalArgumentException("[asMatrix] Not an SEXP object: " + o);
        }
        if (!(o instanceof DoubleVector)) {
            throw new IllegalArgumentException("[asMatrix] Not a DoubleVector object: " + o);
        }
        try {
            Matrix m = new Matrix((DoubleVector) o);
            double[][] mm = new double[m.getNumRows()][m.getNumCols()];
            for (int i = 0; i < mm.length; i++) {
                for (int j = 0; j < mm[i].length; j++) {
                    mm[i][j] = m.getElementAsDouble(i, j);
                }
            }
            return mm;
        } catch (Exception ex) {
            throw new ClassCastException("[asMatrix] Cannot cast to double[][] " + o);
        }
    }

    @Override
    public String asString(Object o) throws ClassCastException {
        if (o == null) {
            return null;
        }
        if (o instanceof String) {
            return (String) o;
        }
        if (!(o instanceof SEXP)) {
            throw new IllegalArgumentException("[asString] Not an SEXP object: " + o);
        }
        try {
            return ((SEXP) o).asString();
        } catch (Exception ex) {
            throw new ClassCastException("[asString] Cannot cast to String " + o);
        }
    }

    @Override
    public String[] asStrings(Object o) throws ClassCastException {
        if (o == null) {
            return null;
        }
        if (o instanceof String[]) {
            return (String[]) o;
        }
        if (o instanceof String) {
            return new String[]{(String) o};
        }
        if (!(o instanceof SEXP)) {
            throw new IllegalArgumentException("[asStrings] Not an SEXP object: " + o);
        }
        if (!(o instanceof StringVector)) {
            throw new IllegalArgumentException("[asStrings] Not a StringVector object: " + o);
        }
        try {
            int n = ((SEXP) o).length();
            String[] s = new String[n];
            for (int i = 0; i < n; i++) {
                s[i] = ((SEXP) o).getElementAsSEXP(i).asString();
            }
            return s;
        } catch (Exception ex) {
            throw new ClassCastException("[asStrings] Cannot cast to String[] " + o);
        }
    }

    @Override
    public int asInteger(Object o) throws ClassCastException {
        if (o == null) {
            return (Integer) null;
        }
        return asIntegers(o)[0];
    }

    @Override
    public int[] asIntegers(Object o) throws ClassCastException {
        if (o == null) {
            return null;
        }
        if (o instanceof int[]) {
            return (int[]) o;
        }
        if (o instanceof Integer) {
            return new int[]{(int) o};
        }
        if (!(o instanceof SEXP)) {
            throw new IllegalArgumentException("[asIntegers] Not an SEXP object: " + o);
        }
        if (!(o instanceof IntVector)) {
            throw new IllegalArgumentException("[asIntegers] Not a IntVector object: " + o);
        }
        try {
            return ((IntVector) o).toIntArray();
        } catch (Exception ex) {
            throw new ClassCastException("[asIntegers] Cannot cast to int[] " + o);
        }
    }

    @Override
    public boolean asLogical(Object o) throws ClassCastException {
        if (o == null) {
            return (Boolean) null;
        }
        if (o instanceof Boolean) {
            return (boolean) o;
        }
        if (o instanceof RException) {
            throw new IllegalArgumentException("[asLogical] Exception: " + ((RException)o).getMessage());
        }
        if (!(o instanceof SEXP)) {
            throw new IllegalArgumentException("[asLogical] Not an SEXP object: " + o);
        }
        try {
            return ((SEXP) o).asLogical() == Logical.TRUE;
        } catch (Exception ex) {
            throw new ClassCastException("[asLogical] Cannot cast to boolean " + o);
        }
    }

    @Override
    public boolean[] asLogicals(Object o) throws ClassCastException {
        if (o == null) {
            return null;
        }
        if (o instanceof boolean[]) {
            return (boolean[]) o;
        }
        if (o instanceof Boolean) {
            return new boolean[]{(boolean) o};
        }
        if (!(o instanceof SEXP)) {
            throw new IllegalArgumentException("[asLogicals] Not an SEXP object: " + o);
        }
        if (!(o instanceof LogicalVector)) {
            throw new IllegalArgumentException("[asLogicals] Not a LogicalVector object: " + o);
        }

        try {
            int n = ((SEXP) o).length();
            boolean[] s = new boolean[n];
            for (int i = 0; i < n; i++) {
                s[i] = ((SEXP) o).getElementAsSEXP(i).asLogical() == Logical.TRUE;
            }
            return s;
        } catch (Exception ex) {
            throw new ClassCastException("[asLogicals] Cannot cast to boolean[] " + o);
        }
    }

    @Override
    public Map asList(Object o) throws ClassCastException {
        if (o == null) {
            return null;
        }
        if (o instanceof Map) {
            return (Map) o;
        }
        if (!(o instanceof SEXP)) {
            throw new IllegalArgumentException("[asList] Not an SEXP object: " + o);
        }
        if (!(o instanceof ListVector)) {
            throw new IllegalArgumentException("[asList] Not a ListVector object: " + o);
        }
        ListVector l = (ListVector) o;
        Map m = new HashMap<String, Object>();
        for (int i = 0; i < l.length(); i++) {
            m.put(l.getName(i), cast(l.get(i)));
        }
        return m;
    }

    @Override
    public boolean isNull(Object o) {
        if (o == null) {
            return true;
        }
        if (!(o instanceof SEXP)) {
            throw new IllegalArgumentException("[isNull] Not an SEXP object: " + o);
        }
        try {
            return o instanceof Null;
        } catch (Exception ex) {
            throw new ClassCastException("[isNull] Cannot cast to Null " + o);
        }
    }

    @Override
    public String toString(Object o) {
        if (o instanceof SEXP) {
            try {
                return ((SEXP) o).toString();
            } catch (Exception ex) {
                throw new ClassCastException("[toString] Cannot toString " + o);
            }
        } else if (o.getClass().isArray()) {
            return Arrays.asList(o).toString();
        } else {
            return o.toString();
        }
    }

    @Override
    public void closeLog() {
        super.closeLog();
        R.getSession().close();
    }

    public Object cast(Object o) throws ClassCastException {
        if (o == null) {
            return null;
        }

        if (!(o instanceof SEXP)) {
            throw new ClassCastException("[cast] Not an SEXP object");
        }

        SEXP s = (SEXP) o;

        if (s.length() != 1) {
            switch (s.getTypeName()) {
                case "logical":
                    return asLogicals(s);
                case "integer":
                    return asIntegers(s);
                case "double":
                    if (s.getAttributes().get("dim").length() == 2) {
                        return asMatrix(s);
                    }
                    return asArray(s);
                case "character":
                    return asStrings(s);
                case "list":
                    return asList(s);
                case "NULL":
                    return null;
                default:
                    throw new ClassCastException("Cannot cast " + s + " (class " + s.getImplicitClass() + ", type " + s.getTypeName() + ")");
            }
        } else {
            switch (s.getTypeName()) {
                case "logical":
                    return asLogical(s);
                case "integer":
                    return asInteger(s);
                case "double":
                    return asDouble(s);
                case "character":
                    return asString(s);
                case "list":
                    return asList(s);
                case "closure":
                    String name = "function_" + (int) Math.floor(1000 * Math.random());
                    //synchronized (R) {
                        R.put(name, s);
                        try {
                            if (((SEXP) rawEval("is.function(" + name + ")")).asLogical() == TRUE) {
                                return new Function(name);
                            }
                        } catch (Exception ex) {
                            log(ex.getMessage(), Level.ERROR);
                        }
                    //}
                case "NULL":
                    return null;
                default:
                    throw new ClassCastException("Cannot cast " + s + " (class " + s.getImplicitClass() + ", type " + s.getTypeName() + ")");

            }
        }
        //throw new ClassCastException("Cannot cast to ? " + s + " (" + s.getTypeName() + ")");
    }

    /**
     * Get R command text output
     *
     * @param command R command returning text
     * @return String
     */
    public String print(String command) {
        StringWriter w = new StringWriter();
        try {
            PrintWriter p = R.getSession().getStdOut();
            R.getSession().setStdOut(new PrintWriter(w));
            note_code("print(" + command + ")");
            silentlyRawEval("print(" + command + ")");
            R.getSession().setStdOut(p);
            return w.toString();//.substring(l);
        } catch (Exception ex) {
            return ex.getMessage();
        }
    }

    /**
     * @param url CRAN repository to use for packages installation (eg
     * http://cran.r-project.org)
     */
    public void setCRANRepository(String url) {
        if (!url.equals(repos)) {
            log("Cannot use another repository that " + repos, Level.WARNING);
        }
    }

    @Override
    public String installPackage(String pack, boolean load) {
        if (!load) {
            return "Renjin does not support yet installing package without loading.";
        }
        try {
            return loadPackage(pack);
        } catch (Exception e) {
            return "Renjin cannot install package " + pack + " yet: " + e.getMessage();
        }
    }

    @Override
    public String installPackage(String pack, File dir, boolean load) {
        if (!load) {
            return "Renjin does not support yet installing package without loading.";
        }
        try {
            return "Renjin does not support yet installing local package.";
            //return super.installPackage(RENJIN_REPO_PREFIX + pack, dir, load);
        } catch (Exception e) {
            return "Renjin cannot install package " + pack + " from " + dir + " yet: " + e.getMessage();
        }
    }

    @Override
    public boolean isAvailable() {
        return true;
    }

    @Override
    public String getwd() {
        String wd = super.getwd(); 
        if (isWindows() && wd.startsWith("/"))
            return wd.substring(1);
        return wd;
    }
    
    
    public File putFileInWorkspace(File file) {
        if (file.isAbsolute()) return file;
        File rf = local2remotePath(file);
        if (!rf.getAbsolutePath().equals(file.getAbsolutePath())) {
            try {
                FileUtils.copyFile(file, rf);
            } catch (IOException ex) {
                log(IO_HEAD + ex.getMessage(), Level.ERROR);
            }
        }
        return rf;
    }
    
    public void getFileFromWorkspace(File file) {
        if (file.isAbsolute()) return;
        File rf = remote2localPath(file);
        if (file.getParentFile()!=null)
            if (!file.getParentFile().isDirectory())
            if (!file.getParentFile().mkdirs()) {
                throw new IllegalArgumentException("Cannot create parent dir: " + file);
            }
        if (!rf.getAbsolutePath().equals(file.getAbsolutePath()))
            try {
                FileUtils.copyFile(rf, new File(".", file.getPath()));
            } catch (IOException ex) {
                ex.printStackTrace();
                log(IO_HEAD + ex.getMessage(), Level.ERROR);
            }
    }
    
    public static void main(String[] args) throws Exception {
        //args = new String[]{"install.packages('lhs',repos='\"http://cloud.r-project.org/\"',lib='.')", "1+1"};
        if (args == null || args.length == 0) {
            args = new String[10];
            for (int i = 0; i < args.length; i++) {
                args[i] = Math.random() + "+pi";
            }
        }
        RenjinSession R = new RenjinSession(System.out, null);

        for (int j = 0; j < args.length; j++) {
            System.out.print(args[j] + ": ");
            System.out.println(R.cast(R.rawEval(args[j])));
        }

        R.closeLog();

        System.out.println(R.notebook());
    }

    @Override
    public void setGlobalEnv(String envName) {
        if (envName == null) {
            envName = ENVIRONMENT_DEFAULT;
        } else {
            envName = ".." + envName + "..";
        }
        try {
            //save previous env for later restore if needed
            if (!asLogical(R.eval("exists('" + this.envName + "')"))) {
                R.eval(this.envName + " = new.env()");
            }
            R.eval("for (.n in ls()) {\n " + this.envName + "[[.n]] = .GlobalEnv[[.n]]\n}");
            
            rmAll();
            if (!asLogical(R.eval("exists('" + envName + "')")))
                R.eval(envName + " = new.env()");
            R.eval("for (.n in ls("+envName+")) {\n .GlobalEnv[[.n]] = " + envName + "[[.n]]\n}");

        } catch (ScriptException ex) {
            Log.Err.println(ex.getMessage());
        }
        this.envName = envName;
    }

    @Override
    public void copyGlobalEnv(String envName) {
        if (envName == null) {
            envName = ENVIRONMENT_DEFAULT;
        } else {
            envName = ".." + envName + "..";
        }
        try {
             if (!asLogical(R.eval("exists('" + envName + "')"))) {
                R.eval(envName + " = new.env()");
            }
            
            R.eval("for (.n in ls()) {\n " + envName + "[[.n]] = .GlobalEnv[[.n]]\n}");
        } catch (ScriptException ex) {
            Log.Err.println(ex.getMessage());
        }
    }
}
