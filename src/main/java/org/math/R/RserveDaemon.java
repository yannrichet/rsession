package org.math.R;

import java.io.File;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Map;
import java.util.Properties;
import org.math.R.RLog.Level;
import org.rosuda.REngine.Rserve.RConnection;

/**
 *
 * @author richet
 */
public class RserveDaemon {

    RserverConf conf;
    Process process;
    private final RLog log;
    private static File R_APP_DIR = new File(System.getProperty("user.home") + File.separator + ".Rserve") {
        @Override
        public String toString() {
            if (RserveDaemon.isWindows()) {
                return super.toString().replace("\\", "/");
            } else {
                return super.toString();
            }
        }
    };

    static File app_dir() {
        boolean app_dir_ok = false;
        if (!R_APP_DIR.exists()) {
            app_dir_ok = R_APP_DIR.mkdir();
        } else {
            app_dir_ok = R_APP_DIR.isDirectory() && R_APP_DIR.canWrite();
        }
        if (!app_dir_ok) {
            Log.Err.println("Cannot write in " + R_APP_DIR.getAbsolutePath());
        }
        return R_APP_DIR;
    }

    public static String R_HOME = null;

    private static String OS = System.getProperty("os.name").toLowerCase();

    static boolean isWindows() {
        return (OS.indexOf("win") >= 0);
    }

    static boolean isMacOSX() {
        return (OS.indexOf("mac") >= 0);
    }

    static boolean isLinux() {
        return OS.indexOf("inux") >= 0;
    }

    public RserveDaemon(RserverConf conf, RLog log, String R_HOME) throws Exception {
        this.conf = conf;
        this.log = log != null ? log : new RLogSlf4j();
        if (!findR_HOME(R_HOME)) {
            this.log.log("Failed to find " + R_HOME_KEY + " (with default " + R_HOME + ") as " + RserveDaemon.R_HOME, Level.ERROR);
            throw new Exception("Failed to find " + R_HOME_KEY + " (with default " + R_HOME + ") as " + RserveDaemon.R_HOME);
        }
        this.log.log(R_HOME_KEY + "=" + RserveDaemon.R_HOME /*+ "\n  " + Rserve_HOME_KEY + "=" + RserveDaemon.Rserve_HOME*/, Level.INFO);
        Runtime.getRuntime().addShutdownHook(new Thread() {
            @Override
            public void run() {
                _stop();
            }
        });
    }

    private void _stop() {
        stop();
    }

    public RserveDaemon(RserverConf conf, RLog log) throws Exception {
        this(conf, log, null);
    }
    public final static String R_HOME_KEY = "R_HOME";

    public static boolean findR_HOME(String r_HOME) {
        Map<String, String> env = System.getenv();
        Properties prop = System.getProperties();

        if (r_HOME != null) {
            R_HOME = r_HOME;
        }
        if (R_HOME == null || !(new File(R_HOME).isDirectory())) {
            if (env.containsKey(R_HOME_KEY)) {
                R_HOME = env.get(R_HOME_KEY);
            }

            if (R_HOME == null || prop.containsKey(R_HOME_KEY) || !(new File(R_HOME).isDirectory())) {
                R_HOME = prop.getProperty(R_HOME_KEY);
            }

            if (R_HOME == null || !(new File(R_HOME).isDirectory())) {
                R_HOME = "R";
            }

            if (R_HOME == null || !(new File(R_HOME).isDirectory())) {
                R_HOME = null;
                if (isWindows()) {
                    for (int major = 20; major >= 0; major--) {
                        //int major = 10;//known to work with R 2.9 only.
                        for (int minor = 10; minor >= 0; minor--) {
                            //int minor = 0;
                            r_HOME = "C:\\Program Files\\R\\R-3." + major + "." + minor + "\\";
                            if (new File(r_HOME).isDirectory()) {
                                R_HOME = r_HOME;
                                break;
                            }
                        }
                    }
                } else if (isMacOSX()) {
                    R_HOME = "/Library/Frameworks/R.framework/Resources"; // standard R install
                    if (new File(R_HOME).isDirectory()) {
                        return true;
                    }

                    for (int major = 20; major >= 0; major--) { // for homebrew install
                        //int major = 10;//known to work with R 2.9 only.
                        for (int minor = 10; minor >= 0; minor--) {
                            //int minor = 0;
                            r_HOME = "/usr/local/Cellar/r/3." + major + "." + minor;
                            if (new File(r_HOME).isDirectory()) {
                                R_HOME = r_HOME;
                                break;
                            }
                        }
                    }
                } else {
                    R_HOME = "/usr/lib/R/";
                    if (new File(R_HOME).isDirectory()) {
                        return true;
                    }

                    R_HOME = "/usr/lcoal/lib/R/";
                    if (new File(R_HOME).isDirectory()) {
                        return true;
                    }
                }
            }
        }

        if (R_HOME == null) {
            return false;
        }

        return new File(R_HOME).isDirectory();
    }

    /*public static boolean findRserve_HOME(String path) {
    Map<String, String> env = System.getenv();
    Properties prop = System.getProperties();
    
    Rserve_HOME = path;
    if (Rserve_HOME == null || !(new File(Rserve_HOME).exists()) || !new File(Rserve_HOME).getName().equals("Rserve")) {
    if (env.containsKey(Rserve_HOME_KEY)) {
    Rserve_HOME = env.get(Rserve_HOME_KEY);
    }
    
    if (Rserve_HOME == null || prop.containsKey(Rserve_HOME_KEY) || !(new File(Rserve_HOME).exists()) || !new File(Rserve_HOME).getName().equals("Rserve")) {
    Rserve_HOME = prop.getProperty(Rserve_HOME_KEY);
    }
    
    if (Rserve_HOME == null || !(new File(Rserve_HOME).exists()) || !new File(Rserve_HOME).getName().equals("Rserve")) {
    Rserve_HOME = null;
    String OS_NAME = prop.getProperty("os.name");
    String OS_ARCH = prop.getProperty("os.arch");
    if (OS_ARCH.equals("amd64")) {
    OS_ARCH = "x86_64";
    }
    if (OS_ARCH.endsWith("86")) {
    OS_ARCH = "x86";
    }
    
    if (OS_NAME.contains("Windows")) {
    Rserve_HOME = "lib\\Windows\\" + OS_ARCH + "\\Rserve\\";
    } else if (OS_NAME.equals("Mac OS X")) {
    Rserve_HOME = "lib/MacOSX/" + OS_ARCH + "/Rserve";
    } else if (OS_NAME.equals("Linux")) {
    Rserve_HOME = "lib/Linux/" + OS_ARCH + "/Rserve";
    } else {
    RLog.err.println("OS " + OS_NAME + "/" + OS_ARCH + " not supported for automated RServe finding.");
    }
    
    if (!new File(Rserve_HOME).exists()) {
    RLog.err.println("Unable to find Rserve in " + Rserve_HOME);
    Rserve_HOME = null;
    } else {
    Rserve_HOME = new File(Rserve_HOME).getPath().replace("\\", "\\\\");
    }
    }
    }
    
    if (Rserve_HOME != null && new File(Rserve_HOME).exists()) {
    setRecursiveExecutable(new File(Rserve_HOME));
    return true;
    } else {
    return false;
    }
    }*/
    static void setRecursiveExecutable(File path) {
        for (File f : path.listFiles()) {
            if (f.isDirectory()) {
                f.setExecutable(true);
                setRecursiveExecutable(f);
            } else if (!f.canExecute() && (f.getName().endsWith(".so") || f.getName().endsWith(".dll"))) {
                f.setExecutable(true);
            }
        }

    }

    public void stop() {
        log.log("stopping R daemon... " + conf, Level.INFO);
        if (!conf.isLocal()) {
            throw new UnsupportedOperationException("Not authorized to stop a remote R daemon: " + conf.toString());
        }

        try {
            RConnection s = conf.connection;//connect();
            if (s == null || !s.isConnected()) {
                log.log("R daemon already stoped.", Level.INFO);
                return;
            }
            s.shutdown();
            if (rserve != null) {
                rserve.getInputStream().close();
                rserve.getErrorStream().close();
            }
        } catch (Exception ex) {
            log.log(ex.getMessage(), Level.ERROR);
        }

        log.log("R daemon stoped.", Level.INFO);
    }

    Process rserve;
    public static boolean USE_RSERVE_FROM_CRAN = false;

    public void start(String http_proxy) {
        if (R_HOME == null || !(new File(R_HOME).exists())) {
            throw new IllegalArgumentException("R_HOME environment variable not correctly set.\nYou can set it using 'java ... -D" + R_HOME_KEY + "=[Path to R] ...' startup command.");
        }

        if (!conf.isLocal()) {
            throw new UnsupportedOperationException("Unable to start a remote R daemon: " + conf.toString());
        }

        /*if (Rserve_HOME == null || !(new File(Rserve_HOME).exists())) {
        throw new IllegalArgumentException("Rserve_HOME environment variable not correctly set.\nYou can set it using 'java ... -D" + Rserve_HOME_KEY + "=[Path to Rserve] ...' startup command.");
        }*/
        log.log("checking Rserve is available... ", Level.INFO);
        boolean RserveInstalled = StartRserve.isRserveInstalled(R_HOME + File.separator + "bin" + File.separator + "R" + (isWindows() ? ".exe" : ""));
        if (!RserveInstalled) {
            log.log("                           ...no", Level.INFO);
            if (USE_RSERVE_FROM_CRAN) {
                RserveInstalled = StartRserve.installRserve(R_HOME + File.separator + "bin" + File.separator + "R" + (isWindows() ? ".exe" : ""), http_proxy, null);
            } else {
                RserveInstalled = StartRserve.installRserve(R_HOME + File.separator + "bin" + File.separator + "R" + (isWindows() ? ".exe" : ""));
            }
            if (RserveInstalled) {
                log.log("                           ...yes", Level.INFO);
            } else {
                log.log("                           ...failed", Level.ERROR);
                String notice = "Please install Rserve manually in your R environment using \"install.packages('Rserve')\" command.";
                log.log(notice, Level.ERROR);
                Log.Err.println(notice);
                return;
            }
        } else {
            log.log("                           ...yes", Level.INFO);
        }

        log.log("Starting R daemon... " + conf, Level.INFO);

        StringBuffer RserveArgs = new StringBuffer("--vanilla");
        if (conf.port > 0) {
            RserveArgs.append(" --RS-port " + conf.port);
        }

        rserve = StartRserve.launchRserve(R_HOME + File.separator + "bin" + File.separator + "R" + (isWindows() ? ".exe" : ""), /*Rserve_HOME + "\\\\..", */ "--vanilla", RserveArgs.toString(), false);

        if (rserve != null) {
            log.log("                 ...ok", Level.INFO);
        } else {
            log.log("                 ...failed", Level.ERROR);
        }
    }

    public static String timeDigest() {
        long time = System.currentTimeMillis();
        SimpleDateFormat sdf = new SimpleDateFormat("yyyyMMdd-HHmmss");
        StringBuffer sb = new StringBuffer();
        sb
                = sdf.format(new Date(time), sb, new java.text.FieldPosition(0));
        return sb.toString();
    }

    public static void main(String[] args) throws Exception {
        RserveDaemon d = new RserveDaemon(new RserverConf(null, -1, null, null, null), new RLogSlf4j());
        d.start(null);
        Thread.sleep(2000);
        d.stop();
        Thread.sleep(2000);
    }
}
