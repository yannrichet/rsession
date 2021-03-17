package org.math.R;

import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.PrintStream;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import org.apache.commons.io.IOUtils;
import org.rosuda.REngine.REXP;
import org.rosuda.REngine.REXPDouble;
import org.rosuda.REngine.REXPGenericVector;
import org.rosuda.REngine.REXPInteger;
import org.rosuda.REngine.REXPList;
import org.rosuda.REngine.REXPLogical;
import org.rosuda.REngine.REXPMismatchException;
import org.rosuda.REngine.REXPNull;
import org.rosuda.REngine.REXPString;
import org.rosuda.REngine.REngineException;
import org.rosuda.REngine.RList;
import org.rosuda.REngine.Rserve.RConnection;
import org.rosuda.REngine.Rserve.RserveException;

/**
 * @author richet
 */
public class RserveSession extends Rsession implements RLog {

    RConnection R;
    public final static int MinRserveVersion = 103;
    public boolean connected = false;
    RserveDaemon localRserve;
    public RserverConf RserveConf;
    public final static String STATUS_NOT_SET = "Unknown status", STATUS_READY = "Ready", STATUS_ERROR = "Error", STATUS_ENDED = "End", STATUS_NOT_CONNECTED = "Not connected", STATUS_CONNECTING = "Connecting...";
    public String status = STATUS_NOT_SET;

    private static final String ENVIRONMENT_DEFAULT = "..rserve..";

    // <editor-fold defaultstate="collapsed" desc="Conveniency static String methods">
    public static String cat(RList list) {
        if (list == null || list.names == null) {
            return null;
        }
        try {
            StringBuffer sb = new StringBuffer("\t");
            double[][] data = new double[list.names.size()][];
            for (int i = 0; i < list.size(); i++) {
                String n = list.keyAt(i);
                sb.append(n + "\t");
                data[i] = list.at(n).asDoubles();
            }
            sb.append("\n");
            for (int i = 0; i < data[0].length; i++) {
                sb.append((i + 1) + "\t");
                for (int j = 0; j < data.length; j++) {
                    sb.append(data[j][i] + "\t");
                }
                sb.append("\n");
            }
            return sb.toString();
        } catch (REXPMismatchException r) {
            return "(Not a numeric dataframe)\n" + new REXPList(list).toDebugString();
        }
    }
    // </editor-fold>

    /**
     * Build a new local Rsession
     *
     * @param console PrintStream for R output
     * @param localRProperties properties to pass to R (eg http_proxy or R
     * libpath)
     * @return RserveSession instanciated
     * @deprecated Use new RserveSession standard constructor instead
     */
    public static RserveSession newLocalInstance(final RLog console, Properties localRProperties) {
        return new RserveSession(console, localRProperties, null);
    }

    /**
     * Build a new remote Rsession
     *
     * @param console PrintStream for R output
     * @param serverconf RserverConf server configuration object, giving IP,
     * port, login, password, properties to pass to R (eg http_proxy or R
     * libpath)
     * @return RserveSession instanciated
     * @deprecated Use new RserveSession standard constructor instead
     */
    public static RserveSession newRemoteInstance(final RLog console, RserverConf serverconf) {
        return new RserveSession(console, null, serverconf);
    }

    /**
     * Build a new Rsession. Fork to local spawned Rsession if given remote one
     * failed to initialized.
     *
     * @param console PrintStream for R output
     * @param serverconf RserverConf server configuration object, giving IP,
     * port, login, password
     * @return RserveSession instanciated
     * @deprecated Use new RserveSession standard constructor instead
     */
    public static RserveSession newInstanceTry(final RLog console, RserverConf serverconf) {
        try {
            RConnection c = serverconf.connect();
            Log.Out.println("Rserve is available on " + serverconf);
            c.close();
        } catch (Exception e) {
            Log.Err.println("Rserve is not available on " + serverconf + ". Trying to spawn a new one");
            serverconf = null;
        }
        return new RserveSession(console, null, serverconf);
    }

    /**
     * Build a new local Rsession
     *
     * @param pconsole PrintStream for R output
     * @param localRProperties properties to pass to R (eg http_proxy or R
     * libpath)
     * @return RserveSession instanciated
     * @deprecated Use new RserveSession standard constructor instead
     */
    public static RserveSession newLocalInstance(PrintStream pconsole, Properties localRProperties) {
        return new RserveSession(pconsole, localRProperties, null);
    }

    /**
     * Build a new remote Rsession
     *
     * @param pconsole PrintStream for R output
     * @param serverconf RserverConf server configuration object, giving IP,
     * port, login, password, properties to pass to R (eg http_proxy or R
     * libpath)
     * @return RserveSession instanciated
     * @deprecated Use new RserveSession standard constructor instead
     */
    public static RserveSession newRemoteInstance(PrintStream pconsole, RserverConf serverconf) {
        return new RserveSession(pconsole, null, serverconf);
    }

    /**
     * Build a new Rsession. Fork to local spawned Rsession if given remote one
     * failed to initialized.
     *
     * @param pconsole PrintStream for R output
     * @param serverconf RserverConf server configuration object, giving IP,
     * port, login, password, properties to pass to R (eg http_proxy)
     * @return RserveSession instanciated
     * @deprecated Use new RserveSession standard constructor instead
     */
    public static RserveSession newInstanceTry(PrintStream pconsole, RserverConf serverconf) {
        return new RserveSession(pconsole, null, serverconf);
    }

    /**
     * create a new Rsession.
     *
     * @param console PrintStream for R output
     * @param properties env variables to setup at R session startup (like
     * http_proxy)
     * @param serverconf RserverConf server configuration object, giving IP,
     * port, login, password
     */
    public RserveSession(final RLog console, Properties properties, RserverConf serverconf) {
        super(console);
        envName = ENVIRONMENT_DEFAULT;

        RserveConf = serverconf;

        // Make sink file specific to current Rserve instance
        SINK_FILE = "./rout.txt";//+SINK_FILE_BASE + "-" + (serverconf == null ? 0 : serverconf.port);
        try {
            startup();
        } catch (Exception ex) {
            console.log(ex.getMessage(), Level.ERROR);
            return;
        }

        silentlyVoidEval("if (!any(file.access(.libPaths(),2)>=0)) .libPaths(new=tempdir())"); // ensure a writable directory for libPath

        setenv(properties);
    }

    /**
     * create rsession using System as a logger
     *
     * @param p PrintStream
     * @param properties R session properties to setup (like hhtp_proxy)
     * @param serverconf RserverConf
     */
    public RserveSession(final PrintStream p, Properties properties, RserverConf serverconf) {
        this(new RLogPrintStream(p), properties, serverconf);
    }

    /**
     * @return status of Rsession
     */
    public String getStatus() {
        return status;
    }

    void startup() throws Exception {
        log(RserveConf == null
                ? "Will start Rserve session without conf."
                : "Will start Rserve session using: " + RserveConf.toString(), Level.INFO);

        status = STATUS_NOT_CONNECTED;

        if (RserveConf == null) {// no RserveConf given, so create one, and need to be started
            RserveConf = new RserverConf(RserverConf.DEFAULT_RSERVE_HOST, -1, null, null);
            log("No Rserve conf given. Trying to use " + RserveConf.toString(), Level.INFO);

            try {
                localRserve = new RserveDaemon(RserveConf, this);
            } catch (Exception ex) {
                log(ex.getMessage(), Level.ERROR);
                throw ex;
            }

            localRserve.start();
            RserveConf = localRserve.conf; //update, as start() may have selected a new free port
        }

        status = STATUS_CONNECTING;

        //int attempts = 10;
        //while (!connected && attempts > 0) {
        R = RserveConf.connect(); // use timeout wrapper to ensure connexion (if possible)
        connected = (R != null);
        //    try {
        //        Thread.sleep(100);
        //    } catch (InterruptedException ie) {
        //    }
        //}

        if (!connected) {
            log("Rserve " + RserveConf + " is not accessible.", Level.ERROR);
            status = STATUS_ERROR;
            throw new RException("Rserve " + RserveConf + " is not accessible.");
        } else if (R.getServerVersion() < MinRserveVersion) {
            log("Rserve " + RserveConf + " version is too old.", Level.ERROR);
            status = STATUS_ERROR;
            throw new RException("Rserve " + RserveConf + " version is too old.");
        } else {
            status = STATUS_READY;
        }
    }

    /**
     * correctly (depending on execution platform) shutdown Rsession.
     */
    @Override
    public void end() {
        super.end();
        if (R == null) {
            log("Void session terminated.", Level.INFO);
            cleanupListeners();
            return;
        }
        if ((!RserveDaemon.UNIX_OPTIMIZE || isWindows()) && localRserve != null) {
            log("Ending local service...", Level.INFO);
            localRserve.stop();
        }
        log("Closing session...", Level.INFO);
        R.close();
        log("Session teminated.", Level.INFO);

        R = null;
        cleanupListeners();
    }

    @Override
    public String getLastError() {
        if (!SINK_MESSAGE) {
            if (R != null) {
                return R.getLastError();
            } else {
                return null;
            }
        } else {
            return lastMessage;
        }
    }

    public String gethomedir() {
        return asString(silentlyRawEval("path.expand('~')"));
    }

    /**
     * Silently (ie no log) launch R command without return value.
     *
     * @param expression R expresison to evaluate
     * @param tryEval encapsulate command in try() to cacth errors
     * @return succeeded ?
     */
    @Override
    protected synchronized boolean silentlyVoidEval(String expression, boolean tryEval) {
        //assert connected : "R environment not initialized.";
        if (!connected) {
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
        REXP e = null;
        //synchronized (R) {
        try {
            if (SINK_OUTPUT) {
                R.eval(".f <- file('" + (SINK_FILE) + "',open='wt')");
                R.eval("sink(.f,type='output')");
            }
            if (SINK_MESSAGE) {
                R.eval(".fm <- file('" + (SINK_FILE) + ".m',open='wt')");
                R.eval("sink(.fm,type='message')");
            }
            if (tryEval) {
                e = R.parseAndEval("try(eval(parse(text='" + expression.replace("'", "\\'") + "')),silent=FALSE)");
            } else {
                e = R.parseAndEval(expression);
            }
        } catch (Exception ex) {
            log(HEAD_EXCEPTION + ex.getMessage() + "\n  " + expression, Level.ERROR);
            return false;
        } finally {
            if (SINK_OUTPUT) {
                try {
                    R.parseAndEval("sink(type='output')");
                    lastOuput = R.parseAndEval("paste(collapse='\n',readLines('" + (SINK_FILE) + "'))").asString();
                    log(lastOuput, Level.OUTPUT);
                } catch (Exception ex) {
                    lastOuput = ex.getMessage();
                    log(lastOuput, Level.WARNING);
                } finally {
                    try {
                        R.eval("close(.f)"); // because Renjin.sink() do not properly closeLog connection, so calling it explicitely
                        R.parseAndEval("unlink('" + (SINK_FILE) + "')");
                    } catch (Exception ex) {
                        log(ex.getMessage(), Level.ERROR);
                    }
                }
            }
            if (SINK_MESSAGE) {
                try {
                    R.parseAndEval("sink(type='message')");
                    lastMessage = R.parseAndEval("paste(collapse='\n',readLines('" + (SINK_FILE) + ".m'))").asString();
                    log(lastMessage, Level.INFO);
                } catch (Exception ex) {
                    lastMessage = ex.getMessage();
                    log(lastMessage, Level.WARNING);
                } finally {
                    try {
                        R.eval("close(.fm)"); // because Renjin.sink() do not properly closeLog connection, so calling it explicitely
                        R.parseAndEval("unlink('" + (SINK_FILE) + ".m')");
                    } catch (Exception ex) {
                        log(ex.getMessage(), Level.ERROR);
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
            } catch (REXPMismatchException ex) {
                log(HEAD_ERROR + ex.getMessage() + "\n  " + expression, Level.ERROR);
                return false;
            }
        }
        return true;
    }

    /**
     * Silently (ie no log) launch R command and return value.
     *
     * @param expression R expression to evaluate
     * @param tryEval encapsulate command in try() to cacth errors
     * @return REXP R expression
     */
    @Override
    protected synchronized Object silentlyRawEval(String expression, boolean tryEval) {
        //assert connected : "R environment not initialized.";
        if (!connected) {
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
                R.eval(".f <- file('" + (SINK_FILE) + "',open='wt')");
                R.eval("sink(.f,type='output')");
            }
            if (SINK_MESSAGE) {
                R.eval(".fm <- file('" + (SINK_FILE) + ".m',open='wt')");
                R.eval("sink(.fm,type='message')");
            }
            if (tryEval) {
                e = R.parseAndEval("try(eval(parse(text='" + expression.replace("'", "\\'") + "')),silent=FALSE)");
            } else {
                e = R.parseAndEval(expression);
            }
        } catch (Exception ex) {
            log(HEAD_EXCEPTION + ex.getMessage() + "\n  " + expression, Level.ERROR);
            return new RException(HEAD_EXCEPTION + ex.getMessage() + "\n  " + expression);
        } finally {
            if (SINK_OUTPUT) {
                try {
                    R.parseAndEval("sink(type='output')");
                    lastOuput = R.parseAndEval("paste(collapse='\n',readLines('" + (SINK_FILE) + "'))").asString();
                    log(lastOuput, Level.OUTPUT);
                } catch (Exception ex) {
                    lastOuput = ex.getMessage();
                    log(lastOuput, Level.WARNING);
                } finally {
                    try {
                        R.eval("close(.f)"); // because Renjin.sink() do not properly closeLog connection, so calling it explicitely
                        R.parseAndEval("unlink('" + (SINK_FILE) + "')");
                    } catch (Exception ex) {
                        log(HEAD_EXCEPTION + ex.getMessage(), Level.ERROR);
                    }
                }
            }
            if (SINK_MESSAGE) {
                try {
                    R.parseAndEval("sink(type='message')");
                    lastMessage = R.parseAndEval("paste(collapse='\n',readLines('" + (SINK_FILE) + ".m'))").asString();
                    log(lastMessage, Level.INFO);
                } catch (Exception ex) {
                    lastMessage = ex.getMessage();
                    log(lastMessage, Level.WARNING);
                } finally {
                    try {
                        R.eval("close(.fm)"); // because Renjin.sink() do not properly closeLog connection, so calling it explicitely
                        R.parseAndEval("unlink('" + (SINK_FILE) + ".m')");
                    } catch (Exception ex) {
                        log(HEAD_EXCEPTION + ex.getMessage(), Level.ERROR);
                    }
                }
            }
        }
        //}

        if (tryEval && e != null) {
            try {
                if (((REXP) e).inherits("try-error")/*e.isString() && e.asStrings().length > 0 && e.asString().toLowerCase().startsWith("error")*/) {
                    log(HEAD_EXCEPTION + ((REXP) e).asString() + "\n  " + expression, Level.WARNING);
                    e = new RException(HEAD_EXCEPTION + ((REXP) e).asString() + "\n  " + expression);
                }
            } catch (REXPMismatchException ex) {
                log(HEAD_ERROR + ex.getMessage() + "\n  " + expression, Level.ERROR);
                return new RException(HEAD_ERROR + ex.getMessage() + "\n  " + expression);
            }
        }
        return e;
    }

    public String getRServeOS() {
        String os = asString(rawEval("Sys.info()['sysname']", TRY_MODE));
        return (os == null ? "NA" : os);
    }

    @Override
    public boolean isWindows() {
        return getRServeOS().startsWith("Windows");
    }

    @Override
    public boolean isLinux() {
        return getRServeOS().startsWith("Linux");
    }

    @Override
    public boolean isMacOSX() {
        return getRServeOS().startsWith("Darwin");
    }

    /**
     * Build R liost in R env.
     *
     * @param data numeric data (eg matrix)
     * @param names names of columns
     * @return RList object
     */
    private static RList buildRList(double[][] data, String... names) {
        if (data == null) {
            if (names == null) {
                return null;
            }
            REXP[] nulls = new REXP[names.length];
            for (int i = 0; i < nulls.length; i++) {
                nulls[i] = new REXPDouble(new double[0]);
            }
            return new RList(nulls, names);
        }

        if (data[0].length != 0 && data[0].length != names.length) {
            throw new IllegalArgumentException("Cannot build R list from " + Arrays.deepToString(data) + " & " + Arrays.toString(names));
        }
        REXP[] vals = new REXP[names.length];

        for (int i = 0; i < names.length; i++) {
            double[] coli = new double[data.length];
            for (int j = 0; j < coli.length; j++) {
                if (data[j] == null) {
                    coli[j] = Double.NaN;
                } else {
                    if (data[j].length > i) {
                        coli[j] = data[j][i];
                    } else {
                        coli[j] = Double.NaN;
                    }
                }
            }
            vals[i] = new REXPDouble(coli);
        }
        return new RList(vals, names);
    }

    /**
     * Build R liost in R env.
     *
     * @param coldata numeric data as an array of numeric vectors
     * @param names names of columns
     * @return RList object
     */
    private static RList buildRList(List<double[]> coldata, String... names) {
        return buildRList(coldata.toArray(new double[coldata.size()][]), names);
    }

    protected static String toRcode(RList l) {
        String sl = "list(";
        for (String k : l.keys()) {
            if (l.get(k) instanceof REXPDouble) {
                REXPDouble v = (REXPDouble) l.get(k);
                sl = sl + k + "=" + toRcode(v.asDoubles()) + ",";
            } else {
                sl = sl + k + "=" + l.get(k).toString() + ",";
            }
        }
        return sl.substring(0, sl.length() - 2) + ")";
    }

    protected static String toRcode(Object o) {
        if (o instanceof RList) {
            return toRcode((RList) o);
        } else {
            return Rsession.toRcode(o);
        }
    }

    /**
     * Set R data.frame in R env.
     *
     * @param varname R list name
     * @param data numeric data in list
     * @param names names of columns
     * @return succeeded ?
     */
    @Override
    public synchronized boolean set(String varname, double[][] data, String... names) {
        note_code("`" + varname + "` <- " + (data == null ? "list()" : toRcode(data)));
        note_code("names(" + varname + ") <- " + toRcode(names));
        note_code("`" + varname + "` <- data.frame(" + varname + ")");

        RList list = buildRList(data, names);
        log(HEAD_SET + varname + " <- " + list, Level.INFO);
        try {
            //synchronized (R) {
            R.assign(varname, REXP.createDataFrame(list));
            //}
        } catch (REXPMismatchException re) {
            log(HEAD_ERROR + " RList " + list.toString() + " not convertible as dataframe.", Level.ERROR);
            return false;
        } catch (RserveException ex) {
            log(HEAD_EXCEPTION + ex.getMessage() + "\n  set(String varname=" + varname + ",double[][] data, String... names)", Level.ERROR);
            return false;
        }
        return true;
    }
    public final static String HEAD_SET = "[set] ";

    /**
     * Set R object in R env.
     *
     * @param varname R object name
     * @param var R object value
     * @return succeeded ?
     * @throws org.math.R.Rsession.RException Could not set var
     */
    @Override
    public synchronized boolean set(String varname, Object var) throws RException {
        note_code("`" + varname + "` <- " + toRcode(var));

        //assert connected : "R environment not initialized. Please make sure that R.init() method was called first.";
        if (!connected) {
            log(HEAD_EXCEPTION + "R environment not initialized. Please make sure that R.init() method was called first.", Level.ERROR);
            return false;
        }

        log(HEAD_SET + varname + " <- " + var, Level.INFO);
        /*if (var instanceof DataFrame) {
         DataFrame df = (DataFrame) var;
         set("names_" + varname, df.keySet().toArray(new String[]{}));
         set("data_" + varname, df.dataSet());
         rawEval(varname + "=data.frame(x=data_" + varname + ")");
         silentlyRawEval("names(" + varname + ") <- names_" + varname);
         silentlyRawEval("rm(names_" + varname + ",data_" + varname + ")");
         }*/
        if (var == null) {
            rm(varname);
            return true;
        } else if (var instanceof RList) {
            RList l = (RList) var;
            try {
                //synchronized (R) {
                R.assign(varname, new REXPList(l));
                //}
            } catch (RserveException ex) {
                log(HEAD_EXCEPTION + ex.getMessage() + "\n  set(String varname=" + varname + ",Object (RList) var)", Level.ERROR);
                return false;
            }
        } else if (var instanceof File) {
            putFile((File) var);
            return silentlyVoidEval(varname + "<-'" + ((File) var).getName() + "'");
        } else if (var instanceof Integer) {
            return silentlyVoidEval(varname + "<-" + (Integer) var);
        } else if (var instanceof Double) {
            return silentlyVoidEval(varname + "<-" + (Double) var);
        } else if (var instanceof Double[]) {
            Double[] varD = (Double[]) var;
            double[] vard = new double[varD.length];
            System.arraycopy(varD, 0, vard, 0, varD.length);
            try {
                //synchronized (R) {
                R.assign(varname, vard);
                //}
            } catch (REngineException ex) {
                log(HEAD_ERROR + ex.getMessage() + "\n  set(String varname=" + varname + ",Object (Double[]) var)", Level.ERROR);
                return false;
            }
            return silentlyVoidEval(varname/*, cat((double[]) var)*/);
        } else if (var instanceof double[]) {
            try {
                //synchronized (R) {
                R.assign(varname, (double[]) var);
                //}
            } catch (REngineException ex) {
                log(HEAD_ERROR + ex.getMessage() + "\n  set(String varname=" + varname + ",Object (double[]) var)", Level.ERROR);
                return false;
            }
            return silentlyVoidEval(varname/*, cat((double[]) var)*/);
        } else if (var instanceof Double[][]) {
            Double[][] array = (Double[][]) var;
            int rows = array.length;
            int col = array[0].length;
            try {
                //synchronized (R) {
                R.assign("row_" + varname, reshapeAsRow(array));
                //}
            } catch (REngineException ex) {
                log(HEAD_ERROR + ex.getMessage() + "\n  set(String varname=" + varname + ",Object (double[][]) var)", Level.ERROR);
                return false;
            }
            //eval("print(row_" + varname + ")");
            boolean done = silentlyVoidEval(varname + "<-array(row_" + varname + ",c(" + rows + "," + col + "))");
            return done && silentlyVoidEval("rm(row_" + varname + ")");
        } else if (var instanceof double[][]) {
            double[][] array = (double[][]) var;
            int rows = array.length;
            int col = array[0].length;
            try {
                //synchronized (R) {
                R.assign("row_" + varname, reshapeAsRow(array));
                //}
            } catch (REngineException ex) {
                log(HEAD_ERROR + ex.getMessage() + "\n  set(String varname=" + varname + ",Object (double[][]) var)", Level.ERROR);
                return false;
            }
            //eval("print(row_" + varname + ")");
            boolean done = silentlyVoidEval(varname + "<-array(row_" + varname + ",c(" + rows + "," + col + "))");
            return done && silentlyVoidEval("rm(row_" + varname + ")");
        } else if (var instanceof String) {
            try {
                //synchronized (R) {
                R.assign(varname, (String) var);
                //}
            } catch (RserveException ex) {
                log(HEAD_EXCEPTION + ex.getMessage() + "\n  set(String varname=" + varname + ",Object (String) var)", Level.ERROR);
                return false;
            }
            return silentlyVoidEval(varname/*, (String) var*/);
        } else if (var instanceof String[]) {
            try {
                //synchronized (R) {
                R.assign(varname, (String[]) var);
                //}
            } catch (REngineException ex) {
                log(HEAD_ERROR + ex.getMessage() + "\n  set(String varname=" + varname + ",Object (String[]) var)", Level.ERROR);
                return false;
            }
            return silentlyVoidEval(varname/*, cat((String[]) var)*/);
        } else if (var instanceof Map) {
            try {
                //synchronized (R) {
                R.assign(varname, asRList((Map) var));
                //}
            } catch (Exception ex) {
                log(HEAD_ERROR + ex.getMessage() + "\n  set(String varname=" + varname + ",Object (Map) var)", Level.ERROR);
                return false;
            }
            return silentlyVoidEval(varname);
        } else {
            throw new IllegalArgumentException("Variable " + varname + " is not double, double[],  double[][], String or String[]. R engine can not handle.");
        }
        return true;
    }

    public static REXPList asRList(Map m) {
        RList l = new RList();
        for (Object o : m.keySet()) {
            Object v = m.get(o);
            if (v instanceof Double) {
                l.put(o.toString(), new REXPDouble((Double) v));
            } else if (v instanceof double[]) {
                l.put(o.toString(), new REXPDouble((double[]) v));
            } else if (v instanceof Integer) {
                l.put(o.toString(), new REXPInteger((Integer) v));
            } else if (v instanceof int[]) {
                l.put(o.toString(), new REXPInteger((int[]) v));
            } else if (v instanceof String) {
                l.put(o.toString(), new REXPString((String) v));
            } else if (v instanceof String[]) {
                l.put(o.toString(), new REXPString((String[]) v));
            } else if (v instanceof Boolean) {
                l.put(o.toString(), new REXPLogical((Boolean) v));
            } else if (v instanceof boolean[]) {
                l.put(o.toString(), new REXPLogical((boolean[]) v));
            } else if (v instanceof Map) {
                l.put(o.toString(), asRList((Map) v));
            } else if (v instanceof RList) {
                l.put(o.toString(), (RList) v);
            } else if (v == null) {
                l.put(o.toString(), new REXPNull());
            } else {
                Log.Err.println("[asRList] Could not cast object " + o + " : " + v);
            }
        }
        return new REXPList(l);
    }

    @Override
    public double asDouble(Object o) throws ClassCastException {
        if (o == null) {
            return (Double) null;
        }
        if (o instanceof Double) {
            return (double) o;
        }
        if (!(o instanceof REXP)) {
            throw new IllegalArgumentException("[asDouble] Not an REXP object: " + o);
        }
        try {
            return ((REXP) o).asDouble();
        } catch (REXPMismatchException ex) {
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
        if (!(o instanceof REXP)) {
            throw new IllegalArgumentException("[asArray] Not an REXP object: " + o);
        }
        if (((REXP) o).isNull()) {
            return null;
        }

        try {
            return ((REXP) o).asDoubles();
        } catch (REXPMismatchException ex) {
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
                throw new ClassCastException("[asMatrix] Cannot cast Map to matrix: " + ex.getMessage());
            }
        }
        if (o instanceof double[]) {
            return t(new double[][]{(double[]) o});
        }
        if (o instanceof Double) {
            return new double[][]{{(double) o}};
        }
        if (!(o instanceof REXP)) {
            throw new IllegalArgumentException("[asMatrix] Not an REXP object: " + o);
        }
        if (((REXP) o).isNull()) {
            return null;
        }

        try {
            return ((REXP) o).asDoubleMatrix();
        } catch (REXPMismatchException ex) {
            throw new ClassCastException("[asMatrix] Cannot cast to matrix " + o);
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
        if (!(o instanceof REXP)) {
            throw new IllegalArgumentException("[asString] Not an REXP object: " + o);
        }
        try {
            return ((REXP) o).asString();
        } catch (REXPMismatchException ex) {
            throw new ClassCastException("[asString] Cannot cast to string " + o);
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
        if (!(o instanceof REXP)) {
            throw new IllegalArgumentException("[asStrings] Not an REXP object: " + o);
        }
        if (((REXP) o).isNull()) {
            return null;
        }

        try {
            return ((REXP) o).asStrings();
        } catch (REXPMismatchException ex) {
            throw new ClassCastException("[asStrings] Cannot cast to strings " + o);
        }
    }

    @Override
    public int asInteger(Object o) throws ClassCastException {
        if (o == null) {
            return (Integer) null;
        }
        if (o instanceof Integer) {
            return (int) o;
        }
        if (!(o instanceof REXP)) {
            throw new IllegalArgumentException("[asInteger] Not an REXP object: " + o.getClass());
        }
        try {
            return ((REXP) o).asInteger();
        } catch (REXPMismatchException ex) {
            throw new ClassCastException("[asInteger] Cannot cast to integer " + o);
        }
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
        if (!(o instanceof REXP)) {
            throw new IllegalArgumentException("[asIntegers] Not an REXP object: " + o);
        }
        if (((REXP) o).isNull()) {
            return null;
        }

        try {
            return ((REXP) o).asIntegers();
        } catch (REXPMismatchException ex) {
            throw new ClassCastException("[asIntegers] Cannot cast to integers " + o);
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
            throw new IllegalArgumentException("[asLogical] Exception: " + ((RException) o).getMessage());
        }
        if (!(o instanceof REXP)) {
            throw new IllegalArgumentException("[asLogical] Not an REXP object: " + o);
        }
        try {
            return ((REXP) o).asInteger() == 1;
        } catch (Exception ex) {
            throw new ClassCastException("[asLogical] Cannot cast to logical " + o);
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
        if (!(o instanceof REXP)) {
            throw new IllegalArgumentException("[asLogicals] Not an REXP object: " + o);
        }
        if (((REXP) o).isNull()) {
            return null;
        }

        try {
            int[] i = ((REXP) o).asIntegers();
            boolean[] ok = new boolean[i.length];
            for (int j = 0; j < ok.length; j++) {
                ok[j] = i[j] == 1;
            }
            return ok;
        } catch (REXPMismatchException ex) {
            throw new ClassCastException("[asLogicals] Cannot cast to logicals " + o);
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
        if (!(o instanceof REXP)) {
            throw new IllegalArgumentException("[asList] Not an REXP object: " + o);
        }
        if (((REXP) o).isNull()) {
            return null;
        }

        try {
            RList l = ((REXP) o).asList();
            Map m = new HashMap(l.size());
            for (String k : l.keys()) {
                m.put(k, cast(l.at(k)));
            }
            return m;
        } catch (REXPMismatchException ex) {
            throw new ClassCastException("[asList] Cannot cast to matrix " + o);
        }
    }

    @Override
    public Object cast(Object o) throws ClassCastException {
        if (o == null) {
            return null;
        }

        if (!(o instanceof REXP)) {
            throw new ClassCastException("[cast] Not an REXP object: " + o);
        }

        REXP eval = (REXP) o;

        try {
            if (eval.isNumeric()) {
                if (eval.dim() == null || eval.dim().length == 1) {
                    if (eval.isInteger()) {
                        int[] array = eval.asIntegers();
                        if (array.length == 0) {
                            return null;
                        }
                        if (array.length == 1) {
                            return array[0];
                        }
                        return array;
                    } else {
                        double[] array = eval.asDoubles();
                        if (array.length == 0) {
                            return null;
                            }
                        if (array.length == 1) {
                            return array[0];
                        }
                        return array;
                    }
                } else {
                    double[][] mat = eval.asDoubleMatrix();
                    if (mat.length == 0) {
                        return null;
                    } else if (mat.length == 1) {
                        if (mat[0].length == 0) {
                            return null;
                        } else if (mat[0].length == 1) {
                            return mat[0][0];
                        } else {
                            return mat[0];
                        }
                    } else {
                        if (mat[0].length == 0) {
                            return null;
                        } else if (mat[0].length == 1) {
                            double[] dmat = new double[mat.length];
                            for (int i = 0; i < dmat.length; i++) {
                                dmat[i] = mat[i][0];
                            }
                            return dmat;
                        } else {
                            return mat;
                        }
                    }
                }
            }

            if (eval.isString()) {
                String[] s = eval.asStrings();
                if (s.length == 1) {
                    return s[0];
                } else {
                    return s;
                }
            }

            if (eval.isLogical()) {
                return eval.asInteger() == 1;
            }

            if (eval.isList()) {
                return asList(eval);
            }

            try {
                String name = "function_" + (int) Math.floor(1000 * Math.random());
                //synchronized (R) {
                R.assign(name, eval);
                //}
                if (R.eval("is.function(" + name + ")").asInteger() == 1) {
                    return new Function(name);
                }
            } catch (RserveException ex) {
                throw new REXPMismatchException(eval, "assign");
            }
        } catch (REXPMismatchException e) {
            throw new ClassCastException(CAST_ERROR + eval + ": REXPMismatchException on " + eval.toDebugString());
        }

        if (eval.isNull()) {
            return null;
        } else {
            Log.Err.println(CAST_ERROR + eval + ": unsupported type " + eval.toDebugString());
            throw new ClassCastException(CAST_ERROR + eval + ": unsupported type " + eval.toDebugString());
        }
        //return rawEval.toString();
    }

    @Override
    public boolean isNull(Object o) {
        if (o == null) {
            return true;
        }
        if (!(o instanceof REXP)) {
            throw new IllegalArgumentException("[isNull] Not an REXP object: " + o);
        }
        try {
            return ((REXP) o).isNull();
        } catch (Exception ex) {
            throw new ClassCastException("[isNull] Cannot check is null " + o);
        }
    }

    @Override
    public String toString(Object o) {
        //assert o instanceof REXP : "Not an REXP object";
        if (o instanceof REXP) {
            if (o instanceof REXPNull) {
                return "NULL";
            } else if (((REXP) o).isList()) {
                RList l = ((REXPGenericVector) o).asList();
                String s = "";
                for (String k : l.keys()) {
                    s = s + k + ": " + toString(l.get(k)) + "\n";
                }
                return s;
            } else if (((REXP) o).isVector()) {
                try {
                    String[] ss = ((REXP) o).asStrings();
                    if (((REXP) o).length() > 10) {
                        return Arrays.asList(new String[]{ss[0], ss[1], "...(" + ss.length + ")...", ss[ss.length - 2], ss[ss.length - 1]}).toString();
                    } else {
                        return Arrays.asList(((REXP) o).asStrings()).toString();
                    }
                } catch (Exception ex) {
                    throw new ClassCastException("[toString] Cannot toString " + o);
                }
            } else {
                try {
                    return ((REXP) o).asString();
                } catch (Exception ex) {
                    throw new ClassCastException("[toString] Cannot toString " + o);
                }
            }
        } else if (o.getClass().isArray()) {
            return Arrays.asList(o).toString();
        } else {
            return o.toString();
        }
    }

    @Override
    public File putFileInWorkspace(File file) {
        return putFile(file, local2remotePath(file).getPath());
    }

    @Override
    public void getFileFromWorkspace(File file) {
        getFile(remote2localPath(file), file.getPath());
    }

    @Override
    public void save(File f, String... vars) throws RException {
        super.save(local2remotePath(f), vars);
    }

    @Override
    public void savels(File f, String... vars) throws RException {
        super.savels(local2remotePath(f), vars);
    }

    @Override
    public void toGraphic(File f, int width, int height, String fileformat, String... commands) {
        super.toGraphic(local2remotePath(f), width, height, fileformat, commands);
    }

    @Override
    public String asR2HTML(String command) {
        String html = super.asR2HTML(command);
        deleteFile("htmlfile_" + command.hashCode());
        return html;
    }

    /**
     * Get file from R environment to user filesystem
     *
     * @param localfile file to get (same name in R env. and user filesystem)
     */
    public void getFile(File localfile) {
        getFile(localfile, localfile.getName());
    }

    /**
     * Get file from R environment to user filesystem
     *
     * @param localfile local filesystem file
     * @param remoteFile R environment file name
     */
    public synchronized void getFile(File localfile, String remoteFile) {
        try {
            if (((REXP) silentlyRawEval("file.exists('" + remoteFile.replace("\\", "/") + "')", TRY_MODE)).asInteger() != 1) {
                log(HEAD_ERROR + IO_HEAD + "file " + remoteFile + " not found.", Level.ERROR);
            }
        } catch (Exception ex) {
            log(HEAD_ERROR + ex.getMessage() + "\n  getFile(File localfile=" + localfile.getAbsolutePath() + ", String remoteFile=" + remoteFile + ")", Level.ERROR);
            return;
        }
        if (localfile.exists()
                && (!RserveConf.isLocal()) //remote host for Rserve
                && !remoteFile.equals(localfile.getAbsolutePath())) { // different file remote & local
            if (!localfile.delete()) {
                log(HEAD_ERROR + IO_HEAD + "file " + localfile + " cannot be deleted.", Level.ERROR);
                return;
            }
            if (!localfile.exists()) {
                log(IO_HEAD + "Local file " + localfile + " deleted.", Level.INFO);
            } else {
                log(HEAD_ERROR + IO_HEAD + "file " + localfile + " still exists !", Level.ERROR);
                return;
            }
        }
        if (localfile.getParentFile() != null) {
            if (!localfile.getParentFile().isDirectory()) {
                if (!localfile.getParentFile().mkdir()) {
                    log(HEAD_ERROR + IO_HEAD + "parent directory " + localfile.getParentFile() + " not created.", Level.ERROR);
                    return;
                }
            }
        }

        note_code("file.copy(from='" + localfile + "',to='" + remoteFile + "') # Rserve.putFile");
        InputStream is = null;
        OutputStream os = null;
        //synchronized (R) {
        try {
            is = R.openFile(remoteFile.replace("\\", "/"));
            os = new BufferedOutputStream(new FileOutputStream(localfile));
            IOUtils.copy(is, os);
            log(IO_HEAD + "File " + remoteFile + " received.", Level.INFO);
            is.close();
            os.close();
        } catch (IOException e) {
            log(HEAD_ERROR + IO_HEAD + R.getLastError() + ": file " + remoteFile + " not transmitted.\n" + e.getMessage(), Level.ERROR);
        } finally {
            IOUtils.closeQuietly(is);
            IOUtils.closeQuietly(os);
        }
        //}
    }

    /**
     * delete R environment file
     *
     * @param remoteFile filename to delete
     */
    private synchronized void deleteFile(String remoteFile) {
        try {
            //synchronized (R) {
            R.removeFile(remoteFile.replace("\\", "/"));
            //}
        } catch (RserveException ex) {
            log(HEAD_EXCEPTION + ex.getMessage() + "\n  removeFile(String remoteFile=" + remoteFile + ")", Level.ERROR);
        }
    }

    /**
     * Send user filesystem file in r environement (like data)
     *
     * @param localfile File to send
     * @return remote File object
     */
    public File putFile(File localfile) {
        return putFile(localfile, localfile.getName());
    }

    /**
     * Send user filesystem file in r environement (like data)
     *
     * @param localfile File to send
     * @param remoteFile filename in R env.
     * @return remot File object
     */
    public synchronized File putFile(File localfile, String remoteFile) {
        if (!localfile.exists()) {
            //synchronized (R) {
            log(HEAD_ERROR + IO_HEAD + R.getLastError() + "\n  file " + localfile.getAbsolutePath() + " does not exists.", Level.ERROR);
            //}
        }
        try {
            if (((REXP) silentlyRawEval("file.exists('" + remoteFile.replace("\\", "/") + "')", TRY_MODE)).asInteger() == 1) {
                silentlyVoidEval("file.remove('" + remoteFile.replace("\\", "/") + "')", TRY_MODE);
                log(IO_HEAD + "Remote file " + remoteFile + " deleted.", Level.INFO);
            }
        } catch (REXPMismatchException ex) {
            log(HEAD_ERROR + ex.getMessage() + "\n  putFile(File localfile=" + localfile.getAbsolutePath() + ", String remoteFile=" + remoteFile + ")", Level.ERROR);
            return null;
        }
        InputStream is = null;
        OutputStream os = null;
        //synchronized (R) {
        try {
            os = R.createFile(remoteFile.replace("\\", "/"));
            is = new BufferedInputStream(new FileInputStream(localfile));
            IOUtils.copy(is, os);
            log(IO_HEAD + "File " + remoteFile + " sent.", Level.INFO);
            is.close();
            os.close();
        } catch (IOException e) {
            log(HEAD_ERROR + IO_HEAD + R.getLastError() + ": file " + remoteFile + " not writable.\n" + e.getMessage(), Level.ERROR);
        } finally {
            IOUtils.closeQuietly(is);
            IOUtils.closeQuietly(os);
        }
        //}
        return new File(remoteFile);
    }

    /**
     * Method to rawEval expression. Holds many optimizations (@see noVarsEvals)
     * and turn around for reliable usage (like engine auto restart). 1D Numeric
     * "vars" are replaced using Java replace engine instead of R one. Intended
     * to not interfer with current R env vars. Yes, it's hard-code :)
     *
     * @param expression String to evaluate
     * @param vars HashMap&lt;String, Object&gt; vars inside expression.
     * Passively overload current R env variables.
     * @return java castStrict Object Warning, UNSTABLE and high CPU cost.
     * @throws org.math.R.Rsession.RException Could not proxyEval with one of
     * vars
     */
    @Override
    public synchronized Object proxyEval(String expression, Map<String, Object> vars) throws RException {
        Object out = super.proxyEval(expression, vars);
        if (out == null) {
            boolean restartR = false;
            try {
                double testOut = asDouble(rawEval(testExpression));
                if (testOut == Double.NaN || Math.abs(testOut - testResult) > 0.1) {
                    restartR = true;
                }
            } catch (Exception e) {
                restartR = true;
            }
            if (restartR) {
                Log.Err.println("Problem occured, R engine restarted.");
                log(HEAD_CACHE + "Problem occured, R engine restarted.", Level.INFO);
                end();
                try {
                    startup();
                } catch (Exception ex) {
                    throw new RException(ex.getMessage());
                }

                return proxyEval(expression, vars);
            }
        }
        return out;
    }

    @Override
    public boolean isAvailable() {
        return connected;
    }

    public static void main(String[] args) throws Exception {
        //args = new String[]{"install.packages('lhs',repos='\"http://cloud.r-project.org/\"',lib='.')", "1+1"};
        if (args == null || args.length == 0) {
            args = new String[10];
            for (int i = 0; i < args.length; i++) {
                args[i] = Math.random() + "+pi";
            }
        }
        RserveSession R = null;
        int i = 0;
        if (args[0].startsWith(RserverConf.RURL_START)) {
            i++;
            R = new RserveSession(System.out, null, RserverConf.parse(args[0]));
        } else {
            R = new RserveSession(System.out, null, null);
        }

        for (int j = i; j < args.length; j++) {
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
            if (!asLogical(R.eval("exists('" + envName + "')"))) {
                R.eval(envName + " = new.env()");
            }
            R.eval("for (.n in ls(" + envName + ")) {\n .GlobalEnv[[.n]] = " + envName + "[[.n]]\n}");

        } catch (RserveException ex) {
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
        } catch (RserveException ex) {
            Log.Err.println(ex.getMessage());
        }
    }
}
