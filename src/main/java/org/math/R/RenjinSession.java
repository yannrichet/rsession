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
import org.renjin.aether.AetherPackageLoader;
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
    Properties properties;

    public static RenjinSession newInstance(final RLog console, Properties properties) {
        return new RenjinSession(console, properties);
    }

    public RenjinSession(RLog console, Properties properties) {
        super(console);

        Session session = new SessionBuilder().bind(PackageLoader.class, new AetherPackageLoader()).build();

        R = new RenjinScriptEngineFactory().getScriptEngine(session);
        if (R == null) {
            throw new RuntimeException("Renjin Script Engine not found in the classpath.");
        }

        try {
            wdir = new File(new File(FileUtils.getTempDirectory(), ".Renjin"), "" + hashCode());
            if (!wdir.mkdirs()) {
                wdir = new File(new File(FileUtils.getUserDirectory(), ".Renjin"), "" + hashCode());
                if (!wdir.mkdirs()) {
                    throw new IOException("Could not create directory " + new File(new File(FileUtils.getTempDirectory(), ".Renjin"), "" + hashCode()) + "\n or " + new File(new File(FileUtils.getUserDirectory(), ".Renjin"), "" + hashCode()));
                }
            }
            R.eval("setwd('" + fixPathSeparator(wdir.getAbsolutePath()) + "')");
            wdir.deleteOnExit();
        } catch (Exception ex) {
            log("Could not use directory: " + wdir + "\n" + ex.getMessage(), Level.ERROR);
        }

        SINK_FILE = SINK_FILE_BASE + "-renjin" + this.hashCode();

        setenv(properties);

        repos = "org.renjin.cran";
    }

    public RenjinSession(final PrintStream p, Properties properties) {
        this(new RLog() {

            public void log(String string, Level level) {
                PrintStream pp = null;
                if (p != null) {
                    pp = p;
                } else {
                    pp = System.err;
                }

                if (level == Level.WARNING) {
                    p.print("(!) ");
                } else if (level == Level.ERROR) {
                    p.print("(!!) ");
                }
                p.println(string);
            }

            public void close() {
                if (p != null) {
                    p.close();
                }
            }
        }, properties);
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
    public boolean silentlyVoidEval(String expression, boolean tryEval) {
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
        synchronized (R) {
            try {
                if (SINK_OUTPUT) {
                    R.eval(".f <- file('" + fixPathSeparator(SINK_FILE) + "',open='wt')");
                    R.eval("sink(.f,type='output')");
                }
                if (SINK_MESSAGE) {
                    R.eval(".fm <- file('" + fixPathSeparator(SINK_FILE) + ".m',open='wt')");
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
                        lastOuput = asString(R.eval("paste(collapse='\n',readLines('" + fixPathSeparator(SINK_FILE) + "'))"));
                        log(lastOuput, Level.OUTPUT);
                    } catch (Exception ex) {
                        lastOuput = ex.getMessage();
                        log(lastOuput, Level.WARNING);
                    } finally {
                        try {
                            R.eval("close(.f)");
                            R.eval("unlink('" + fixPathSeparator(SINK_FILE) + "')");
                        } catch (Exception ex) {
                            log(HEAD_EXCEPTION + ex.getMessage(), Level.ERROR);
                        }
                    }
                }
                if (SINK_MESSAGE) {
                    try {
                        R.eval("sink(type='message')");
                        lastMessage = asString(R.eval("paste(collapse='\n',readLines('" + fixPathSeparator(SINK_FILE) + ".m'))"));
                        log(lastMessage, Level.INFO);
                    } catch (Exception ex) {
                        lastMessage = ex.getMessage();
                        log(lastMessage, Level.WARNING);
                    } finally {
                        try {
                            R.eval("close(.fm)");
                            R.eval("unlink('" + fixPathSeparator(SINK_FILE) + ".m')");
                        } catch (Exception ex) {
                            log(HEAD_EXCEPTION + ex.getMessage(), Level.ERROR);
                        }
                    }
                }
            }
        }

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
    public Object silentlyRawEval(String expression, boolean tryEval) {
        if (R == null) {
            log(HEAD_EXCEPTION + "R environment not initialized.", Level.ERROR);
            return null;
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
        SEXP e = null;
        synchronized (R) {
            try {
                if (SINK_OUTPUT) {
                    R.eval(".f <- file('" + fixPathSeparator(SINK_FILE) + "',open='wt')");
                    R.eval("sink(.f,type='output')");
                }
                if (SINK_MESSAGE) {
                    R.eval(".fm <- file('" + fixPathSeparator(SINK_FILE) + ".m',open='wt')");
                    R.eval("sink(.fm,type='message')");
                }
                if (tryEval) {
                    e = (SEXP) (R.eval("try(eval(parse(text='" + expression.replace("'", "\\'") + "')),silent=FALSE)"));
                } else {
                    e = (SEXP) (R.eval(expression));
                }
            } catch (Exception ex) {
                log(HEAD_EXCEPTION + ex.getMessage() + "\n  " + expression, Level.ERROR);
            } finally {
                if (SINK_OUTPUT) {
                    try {
                        R.eval("sink(type='output')");
                        lastOuput = asString(R.eval("paste(collapse='\n',readLines('" + fixPathSeparator(SINK_FILE) + "'))"));
                        log(lastOuput, Level.OUTPUT);
                    } catch (Exception ex) {
                        lastOuput = ex.getMessage();
                        log(lastOuput, Level.WARNING);
                    } finally {
                        try {
                            R.eval("close(.f)"); // because Renjin.sink() do not properly close connection, so calling it explicitely
                            R.eval("unlink('" + fixPathSeparator(SINK_FILE) + "')");
                        } catch (Exception ex) {
                            log(HEAD_EXCEPTION + ex.getMessage(), Level.ERROR);
                        }
                    }
                }
                if (SINK_MESSAGE) {
                    try {
                        R.eval("sink(type='message')");
                        lastMessage = asString(R.eval("paste(collapse='\n',readLines('" + fixPathSeparator(SINK_FILE) + ".m'))"));
                        log(lastMessage, Level.INFO);
                    } catch (Exception ex) {
                        lastMessage = ex.getMessage();
                        log(lastMessage, Level.WARNING);
                    } finally {
                        try {
                            R.eval("close(.fm)");
                            R.eval("unlink('" + fixPathSeparator(SINK_FILE) + ".m')");
                        } catch (Exception ex) {
                            log(HEAD_EXCEPTION + ex.getMessage(), Level.ERROR);
                        }
                    }
                }
            }
        }

        if (tryEval && e != null) {
            try {
                if (e.inherits("try-error")/*e.isString() && e.asStrings().length > 0 && e.asString().toLowerCase().startsWith("error")*/) {
                    log(HEAD_EXCEPTION + e.asString() + "\n  " + expression, Level.WARNING);
                    e = null;
                }
            } catch (Exception ex) {
                log(HEAD_ERROR + ex.getMessage() + "\n  " + expression, Level.ERROR);
                return null;
            }
        }
        return e;
    }

    @Override
    public boolean set(String varname, double[][] data, String... names) {
        if (data == null) {

            if (names == null) {
                return false;
            }
            List<SEXP> nulls = new LinkedList<>();
            for (int i = 0; i < names.length; i++) {
                nulls.add(Null.INSTANCE.clone());
            }

            ListVector l = new ListVector(nulls);
            synchronized (R) {
                R.put(varname, l);
                R.put(varname + ".names", new StringArrayVector(names));
                try {
                    R.eval("names(" + varname + ") <- " + varname + ".names");
                    //R.eval(varname + " <- data.frame(" + varname + ")");
                } catch (ScriptException ex) {
                    ex.printStackTrace();
                    return false;
                }
            }
            return true;

        } else {

            DoubleVector[] d = new DoubleVector[data[0].length];
            for (int i = 0; i < d.length; i++) {
                d[i] = new DoubleArrayVector(DoubleArray.getColumnCopy(data, i));
            }
            ListVector l = new ListVector(d);
            //l.setAttribute(Symbols.NAMES, new StringArrayVector(names)); 
            synchronized (R) {
                R.put(varname, l);
                //R.put("names("+varname+")",new StringArrayVector(names));
                R.put(varname + ".names", new StringArrayVector(names));
                try {
                    R.eval("names(" + varname + ") <- " + varname + ".names");
                    R.eval(varname + " <- data.frame(" + varname + ")");
                } catch (ScriptException ex) {
                    ex.printStackTrace();
                    return false;
                }
            }
            return true;
        }
    }

    @Override
    public boolean set(String varname, Object var) {
        if (var instanceof double[][]) {
            double[][] dd = (double[][]) var;
            double[] d = reshapeAsRow(dd);
            synchronized (R) {
                R.put(varname, d);
                try {
                    R.eval(varname + " <- matrix(" + varname + ",nrow=" + dd.length + ")");
                } catch (ScriptException ex) {
                    ex.printStackTrace();
                    return false;
                }
            }
        } else {
            synchronized (R) {
                R.put(varname, var);
            }
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
    public void close() {
        super.close();
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
                    synchronized (R) {
                        R.put(name, s);
                        try {
                            if (((SEXP) rawEval("is.function(" + name + ")")).asLogical() == TRUE) {
                                return new Function(name);
                            }
                        } catch (Exception ex) {
                            ex.printStackTrace();
                        }
                    }
                case "NULL":
                    return null;
                default:
                    throw new ClassCastException("Cannot cast " + s + " (class " + s.getImplicitClass() + ", type " + s.getTypeName() + ")");

            }
        }
        //throw new ClassCastException("Cannot cast to ? " + s + " (" + s.getTypeName() + ")");
    }

    //protected Context topLevelContext = Context.newTopLevelContext();
    @Override
    public void toGraphic(File f, int width, int height, String fileformat, String... commands) {
        throw new UnsupportedOperationException("Graphics not yet available using Renjin");

        /*BufferedImage image = new BufferedImage(width, height, ColorSpace.TYPE_RGB);

         Graphics2D g2d = (Graphics2D) image.getGraphics();
         g2d.setColor(Color.WHITE);
         g2d.setBackground(Color.WHITE);
         g2d.fill(g2d.getDeviceConfiguration().getBounds());

         AwtGraphicsDevice driver = new AwtGraphicsDevice(g2d);
         topLevelContext.getSingleton(GraphicsDevices.class).setActive(new org.renjin.graphics.GraphicsDevice(driver));

         try {
         StringWriter w = new StringWriter();
         PrintWriter p = R.getSession().getStdErr();
         R.getSession().setStdErr(new PrintWriter(w));

         for (String command : commands) {
         voidEval(command);
         }
         R.getSession().setStdErr(p);
         System.err.println(w.getBuffer());
         } finally {
         try {
         FileOutputStream fos = new FileOutputStream(f);

         ImageIO.write(image, fileformat.toUpperCase(), fos);
         fos.close();
         } catch (IOException ex) {
         ex.printStackTrace();
         }
         }*/
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
            log("Cannot use another repositroy that " + repos, Level.WARNING);
        }
    }

    @Override
    public String loadPackage(String pack) {
        log("  request package " + pack + " loading...", Level.INFO);
        try {
            boolean ok = asLogical(rawEval("library('" + pack + "')", TRY_MODE));
            if (ok) {
                log(_PACKAGE_ + pack + " loading sucessfull.", Level.INFO);
                return PACKAGELOADED;
            } else {
                log(_PACKAGE_ + pack + " loading failed.", Level.ERROR);
                return "Impossible to load package " + pack + ": " + getLastLogEntry();
            }
        } catch (Exception ex) {
            log(_PACKAGE_ + pack + " loading failed.", Level.ERROR);
            return "Impossible to load package " + pack + ": " + ex.getLocalizedMessage();
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

    public static void main(String[] args) throws Exception {
        //args = new String[]{"install.packages('lhs',repos='\"http://cloud.r-project.org/\"',lib='.')", "1+1"};
        if (args == null || args.length == 0) {
            args = new String[10];
            for (int i = 0; i < args.length; i++) {
                args[i] = Math.random() + "+pi";
            }
        }
        RenjinSession R = new RenjinSession(new RLogSlf4j(), null);

        for (int j = 0; j < args.length; j++) {
            System.out.print(args[j] + ": ");
            System.out.println(R.cast(R.rawEval(args[j])));
        }

        R.close();
    }
}
