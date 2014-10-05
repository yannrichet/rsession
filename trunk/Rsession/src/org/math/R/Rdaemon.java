package org.math.R;

import java.io.File;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Map;
import java.util.Properties;
import org.math.R.Logger.Level;
import org.rosuda.REngine.Rserve.RConnection;

/**
 *
 * @author richet
 */
public class Rdaemon {
    
    RserverConf conf;
    Process process;
    Logger log;
    static File APP_DIR = new File(System.getProperty("user.home") + File.separator + ".Rserve");
    public static String R_HOME = null;
    
    static {
        boolean app_dir_ok = false;
        if (!APP_DIR.exists()) {
            app_dir_ok = APP_DIR.mkdir();
        } else {
            app_dir_ok = APP_DIR.isDirectory() && APP_DIR.canWrite();
        }
        if (!app_dir_ok) {
            System.err.println("Cannot write in " + APP_DIR.getAbsolutePath());
        }
    }
    
    public Rdaemon(RserverConf conf, Logger log, String R_HOME) {
        this.conf = conf;
        this.log = log;
        findR_HOME(R_HOME);
        //findRserve_HOME(Rserve_HOME);
        println("Environment variables:\n  " + R_HOME_KEY + "=" + Rdaemon.R_HOME /*+ "\n  " + Rserve_HOME_KEY + "=" + Rdaemon.Rserve_HOME*/, Level.INFO);
        
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
    
    public Rdaemon(RserverConf conf, Logger log) {
        this(conf, log, null);
    }
    public final static String R_HOME_KEY = "R_HOME";
    
    public static boolean findR_HOME(String r_HOME) {
        Map<String, String> env = System.getenv();
        Properties prop = System.getProperties();
        
        R_HOME = r_HOME;
        if (R_HOME == null || !(new File(R_HOME).exists())) {
            if (env.containsKey(R_HOME_KEY)) {
                R_HOME = env.get(R_HOME_KEY);
            }
            
            if (R_HOME == null || prop.containsKey(R_HOME_KEY) || !(new File(R_HOME).exists())) {
                R_HOME = prop.getProperty(R_HOME_KEY);
                
            }
            
            if (R_HOME == null || !(new File(R_HOME).exists())) {
                R_HOME = null;
                if (System.getProperty("os.name").contains("Win")) {
                    for (int major = 20; major >= 0; major--) {
                        //int major = 10;//known to work with R 2.9 only.
                        if (R_HOME == null) {
                            for (int minor = 10; minor >= 0; minor--) {
                                //int minor = 0;
                                r_HOME = "C:\\Program Files\\R\\R-3." + major + "." + minor + "\\";
                                if (new File(r_HOME).exists()) {
                                    R_HOME = r_HOME;
                                    break;
                                }
                            }
                        } else {
                            break;
                        }
                    }
                } else {
                    R_HOME = "/usr/lib/R/";
                }
            }
        }
        
        if (R_HOME == null) {
            return false;
        }
        return new File(R_HOME).exists();
        
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
    System.err.println("OS " + OS_NAME + "/" + OS_ARCH + " not supported for automated RServe finding.");
    }
    
    if (!new File(Rserve_HOME).exists()) {
    System.err.println("Unable to find Rserve in " + Rserve_HOME);
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
    
    public void println(String m, Level l) {
        //System.out.println(m);
        if(log!=null)
        log.println(m, l);
        else System.out.println(l+" "+m);
    }
    
    public void stop() {
        println("stopping R daemon... " + conf, Level.INFO);
        if (!conf.isLocal()) {
            throw new UnsupportedOperationException("Not authorized to stop a remote R daemon: " + conf.toString());
        }
        
        try {
            RConnection s = conf.connect();
            if (s == null || !s.isConnected()) {
                println("R daemon already stoped.", Level.INFO);
                return;
            }
            s.shutdown();
            
        } catch (Exception ex) {
            //ex.printStackTrace();
            println(ex.getMessage(), Level.ERROR);
        }

        /*if (br != null) {
        try {
        br.close();
        } catch (IOException ex) {
        ex.printStackTrace();
        println(ex.getMessage());
        }
        }*/

        //if (pw != null) {
        //    pw.close();
        //}

        //process.destroy();

        println("R daemon stoped.", Level.INFO);
        
        log = null;
    }
    
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
        
        println("checking Rserve is available... ", Level.INFO);
        boolean RserveInstalled = StartRserve.isRserveInstalled(R_HOME + File.separator + "bin" + File.separator + "R" + (System.getProperty("os.name").contains("Win") ? ".exe" : ""));
        if (!RserveInstalled) {
            println("  no", Level.INFO);
            RserveInstalled = StartRserve.installRserve(R_HOME + File.separator + "bin" + File.separator + "R" + (System.getProperty("os.name").contains("Win") ? ".exe" : ""), http_proxy, null);
            if (RserveInstalled) {
                println("  ok", Level.INFO);
            } else {
                println("  failed.", Level.ERROR);
                String notice = "Please install Rserve manually in your R environment using \"install.packages('Rserve')\" command.";
                println(notice, Level.ERROR);
                System.err.println(notice);
                return;
            }
        } else {
            println("  ok", Level.INFO);
        }
        
        println("starting R daemon... " + conf, Level.INFO);
        
        StringBuffer RserveArgs = new StringBuffer("--no-save --slave");
        if (conf.port > 0) {
            RserveArgs.append(" --RS-port " + conf.port);
        }
        
        boolean started = StartRserve.launchRserve(R_HOME + File.separator + "bin" + File.separator + "R" + (System.getProperty("os.name").contains("Win") ? ".exe" : ""), /*Rserve_HOME + "\\\\..", */ "--no-save --slave", RserveArgs.toString(), false);
        
        if (started) {
            println("  ok", Level.INFO);
        } else {
            println("  failed", Level.ERROR);
        }
    }
    
    public static String timeDigest() {
        long time = System.currentTimeMillis();
        SimpleDateFormat sdf = new SimpleDateFormat("yyyyMMdd-HHmmss");
        StringBuffer sb = new StringBuffer();
        sb =
        sdf.format(new Date(time), sb, new java.text.FieldPosition(0));
        return sb.toString();
    }
    
    public static void main(String[] args) throws InterruptedException {
        /*final ProcessBuilder builder = new ProcessBuilder("/usr/lib/R/bin/Rserve", "--vanilla");
        builder.environment().put("R_HOME", "/usr/lib/R");
        builder.redirectErrorStream(true);
        final Process process = builder.start();
        BufferedReader br = null;
        try {
        final InputStream is = process.getInputStream();
        final InputStreamReader isr = new InputStreamReader(is);
        br = new BufferedReader(isr);
        String line;
        while ((line = br.readLine()) != null) {
        //if (LOG.isDebugEnabled()) {
        //    LOG.debug(host + ":" + port + "> " + line);
        //}
        System.out.println(line);
        }
        
        process.waitFor();
        //                    if (LOG.isInfoEnabled()) {
        //                        LOG.info("Rserve on " + host + ":" + port + " terminated");
        //                    }
        } catch (InterruptedException e) {
        System.out.println("Interrupted!");
        //                    LOG.error("Interrupted!", e);
        process.destroy();
        Thread.currentThread().interrupt();
        } finally {
        br.close();
        //IOUtils.closeQuietly(br);
        }
        
        Thread.sleep(1000);
        process.destroy();
        
        
        final int exitValue = process.exitValue();
        System.out.println("exitValue=" + exitValue);
        //                if (LOG.isInfoEnabled()) {
        //                    LOG.info("Rserve on " + host + ":" + port + " returned " + exitValue);
        //                }
        //                return exitValue;
         */
        //System.setProperty("Rserve.home","/usr/lib/R/bin/");
        Rdaemon d = new Rdaemon(new RserverConf(null, -1, null, null, null), new Logger() {
            
            public void println(String message, Level l) {
                if (l == Level.INFO) {
                    System.out.println(message);
                } else {
                    System.err.println(message);
                }
            }

            public void close() {
            }
            
        });
        d.start(null);
        Thread.sleep(2000);
        d.stop();
        Thread.sleep(2000);
    }
}
