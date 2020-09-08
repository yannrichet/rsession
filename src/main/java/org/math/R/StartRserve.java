package org.math.R;

import java.io.BufferedReader;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.FilenameFilter;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.BindException;
import java.net.ServerSocket;
import java.net.Socket;
import java.text.SimpleDateFormat;
import java.util.Arrays;
import java.util.Calendar;
import java.util.LinkedList;
import java.util.List;
import java.util.StringTokenizer;
import java.util.logging.Level;
import java.util.logging.Logger;
import javax.imageio.IIOException;
import org.codehaus.plexus.util.FileUtils;
import static org.math.R.RserveDaemon.isWindows;
import org.math.R.RserverConf.TimeOut;
import org.math.R.RserverConf.TimeOut.TimeOutException;
import org.rosuda.REngine.REXPMismatchException;
import org.rosuda.REngine.Rserve.RConnection;
import org.rosuda.REngine.Rserve.RserveException;

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
    public static boolean isRserveInstalled(String Rcmd) throws IOException, InterruptedException {
        // shortcut & try avoid filesystem sync issues on windows
        File dir = new File(RserveDaemon.app_dir(), "Rserve");
        if (dir.isDirectory()) {
            File desc = new File(dir, "DESCRIPTION");
            if (desc.isFile() && org.apache.commons.io.FileUtils.readFileToString(desc).contains("1.7-5")) {
                return true;
            } else {
                Log.Err.println("Seems Rserve is not well installed. Force remove!");
                if (RserveDaemon.isWindows()) {
                    Log.Err.println("  OS:Windows, so try to kill Rserve.exe before:");
                    KillAll("Rserve.exe");
                    KillAll("Rserve_d.exe");
                }
                int n = 10;
                while ((n--) > 0 && dir.isDirectory()) {
                    FileUtils.forceDelete(dir);
                }
                if (dir.isDirectory()) {
                    throw new IOException("Could not cleanup " + dir.getAbsolutePath() + " directory");
                }
                return false;
            }
        } else {
            return false;
        }

        /*if (!new File(RserveDaemon.app_dir(), "Rserve").isDirectory()) {
            return false;
        }

        Log.Out.println("Check Rserve is installed in " + RserveDaemon.app_dir() + " ");
        File out = File.createTempFile("isRserveInstalled", "Rout");
        Process p = doInR("is.element(set=installed.packages(lib.loc='" + RserveDaemon.app_dir() + "'),el='Rserve')", Rcmd, "--vanilla --silent", out);
        if (p == null) {
            throw new IOException("Failed to ask if Rserve is installed");
        }

        if (!RserveDaemon.isWindows()) {// on Windows the process will never return, so we cannot wait
            p.waitFor();
        }

        String result = org.apache.commons.io.FileUtils.readFileToString(out);

        int attempts = 10;
        while (attempts > 0) {
            try {
                Thread.sleep(1000);
                Log.Out.print(".");
            } catch (InterruptedException ex) {
            }
            if (result.contains("[1] TRUE")) {
                Log.Out.println(" true.");
                return true;
            } else if (result.contains("[1] FALSE")) {
                Log.Out.println(" false.");
                return false;
            }
            attempts--;
        }
        throw new IOException("Cannot check if Rserve is installed: " + result.replaceAll("\n", "\n  | "));*/
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
    // If posisble, do not use this legacy Rserve (use patched version github.com/yannrichet/Rserve-1.7)
    public static boolean installRserve(String Rcmd, String http_proxy, String repository) throws IOException, InterruptedException {
        if (repository == null || repository.length() == 0) {
            repository = Rsession.DEFAULT_REPOS;
        }
        if (http_proxy == null) {
            http_proxy = "";
        }
        Log.Out.println("Install Rserve from " + repository + " (http_proxy='" + http_proxy + "') ");
        File out = File.createTempFile("installRserve", "Rout");
        Process p = doInR((http_proxy != null ? "Sys.setenv(http_proxy='" + http_proxy + "');" : "") + "install.packages('Rserve',repos='" + repository + "',lib='" + RserveDaemon.app_dir() + "')", Rcmd, "--vanilla --silent", out);
        if (p == null) {
            throw new IOException("Failed to install Rserve");
        }

        int attempts = 10;
        while (attempts > 0) {
            try {
                Thread.sleep(1000);
                Log.Out.print(".");
            } catch (InterruptedException ex) {
            }

            String result = org.apache.commons.io.FileUtils.readFileToString(out);

            if (result.contains("package 'Rserve' successfully unpacked and MD5 sums checked") || result.contains("* DONE (Rserve)")) {
                Log.Out.print(" true ");
                break;
            } else if (result.contains("FAILED") || result.contains("Error")) {
                Log.Out.println(" false.\nRserve install failed: " + result.replaceAll("\n", "\n  | "));
                return false;
            }
            attempts--;
        }
        // If non english setup, it should be ignored... So just use isRserveInstalled instead
        //if (attempts <= 0) {
        //    throw new IOException("Rserve install unknown: " + org.apache.commons.io.FileUtils.readFileToString(out).replaceAll("\n", "\n  | "));
        //}

        int n = 10;
        while (n > 0) {
            try {
                Thread.sleep(1000);
                Log.Out.print(".");
            } catch (InterruptedException ex) {
            }
            if (isRserveInstalled(Rcmd)) {
                Log.Out.println(" well installed.");
                return true;
            }
            n--;
        }

        Log.Out.print(" but not well installed !");
        return false;
    }

    /**
     * R batch to install Rserve
     *
     * @param Rcmd command necessary to start R
     * @return success
     */
    public static boolean installCustomRserve(String Rcmd) throws InterruptedException, IOException {
        if (isRserveInstalled(Rcmd)) {
            Log.Out.println("Rserve already installed (in " + RserveDaemon.app_dir().getAbsolutePath() + ")");
            return true;
        }

        Log.Out.println("Install Rserve from local filesystem... (in " + RserveDaemon.app_dir().getAbsolutePath() + ")");

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
            packFile = File.createTempFile("Rserve_1.7-5", pack_suffix);
            packFile.deleteOnExit();
        } catch (IOException ex) {
            Log.Err.println(ex.getMessage());
            return false;
        }
        try {
            ClassLoader classloader = Thread.currentThread().getContextClassLoader();
            InputStream fileStream = classloader.getResourceAsStream("org/math/R/Rserve_1.7-5" + pack_suffix);

            if (fileStream == null) {
                throw new IOException("Cannot find resource " + "org/math/R/Rserve_1.7-5" + pack_suffix);
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
            throw new IOException("Could not create file " + packFile);
        }

        File out = File.createTempFile("installRserve", "Rout");
        Process p = doInR("install.packages('" + packFile.getAbsolutePath().replace("\\", "/") + "',repos=NULL,lib='" + RserveDaemon.app_dir() + "')", Rcmd, "--vanilla --silent", out);
        if (p == null) {
            throw new IOException("Failed to install Rserve");
        }

        int attempts = 10;
        while (attempts > 0) {
            try {
                Thread.sleep(1000);
                Log.Out.print(".");
            } catch (InterruptedException ex) {
            }

            String result = org.apache.commons.io.FileUtils.readFileToString(out);

            if (result.contains("package 'Rserve' successfully unpacked and MD5 sums checked") || result.contains("* DONE (Rserve)")) {
                Log.Out.print(" true ");
                break; //return true;
            } else if (result.contains("FAILED") || result.contains("Error")) {
                Log.Out.println(" false.\nRserve install failed: " + result.replaceAll("\n", "\n  | "));
                return false;
            }
            attempts--;
        }
        // If non english setup, it should be ignored... So just use isRserveInstalled instead
        //if (attempts <= 0) {
        //    throw new IOException("Rserve install unknown: " + org.apache.commons.io.FileUtils.readFileToString(out).replaceAll("\n", "\n  | "));
        //}

        int n = 10;
        while (n > 0) {
            try {
                Thread.sleep(1000);
                Log.Out.print(".");
            } catch (InterruptedException ex) {
            }
            if (isRserveInstalled(Rcmd)) {
                Log.Out.println(" well installed.");
                return true;
            }
            n--;
        }

        Log.Out.print(" but not well installed !");
        return false;
    }

    static String[] splitCommand(String command) {
        StringTokenizer st = new StringTokenizer(command);
        String[] cmdarray = new String[st.countTokens()];
        for (int i = 0; st.hasMoreTokens(); i++) {
            cmdarray[i] = st.nextToken();
        }
        return cmdarray;
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
    public static Process doInR(String todo, String Rcmd, String rargs, File redirect) {
        Process p = null;
        try {
            String command = Rcmd + " " + rargs + " -e \"" + todo + "\" " + (redirect == null ? "" : " > " + redirect.getAbsolutePath() + (!RserveDaemon.isWindows() ? " 2>&1" : ""));
            Log.Out.println("  R> " + todo);
            if (RserveDaemon.isWindows()) {
                ProcessBuilder pb = new ProcessBuilder(splitCommand(command));
                if (redirect == null) {
                    pb.redirectErrorStream(true);
                }
                p = pb.start();
                if (redirect == null) {
                    BufferedReader reader = new BufferedReader(new InputStreamReader(p.getInputStream()));
                    String line;
                    while ((line = reader.readLine()) != null) {
                        Log.Out.println("  " + line);
                    }
                }
                //p.waitFor(); 
            } else /* unix startup */ {
                ProcessBuilder pb = new ProcessBuilder(new String[]{"/bin/sh", "-c", command});
                if (redirect == null) {
                    pb.redirectErrorStream(true);
                }
                p = pb.start();
                if (redirect == null) {
                    BufferedReader reader = new BufferedReader(new InputStreamReader(p.getInputStream()));
                    String line;
                    while ((line = reader.readLine()) != null) {
                        Log.Out.println("  " + line);
                    }
                }
                p.waitFor();
                //new File(Rout).deleteOnExit();
            }
        } catch (Exception x) {
            Log.Err.println(x.getMessage());
        }
        return p;
    }

    static String UGLY_FIXES = "";//flush.console <- function(...) {return;}; options(error=function() NULL)";

    public static boolean KillAll(String taskname) {
        try {
            Log.Out.print("Kill process " + taskname + ": ");
            if (isWindows()) {
                ProcessBuilder pb = new ProcessBuilder(splitCommand("taskkill /F /IM " + taskname));
                pb.redirectErrorStream(true);
                Process k = pb.start();
                BufferedReader reader = new BufferedReader(new InputStreamReader(k.getInputStream()));
                String line;
                while ((line = reader.readLine()) != null) {
                    Log.Out.println("  " + line);
                }
                return true;//k.waitFor() == 0;
            } else {
                ProcessBuilder pb = new ProcessBuilder(splitCommand("killall " + taskname));
                pb.redirectErrorStream(true);
                Process k = pb.start();
                BufferedReader reader = new BufferedReader(new InputStreamReader(k.getInputStream()));
                String line;
                while ((line = reader.readLine()) != null) {
                    Log.Out.println("  " + line);
                }
                return k.waitFor() == 0;
            }
        } catch (Exception ex) {
            Log.Err.println("Exception: " + ex.getMessage());
            return false;
        }
    }

    public static boolean Kill(int pid) {
        try {
            Log.Out.print("Kill PID " + pid + ": ");
            if (isWindows()) {
                ProcessBuilder pb = new ProcessBuilder(splitCommand("taskkill /F /T /PID " + pid));
                pb.redirectErrorStream(true);
                Process k = pb.start();
                BufferedReader reader = new BufferedReader(new InputStreamReader(k.getInputStream()));
                String line;
                while ((line = reader.readLine()) != null) {
                    Log.Out.println("  " + line);
                }
                return true;//k.waitFor() == 0;
            } else {
                ProcessBuilder pb = new ProcessBuilder(splitCommand("kill -9 " + pid));
                pb.redirectErrorStream(true);
                Process k = pb.start();
                BufferedReader reader = new BufferedReader(new InputStreamReader(k.getInputStream()));
                String line;
                while ((line = reader.readLine()) != null) {
                    Log.Out.println("  " + line);
                }
                return k.waitFor() == 0;
            }
        } catch (Exception ex) {
            Log.Err.println("Exception: " + ex.getMessage());
            return false;
        }
    }

    public static class ProcessToKill {

        public Process process;
        public int pid;

        public ProcessToKill(Process p, int pid) {
            process = p;
            this.pid = pid;
        }

        public void kill() {
            Kill(pid);
        }
    }

    final static Object lockRserveLauncher = new Object();

    /**
     * attempt to start Rserve.Note: parameters are <b>not</b> quoted, so avoid
     * using any quotes in arguments
     *
     * @param cmd command necessary to start R
     * @param rargs arguments are are to be passed to R
     * @param rsrvargs arguments to be passed to Rserve
     * @param debug Rserve debug mode ?
     * @param lock ServerSocket locker. Should be closed before using Rserve
     * @return <code>true</code> if Rserve is running or was successfully
     * started, <code>false</code> otherwise.
     */
    public static ProcessToKill launchRserve(String cmd, /*String libloc,*/ String rargs, String rsrvargs, boolean debug, ServerSocket lock) throws IOException {
        Log.Out.println("Will launch Rserve (" + cmd + " " + rargs + ")");
        Log.Out.println("  From lib directory: " + RserveDaemon.app_dir());// + " , which contains: " + Arrays.toString(RserveDaemon.app_dir().list()));
        File wd = new File(RserveDaemon.app_dir(), new SimpleDateFormat("yyyy-MM-dd_HH-mm-ss").format(Calendar.getInstance().getTime()));
        Log.Out.println("  In working directory: " + wd.getAbsolutePath());
        try {
            FileUtils.forceMkdir(wd);
        } catch (IOException ex) {
        }
        if (!wd.isDirectory()) {
            throw new IOException("Working dir " + wd + " not available.");
        }
        wd.deleteOnExit();

        Process p = null;
        //synchronized (lockRserveLauncher) {
        int[] last_pids = getRservePIDs();

        if (lock != null) {
            //Log.Err.println("Release lock "+lock);
            try {
                lock.close(); // release lock on this port, at last
            } catch (IOException ex) {
                throw new IOException("Could not close Rserve locker");
            }
        }

        File outstream = new File(RserveDaemon.app_dir(), new SimpleDateFormat("yyyy-MM-dd_HH-mm-ss").format(Calendar.getInstance().getTime()) + ".Rout");
        p = doInR("packageDescription('Rserve',lib.loc='" + RserveDaemon.app_dir() + "'); "
                + "library(Rserve,lib.loc='" + RserveDaemon.app_dir() + "'); "
                + "setwd('" + wd.getAbsolutePath().replace('\\', '/') + "'); "
                + "print(getwd()); "
                + "\n     Rserve(" + (debug ? "TRUE" : "FALSE") + ",args='" + rsrvargs + "');" + UGLY_FIXES, cmd, rargs, outstream);
        if (p == null) {
            throw new IOException("Failed to start Rserve process:\n" + org.apache.commons.io.FileUtils.readFileToString(outstream).replaceAll("\n", "\n  | "));
        }

        int pid_attempts = 50;
        int pid = -1; // means "none"
        while (pid < 0 && pid_attempts > 0) {
            pid = diff(getRservePIDs(), last_pids);
            try {
                Thread.sleep(100);
            } catch (InterruptedException ix) {
            }
            Log.Out.print(".");
            pid_attempts--;
        }
        if (pid == -1) {
            throw new IOException("Failed to get Rserve PID:\n" + org.apache.commons.io.FileUtils.readFileToString(outstream).replaceAll("\n", "\n  | "));
        }
        Log.Out.println((pid_attempts < 50 ? "\n" : "") + "  With PID: " + pid);

        //}
        int connect_attempts = 30;
        while (connect_attempts > 0) {
            try {
                try {
                    Thread.sleep(1000);/* a safety sleep just in case the start up is delayed or asynchronous */
                } catch (InterruptedException ix) {
                }
                RConnection c = null;
                int port = -1;
                RserverConf testconf;
                if (rsrvargs.contains("--RS-port")) {
                    String rsport = rsrvargs.split("--RS-port")[1].trim().split(" ")[0];
                    port = Integer.parseInt(rsport);
                    testconf = new RserverConf("localhost", port, null, null);
                } else {
                    testconf = new RserverConf("localhost", -1, null, null);
                }
                c = testconf.connect();
                if (c == null) {
                    throw new RserverConf.TimeOut.TimeOutException("Failed start connection to " + testconf);
                }
                if (!c.isConnected()) {
                    throw new RserverConf.TimeOut.TimeOutException("Failed to connect to " + testconf);
                }
                Log.Out.println((connect_attempts < 30 ? "\n" : "") + "  On port: " + testconf.port);

                if (c.eval("exists('.RSERVE_PID')").asInteger() != 0) {
                    int previous_pid = c.eval(".RSERVE_PID").asInteger();
                    Kill(pid);
                    throw new IOException("Rserve was already running on port " + port + " with previous PID " + previous_pid);
                }
                c.voidEval(".RSERVE_PID <- " + pid);
                Log.Out.println("Rserve is well running on port " + testconf.port + " (PID " + pid + ")");
                Log.Err.println(">>>>>>>>>>>>>>>>> Rserve OK " + testconf.port + " (PID " + pid + ")");
                c.close();
                Log.Err.println(">>>>>>>>>>>>>>>>> close connection on " + testconf.port + " (PID " + pid + ")");
                return new ProcessToKill(p, pid);
            } catch (NumberFormatException | REXPMismatchException | RserveException | RserverConf.TimeOut.TimeOutException e2) {
                Log.Out.print("o");
                e2.printStackTrace();
            }
            connect_attempts--;
        }
        throw new IOException("Failed to launch Rserve:\n" + org.apache.commons.io.FileUtils.readFileToString(outstream).replaceAll("\n", "\n  | "));
    }

    // find new elements in news, regarding previous
    static int diff(int[] news, int[] previous) {
        for (int i = 0; i < news.length; i++) {
            boolean in = false;
            for (int j = 0; j < previous.length; j++) {
                if (news[i] == previous[j]) {
                    in = true;
                    break;
                }
            }
            if (!in) {
                return news[i];
            }
        }
        return -1;
    }

    public static int[] getRservePIDs() {
        List<Integer> pids = new LinkedList<>();
        if (RserveDaemon.isWindows()) { // Windows, so we expect tasklist is available in PATH
            try {
                ProcessBuilder pb = new ProcessBuilder("tasklist");
                pb.redirectErrorStream(true);
                Process process = pb.start();
                BufferedReader reader = new BufferedReader(new InputStreamReader(process.getInputStream()));
                String line;
                while ((line = reader.readLine()) != null) {
                    //Log.Out.print("\n> " + line);
                    if (line.startsWith("Rserve.exe") || line.startsWith("Rserve_d.exe")) {
                        String[] info = line.split("\\s+");
                        int pid = Integer.parseInt(info[1]);
                        pids.add(pid);
                    }
                }
                //process.waitFor();
            } catch (Exception e) {
                Log.Err.println(e.getMessage());
            }
            //Log.Out.println(">> "+pid);
        } else if (RserveDaemon.isLinux()) {
            try {
                ProcessBuilder pb = new ProcessBuilder(splitCommand("ps -aux"));
                pb.redirectErrorStream(true);
                Process process = pb.start();
                BufferedReader reader = new BufferedReader(new InputStreamReader(process.getInputStream()));
                String line;
                while ((line = reader.readLine()) != null) {
                    if ((line.contains("Rserve --vanilla") || line.contains("Rserve_d --vanilla")) && line.contains("Ss")) {
                        //Log.Out.print("\n> " + line);
                        String[] info = line.split("\\s+");
                        int pid = Integer.parseInt(info[1]);
                        pids.add(pid);
                    }
                }
                process.waitFor();
            } catch (Exception e) {
                Log.Err.println(e.getMessage());
            }
        } else if (RserveDaemon.isMacOSX()) { // MacOS
            try {
                ProcessBuilder pb = new ProcessBuilder(splitCommand("ps aux"));
                pb.redirectErrorStream(true);
                Process process = pb.start();
                System.err.println("process builder" + pb);
                System.err.println("process " + process);
                BufferedReader reader = new BufferedReader(new InputStreamReader(process.getInputStream()));
                String line;
                while ((line = reader.readLine()) != null) {
                    if ((line.contains("Rserve --vanilla") || line.contains("Rserve_d --vanilla")) && line.contains("Ss")) {
                        Log.Out.print("\n> " + line);
                        String[] info = line.split("\\s+");
                        int pid = Integer.parseInt(info[1]);
                        pids.add(pid);
                    }
                }
                process.waitFor();
            } catch (Exception e) {
                Log.Err.println(e.getMessage());
            }
        } else {
            Log.Err.println("Cannot recognize OS: " + System.getProperty("os.name"));
        }
        Log.Out.println("Rserve PIDS: " + pids);
        int[] ps = new int[pids.size()];
        for (int i = 0; i < pids.size(); i++) {
            ps[i] = pids.get(i);
        }
        return ps;
    }

    /*public static Socket acceptTimeout(ServerSocket s) throws IOException {
        if (RserveDaemon.isMacOSX()) {
            try {
                TimeOut t = new RserverConf.TimeOut() {
                    @Override
                    protected Object defaultResult() {
                        return (Socket) null;
                    }

                    @Override
                    protected Object command() {
                        try {
                            return s.accept();
                        } catch (IOException ex) {
                            return ex;
                        }
                    }
                };
                t.execute(2000);
                Object result = t.getResult();
                if (result instanceof IOException) {
                    throw new IOException(((IOException) result).getMessage());
                } else {
                    return (Socket) result;
                }
            } catch (TimeOutException e) {
                throw new IOException(e.getMessage());
            }
        }

        return s.accept();
    }*/

    final static Object lockPort = new Object();

    // Returns an open socket to lock the port on system
    public static ServerSocket lockPort(int p) {
        Log.Err.println(">>>>>>>>>>>> lockPort " + p);
        // synchronized (lockPort) {
        //System.err.print("? " + p);
        ServerSocket ss = null;
        final String id = "" + Math.random();
        try {
            final ServerSocket sss = new ServerSocket(p);
            ss = sss;
            Thread t = new Thread(new Runnable() {
                @Override
                public void run() {
                    try {
                        Socket s = sss.accept(); //acceptTimeout(sss);
                        //s.setReuseAddress(false);
                        s.setKeepAlive(true);
                        s.setSoTimeout(5000);
                        DataInputStream dis = new DataInputStream(s.getInputStream());
                        String str = (String) dis.readUTF();
                        if (!str.equals(id)) { // ensure there is no mess there...
                            sss.close();
                            throw new IOException("Wrong port id!");
                        }
                    } catch (IOException ex) {
                        Log.Err.println("Lock port " + p + " failed!");
                    }
                }
            });
            t.start();

            Socket cs = new Socket("localhost", p);
            DataOutputStream dout = new DataOutputStream(cs.getOutputStream());
            dout.writeUTF(id);
            dout.flush();
            dout.close();
            cs.close();
            t.join();
        } catch (BindException e) {
            Log.Err.println(">>>>>>>>>>>> lockPort " + p + ": " + e.getMessage());
            return null;
        } catch (IOException e) {
            Log.Err.println(">>>>>>>>>>>> lockPort " + p + ": " + e.getMessage());
            return null;
        } catch (InterruptedException e) {
            Log.Err.println(">>>>>>>>>>>> lockPort " + p + ": " + e.getMessage());
            return null;
        }
        Log.Err.println(">>>>>>>>>>>> lockPort " + p + ": " + !ss.isClosed());
        //System.err.println(" OK");
        return ss.isClosed() ? null : ss;
        //}
    }

    /**
     * checks whether Rserve is running and if that's not the case it attempts
     * to start it using the defaults for the platform where it is run on.This
     * method is meant to be set-and-forget and cover most default setups. For
     * special setups you may get more control over R with
     * <code>launchRserve</code> instead.
     *
     * @param port Rserve port to check
     * @return is ok ?
     */
    public static boolean checkLocalRserve(int port) {
        if (isRserveListening(port)) {
            return true;
        }
        if (!RserveDaemon.findR_HOME(RserveDaemon.R_HOME)) {
            return false; // this will aslo initialize R_HOME if passes
        }
        try {
            if (RserveDaemon.isWindows()) {
                ProcessToKill p = launchRserve(RserveDaemon.R_HOME + "\\bin\\R.exe", "--vanilla", "--vanilla --RS-enable-control --RS-port " + port, false, null);
                if (p == null) {
                    return false;
                }
                p.kill();
                return true;
            } else {
                ProcessToKill p = launchRserve(RserveDaemon.R_HOME + "/bin/R", "--vanilla", "--vanilla --RS-enable-control --RS-port " + port, false, null);
                if (p == null) {
                    return false;
                }
                p.kill();
                return true;
            }
        } catch (Exception e) {
            Log.Err.println("Local Rserve not available.");
            return false;
        }
    }

    /**
     * check whether Rserve is currently running (on local machine and default
     * port).
     *
     * @param port Rserve port to check
     * @return <code>true</code> if local Rserve instance is running,
     * <code>false</code> otherwise
     */
    public static boolean isRserveListening(int port) {
        try {
            RConnection c = new RConnection("localhost", port);
            Log.Out.println("Rserve is running on port " + port);
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

        System.out.println("checkLocalRserve: " + checkLocalRserve(6311));
        try {
            RConnection c = new RConnection(RserverConf.DEFAULT_RSERVE_HOST, RserverConf.DEFAULT_RSERVE_PORT);
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
