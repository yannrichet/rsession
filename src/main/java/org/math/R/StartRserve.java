package org.math.R;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileOutputStream;
import java.io.FilenameFilter;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.text.SimpleDateFormat;
import java.util.Calendar;
import org.rosuda.REngine.Rserve.RConnection;

/**
 * helper class that consumes output of a process. In addition, it filter output
 * of the REG command on Windows to look for InstallPath registry entry which
 * specifies the location of R.
 */
class RegistryHog extends Thread {

    InputStream is;
    boolean capture;
    String installPath;

    RegistryHog(InputStream is, boolean capture) {
        this.is = is;
        this.capture = capture;
        start();
    }

    public String getInstallPath() {
        return installPath;
    }

    @Override
    public void run() {
        BufferedReader br = null;
        try {
            br = new BufferedReader(new InputStreamReader(is));
            String line = null;
            while ((line = br.readLine()) != null) {
                if (capture) { // we are supposed to capture the output from REG command

                    int i = line.indexOf("InstallPath");
                    if (i >= 0) {
                        String s = line.substring(i + 11).trim();
                        int j = s.indexOf("REG_SZ");
                        if (j >= 0) {
                            s = s.substring(j + 6).trim();
                        }
                        installPath = s;
                    }
                }
            }
        } catch (IOException e) {
            e.printStackTrace();
        } finally {
            if (br != null) {
                try {
                    br.close();
                } catch (IOException ex) {
                    ex.printStackTrace();
                }
            }
        }
    }
}

class StreamHog extends Thread {

    InputStream is;
    boolean capture;
    StringBuffer out = new StringBuffer();

    StreamHog(InputStream is, boolean capture) {
        this.is = is;
        this.capture = capture;
        start();
    }

    public String getOutput() {
        return out.toString();
    }

    @Override
    public void run() {
        //Logger.err.println("start streamhog");
        BufferedReader br = null;
        InputStreamReader isr = null;
        try {
            isr = new InputStreamReader(is);
            br = new BufferedReader(isr);
            String line = null;
            while ((line = br.readLine()) != null) {
                if (capture) {
                    out.append("\n").append(line);
                } else {
                }
            }
        } catch (IOException e) {
            e.printStackTrace();
        } finally {
            if (br != null) {
                try {
                    br.close();
                } catch (IOException ex) {
                    ex.printStackTrace();
                }
            }
            if (isr != null) {
                try {
                    isr.close();
                } catch (IOException ex) {
                    ex.printStackTrace();
                }
            }
        }
        //Logger.err.println("finished streamhog");
    }
}

/**
 * simple class that start Rserve locally if it's not running already - see
 * mainly <code>checkLocalRserve</code> method. It spits out quite some
 * debugging outout of the console, so feel free to modify it for your
 * application if desired.<p>
 * <i>Important:</i> All applications should shutdown every Rserve that they
 * started! Never leave Rserve running if you started it after your application
 * quits since it may pose a security risk. Inform the user if you started an
 * Rserve instance.
 */
public class StartRserve {

    /**
     * R batch to check Rserve is installed
     *
     * @param Rcmd command necessary to start R
     * @return Rserve is already installed
     */
    public static boolean isRserveInstalled(String Rcmd) {
        Process p = doInR("is.element(set=installed.packages(lib.loc='" + RserveDaemon.R_APP_DIR + "'),el='Rserve')", Rcmd, "--vanilla --silent", false);
        if (p == null) {
            Log.Err.println("Failed to ask if Rserve is installed");
            return false;
        }

        try {
            StringBuffer result = new StringBuffer();
            // we need to fetch the output - some platforms will die if you don't ...
            StreamHog error = new StreamHog(p.getErrorStream(), true);
            StreamHog output = new StreamHog(p.getInputStream(), true);
            error.join();
            output.join();

            if (!RserveDaemon.isWindows()) /* on Windows the process will never return, so we cannot wait */ {
                p.waitFor();
            } else {
                Thread.sleep(2000);
            }
            result.append(output.getOutput());
            result.append(error.getOutput());

            //Logger.err.println("output=\n===========\n" + result.toString() + "\n===========\n");
            if (result.toString().contains("[1] TRUE")) {
                Log.Out.println("Rserve is already installed.");
                return true;
            } else if (result.toString().contains("[1] FALSE")) {
                Log.Out.println("Rserve is not yet installed.");
                return false;
            } else {
                Log.Err.println("Cannot check if Rserve is installed: " + result.toString());
                return false;
            }
        } catch (InterruptedException e) {
            return false;
        }
    }

    /**
     * R batch to install Rserve
     *
     * @param Rcmd command necessary to start R
     * @param http_proxy http://login:password@proxy:port string to enable
     * internet access to rforge server
     * @param repository from which R repo ?
     * @return success
     */
    public static boolean installRserve(String Rcmd, String http_proxy, String repository) {
        if (repository == null || repository.length() == 0) {
            repository = Rsession.DEFAULT_REPOS;
        }
        if (http_proxy == null) {
            http_proxy = "";
        }
        Log.Out.println("Install Rserve from " + repository + " ... (http_proxy='" + http_proxy + "') ");
        Process p = doInR((http_proxy != null ? "Sys.setenv(http_proxy='" + http_proxy + "');" : "") + "install.packages('Rserve',repos='" + repository + "',lib='" + RserveDaemon.R_APP_DIR + "')", Rcmd, "--vanilla --silent", false);
        if (p == null) {
            Log.Err.println("Failed to launch Rserve install");
            return false;
        }
        try {
            StringBuffer result = new StringBuffer();
            // we need to fetch the output - some platforms will die if you don't ...
            StreamHog error = new StreamHog(p.getErrorStream(), true);
            StreamHog output = new StreamHog(p.getInputStream(), true);
            error.join();
            output.join();

            if (!RserveDaemon.isWindows()) /* on Windows the process will never return, so we cannot wait */ {
                p.waitFor();
            } else {
                Thread.sleep(2000);
            }
            result.append(output.getOutput());
            result.append(error.getOutput());

            //Logger.err.println("output=\n===========\n" + result.toString() + "\n===========\n");
            if (result.toString().contains("package 'Rserve' successfully unpacked and MD5 sums checked") || result.toString().contains("* DONE (Rserve)")) {
                Log.Out.println("Rserve install succeded.");
                //return true;
            } else if (result.toString().contains("FAILED") || result.toString().contains("Error")) {
                Log.Out.println("Rserve install failed: " + result.toString().replace("\n", "\n| "));
                return false;
            } else {
                Log.Err.println("Rserve install unknown: " + result.toString().replace("\n", "\n| "));
                return false;
            }
        } catch (InterruptedException e) {
            return false;
        }
        int n = 5;
        while (n > 0) {
            try {
                Thread.sleep(2000);
                Log.Out.print(".");
            } catch (InterruptedException ex) {
            }
            if (isRserveInstalled(Rcmd)) {
                Log.Out.print("Rserve is installed");
                return true;
            }
            n--;
        }
        Log.Out.print("Rserve is not installed");
        return false;
    }

    /**
     * R batch to install Rserve
     *
     * @param Rcmd command necessary to start R
     * @return success
     */
    public static boolean installRserve(String Rcmd) {
        Log.Out.println("Install Rserve from local filesystem");

        String pack_suffix = ".tar.gz";
        if (RserveDaemon.isWindows()) {
            pack_suffix = ".zip";
        } else {
            if (RserveDaemon.isMacOSX()) {
                pack_suffix = ".tgz";
            }
        }
        File packFile;
        try {
            packFile = File.createTempFile("Rserve_1.7-3", pack_suffix);
            packFile.deleteOnExit();
        } catch (IOException ex) {
            Log.Err.println(ex.getMessage());
            return false;
        }
        try {
            ClassLoader classloader = Thread.currentThread().getContextClassLoader();
            InputStream fileStream = classloader.getResourceAsStream("org/math/R/Rserve_1.7-3" + pack_suffix);

            if (fileStream == null) {
                Log.Err.println("Cannot find resource " + "org/math/R/Rserve_1.7-3" + pack_suffix);
                return false;
            }

            // Create an output stream to barf to the temp file
            OutputStream out = new FileOutputStream(packFile);

            // Write the file to the temp file
            byte[] buffer = new byte[1024];
            int len = fileStream.read(buffer);
            while (len != -1) {
                out.write(buffer, 0, len);
                len = fileStream.read(buffer);
            }

            // Close the streams
            fileStream.close();
            out.close();
        } catch (Exception e) {
            Log.Err.println(e.getMessage());
            return false;
        }

        if (!packFile.isFile()) {
            Log.Err.println("Could not create file " + packFile);
            return false;
        }

        Process p = doInR("install.packages('" + packFile.getAbsolutePath().replace("\\", "/") + "',repos=NULL,lib='" + RserveDaemon.R_APP_DIR + "')", Rcmd, "--vanilla --silent", false);
        if (p == null) {
            Log.Err.println("Failed to launch Rserve install");
            return false;
        }
        try {
            StringBuffer result = new StringBuffer();
            // we need to fetch the output - some platforms will die if you don't ...
            StreamHog error = new StreamHog(p.getErrorStream(), true);
            StreamHog output = new StreamHog(p.getInputStream(), true);
            error.join();
            output.join();

            if (!RserveDaemon.isWindows()) /* on Windows the process will never return, so we cannot wait */ {
                p.waitFor();
            } else {
                Thread.sleep(2000);
            }
            result.append(output.getOutput());
            result.append(error.getOutput());

            //Logger.err.println("output=\n===========\n" + result.toString() + "\n===========\n");
            if (result.toString().contains("package 'Rserve' successfully unpacked and MD5 sums checked") || result.toString().contains("* DONE (Rserve)")) {
                Log.Out.println("Rserve install succeded.");
                //return true;
            } else if (result.toString().contains("FAILED") || result.toString().contains("Error")) {
                Log.Out.println("Rserve install failed: " + result.toString().replace("\n", "\n| "));
                return false;
            } else {
                Log.Err.println("Rserve install unknown: " + result.toString().replace("\n", "\n| "));
                return false;
            }
        } catch (InterruptedException e) {
            return false;
        }

        int n = 5;
        while (n > 0) {
            try {
                Thread.sleep(2000);
            } catch (InterruptedException ex) {
            }
            if (isRserveInstalled(Rcmd)) {
                Log.Out.print("Rserve is installed");
                return true;
            }
            n--;
        }
        Log.Out.print("Rserve is not installed");
        return false;
    }

    /**
     * attempt to start Rserve. Note: parameters are <b>not</b> quoted, so avoid
     * using any quotes in arguments
     *
     * @param todo command to execute in R
     * @param Rcmd command necessary to start R
     * @param rargs arguments are are to be passed to R (e.g. --vanilla -q)
     * @param redirect should we redirect output to a file ?
     * @return <code>true</code> if Rserve is running or was successfully
     * started, <code>false</code> otherwise.
     */
    public static Process doInR(String todo, String Rcmd, String rargs/*, StringBuffer out, StringBuffer err*/, boolean redirect) {
        Process p = null;
        try {
            String Rout = new SimpleDateFormat("yyyy-MM-dd_HH-mm-ss").format(Calendar.getInstance().getTime()) + ".Rout";
            String command = Rcmd + " " + rargs + " -e \"" + todo + "\" " + (redirect ? " > " + Rout + (!RserveDaemon.isWindows() ? " 2>&1" : "") : "");
            Log.Out.println("Doing (in R): " + command);
            if (RserveDaemon.isWindows()) {
                p = Runtime.getRuntime().exec(command);
            } else /* unix startup */ {
                p = Runtime.getRuntime().exec(new String[]{"/bin/sh", "-c", command});
                //new File(Rout).deleteOnExit();
            }
        } catch (Exception x) {
            Log.Err.println(x.getMessage());
        }
        return p;
    }

    /**
     * shortcut to <code>launchRserve(cmd, "--vanilla", "", false)</code>
     *
     * @param cmd Rserve command
     * @return launcher Process
     */
    public static Process launchRserve(String cmd) {
        return launchRserve(cmd, "--vanilla", "--vanilla", false);
    }

    static String UGLY_FIXES = "flush.console <- function(...) {return;}; options(error=function() NULL)";

    /**
     * attempt to start Rserve. Note: parameters are <b>not</b> quoted, so avoid
     * using any quotes in arguments
     *
     * @param cmd command necessary to start R
     * @param rargs arguments are are to be passed to R
     * @param rsrvargs arguments to be passed to Rserve
     * @param debug Rserve debug mode ?
     * @return <code>true</code> if Rserve is running or was successfully
     * started, <code>false</code> otherwise.
     */
    public static Process launchRserve(String cmd, /*String libloc,*/ String rargs, String rsrvargs, boolean debug) {
        Log.Out.println("Waiting for Rserve to start ... (" + cmd + " " + rargs + ")");
        Process p = doInR("library(Rserve,lib.loc='" + RserveDaemon.R_APP_DIR + "');Rserve(" + (debug ? "TRUE" : "FALSE") + ",args='" + rsrvargs + "');" + UGLY_FIXES, cmd, rargs, true);
        if (p != null) {
            Log.Out.println("Rserve startup done, let us try to connect ...");
        } else {
            Log.Err.println("Failed to start Rserve process.");
            return null;
        }

        int attempts = 30;
        /* try up to 15 times before giving up. We can be conservative here, because at this point the process execution itself was successful and the start up is usually asynchronous */

        while (attempts > 0) {
            try {
                RConnection c = null;
                int port = -1;
                if (rsrvargs.contains("--RS-port")) {
                    String rsport = rsrvargs.split("--RS-port")[1].trim().split(" ")[0];
                    port = Integer.parseInt(rsport);
                    c = new RConnection("localhost", port);
                } else {
                    c = new RConnection("localhost");
                }
                Log.Out.println("Rserve is running.");
                c.close();
                return p;
            } catch (Exception e2) {
                Log.Err.println("Try failed with: " + e2.getMessage());
            }
            /* a safety sleep just in case the start up is delayed or asynchronous */
            try {
                Thread.sleep(1000);
            } catch (InterruptedException ix) {
            }

            attempts--;
        }
        return null;
    }

    /**
     * checks whether Rserve is running and if that's not the case it attempts
     * to start it using the defaults for the platform where it is run on. This
     * method is meant to be set-and-forget and cover most default setups. For
     * special setups you may get more control over R with
     * <code>launchRserve</code> instead.
     *
     * @return is ok ?
     */
    public static boolean checkLocalRserve() {
        if (isRserveRunning()) {
            return true;
        }
        if (RserveDaemon.isWindows()) {
            Log.Out.println("Windows: query registry to find where R is installed ...");
            String installPath = null;
            try {
                Process rp = Runtime.getRuntime().exec("reg query HKLM\\Software\\R-core\\R");
                RegistryHog regHog = new RegistryHog(rp.getInputStream(), true);
                rp.waitFor();
                regHog.join();
                installPath = regHog.getInstallPath();
            } catch (Exception rge) {
                Log.Err.println("ERROR: unable to run REG to find the location of R: " + rge);
                return false;
            }
            if (installPath == null) {
                Log.Err.println("ERROR: cannot find path to R. Make sure reg is available and R was installed with registry settings.");
                return false;
            }
            return launchRserve(installPath + "\\bin\\R.exe") != null;
        } else if (RserveDaemon.isMacOSX()) {
            FilenameFilter ff = new FilenameFilter() {
                @Override
                public boolean accept(File dir, String name) {
                    return name.startsWith("3.");
                }
            };
            return ((launchRserve("R") != null)
                    || (new File("/usr/local/Cellar/r/").exists() && new File("/usr/local/Cellar/r/").list(ff).length > 0 && launchRserve(new File("/usr/local/Cellar/r/").list(ff)[0]) != null)
                    || (new File("/Library/Frameworks/R.framework/Resources/bin/R").exists() && launchRserve("/Library/Frameworks/R.framework/Resources/bin/R") != null)
                    || (new File("/usr/lib/R/bin/R").exists() && launchRserve("/usr/lib/R/bin/R") != null)
                    || (new File("/usr/local/lib/R/bin/R").exists() && launchRserve("/usr/local/lib/R/bin/R") != null));
        } else {
            return ((launchRserve("R") != null)
                    || (new File("/usr/local/lib/R/bin/R").exists() && launchRserve("/usr/local/lib/R/bin/R") != null)
                    || (new File("/usr/lib/R/bin/R").exists() && launchRserve("/usr/lib/R/bin/R") != null)
                    || (new File("/usr/local/bin/R").exists() && launchRserve("/usr/local/bin/R") != null)
                    || (new File("/sw/bin/R").exists() && launchRserve("/sw/bin/R") != null)
                    || (new File("/usr/common/bin/R").exists() && launchRserve("/usr/common/bin/R") != null)
                    || (new File("/opt/bin/R").exists() && launchRserve("/opt/bin/R") != null));
        }
    }
    
    /**
     * check whether Rserve is currently running (on local machine and default
     * port).
     *
     * @return <code>true</code> if local Rserve instance is running,
     * <code>false</code> otherwise
     */
    public static boolean isRserveRunning() {
        try {
            RConnection c = new RConnection();
            Log.Out.println("Rserve is running.");
            c.close();
            return true;
        } catch (Exception e) {
            Log.Err.println("First connect try failed with: " + e.getMessage());
        }
        return false;
    }

    /**
     * just a demo main method which starts Rserve and shuts it down again
     *
     * @param args ...
     */
    public static void main(String[] args) {
        File dir = null;

        System.out.println("checkLocalRserve: " + checkLocalRserve());
        try {
            RConnection c = new RConnection();
            //c.eval("cat('123')");
            dir = new File(c.eval("getwd()").asString());
            System.err.println("wd: " + dir);
            //c.eval("flush.console <-function(...) return;"); // will crash without that...
            c.eval("download.file('https://www.r-project.org/',paste0(getwd(),'/log.txt'))");
            c.shutdown();
        } catch (Exception x) {
            x.printStackTrace();
        }

        if (new File(dir, "log.txt").exists()) {
            System.err.println("OK: file exists");
            if (new File(dir, "log.txt").length() > 10) {
                System.err.println("OK: file not empty");
            } else {
                System.err.println("NO: file EMPTY");
            }
        } else {
            System.err.println("NO: file DOES NOT exist");
        }
    }
}
