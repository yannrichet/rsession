package org.math.R;

import java.io.BufferedReader;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.ServerSocket;
import java.net.Socket;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.LinkedList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.apache.commons.io.FileUtils;
import org.apache.commons.io.IOUtils;
import org.apache.commons.io.filefilter.FileFilterUtils;
import org.apache.commons.io.filefilter.IOFileFilter;

import static org.math.R.RserveDaemon.isWindows;
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
     * R batch to check Rserve is installed in RserveDaemon.app_dir()
     *
     * @throws java.io.IOException
     * @return Rserve is already installed
     */
    public static boolean isRserveInstalled() throws IOException {
        // shortcut & try avoid filesystem sync issues on windows
        File dir = new File(RserveDaemon.app_dir(), "Rserve");
        if (dir.isDirectory()) {
            File desc = new File(dir, "DESCRIPTION");
            // validate install if version >=1.7-5 or 1.8
            if (desc.isFile() && (org.apache.commons.io.FileUtils.readFileToString(desc).contains("1.7-5") | org.apache.commons.io.FileUtils.readFileToString(desc).contains("1.8"))) {
                return true;
            } else {
                Log.Err.println("Seems Rserve is not _well_ installed: " + (desc.isFile()?org.apache.commons.io.FileUtils.readFileToString(desc):"No DESCRIPTION") +" Force remove!");
                if (RserveDaemon.isWindows()) {
                    //Log.Err.println("  OS:Windows, so try to kill Rserve.exe before:");
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
            Log.Err.println("No Rserve directory in "+RserveDaemon.app_dir().getAbsolutePath());
            return false;
        }
    }

    /**
     * R batch to install Rserve
     *
     * @param Rcmd command to start R
     * @return success
     * @throws IOException
     */
    public static boolean installRserveFromLocalLibrary(String Rcmd) throws InterruptedException, IOException {
        if (isRserveInstalled()) {
            Log.Out.println("Rserve already available. (skip installRserveFromLocalLibrary)");
            return true;
        }

        Log.Out.println("Install/copy Rserve from local library");

        try {
            //String result = doInR("if (is.element(set=installed.packages(),el='Rserve')) packageVersion('Rserve') else print(paste('No','Rserve'))", Rcmd, "--vanilla --silent", null);
            // That should emulate without using installed.packages, which may be locked by a parent install.packages
            String result = doInR("which_Rserve = which(gregexpr('/Rserve$',list.files(.libPaths(),full.names=T))>0);"+
                                    " if (length(which_Rserve)>0) "+
                                    "print(readLines(file.path(list.files(.libPaths(),full.names=T)[which_Rserve],'DESCRIPTION'))[2]) "+
                                    "else print(paste('No','Rserve'))", 
                                Rcmd, "--vanilla --silent", null);
            if (result.contains("No Rserve")) {
                Log.Out.println("Rserve not available in standard lib.loc");
                return false;
            } else {
                String version = result.replaceAll(">.*", "").trim();
                if (version.contains("1.7-5") || version.contains("1.7.5") || version.contains("1.8")) {
                    Log.Out.println("Rserve version suitable: "+version+". Trying to copy in lib.loc="+RserveDaemon.app_dir());
                    // perform a hard copy to RserveDaemon.app_dir()
                    //String desc = doInR("packageDescription('Rserve')", Rcmd, "--vanilla --silent", null);
                    //Pattern regex = Pattern.compile("^(.*)File(.*)Meta(.*)$", Pattern.MULTILINE);
                    //Matcher regexMatcher = regex.matcher(desc);
                    //if (!regexMatcher.find()) {
                    //    Log.Err.println("Could not find pattern "+regex+" in "+desc.replaceAll("\n", "\n  | "));
                    //    return false;
                    //}
                    //String match = regexMatcher.group(2).replaceAll("/", File.separator);
                    //File path = new File(match.substring(2)); 
                    
                    String print_path = doInR("which_Rserve = which(gregexpr('/Rserve$',list.files(.libPaths(),full.names=T))>0); "+
                                            "print(file.path(list.files(.libPaths(),full.names=T)[which_Rserve]))", 
                                            Rcmd, "--vanilla --silent", null);
                    Pattern regex = Pattern.compile("^(.*)/Rserve\"", Pattern.MULTILINE);
                    Matcher regexMatcher = regex.matcher(print_path);
                    if (!regexMatcher.find()) {
                        Log.Err.println("Could not find pattern "+regex+" in "+print_path.replaceAll("\n", "\n  | "));
                        return false;
                    }
                    String match = isWindows() ? (regexMatcher.group(1)+"/Rserve").replaceAll("/", "\\\\") : regexMatcher.group(1)+"/Rserve";
                    File path = new File(match.substring(5));
                
                    FileUtils.copyDirectoryToDirectory(path, RserveDaemon.app_dir());
                    for (File Rserve_exe : FileUtils.listFiles(RserveDaemon.app_dir(),FileFilterUtils.prefixFileFilter("Rserve"),FileFilterUtils.directoryFileFilter())) {
                        if (!Rserve_exe.setExecutable(true)) {
                            Log.Err.println("Could not set executable: "+Rserve_exe);
                            return false;
                        }
                    }
                    for (File Rserve_exe : FileUtils.listFiles(RserveDaemon.app_dir(),FileFilterUtils.prefixFileFilter("Rserve.dbg"),FileFilterUtils.directoryFileFilter())) {
                        if (!Rserve_exe.setExecutable(true)) {
                            Log.Err.println("Could not set executable: "+Rserve_exe);
                            return false;
                        }
                    }
                    for (File Rserve_exe : FileUtils.listFiles(RserveDaemon.app_dir(),FileFilterUtils.prefixFileFilter("Rserve_d"),FileFilterUtils.directoryFileFilter())) {
                        if (!Rserve_exe.setExecutable(true)) {
                            Log.Err.println("Could not set executable: "+Rserve_exe);
                            return false;
                        }
                    }
                } else {
                    Log.Out.println("Rserve library version not suitable: "+version);
                    return false;
                }
            }
        } catch (Exception e) {
            Log.Err.println("Failed to check Rserve in standard lib.loc:"+ e.getMessage());
            return false;
        }

        if (isRserveInstalled()) {
            Log.Out.println(" well installed.");
            return true;
        } else { 
            Log.Out.println(" not well installed !");
            return false;
        }
    }

    /**
     * R batch to install Rserve from net
     *
     * @param Rcmd command necessary to start R
     * @param http_proxy http://login:password@proxy:port string to enable
     * internet access to rforge server
     * @param repository from which R repo ?
     * @return success
     * @throws IOException
     */
    // If possible, do not use this legacy Rserve (use patched version github.com/yannrichet/Rserve-1.7)
    public static boolean installRserve(String Rcmd, String http_proxy, String repository) throws IOException {
        if (isRserveInstalled()) {
            Log.Out.println("Rserve already available. (skip installRserve)");
            return true;
        }
        
        if (repository == null || repository.length() == 0) {
            repository = Rsession.DEFAULT_REPOS;
        }
        if (http_proxy == null) {
            http_proxy = "";
        }

        Log.Out.println("Install Rserve from " + repository + " (http_proxy='" + http_proxy + "') ");
        try {
            String result = doInR((http_proxy != null ? "Sys.setenv(http_proxy='" + http_proxy + "');" : "") + 
                                "install.packages('Rserve',repos='" + repository + "',lib='" + RserveDaemon.app_dir() + "')", Rcmd, "--vanilla --silent", null);

            String installed = doInR("'Rserve' %in% installed.packages(lib.loc='" + RserveDaemon.app_dir() + "')", Rcmd, "--vanilla --silent", null);
            if ((result.contains("MD5") && result.contains("'Rserve'"))||
                result.contains("* DONE (Rserve)") ||
                installed.contains("TRUE")) {
                Log.Out.println("  OK: \n  |" + result.replaceAll("\n", "\n  | ") + "  ---\n" + installed.replaceAll("\n", "\n  | "));
            } else if (result.contains("FAILED") || result.contains("Error") || installed.contains("FALSE")) {
                Log.Out.println("  FAILED: \n  |" + result.replaceAll("\n", "\n  | ") + "  ---\n" + installed.replaceAll("\n", "\n  | "));
            } else {
                Log.Out.println("  UNKNOWN: \n  |" + result.replaceAll("\n", "\n  | ")+ "  ---\n" + installed.replaceAll("\n", "\n  | "));
            }

        } catch (IOException ioe) {
            Log.Err.print("Rserve NOT well installed: "+ioe.getMessage());
            return false;
        }            
        
        if (isRserveInstalled()) {
            Log.Out.println(" well installed.");
            return true;
        } else { 
            Log.Out.println(" not well installed !");
            return false;
        }  
    }

    /**
     * R batch to install Rserve from included jar resources
     *
     * @param Rcmd command necessary to start R
     * @throws java.io.IOException
     * @throws java.lang.InterruptedException
     * @return success
     */
    public static boolean installBundledRserve(String Rcmd) throws InterruptedException, IOException {
        if (isRserveInstalled()) {
            Log.Out.println("Rserve already available. (skip installBundledRserve)");
            return true;
        }

        Log.Out.println("Install Rserve from Rsession bundle");

        String R_version_path = ".";
        String Rserve_version = "";
        String outv_str = "?";
        try {
            outv_str = doInR("cat(paste0(R.version[['major']],'.',floor(as.numeric(R.version[['minor']]))))", Rcmd, "--silent", null).replaceAll(">.*", "").trim();
            if (outv_str.startsWith("3.6")) {
                R_version_path = "R-3.6";
                Rserve_version = "1.7-5";
            } else if (outv_str.startsWith("4.1")) {
                R_version_path = "R-4.1";
                Rserve_version = "1.8-10";
            } else if (outv_str.startsWith("4.2")) {
                R_version_path = "R-4.2";
                Rserve_version = "1.8-10";
            } else Log.Err.println("R version ('"+outv_str+"') not supported.\n  Will try to use source install."+ (isWindows()?" (assuming Rtools is available)":""));
        } catch (Exception ex) {
            Log.Err.println(ex.getMessage()+": \n"+outv_str);
            return false;
        }
        
        String pack_suffix = ".tar.gz";
        if (!R_version_path.equals("."))
            if (RserveDaemon.isWindows()) {
                pack_suffix = ".zip";
            } else if (RserveDaemon.isMacOSX()) {
                pack_suffix = ".tgz";
            } else R_version_path="."; //back to source install

        File packFile;
        try {
            packFile = File.createTempFile("Rserve_" + Rserve_version, pack_suffix);
            packFile.deleteOnExit();
        } catch (IOException ex) {
            Log.Err.println(ex.getMessage());
            return false;
        }
        try {
            ClassLoader classloader = Thread.currentThread().getContextClassLoader();
            InputStream fileStream = classloader.getResourceAsStream("org/math/R/"+R_version_path+"/Rserve_" + Rserve_version + pack_suffix);

            if (fileStream == null) {
                throw new IOException("Cannot find resource " + "org/math/R/"+R_version_path+"/Rserve_" + Rserve_version + pack_suffix);
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

        String result = doInR("install.packages('" + packFile.getAbsolutePath().replace("\\", "/") + 
                            "', type="+(packFile.getName().endsWith(".tar.gz")?"'source'":"'binary'")+
                            ", repos=NULL,lib='" + RserveDaemon.app_dir() + "')", 
                            Rcmd, "--vanilla --silent", null);

        if (result.contains("package 'Rserve' successfully unpacked and MD5 sums checked") || 
            result.contains("* DONE (Rserve)") ||
            doInR("'Rserve' %in% installed.packages(lib.loc='" + RserveDaemon.app_dir() + "')", Rcmd, "--vanilla --silent", null).contains("TRUE")) {
            return true;
        } else if (result.contains("FAILED") || result.contains("ERROR")) {
            Log.Out.println("\nRserve install failed: " + result.replaceAll("\n", "\n  | "));
            return false;
        } else Log.Out.println("\nRserve install result:" + result.replaceAll("\n", "\n  | "));
        
        if (isRserveInstalled()) {
            Log.Out.println(" well installed.");
            return true;
        } else { 
            Log.Out.println(" not well installed !");
            return false;
        }       
    }

    static String[] splitCommand(String command) {
        List<String> matchList = new ArrayList<String>();
        Pattern regex = Pattern.compile("[^\\s\"']+|\"[^\"]*\"|'[^']*'");
        Matcher regexMatcher = regex.matcher(command);
        while (regexMatcher.find()) {
            matchList.add(regexMatcher.group());
        }
        //Log.Out.println(command+" -> "+matchList);
        return matchList.toArray(new String[matchList.size()]);
    }

    /**
     * attempt to start Rserve. Note: parameters are <b>not</b> quoted, so avoid
     * using any quotes in arguments
     *
     * @param todo command to execute in R
     * @param Rcmd command necessary to start R
     * @param rargs arguments are are to be passed to R (e.g. --vanilla -q)
     * @param out file to redirect R output. If null set a default name.
     * @return <code>true</code> if Rserve is running or was successfully
     * started, <code>false</code> otherwise.
     * @throws java.io.IOException
     */
    public static String doInR(String todo, String Rcmd, String rargs, File out) throws IOException {
        if (out==null) {
            out = File.createTempFile("doInR_", ".Rout");
            out.deleteOnExit();
        }
        Process p = system(Rcmd + " "+ rargs + " -e \"" + todo + "\"", out, true);
        if (p == null) {
            throw new IOException("Failed to do in R: " + Rcmd + " "+ rargs + " -e \"" + todo + "\"");
        }
        return org.apache.commons.io.FileUtils.readFileToString(out);
    }

    public static Process system(String command, File redirect, boolean waitFor) { 
        command = command +" > " + redirect.getAbsolutePath() + (!RserveDaemon.isWindows() ? " 2>&1" : "");
        boolean system_log = Boolean.parseBoolean(System.getProperty("system.print", "false"));
        if (system_log) Log.Out.println("  $  " + command );
        Process p = null;
        try {
            if (RserveDaemon.isWindows()) {
                ProcessBuilder pb = new ProcessBuilder("cmd.exe","/c", command);
                pb.redirectError(); // ~ 2>&1
                p = pb.start();

                if (waitFor) {
                    String line;
                    BufferedReader reader = new BufferedReader(new InputStreamReader(p.getInputStream()));
                    int t = 0;
                    while ((line = reader.readLine()) == null && t<100) {
                        try{Thread.sleep(100);}catch(InterruptedException e){} // ensure writer started or wait 10 s.
                        t+=1;
                    }
                    while ((line = reader.readLine()) != null) {
                        try{Thread.sleep(100);}catch(InterruptedException e){}    
                    }

                    try {p.destroyForcibly();} catch (Exception e) {e.printStackTrace();}

                    /* Another (but slower) alternative to emulate waitFor:
                    String lines = ".";
                    String last_lines = lines;
                    boolean started=false; // try emulate waitFor, which does not work in Windows
                    long attempts = TIMEOUT;
                    int same = 10; // check no progress in last 5 s.
                    while ((attempts-- > 0 && (!started || !(lines.equals(last_lines)))) || same>0) {
                        if (lines.equals(".")) {
                            //Log.Out.print(".");
                        } else
                            started = redirect.isFile() && redirect.length()>0;
                        Thread.sleep(500);
                        last_lines = lines;
                        lines = org.apache.commons.io.FileUtils.readFileToString(redirect);
                        if (lines.equals(last_lines)) same--; else same = 10;
                        System.err.println("same:"+same);
                    }*/
                }
                //String lines = org.apache.commons.io.FileUtils.readFileToString(redirect);
                //Log.Out.println("> " + lines);
            } else /* unix startup */ {
                ProcessBuilder pb = new ProcessBuilder("/bin/sh", "-c", command);
                p = pb.start();

                if (waitFor) {
                    p.waitFor(TIMEOUT, java.util.concurrent.TimeUnit.SECONDS);
                    try {p.destroyForcibly();} catch (Exception e) {e.printStackTrace();}
                }
            }
        } catch (Exception x) {
            Log.Err.println("Command: "+command + " failed:\n" +x.getMessage());
            return null;
        }
        return p;
    }

    public static long TIMEOUT = Long.parseLong(System.getProperty("system.timeout","60")); // 1 min. as default timeout for process waiting

    static String UGLY_FIXES = "";//flush.console <- function(...) {return;}; options(error=function() NULL)";

    public static boolean KillAll(String taskname) {
        try {
            //Log.Out.print("Kill process " + taskname + ": ");
            if (isWindows()) {
                ProcessBuilder pb = new ProcessBuilder(splitCommand("taskkill /F /IM " + taskname));
                pb.redirectErrorStream(true);
                Process k = pb.start();
                BufferedReader reader = new BufferedReader(new InputStreamReader(k.getInputStream()));
                String line;
                while ((line = reader.readLine()) != null) {
                    //Log.Out.println("  " + line);
                }
                return true;//k.waitFor(TIMEOUT, java.util.concurrent.TimeUnit.SECONDS) == 0;
            } else {
                ProcessBuilder pb = new ProcessBuilder(splitCommand("killall " + taskname));
                pb.redirectErrorStream(true);
                Process k = pb.start();
                BufferedReader reader = new BufferedReader(new InputStreamReader(k.getInputStream()));
                String line;
                while ((line = reader.readLine()) != null) {
                    //Log.Out.println("  " + line);
                }
                return k.waitFor(TIMEOUT, java.util.concurrent.TimeUnit.SECONDS);
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
                return true;//k.waitFor(TIMEOUT, java.util.concurrent.TimeUnit.SECONDS) == 0;
            } else {
                ProcessBuilder pb = new ProcessBuilder(splitCommand("kill -9 " + pid));
                pb.redirectErrorStream(true);
                Process k = pb.start();
                BufferedReader reader = new BufferedReader(new InputStreamReader(k.getInputStream()));
                String line;
                while ((line = reader.readLine()) != null) {
                    Log.Out.println("  " + line);
                }
                return k.waitFor(TIMEOUT, java.util.concurrent.TimeUnit.SECONDS);
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
            try {process.destroyForcibly();} catch (Exception e) {e.printStackTrace();}
            Kill(pid);
        }
    }

    /**
     * attempt to start Rserve.Note: parameters are <b>not</b> quoted, so avoid
     * using any quotes in arguments
     *
     * @param Rcmd command necessary to start R
     * @param rargs arguments are are to be passed to R
     * @param rsrvargs arguments to be passed to Rserve
     * @param debug Rserve debug mode ?
     * @param lock ServerSocket locker. Should be closed before using Rserve
     * @return <code>true</code> if Rserve is running or was successfully
     * started, <code>false</code> otherwise.
     * @throws java.io.IOException
     */
    public static ProcessToKill launchRserve(String Rcmd, /*String libloc,*/ String rargs, String rsrvargs, boolean debug, ServerSocket lock) throws IOException {
        Log.Out.println("Will launch Rserve (" + Rcmd + " " + rargs + ")");
        Log.Out.println("  With lib directory: " + RserveDaemon.app_dir());// + " , which contains: " + Arrays.toString(RserveDaemon.app_dir().list()));
        File wd = new File(RserveDaemon.app_dir(), new SimpleDateFormat("yyyy-MM-dd_HH-mm-ss").format(Calendar.getInstance().getTime()));
        Log.Out.println("  In working directory: " + wd.getAbsolutePath());
        try {
            FileUtils.forceMkdir(wd);
        } catch (IOException ex) {
            ex.printStackTrace();
        }
        if (!wd.isDirectory()) {
            if (debug) Log.Err.println("Working dir " + wd + " not available.");
            throw new IOException("Working dir " + wd + " not available.");
        }
        wd.deleteOnExit();

        Process p = null;
        //synchronized (lockRserveLauncher) {

        if (lock != null) {
            //Log.Err.println("Release lock "+lock);
            try {
                lock.close(); // release lock on this port, at last
            } catch (IOException ex) {
                if (debug) Log.Err.println("Could not close Rserve locker");
                throw new IOException("Could not close Rserve locker");
            }
        }

        File outstream = new File(RserveDaemon.app_dir(), new SimpleDateFormat("yyyy-MM-dd_HH-mm-ss").format(Calendar.getInstance().getTime()) + ".Rout");
        String todo = "library(Rserve,lib.loc='" + RserveDaemon.app_dir() + "'); "
                + "setwd('" + wd.getAbsolutePath().replace('\\', '/') + "'); "
                + "Rserve(" + (debug ? "TRUE" : "FALSE") + ",args='" + rsrvargs + "');" + UGLY_FIXES; 
        int[] last_pids = getRservePIDs();
        p = system(Rcmd + " "+ rargs + " -e \"" + todo + "\"", outstream, false);
        if (p == null) {
            if (debug) Log.Err.println("Failed to call: "+Rcmd + " "+ rargs + " -e \"" + todo + "\":\n"+org.apache.commons.io.FileUtils.readFileToString(outstream).replaceAll("\n", "\n  | "));
            throw new IOException("Failed to call: "+Rcmd + " "+ rargs + " -e \"" + todo + "\":\n"+org.apache.commons.io.FileUtils.readFileToString(outstream).replaceAll("\n", "\n  | "));
        }
        
        // just waitFor Rserve starting (not waiting for return)
        int attempts = 50;
        while (attempts-->10 && !outstream.exists())
            try {
                Thread.sleep(100);
            } catch (InterruptedException e) {
            }
        if (debug) Log.Err.println(org.apache.commons.io.FileUtils.readFileToString(outstream).replaceAll("\n", "\n    | "));

        int pid_attempts = 50;
        int pid = -1; // means "none"
        while (pid < 0 && pid_attempts-- > 0) {
            pid = diff(getRservePIDs(), last_pids);
            try {
                Thread.sleep(100);
            } catch (InterruptedException ix) {
            }
            //Log.Out.print(".");
        }
        if (pid == -1) {
            if (debug) Log.Err.println("Failed to get Rserve PID:\n" + org.apache.commons.io.FileUtils.readFileToString(outstream).replaceAll("\n", "\n  | "));
            throw new IOException("Failed to get Rserve PID:\n" + org.apache.commons.io.FileUtils.readFileToString(outstream).replaceAll("\n", "\n  | "));
        }
        Log.Out.println("  With PID: " + pid);

        //}
        int connect_attempts = 30;
        while (connect_attempts-- > 0) {
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
                Log.Out.println("  On port: " + testconf.port);

                if (c.eval("exists('.RSERVE_PID')").asInteger() != 0) {
                    int previous_pid = c.eval(".RSERVE_PID").asInteger();
                    Kill(pid);
                    throw new IOException("Rserve was already running on port " + port + " with previous PID " + previous_pid);
                }
                c.voidEval(".RSERVE_PID <- " + pid);
                Log.Out.println("  Rserve is well running on port " + testconf.port + " (PID " + pid + ")");
                c.close();
                return new ProcessToKill(p, pid);
            } catch (NumberFormatException | REXPMismatchException | RserveException | RserverConf.TimeOut.TimeOutException e2) {
                if (debug) Log.Err.println("  "+e2.getMessage());
            }
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
                //process.waitFor(TIMEOUT, java.util.concurrent.TimeUnit.SECONDS);
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
                process.waitFor(TIMEOUT, java.util.concurrent.TimeUnit.SECONDS);
            } catch (Exception e) {
                Log.Err.println(e.getMessage());
            }
        } else if (RserveDaemon.isMacOSX()) { // MacOS
            try {
                ProcessBuilder pb = new ProcessBuilder(splitCommand("ps aux"));
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
                process.waitFor(TIMEOUT, java.util.concurrent.TimeUnit.SECONDS);
            } catch (Exception e) {
                Log.Err.println(e.getMessage());
            }
        } else {
            Log.Err.println("Cannot recognize OS: " + System.getProperty("os.name"));
        }
        //Log.Out.println("Rserve PIDS: " + pids);
        int[] ps = new int[pids.size()];
        for (int i = 0; i < pids.size(); i++) {
            ps[i] = pids.get(i);
        }
        return ps;
    }

    static volatile boolean locking = false;

    // Returns an open socket to lock the port on system
    public static ServerSocket lockPort(int p) {
        ServerSocket ss = null;
        final String id = "" + Math.random();
        DataOutputStream dout = null;
        Socket cs = null;
        try {
            final ServerSocket sss = new ServerSocket(p);
            sss.setSoTimeout(5000);
            ss = sss;
            Thread t = new Thread(new Runnable() {
                @Override
                public void run() {
                    try {
                        locking = true;
                        Socket s = sss.accept();
                        DataInputStream dis = new DataInputStream(s.getInputStream());
                        String str = (String) dis.readUTF();
                        if (!str.equals(id)) { // ensure there is no mess there...
                            throw new IOException("Wrong port id!");
                        }
                    } catch (IOException ex) {
                        try {
                            sss.close();
                        } catch (IOException ex1) {
                        }
                        Log.Err.println("Lock port " + p + " failed: " + ex.getMessage());
                    }
                }
            });
            t.start();

            int n = 50;
            while ((n--) > 0 && !locking) {
                Thread.sleep(100);
                //Log.Err.print("x");
            }
            if (!locking) {
                throw new IOException("Did not start ServerSocket on port " + p);
            }
            locking = false;

            cs = new Socket("localhost", p);
            dout = new DataOutputStream(cs.getOutputStream());
            dout.writeUTF(id);
            dout.flush();
            t.join();
        } catch (IOException | InterruptedException e) {
            if (ss != null) {
                try {
                    ss.close();
                } catch (IOException ex) {
                }
            }
            return null;
        } finally {
            if (dout != null) {
                try {
                    dout.close();
                } catch (IOException ex) {
                }
            }
            if (cs != null) {
                try {
                    cs.close();
                } catch (IOException ex) {
                }
            }
        }
        return ss.isClosed() ? null : ss;
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
                ProcessToKill p = launchRserve(RserveDaemon.R_HOME + "\\bin\\R.exe", "--vanilla", "--vanilla "+/*--RS-enable-control */"--RS-port " + port, false, null);
                if (p == null) {
                    return false;
                }
                p.kill();
                return true;
            } else {
                ProcessToKill p = launchRserve(RserveDaemon.R_HOME + "/bin/R", "--vanilla", "--vanilla "+/*--RS-enable-control */"--RS-port " + port, false, null);
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
