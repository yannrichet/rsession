package org.math.R;

import java.io.File;
import java.io.FileFilter;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.PrintStream;
import java.util.Collection;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Properties;
import org.rosuda.REngine.REXP;
import org.rosuda.REngine.REXPDouble;
import org.rosuda.REngine.REXPList;
import org.rosuda.REngine.REXPMismatchException;
import org.rosuda.REngine.REngineException;
import org.rosuda.REngine.RList;
import org.rosuda.REngine.Rserve.RConnection;
import org.rosuda.REngine.Rserve.RFileInputStream;
import org.rosuda.REngine.Rserve.RFileOutputStream;
import org.rosuda.REngine.Rserve.RserveException;

/**
 * @author richet
 */
public class Rsession implements Logger {

    public RConnection connection;
    PrintStream console;
    public static final String PACKAGEINSTALLED = "Package installed.";
    public static final String PACKAGELOADED = "Package loaded.";
    public boolean connected = false;
    static String separator = ",";
    public final static int MinRserveVersion = 103;
    Rdaemon localRserve;
    public RserverConf RserveConf;
    public final static String STATUS_NOT_SET = "Unknown status", STATUS_READY = "Ready", STATUS_ERROR = "Error", STATUS_ENDED = "End", STATUS_NOT_CONNECTED = "Not connected", STATUS_CONNECTING = "Connecting...";
    public String status = STATUS_NOT_SET;
    // <editor-fold defaultstate="collapsed" desc="Add/remove interfaces">
    LinkedList<Logger> loggers;

    public void addLogger(Logger l) {
        if (!loggers.contains(l)) {
            //System.out.println("+ logger " + l.getClass().getSimpleName());
            loggers.add(l);
        }
    }

    public void removeLogger(Logger l) {
        if (loggers.contains(l)) {
            loggers.remove(l);
        }
    }

    public void println(String message) {
        //System.out.println("println " + message+ " in "+loggers.size()+" loggers.");
        for (Logger l : loggers) {
            //System.out.println("  log in " + l.getClass().getSimpleName());
            l.println(message);
        }

    }
    LinkedList<BusyListener> busy = new LinkedList<BusyListener>();

    public void addBusyListener(BusyListener b) {
        if (!busy.contains(b)) {
            busy.add(b);
        }
    }

    public void removeBusyListener(BusyListener b) {
        if (busy.contains(b)) {
            busy.remove(b);
        }
    }

    public void setBusy(boolean bb) {
        for (BusyListener b : busy) {
            b.setBusy(bb);
        }

    }
    LinkedList<UpdateObjectsListener> updateObjects = new LinkedList<UpdateObjectsListener>();

    public void addUpdateObjectsListener(UpdateObjectsListener b) {
        if (!updateObjects.contains(b)) {
            updateObjects.add(b);
        }
    }

    public void removeUpdateObjectsListener(UpdateObjectsListener b) {
        if (updateObjects.contains(b)) {
            updateObjects.remove(b);
        }
    }
    LinkedList<EvalListener> eval = new LinkedList<EvalListener>();

    public void addEvalListener(EvalListener b) {
        if (!eval.contains(b)) {
            eval.add(b);
        }
    }

    public void removeEvalListener(EvalListener b) {
        if (eval.contains(b)) {
            eval.remove(b);
        }
    }
    // </editor-fold>

    // <editor-fold defaultstate="collapsed" desc="Conveniency static String methods">
    public static String toString(Object o) {
        if (o == null) {
            return "NULL";
        } else if (o instanceof double[]) {
            return cat((double[]) o);
        } else if (o instanceof double[][]) {
            return cat((double[][]) o);
        } else if (o instanceof int[]) {
            return cat((int[]) o);
        } else if (o instanceof int[][]) {
            return cat((int[][]) o);
        } else if (o instanceof Object[]) {
            return cat((Object[]) o);
        } else if (o instanceof Object[][]) {
            return cat((Object[][]) o);
        } else if (o instanceof RList) {
            return cat((RList) o);
        } else {
            return o.toString();
        }
    }

    public static String cat(RList list) {
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

    public static String cat(double[] array) {
        if (array == null || array.length == 0) {
            return "NA";
        }

        String o = array[0] + "";
        if (array.length > 1) {
            for (int i = 1; i < array.length; i++) {
                o += (separator + (array[i] + ""));
            }
        }
        return o;
    }

    public static String cat(int[] array) {
        if (array == null || array.length == 0) {
            return "NA";
        }

        String o = array[0] + "";
        if (array.length > 1) {
            for (int i = 1; i < array.length; i++) {
                o += (separator + (array[i] + ""));
            }
        }
        return o;
    }

    public static String cat(double[][] array) {
        if (array == null || array.length == 0 || array[0].length == 0) {
            return "NA";
        }

        String o = cat(array[0]);
        if (array.length > 1) {
            for (int i = 1; i < array.length; i++) {
                o += "\n" + cat(array[i]);
            }
        }
        return o;
    }

    public static String cat(int[][] array) {
        if (array == null || array.length == 0 || array[0].length == 0) {
            return "NA";
        }

        String o = cat(array[0]);
        if (array.length > 1) {
            for (int i = 1; i < array.length; i++) {
                o += "\n" + cat(array[i]);
            }
        }
        return o;
    }

    public static String cat(Object[] array) {
        if (array == null || array.length == 0 || array[0] == null) {
            return "";
        }

        String o = array[0].toString();
        if (array.length > 1) {
            for (int i = 1; i < array.length; i++) {
                o += (separator + (array[i] == null ? "" : array[i].toString()));
            }
        }

        return o;
    }

    public static String cat(String sep, String[] array) {
        if (array == null || array.length == 0 || array[0] == null) {
            return "";
        }

        String o = array[0].toString();
        if (array.length > 1) {
            for (int i = 1; i < array.length; i++) {
                o += (sep + (array[i] == null ? "" : array[i].toString()));
            }
        }

        return o;
    }

    public static String cat(Object[][] array) {
        if (array == null || array.length == 0 || array[0].length == 0) {
            return "NA";
        }

        String o = cat(array[0]);
        if (array.length > 1) {
            for (int i = 1; i < array.length; i++) {
                o += "\n" + cat(array[i]);
            }
        }
        return o;
    }
    // </editor-fold>

    /** Map java File object to R path (as string)
     * @param path java File object
     */
    public static String toRpath(File path) {
        return toRpath(path.getAbsolutePath());
    }

    /** Map java path to R path (as string)
     * @param path java string path
     */
    public static String toRpath(String path) {
        return path.replaceAll("\\\\", "/");
    }

    /** Build a new local Rsession
     * @param console PrintStream for R output
     * @param localRProperties properties to pass to R (eg http_proxy or R libpath)
     */
    public static Rsession newLocalInstance(final PrintStream console, Properties localRProperties) {
        return new Rsession(console, RserverConf.newLocalInstance(localRProperties), false);
    }

    /** Build a new remote Rsession
     * @param console PrintStream for R output
     * @param serverconf RserverConf server configuration object, giving IP, port, login, password, properties to pass to R (eg http_proxy or R libpath)
     */
    public static Rsession newRemoteInstance(final PrintStream console, RserverConf serverconf) {
        return new Rsession(console, serverconf, false);
    }

    /** Build a new Rsession. Fork to local spawned Rsession if given remote one failed to initialized.
     * @param console PrintStream for R output
     * @param serverconf RserverConf server configuration object, giving IP, port, login, password, properties to pass to R (eg http_proxy)
     */
    public static Rsession newInstanceTry(final PrintStream console, RserverConf serverconf) {
        return new Rsession(console, serverconf, true);
    }

    /** create a new Rsession.
     * @param console PrintStream for R output
     * @param serverconf RserverConf server configuration object, giving IP, port, login, password, properties to pass to R (eg http_proxy or R libpath)
     * @param tryLocalRServe local spawned Rsession if given remote one failed to initialized
     */
    public Rsession(final PrintStream console, RserverConf serverconf, boolean tryLocalRServe) {
        Runtime.getRuntime().addShutdownHook(new Thread() {

            @Override
            public void run() {
                end();
            }
        });

        this.console = console;
        RserveConf = serverconf;

        loggers = new LinkedList<Logger>();
        loggers.add(new Logger() {

            public void println(String message) {
                console.println(message);
            }
        });

        if (RserveConf == null) {
            if (tryLocalRServe) {
                RserveConf = RserverConf.newLocalInstance(null);
                println("No Rserve conf given. Trying to use " + RserveConf.toString());
                begin(true);
            } else {
                println("No Rserve conf given. Failed to start session.");
                status = STATUS_ERROR;
            }
        } else {
            begin(tryLocalRServe);
        }
    }

    /**
     * @return status of Rsession
     */
    public String getStatus() {
        return status;
    }

    void begin(boolean tryLocal) {
        status = STATUS_NOT_CONNECTED;

        /*if (RserveConf == null) {
        RserveConf = RserverConf.newLocalInstance(null);
        println("No Rserve conf given. Trying to use " + RserveConf.toString());
        }*/

        status = STATUS_CONNECTING;

        connection = RserveConf.connect();
        connected = (connection != null);

        if (!connected) {
            status = STATUS_ERROR;
            String message = "Rserve " + RserveConf + " is not accessible.";
            println(message);
        } else if (connection.getServerVersion() < MinRserveVersion) {
            status = STATUS_ERROR;
            String message = "Rserve " + RserveConf + " version is too low.";
            println(message);
        } else {
            status = STATUS_READY;
            return;
        }

        if (tryLocal) {//try a local start of Rserve
            status = STATUS_CONNECTING;

            RserveConf = RserverConf.newLocalInstance(null);
            println("Trying to spawn " + RserveConf.toString());

            localRserve = new Rdaemon(RserveConf, this);
            localRserve.start();

            try {
                Thread.sleep(1000);
            } catch (InterruptedException ie) {
                ie.printStackTrace();
            }

            connection = RserveConf.connect();
            connected = (connection != null);

            if (!connected) {//failed !
                String message2 = "Failed to launch local Rserve. Unable to initialize Rsession.";
                println(message2);
                System.err.println(message2);
                throw new IllegalArgumentException(message2);
            } else {
                println("Local Rserve started. (Version " + connection.getServerVersion() + ")");
            }

        }
        //if (r.getServerVersion() < MinRserveVersion) {
        //    throw new IllegalArgumentException("RServe version too low: " + r.getServerVersion() + "\n  Rserve >= 0.6 needed.");
        //}

    }
    //RSession previous;

    /**
     * correctly (depending on execution platform) shutdown Rsession.
     */
    public void end() {
        log("Ending session...");
        //try {
            /*previous = */ connection.close();

        //} catch (RserveException e) {
        //    log(e.getMessage());
        //}
        if ((!UNIX_OPTIMIZE || System.getProperty("os.name").contains("Win")) && localRserve != null) {
            localRserve.stop();
        }
        log("Session teminated.");
    }
    public final static boolean UNIX_OPTIMIZE = true;
    static String lastmessage = "";
    static int repeated = 0;

    public void log(String message) {
        if (message.equals(lastmessage) && repeated < 100) {
            repeated++;
            return;
        } else {
            if (repeated > 0) {
                println("    Repeated " + repeated + " times.");
                repeated = 0;
                lastmessage = message;
                println(message);
            } else {
                lastmessage = message;
                println(message);
            }
        }
    }

    /**
     * @return available R commands
     */
    public String[] listCommands() {
        silentlyEval(".keyWords <- function() {n <- length(search());result <- c();for (i in 2:n) {result <- c(result,ls(pos=i,all.names=TRUE))}; result}");
        REXP rexp = silentlyEval(".keyWords()");
        String as[] = null;
        try {
            if (rexp != null && (as = rexp.asStrings()) != null) {
                return as;
            } else {
                return null;
            }
        } catch (REXPMismatchException ex) {
            log(HEAD_ERROR + ex.getMessage() + "\n  listCommands()");
            return null;
        }
    }
    // <editor-fold defaultstate="collapsed" desc="Packages management">
    public String repos = "http://cran.r-project.org";

    /**
     * @param url CRAN repository to use for packages installation (eg http://cran.r-project.org)
     */
    public void setCRANRepository(String url) {
        repos = url;
    }

    /**
     * @return CRAN repository used for packages installation
     */
    public String getCRANRepository() {
        return repos;
    }

    /**
     * Check for package loaded in R environment.
     * @param pack R package name
     * @return package loading status
     */
    public boolean isPackageLoaded(String pack) {
        silentlyVoidEval("loadedpacks<-.packages()", false);
        boolean isloaded = false;
        try {
            isloaded = silentlyEval("is.element(set=loadedpacks,el='" + pack + "')").asInteger() == 1;
        } catch (REXPMismatchException ex) {
            log(HEAD_ERROR + ex.getMessage() + "\n  isPackageLoaded(String pack=" + pack + ")");
        }
        if (isloaded) {
            log("   package " + pack + " is loaded.");
        } else {
            log("   package " + pack + " is not loaded.");
        }

        silentlyEval("rm(loadedpacks)");
        return isloaded;
    }

    /**
     * Check for package installed in R environment.
     * @param pack R package name
     * @param version R package version
     * @return package loading status
     */
    public boolean isPackageInstalled(String pack, String version) {
        silentlyVoidEval("packs<-installed.packages(noCache=TRUE)", false);
        boolean isinstalled = false;
        REXP r = silentlyEval("is.element(set=packs,el='" + pack + "')");
        try {
            if (r != null) {
                isinstalled = (r.asInteger() == 1);
            } else {
                log(HEAD_ERROR + "Could not list installed packages" + "\n  isPackageInstalled(String pack=" + pack + ", String version=" + version + ")");
            }
        } catch (REXPMismatchException ex) {
            log(HEAD_ERROR + ex.getMessage() + "\n  isPackageInstalled(String pack=" + pack + ", String version=" + version + ")");
        }
        if (isinstalled) {
            log("   package " + pack + " is installed.");
        } else {
            log("   package " + pack + " is not installed.");
        }

        if (isinstalled && version != null && version.length() > 0) {
            try {
                isinstalled = silentlyEval("packs['" + pack + "','Version'] == \"" + version + "\"").asInteger() == 1;
            } catch (REXPMismatchException ex) {
                log(HEAD_ERROR + ex.getMessage() + "\n  isPackageInstalled(String pack=" + pack + ", String version=" + version + ")");
            }
            try {
                log("    version of package " + pack + " is " + silentlyEval("packs['" + pack + "','Version']").asString());
            } catch (REXPMismatchException ex) {
                log(HEAD_ERROR + ex.getMessage() + "\n  isPackageInstalled(String pack=" + pack + ", String version=" + version + ")");
            }
            if (isinstalled) {
                log("   package " + pack + " (" + version + ") " + " is installed.");
            } else {
                log("   package " + pack + " (" + version + ") " + " is not installed.");
            }

        }
        silentlyEval("rm(packs)");
        return isinstalled;
    }

    /**
     * Start installation procedure of R packages
     * @param pack packages to install
     * @param load automatically load packages after successfull installation
     * @return installation status
     */
    public String installPackages(String[] pack, boolean load) {
        String resall = "";
        for (String pv : pack) {
            String res = installPackage(pv, load);
            if (load) {
                if (!res.equals(PACKAGELOADED)) {
                    resall += "\n" + res;
                }
            } else {
                if (!res.equals(PACKAGEINSTALLED)) {
                    resall += "\n" + res;
                }
            }
        }
        if (resall.length() > 0) {
            return resall;
        } else {
            return load ? PACKAGELOADED : PACKAGEINSTALLED;
        }
    }

    /**
     * Start installation procedure of local R package
     * @param pack package to install
     * @param dir directory where package file (.zip, .tar.gz or .tgz) is located.
     * @param load automatically load package after successfull installation
     * @return installation status
     */
    public String installPackage(final String pack, File dir, boolean load) {
        log("  trying to load package " + pack);

        if (isPackageInstalled(pack, null)) {
            log("  package " + pack + " already installed.");
            if (load) {
                return loadPackage(pack);
            } else {
                return PACKAGEINSTALLED;
            }
        } else {
            log("  package " + pack + " not yet installed.");
        }

        File[] pack_files = dir.listFiles(new FileFilter() {

            public boolean accept(File pathname) {
                return pathname.getName().contains(pack);
            }
        });
        if (pack_files == null || pack_files.length == 0) {
            log("  impossible to find package " + pack + " in directory " + dir.getAbsolutePath() + " !");
            return "Impossible to find package " + pack + " in directory " + dir.getAbsolutePath() + " !";
        } else {
            log("  found package " + pack + " : " + pack_files[0].getAbsolutePath());
        }

        sendFile(pack_files[0]);
        eval("install.packages('" + pack_files[0].getName() + "',repos=NULL," + (RserveConf.RLibPath == null ? "" : "lib=" + RserveConf.RLibPath + ",") + "dependencies=TRUE)");
        log("  request package " + pack + " install...");

        if (isPackageInstalled(pack, null)) {
            log("  package " + pack + " installation sucessfull.");
            if (load) {
                return loadPackage(pack);
            } else {
                return PACKAGEINSTALLED;
            }
        } else {
            log("  package " + pack + " installation failed.");
            return "Impossible to install package " + pack + " !";
        }
    }

    /**
     * Start installation procedure of CRAN R package
     * @param pack package to install
     * @param load automatically load package after successfull installation
     * @return installation status
     */
    public String installPackage(String pack, boolean load) {
        log("  trying to load package " + pack);

        if (isPackageInstalled(pack, null)) {
            log("  package " + pack + " already installed.");
            if (load) {
                return loadPackage(pack);
            } else {
                return PACKAGEINSTALLED;
            }
        } else {
            log("  package " + pack + " not yet installed.");
        }

        /*if (!Configuration.isWWWConnected()) {
        log("  package " + pack + " not accessible on " + repos + ": CRAN unreachable.");
        return "Impossible to get package " + pack + " from " + repos;
        }*/

        eval("install.packages('" + pack + "',repos='" + repos + "'," + (RserveConf.RLibPath == null ? "" : "lib=" + RserveConf.RLibPath + ",") + "dependencies=TRUE)");
        log("  request package " + pack + " install...");


        if (isPackageInstalled(pack, null)) {
            log("  package " + pack + " installation sucessfull.");
            if (load) {
                return loadPackage(pack);
            } else {
                return PACKAGEINSTALLED;
            }
        } else {
            log("  package " + pack + " installation failed.");
            return "Impossible to install package " + pack + " !";
        }
    }

    /**
     * load R backage using library() command
     * @param pack R package name
     * @return loading status
     */
    public String loadPackage(String pack) {
        eval("library(" + pack + ")");
        log("  request package " + pack + " loading...");

        if (isPackageLoaded(pack)) {
            log("  package " + pack + " loading sucessfull.");
            return PACKAGELOADED;
        } else {
            log("  package " + pack + " loading failed.");
            return "Impossible to loading package " + pack + " !";
        }
    }
    // </editor-fold>
    final static String HEAD_EVAL = "[eval] ";
    final static String HEAD_EXCEPTION = "[exception] ";
    final static String HEAD_ERROR = "[error] ";

    /**
     * Silently (ie no log) launch R command without return value. Encapsulate command in try() to cacth errors
     * @param expression R expresison to evaluate
     */
    public void silentlyVoidEval(String expression) {
        silentlyVoidEval(expression, true);
    }

    /**
     * Silently (ie no log) launch R command without return value.
     * @param expression R expresison to evaluate
     * @param tryEval encapsulate command in try() to cacth errors
     */
    public void silentlyVoidEval(String expression, boolean tryEval) {

        assert connected : "R environment not initialized.";
        if (expression == null) {
            return;
        }
        if (expression.trim().length() == 0) {
            return;
        }
        for (EvalListener b : eval) {
            b.eval(expression);
        }
        try {
            connection.voidEval((tryEval ? "try(" : "") + expression + (tryEval ? ")" : ""));
        } catch (RserveException ex) {
            log(HEAD_EXCEPTION + ex.getMessage() + "\n  " + expression);
        }
    }

    /**
     * Launch R command without return value.
     * @param expression R expresison to evaluate
     * @param tryEval encapsulate command in try() to cacth errors
     */
    public void voidEval(String expression, boolean tryEval) {
        log(HEAD_EVAL + expression);

        silentlyVoidEval(expression, tryEval);

        for (UpdateObjectsListener b : updateObjects) {
            b.update();
        }
    }

    /**
     * Launch R command without return value. Encapsulate command in try() to cacth errors.
     * @param expression R expresison to evaluate
     */
    public void voidEval(String expression) {
        voidEval(expression, true);
    }

    /**
     * Silently (ie no log) launch R command and return value. Encapsulate command in try() to cacth errors.
     * @param expression R expresison to evaluate
     * @return REXP R expression
     */
    public REXP silentlyEval(String expression) {
        return silentlyEval(expression, true);
    }

    /**
     * Silently (ie no log) launch R command and return value.
     * @param expression R expresison to evaluate
     * @param tryEval encapsulate command in try() to cacth errors
     * @return REXP R expression
     */
    public REXP silentlyEval(String expression, boolean tryEval) {
        assert connected : "R environment not initialized.";
        if (expression == null) {
            return null;
        }
        if (expression.trim().length() == 0) {
            return null;
        }
        for (EvalListener b : eval) {
            b.eval(expression);
        }
        REXP e = null;
        try {
            e = connection.eval((tryEval ? "try(" : "") + expression + (tryEval ? ")" : ""));
        } catch (RserveException ex) {
            log(HEAD_EXCEPTION + ex.getMessage() + "\n  " + expression);
        }

        if (tryEval && e != null) {
            try {
                if (e.isString() && e.asStrings().length > 0 && e.asString().toLowerCase().startsWith("error")) {
                    log(HEAD_ERROR + e.asString() + "\n  " + expression);
                    e = null;
                }
            } catch (REXPMismatchException ex) {
                log(HEAD_ERROR + ex.getMessage() + "\n  " + expression);
                return null;
            }
        }
        return e;
    }

    /**
     * Launch R command and return value.
     * @param expression R expresison to evaluate
     * @param tryEval encapsulate command in try() to cacth errors
     * @return REXP R expression
     */
    public REXP eval(String expression, boolean tryEval) {
        log(HEAD_EVAL + expression);

        REXP e = silentlyEval(expression, tryEval);

        for (UpdateObjectsListener b : updateObjects) {
            b.update();
        }

        return e;
    }

    /**
     * Launch R command and return value. Encapsulate command in try() to cacth errors.
     * @param expression R expresison to evaluate
     * @return REXP R expression
     */
    public REXP eval(String expression) {
        return eval(expression, true);
    }

    /**
     * delete all variables in R environment
     */
    public void rmAll() {
        eval("rm(list=ls(all=TRUE))");
    }

    /**
     * create a R list with given R objects
     * @param vars R object names
     * @return list expression
     */
    public static String buildList(String... vars) {
        if (vars.length > 1) {
            StringBuffer b = new StringBuffer("c(");
            for (String v : vars) {
                b.append(v + ",");
            }

            return b.substring(0, b.length() - 1) + ")";
        } else {
            return vars[0];
        }
    }

    /**
     * create a R list with given R strings
     * @param vars R strings
     * @return String list expression
     */
    public static String buildListString(String... vars) {
        if (vars.length > 1) {
            StringBuffer b = new StringBuffer("c(");
            for (String v : vars) {
                b.append("'" + v + "',");
            }

            return b.substring(0, b.length() - 1) + ")";
        } else {
            return "'" + vars[0] + "'";
        }
    }

    /**
     * create a R list with given R string patterns
     * @param vars R string patterns
     * @return ls pattern expression
     */
    public static String buildListPattern(String... vars) {
        if (vars.length > 1) {
            StringBuffer b = new StringBuffer("c(");
            for (String v : vars) {
                b.append("ls(pattern='" + v + "'),");
            }

            return b.substring(0, b.length() - 1) + ")";
        } else {
            return "ls(pattern='" + vars[0] + "')";
        }
    }

    /**
     * loads R source file (eg ".R" file)
     * @param f ".R" file to source
     */
    public void source(File f) {
        sendFile(f);
        voidEval("source('" + f.getName() + "')");
    }

    /**
     * loads R data file (eg ".Rdata" file)
     * @param f ".Rdata" file to load
     */
    public void load(File f) {
        sendFile(f);
        try {
            assert eval("file.exists('" + f.getName() + "')").asInteger() == 1;
        } catch (REXPMismatchException r) {
            r.printStackTrace();
        }
        voidEval("load('" + f.getName() + "')");
    }

    /**
     * list R variables in R env.
     * @return list of R objects names
     */
    public String[] ls() {
        try {
            return (String[]) cast(eval("ls()"));
        } catch (REXPMismatchException re) {
            return new String[0];
        }
    }

    /**
     * list R variables in R env. matching patterns
     * @param vars R object name patterns
     * @return list of R objects names
     */
    public String[] ls(String... vars) {
        if (vars == null || vars.length == 0) {
            try {
                return (String[]) cast(eval("ls()"));
            } catch (REXPMismatchException re) {
                return new String[0];
            }
        } else if (vars.length == 1) {
            try {
                return (String[]) cast(eval(buildListPattern(vars[0])));
            } catch (REXPMismatchException re) {
                return new String[0];
            }
        } else {
            try {
                return (String[]) cast(eval(buildListPattern(vars)));
            } catch (REXPMismatchException re) {
                return new String[0];
            }
        }
    }

    /**
     * delete R variables in R env.
     * @param vars R objects names
     */
    public void rm(String... vars) {
        if (vars.length == 1) {
            voidEval("rm(" + vars[0] + ")");
        } else {
            voidEval("rm(list=" + buildListString(vars) + ")");
        }
    }

    /**
     * delete R variables in R env. matching patterns
     * @param vars R object name patterns
     */
    public void rmls(String... vars) {
        if (vars.length == 1) {
            voidEval("rm(list=" + buildListPattern(vars[0]) + ")");
        } else {
            voidEval("rm(list=" + buildListPattern(vars) + ")");
        }
    }

    /**
     * Save R variables in data file
     * @param f file to store data (eg ".Rdata")
     * @param vars R variables to save
     */
    public void save(File f, String... vars) {
        if (vars.length == 1) {
            voidEval("save(file='" + f.getName() + "'," + vars[0] + ",ascii=TRUE)");
        } else {
            voidEval("save(file='" + f.getName() + "',list=" + buildListString(vars) + ",ascii=TRUE)");
        }
        receiveFile(f);
        removeFile(f.getName());
    }

    /**
     * Save R variables in data file
     * @param f file to store data (eg ".Rdata")
     * @param vars R variables names patterns to save
     */
    public void savels(File f, String... vars) {
        if (vars.length == 1) {
            voidEval("save(file='" + f.getName() + "',list=" + buildListPattern(vars[0]) + ",ascii=TRUE)");
        } else {
            voidEval("save(file='" + f.getName() + "',list=" + buildListPattern(vars) + ",ascii=TRUE)");
        }
        receiveFile(f);
        removeFile(f.getName());
    }
    final static String[] types = {"data.frame", "null", "function", "array", "integer", "character", "double"};

    /**
     *
     * @param robject R object name
     * @return R type of object
     */
    public String typeOf(String robject) {
        if (robject == null) {
            return "NULL";
        }
        for (String t : types) {
            REXP is = silentlyEval("is." + t + "(" + robject + ")");
            try {
                if (is != null && is.asInteger() == 1) {
                    return t;
                }
            } catch (REXPMismatchException ex) {
                log(HEAD_ERROR + "[typeOf] " + robject + " type unknown.");
                return null;
            }
        }
        return "unknown";
    }

    /**
     * Build R liost in R env.
     * @param data numeric data (eg matrix)
     * @param names names of columns
     * @return RList object
     */
    public static RList buildRList(double[][] data, String... names) {
        assert data[0].length == names.length;
        REXP[] vals = new REXP[names.length];

        for (int i = 0; i < names.length; i++) {
            //System.out.println("i=" + i);
            double[] coli = new double[data.length];
            for (int j = 0; j < coli.length; j++) {
                //System.out.println("  j=" + j);
                coli[j] = data[j][i];
            }
            vals[i] = new REXPDouble(coli);
        }
        return new RList(vals, names);
    }

    /**
     * Build R liost in R env.
     * @param coldata numeric data as an array of numeric vectors
     * @param names names of columns
     * @return RList object
     */
    public static RList buildRList(List<double[]> coldata, String... names) {
        assert coldata.size() == names.length;
        RList list = new RList(coldata.size(), true);
        for (int i = 0; i < names.length; i++) {
            list.put(names[i], new REXPDouble(coldata.get(i)));
        }
        return list;
    }

    /**
     * delete R object in R env.
     * @param varname R objects to delete
     */
    public void unset(String... varname) {
        rm(varname);
    }

    /**
     * delete R object in R env.
     * @param varname R objects to delete
     */
    public void unset(Collection varname) {
        for (Object v : varname) {
            rm(v.toString());
        }
    }

    /**
     * Set R object in R env.
     * @param _vars R objects to set as key/values
     */
    public void set(HashMap<String, Object> _vars) {
        for (String varname : _vars.keySet()) {
            set(varname, _vars.get(varname));
        }
    }

    /**
     *  Set R list in R env.
     * @param varname R list name
     * @param data numeric data in list
     * @param names names of columns
     */
    public void set(String varname, double[][] data, String... names) {
        RList list = buildRList(data, names);
        log(HEAD_SET + varname + " <- " + toString(list));
        try {
            connection.assign(varname, REXP.createDataFrame(list));
        } catch (REXPMismatchException re) {
            log(HEAD_ERROR + " RList " + list.toString() + " not convertible as dataframe.");
        } catch (RserveException ex) {
            log(HEAD_EXCEPTION + ex.getMessage() + "\n  set(String varname=" + varname + ",double[][] data, String... names)");
        }
    }
    public final static String HEAD_SET = "[set] ";

    /**
     * Set R object in R env.
     * @param varname R object name
     * @param var R object value
     */
    public void set(String varname, Object var) {
        assert connected : "R environment not initialized. Please make sure that R.init() method was called first.";

        log(HEAD_SET + varname + " <- " + var.toString());
        /*if (var instanceof DataFrame) {
        DataFrame df = (DataFrame) var;
        set("names_" + varname, df.keySet().toArray(new String[]{}));
        set("data_" + varname, df.dataSet());
        eval(varname + "=data.frame(x=data_" + varname + ")");
        silentlyEval("names(" + varname + ") <- names_" + varname);
        silentlyEval("rm(names_" + varname + ",data_" + varname + ")");
        }*/
        if (var instanceof RList) {
            RList l = (RList) var;
            try {
                connection.assign(varname, new REXPList(l));
            } catch (RserveException ex) {
                log(HEAD_EXCEPTION + ex.getMessage() + "\n  set(String varname=" + varname + ",Object (RList) var)");
            }
        } else if (var instanceof File) {
            silentlyVoidEval(varname + "<-'" + ((File) var).getName() + "'");
        } else if (var instanceof Integer) {
            silentlyVoidEval(varname + "<-" + (Integer) var);
        } else if (var instanceof Double) {
            silentlyVoidEval(varname + "<-" + (Double) var);
        } else if (var instanceof double[]) {
            try {
                connection.assign(varname, (double[]) var);
            } catch (REngineException ex) {
                log(HEAD_ERROR + ex.getMessage() + "\n  set(String varname=" + varname + ",Object (double[]) var)");
            }
            silentlyVoidEval(varname/*, cat((double[]) var)*/);
        } else if (var instanceof double[][]) {
            double[][] array = (double[][]) var;
            int rows = array.length;
            int col = array[0].length;
            try {
                connection.assign("row_" + varname, reshapeAsRow(array));
            } catch (REngineException ex) {
                log(HEAD_ERROR + ex.getMessage() + "\n  set(String varname=" + varname + ",Object (double[][]) var)");
            }
            //eval("print(row_" + varname + ")");
            silentlyVoidEval(varname + "<-array(row_" + varname + ",c(" + rows + "," + col + "))");
            silentlyVoidEval("rm(row_" + varname + ")");
        } else if (var instanceof String) {
            try {
                connection.assign(varname, (String) var);
            } catch (RserveException ex) {
                log(HEAD_EXCEPTION + ex.getMessage() + "\n  set(String varname=" + varname + ",Object (String) var)");

            }
            silentlyVoidEval(varname/*, (String) var*/);
        } else if (var instanceof String[]) {
            try {
                connection.assign(varname, (String[]) var);
            } catch (REngineException ex) {
                log(HEAD_ERROR + ex.getMessage() + "\n  set(String varname=" + varname + ",Object (String[]) var)");
            }
            silentlyVoidEval(varname/*, cat((String[]) var)*/);
        } else {
            throw new IllegalArgumentException("Variable " + varname + " is not double, double[],  double[][], String or String[]. R engine can not handle.");
        }

    }

    private static double[] reshapeAsRow(double[][] a) {
        double[] reshaped = new double[a.length * a[0].length];
        int ir = 0;
        for (int j = 0; j < a[0].length; j++) {
            for (int i = 0; i < a.length; i++) {
                reshaped[ir] = a[i][j];
                ir++;
            }
        }
        return reshaped;
    }

    /**
     * cast R object in java object
     * @param eval REXP R object
     * @return java object
     * @throws org.rosuda.REngine.REXPMismatchException
     */
    public static Object cast(REXP eval) throws REXPMismatchException {
        if (eval == null) {
            return null;
        }

        /*int[] dim = eval.dim();
        String dims = "[";
        if (dim == null) {
        dims = "NULL";
        } else {
        for (int i : dim) {
        dims += (i + " ");
        }
        dims += "]";
        }
        
        System.out.println(eval.toString() +
        "\n  isComplex=     " + (eval.isComplex() ? "TRUE" : "    false") +
        "\n  isEnvironment= " + (eval.isEnvironment() ? "TRUE" : "    false") +
        "\n  isExpression=  " + (eval.isExpression() ? "TRUE" : "    false") +
        "\n  isFactor=      " + (eval.isFactor() ? "TRUE" : "    false") +
        "\n  isFactor=      " + (eval.isFactor() ? "TRUE" : "    false") +
        "\n  isInteger=     " + (eval.isInteger() ? "TRUE" : "    false") +
        "\n  isLanguage=    " + (eval.isLanguage() ? "TRUE" : "    false") +
        "\n  isList=        " + (eval.isList() ? "TRUE" : "    false") +
        "\n  isLogical=     " + (eval.isLogical() ? "TRUE" : "    false") +
        "\n  isNull=        " + (eval.isNull() ? "TRUE" : "    false") +
        "\n  isNumeric=     " + (eval.isNumeric() ? "TRUE" : "    false") +
        "\n  isRaw=         " + (eval.isRaw() ? "TRUE" : "    false") +
        "\n  isRecursive=   " + (eval.isRecursive() ? "TRUE" : "    false") +
        "\n  isString=      " + (eval.isString() ? "TRUE" : "    false") +
        "\n  isSymbol=      " + (eval.isSymbol() ? "TRUE" : "    false") +
        "\n  isVector=      " + (eval.isVector() ? "TRUE" : "    false") +
        "\n  length=  " + (eval.length()) +
        "\n  dim=  " + dims);*/

        if (eval.isNumeric()) {
            if (eval.dim() == null) {
                double[] array = eval.asDoubles();
                if (array.length == 0) {
                    return null;
                }
                if (array.length == 1) {
                    return array[0];
                }
                return array;
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
            return eval.asList();
        }

        if (eval.isNull()) {
            return null;
        } else {
            System.out.println("Unsupported type: " + eval.toDebugString());
        }
        return eval.toString();
    }

    /**
     * cast to java String representation of object
     * @param eval REXP R object
     * @return String representation
     */
    public static String castToString(REXP eval) {
        if (eval == null) {
            return "";
        }
        return eval.toString();
    }

    /**
     * Create a JPEG file for R graphical command output
     * @param f File to store data (eg .jpg file)
     * @param width width of image
     * @param height height of image
     * @param command R command to create image (eg plot())
     */
    public void toJPEG(File f, int width, int height, String command) {
        int h = Math.abs(f.hashCode());
        set("plotfile_" + h, f.getName());
        silentlyEval("jpeg(plotfile_" + h + ", width=" + width + ", height=" + height + ")");
        eval(command);
        silentlyEval("dev.off()");
        receiveFile(f);
        rm("plotfile_" + h);
        removeFile(f.getName());
    }

    /**
     * Get R command text output in HTML format
     * @param command R command returning text
     * @return HTML string
     */
    public String asHTML(String command) {
        installPackage("R2HTML", true);
        int h = Math.abs(command.hashCode());
        silentlyEval("HTML(file=\"htmlfile_" + h + "\", " + command + ")");
        String[] lines = null;
        try {
            lines = silentlyEval("readLines(\"htmlfile_" + h + "\")").asStrings();
        } catch (REXPMismatchException e) {
            return e.getMessage();
        }
        removeFile("htmlfile_" + h);
        if (lines == null) {
            return "";
        }

        StringBuffer sb = new StringBuffer();
        for (String l : lines) {
            sb.append(l);
            sb.append("\n");
        }
        return sb.toString();
    }

    /**
     * Get R command text output
     * @param command R command returning text
     * @return String
     */
    public String asString(String command) {
        int h = Math.abs(command.hashCode());
        String[] lines = null;
        try {
            lines = silentlyEval("capture.output( " + command + ")").asStrings();
        } catch (REXPMismatchException e) {
            return e.getMessage();
        }

        if (lines == null) {
            return "";
        }

        StringBuffer sb = new StringBuffer();
        for (String l : lines) {
            sb.append(l);
            sb.append("\n");
        }
        return sb.toString();
    }
    final static String IO_HEAD = "[IO] ";

    /**
     * Get file from R environment to user filesystem
     * @param localfile file to get (same name in R env. and user filesystem)
     */
    public void receiveFile(File localfile) {
        receiveFile(localfile, localfile.getName());
    }

    /**
     * Get file from R environment to user filesystem
     * @param localfile local filesystem file
     * @param remoteFile R environment file name
     */
    public void receiveFile(File localfile, String remoteFile) {
        try {
            if (silentlyEval("file.exists('" + remoteFile + "')").asInteger() != 1) {
                log(HEAD_ERROR + IO_HEAD + "file " + remoteFile + " not found.");
            }
        } catch (REXPMismatchException ex) {
            log(HEAD_ERROR + ex.getMessage() + "\n  getFile(File localfile=" + localfile.getAbsolutePath() + ", String remoteFile=" + remoteFile + ")");
            return;
        }
        if (localfile.exists()) {
            localfile.delete();
            if (!localfile.exists()) {
                log(IO_HEAD + "Local file " + localfile.getAbsolutePath() + " deleted.");
            } else {
                log(HEAD_ERROR + IO_HEAD + "file " + localfile + " still exists !");
                return;
            }
        }

        RFileInputStream is = null;
        FileOutputStream os = null;
        try {
            is = connection.openFile(remoteFile);
            os = new FileOutputStream(localfile);
            byte[] buf = new byte[512];
            try {
                connection.setSendBufferSize(buf.length);
            } catch (RserveException ex) {
                log(HEAD_EXCEPTION + ex.getMessage() + "\n  getFile(File localfile=" + localfile.getAbsolutePath() + ", String remoteFile=" + remoteFile + ")");
            }
            int n = 0;
            while ((n = is.read(buf)) > 0) {
                os.write(buf, 0, n);
            }

        } catch (IOException e) {
            log(HEAD_ERROR + IO_HEAD + connection.getLastError() + ": file " + remoteFile + " not found.\n" + e.getMessage());
            return;
        } finally {
            try {
                if (os != null) {
                    os.close();
                }
                if (is != null) {
                    is.close();
                }
            } catch (IOException ee) {
                ee.printStackTrace();
            }
        }
        log(IO_HEAD + "File " + remoteFile + " received.");
    }

    /**
     * delete R environment file
     * @param remoteFile filename to delete
     */
    public void removeFile(String remoteFile) {
        try {
            connection.removeFile(remoteFile);
        } catch (RserveException ex) {
            log(HEAD_EXCEPTION + ex.getMessage() + "\n  removeFile(String remoteFile=" + remoteFile + ")");
        }
    }

    /**
     * Send user filesystem file in r environement (like data)
     * @param localfile File to send
     */
    public void sendFile(File localfile) {
        sendFile(localfile, localfile.getName());
    }

    /**
     * Send user filesystem file in r environement (like data)
     * @param localfile File to send
     * @param remoteFile filename in R env.
     */
    public void sendFile(File localfile, String remoteFile) {
        if (!localfile.exists()) {
            log(HEAD_ERROR + IO_HEAD + connection.getLastError() + "\n  file " + localfile.getAbsolutePath() + " does not exists.");
        }
        try {
            if (silentlyEval("file.exists('" + remoteFile + "')").asInteger() == 1) {
                silentlyVoidEval("file.remove('" + remoteFile + "')");
                //connection.removeFile(remoteFile);
                log(IO_HEAD + "Remote file " + remoteFile + " deleted.");
            }
            /*} catch (RserveException ex) {
            log(HEAD_EXCEPTION + ex.getMessage() + "\n  putFile(File localfile=" + localfile.getAbsolutePath() + ", String remoteFile=" + remoteFile + ")");
             */        } catch (REXPMismatchException ex) {
            log(HEAD_ERROR + ex.getMessage() + "\n  putFile(File localfile=" + localfile.getAbsolutePath() + ", String remoteFile=" + remoteFile + ")");
            return;
        }
        FileInputStream is = null;
        RFileOutputStream os = null;

        try {
            os = connection.createFile(remoteFile);
            is = new FileInputStream(localfile);
            byte[] buf = new byte[512];
            try {
                connection.setSendBufferSize(buf.length);
            } catch (RserveException ex) {
                log(HEAD_EXCEPTION + ex.getMessage() + "\n  putFile(File localfile=" + localfile.getAbsolutePath() + ", String remoteFile=" + remoteFile + ")");
            }
            int n = 0;
            while ((n = is.read(buf)) > 0) {
                os.write(buf, 0, n);
            }
        } catch (IOException e) {
            log(HEAD_ERROR + IO_HEAD + connection.getLastError() + ": file " + remoteFile + " not writable.\n" + e.getMessage());
            return;
        } finally {
            try {
                if (os != null) {
                    os.close();
                }
                if (is != null) {
                    is.close();
                }
            } catch (IOException ee) {
                ee.printStackTrace();
            }
        }
        log(IO_HEAD + "File " + remoteFile + " sent.");
    }

    public static void main(String[] args) {
    }
}
