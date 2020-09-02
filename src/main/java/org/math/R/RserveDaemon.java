package org.math.R;

import java.io.File;
import java.io.IOException;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Map;
import java.util.Properties;
import java.util.logging.Logger;
import org.math.R.RLog.Level;
import org.rosuda.REngine.Rserve.RConnection;
import org.rosuda.REngine.Rserve.RserveException;

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
                log.log("Shutdown hook: stop Rserve daemon", Level.INFO);
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
                    try {
                        Process rp = Runtime.getRuntime().exec("reg query HKLM\\Software\\R-core\\R");
                        RegistryHog regHog = new RegistryHog(rp.getInputStream(), true);
                        rp.waitFor();
                        regHog.join();
                        R_HOME = regHog.getInstallPath();
                    } catch (Exception rge) {
                        for (int version = 4; version >= 0; version--) {
                            for (int major = 20; major >= 0; major--) {
                                //int major = 10;//known to work with R 2.9 only.
                                for (int minor = 10; minor >= 0; minor--) {
                                    //int minor = 0;
                                    r_HOME = "C:\\Program Files\\R\\R-" + version + "." + major + "." + minor + "\\";
                                    if (new File(r_HOME).isDirectory()) {
                                        R_HOME = r_HOME;
                                        break;
                                    }
                                }
                            }
                        }
                    }
                } else if (isMacOSX()) {
                    String[] paths = {"/Library/Frameworks/R.framework/Resources/", "/usr/lib/R", "/usr/local/lib/R"};
                    for (String r_home : paths) {
                        R_HOME = r_home; // standard R install
                        if (new File(R_HOME).isDirectory()) {
                            return true;
                        }
                    }

                    for (int version = 4; version >= 0; version--) {
                        for (int major = 20; major >= 0; major--) { // for homebrew install
                            //int major = 10;//known to work with R 2.9 only.
                            for (int minor = 10; minor >= 0; minor--) {
                                //int minor = 0;
                                r_HOME = "/usr/local/Cellar/r/" + version + "." + major + "." + minor;
                                if (new File(r_HOME + "_3").isDirectory()) {
                                    R_HOME = r_HOME + "_3";
                                    break;
                                } else if (new File(r_HOME + "_2").isDirectory()) {
                                    R_HOME = r_HOME + "_2";
                                    break;
                                } else if (new File(r_HOME + "_1").isDirectory()) {
                                    R_HOME = r_HOME + "_1";
                                    break;
                                } else if (new File(r_HOME).isDirectory()) {
                                    R_HOME = r_HOME;
                                    break;
                                }
                            }
                        }
                    }
                } else {
                    String[] paths = {"/usr/lib/R", "/usr/local/lib/R/", "/usr/lib64/R"};
                    for (String r_home : paths) {
                        R_HOME = r_home; // standard R install
                        if (new File(R_HOME).isDirectory()) {
                            return true;
                        }
                    }
                }
            }
        }

        if (R_HOME == null) {
            return false;
        }

        return new File(R_HOME).isDirectory();
    }

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

    boolean stopped = false;

    public void stop() {
        if (stopped) {
            log.log("R daemon " + conf + " already stopped.", Level.INFO);
            return;
        }
        log.log("stopping R daemon... " + conf, Level.INFO);
        if (!conf.isLocal()) {
            throw new UnsupportedOperationException("Not authorized to stop a remote R daemon: " + conf.toString());
        }

        try {
            RConnection s = conf.connection;//connect();
            if (s == null || !s.isConnected()) {
                log.log("R daemon connection already closed.", Level.INFO);
                s = conf.connect();
            }
            if (s != null && s.isConnected()) {
                try {
                    s.serverShutdown();
                } catch (RserveException ex) {
                    log.log("Could not shutdown server", Level.WARNING);
                }
                s.shutdown();
                if (rserve.process != null && rserve.process.isAlive()) {
                    rserve.process.destroyForcibly();
                    rserve.process.getInputStream().close();
                    rserve.process.getErrorStream().close();
                }
            } else {
                log.log("Could not connect Rserve to shutdown", Level.WARNING);
            }
        } catch (Exception ex) {
            log.log("Failed to connect Rserve to shutdown", Level.ERROR);
        }

        try {
            if (rserve.pid > 0) {// avoid if pid was not well detected (so is <0)
                int[] pids = StartRserve.getRservePIDs();
                boolean in = false;
                for (int i = 0; i < pids.length; i++) {
                    if (pids[i] == rserve.pid) {
                        in = true;
                        break;
                    }
                }
                if (in) {
                    log.log("Will kill PID: " + rserve.pid, Level.INFO);
                    if (isWindows()) {
                        Process k = Runtime.getRuntime().exec("taskkill /F /T /PID " + rserve.pid);
                        log.log("Killed server: " + k.waitFor(), Level.INFO);
                    } else {
                        Process k = Runtime.getRuntime().exec("kill -9 " + rserve.pid);
                        log.log("Killed server: " + k.waitFor(), Level.INFO);
                    }
                } else {
                    log.log("Rserve PID not alive.", Level.WARNING);
                }
            } else {
                log.log("No Rserve PID to kill.", Level.WARNING);
            }
        } catch (Exception ex) {
            log.log("Could not kill Rserve process: " + ex.getMessage(), Level.WARNING);
        }

        stopped = true;
        log.log("R daemon stopped.", Level.INFO);
    }

    public StartRserve.ProcessToKill rserve;
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

        StringBuffer RserveArgs = new StringBuffer("--vanilla --RS-enable-control");
        if (conf.port > 0) {
            RserveArgs.append(" --RS-port " + conf.port);
        }

        rserve = StartRserve.launchRserve(R_HOME + File.separator + "bin" + File.separator + "R" + (isWindows() ? ".exe" : ""),
                "--vanilla",
                RserveArgs.toString(), false);

        if (rserve != null) {
            log.log("  R daemon started.", Level.INFO);
        } else {
            log.log("  R daemon startup failed!", Level.ERROR);
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
        RserveDaemon d = new RserveDaemon(RserverConf.newLocalInstance(null), new RLog() {
            @Override
            public void closeLog() {
            }

            @Override
            public void log(String message, Level l) {
                System.out.println(l + ": " + message);
            }
        });
        d.start(null);

        // Already in RserveDaemon constructor:
        /*Runtime.getRuntime().addShutdownHook(new Thread(new Runnable() {
            @Override
            public void run() {
                d.stop();
            }
        }));*/
    }
}
