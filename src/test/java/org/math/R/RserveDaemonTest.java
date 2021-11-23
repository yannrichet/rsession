package org.math.R;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileFilter;
import java.io.FilenameFilter;
import java.io.IOException;
import java.io.InputStreamReader;
import java.net.ServerSocket;
import java.util.Properties;
import java.util.logging.Level;
import java.util.logging.Logger;
import org.codehaus.plexus.util.FileUtils;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import static org.math.R.StartRserve.doInR;

/**
 *
 * @author richet
 */
public class RserveDaemonTest {

    String http_proxy_env;
    String Rcmd;

    public static void main(String args[]) {
        org.junit.runner.JUnitCore.main(RserveDaemonTest.class.getName());
    }

    @Test
    public void testRExecWindows() throws Exception {
        System.err.println("====================================== testRExecWindows");
        if (RserveDaemon.isWindows()) {
            String command = "C:\\R\\bin\\R.exe"; // matches GHA standard install
            if (new File(command).isFile()) {
                String arg="--version";
                ProcessBuilder pb = new ProcessBuilder("cmd.exe", "/C", command+" "+arg) ;
                pb.redirectErrorStream(true);
                System.err.println("pb: "+pb);
                
                Process p = pb.start();
                BufferedReader input = new BufferedReader(new InputStreamReader(p.getInputStream()));

                String line;
                StringBuffer ret = new StringBuffer();
                while ((line = input.readLine()) != null)  {
                    ret.append(line.trim()+"\n");
                }      
                System.err.println("ret: "+ret);
 
                assert ret.toString().contains("R version") : "Wrong R message: "+ret.toString();
        } else 
            System.err.println("Cannot use R command: "+command+" , so skipping test");
        }
    }

    @Test
    public void testWindowsCall() throws Exception {
        System.err.println("====================================== testSystemCall");
        if (RserveDaemon.isWindows()) {
            String command = "C:\\R\\bin\\R.exe"; // matches GHA standard install
            if (new File(command).isFile()) {
                File redirect = File.createTempFile("test", "systemCall");
                if (new File(command).isFile()) {
                    assert StartRserve.system("C:\\R\\bin\\R.exe -e ls()", redirect, true).exitValue()==0;
                }
            }
        }
    }

    @Test
    public void testDoInR() throws Exception {
        System.err.println("====================================== testDoInR");

        if (RserveDaemon.R_HOME == null || Rcmd == null) {
            testFindR_HOME();
        }

        String expr = ".libPaths(); 1+1==2";
        String result = doInR(expr, Rcmd, "--vanilla -q", null);

        assert result.contains("TRUE") : "Failed to eval " + expr + ": " + result;
    }

    // Replaced by custom install instead... 
    @Test
    public void testInstallRserve() throws Exception {
        if (RserveDaemon.R_HOME == null || Rcmd == null) {
            testFindR_HOME();
        }

        if (StartRserve.isRserveInstalled()) {
            System.err.println("Rserve is already installed. Removing...");
            String res = doInR("remove.packages('Rserve')", Rcmd, "--vanilla -q", null);
            assert res.contains(" package ") : "Could not remove package Rserve...";
        } else {
            System.err.println("Rserve is not installed.");
        }

        boolean install = StartRserve.installRserve(Rcmd, http_proxy_env, Rsession.DEFAULT_REPOS);

        File[] rout = new File(".").listFiles(
                new FilenameFilter() {

            @Override
            public boolean accept(File dir, String name) {
                return name.endsWith(".Rout");
            }
        });
        for (File f : rout) {
            try {
                System.err.println(f + ":\n" + org.apache.commons.io.FileUtils.readFileToString(f));
            } catch (IOException ex) {
                System.err.println(f + ": " + ex.getMessage());
            }
        }

        assert install : "Could not install Rserve";

        assert StartRserve.isRserveInstalled() : "Could not find package Rserve";
    }

    @Test
    public void testInstallCustomRserve() throws Exception {
        System.err.println("====================================== testInstallCustomRserve");

        if (RserveDaemon.R_HOME == null || Rcmd == null) {
            testFindR_HOME();
        }

        if (StartRserve.isRserveInstalled()) {
            System.err.println("Rserve is already installed. Removing...");
            FileUtils.forceDelete(new File(RserveDaemon.app_dir(), "Rserve"));
//            Process p = doInR("remove.packages('Rserve',lib='" + RserveDaemon.app_dir() + "');q(save='no')", Rcmd, "--vanilla --silent", null);
//            if (!RserveDaemon.isWindows()) /* on Windows the process will never return, so we cannot wait */ {
//                p.waitFor();
//                assert p.exitValue() == 0 : "Could not remove package Rserve...";
//            }

            int n = 10;
            while (StartRserve.isRserveInstalled() && (n--) > 0) {
                Thread.sleep(2000);
            }
            assert n > 1 : "Package Rserve was not removed !";
        } else {
            System.err.println("Rserve is not yet installed. Proceed...");
        }

        ClassLoader classloader = Thread.currentThread().getContextClassLoader();
        assert classloader.getResource("org/math/R/Rsession.class") != null : "cannot access class resources...";
        assert classloader.getResource("org/math/R/Rserve_1.7-5.tar.gz") != null : "cannot access Rserve local source...";

        boolean install = StartRserve.installBundledRserve(Rcmd);

        File[] rout = new File(".").listFiles(
                new FilenameFilter() {

            @Override
            public boolean accept(File dir, String name) {
                return name.endsWith(".Rout");
            }
        });
        for (File f : rout) {
            try {
                System.err.println(f + ":\n" + org.apache.commons.io.FileUtils.readFileToString(f));
            } catch (IOException ex) {
                System.err.println(f + ": " + ex.getMessage());
            }
        }

        assert install : "Could not install Rserve";

        assert StartRserve.isRserveInstalled() : "Could not find package Rserve";
    }

    @Test
    public void testInstallRserveFromLocalLibrary() throws Exception {
        System.err.println("====================================== testInstallRserveFromLocalLibrary");

        if (RserveDaemon.R_HOME == null || Rcmd == null) {
            testFindR_HOME();
        }

        if (StartRserve.isRserveInstalled()) {
            System.err.println("Rserve is already installed. Removing...");
            FileUtils.forceDelete(new File(RserveDaemon.app_dir(), "Rserve"));
//            Process p = doInR("remove.packages('Rserve',lib='" + RserveDaemon.app_dir() + "');q(save='no')", Rcmd, "--vanilla --silent", null);
//            if (!RserveDaemon.isWindows()) /* on Windows the process will never return, so we cannot wait */ {
//                p.waitFor();
//                assert p.exitValue() == 0 : "Could not remove package Rserve...";
//            }

            int n = 10;
            while (StartRserve.isRserveInstalled() && (n--) > 0) {
                Thread.sleep(2000);
            }
            assert n > 1 : "Package Rserve was not removed !";
        } else {
            System.err.println("Rserve is not yet installed. Proceed...");
        }

        try{
            String result = doInR("install.packages('Rserve',repos='" + Rsession.DEFAULT_REPOS + "')", Rcmd, "--vanilla --silent", null);
            assert result.contains("package 'Rserve' successfully unpacked and MD5 sums checked") || result.contains("* DONE (Rserve)") : "  FAILED to install Rserve: \n" + result.replaceAll("\n", "\n  | ");
        } catch (IOException ioe) {
            Log.Err.print("Rserve NOT well installed: "+ioe.getMessage());
            assert false;
        }            
                
        boolean install = StartRserve.installRserveFromLocalLibrary(Rcmd);

        assert install : "Could not install Rserve";

        assert StartRserve.isRserveInstalled() : "Could not find package Rserve";
    }

    @Test
    public void testFindR_HOME() {
        System.err.println("====================================== testFindR_HOME");

        assert RserveDaemon.findR_HOME(null) : "Could not find R directory";
        assert RserveDaemon.R_HOME != null : "Error finding R dir";
        assert new File(RserveDaemon.R_HOME).isDirectory() : "Error finding R dir";
        assert new File(RserveDaemon.R_HOME).listFiles(new FileFilter() {

            @Override
            public boolean accept(File pathname) {
                return pathname.isDirectory() && pathname.getName().equals("bin");
            }
        }).length == 1 : "Bad R dir";
        Rcmd = RserveDaemon.R_HOME + File.separator + "bin" + File.separator + "R" + (RserveDaemon.isWindows() ? ".exe" : "");
        System.err.println("Rcmd: " + Rcmd);
    }

    @Test
    public void testParsePrintConf() {
        System.err.println("====================================== testParsePrintConf");

        RserverConf c = new RserverConf("localhost", 3600, "me", "whatever");
        System.err.println(c.toString());
        System.err.println(RserverConf.parse(c.toString()));
    }

    @Test
    public void testStartStopRserve() throws Exception {
        System.err.println("====================================== testStartStopRserve");

        System.err.println("--- Get PREVIOUS Rserve PID");
        int[] pids = StartRserve.getRservePIDs();
        int last_pid = pids.length > 0 ? pids[pids.length - 1] : -1;
        System.err.println("---  " + last_pid);

        System.err.println("--- Start Rserve daemon");
        RserverConf conf = new RserverConf(RserverConf.DEFAULT_RSERVE_HOST, RserverConf.DEFAULT_RSERVE_PORT, null, null);
        RserveDaemon d = new RserveDaemon(conf, new RLogPrintStream(System.out));
        d.start();
        Thread.sleep(1000);

        System.err.println("--- Get THIS Rserve PID");
        pids = StartRserve.getRservePIDs();
        int pid = pids.length > 0 ? pids[pids.length - 1] : -666;
        System.err.println("---  " + pid);

        assert pid != last_pid : "Did not start Rserve (no new PID)";

        System.err.println("--- Create Rsession");
        RserveSession s = new RserveSession(System.out, null, conf);
        assert s.asDouble(s.eval("1+1")) == 2 : "Failed basic computation in Rserve";
        System.err.println("--- Destroy Rsession");
        s.end();

        System.err.println("--- Stop Rserve");
        d.stop();
        Thread.sleep(1000);

        assert !haveRservePID(d.rserve.pid) : "Still alive !";

        //System.err.println("--- Get REMAINING Rserve PID");
        //int other_pid = StartRserve.getLastRservePID();
        //System.err.println("---  " + other_pid);
        //assert other_pid != pid : "Did not kill Rserve (still live PID)";
    }

    @Test
    public void testStartStop10Rserves() throws Exception {
        System.err.println("====================================== testStartStop10Rserves");

        final Thread[] tests = new Thread[10];
        final RserveDaemon[] daemons = new RserveDaemon[tests.length];
        final RserverConf[] confs = new RserverConf[tests.length];

        for (int ii = 0; ii < tests.length; ii++) {
            final int i = ii;
            tests[i] = new Thread(new Runnable() {
                @Override
                public void run() {
                    try {
                        System.err.println("--- Start Rserve daemon " + i);
                        confs[i] = new RserverConf(RserverConf.DEFAULT_RSERVE_HOST, -1, null, null);// -1 will let Rdaemon find a free port to host Rserve
                        daemons[i] = new RserveDaemon(confs[i], new RLogPrintStream(System.out));
                        daemons[i].start();
                        System.err.println("--- Rserve daemon " + i+" Started");
                        confs[i] = daemons[i].conf;
                    } catch (Exception e) {
                        assert false : e.getMessage();
                    }
                }
            });
            tests[i].start();
            Thread.sleep(100);
        }

        for (int i = 0; i < tests.length; i++) {
            System.err.println("--- Wait Rserve daemon " + i);
            tests[i].join();
        }

        for (int i = 0; i < tests.length; i++) {
            //System.err.println("--- Get THIS Rserve PID");
            //int pid = StartRserve.getLastRservePID();
            //System.err.println("---  " + pid);
            //assert pid != last_pid : "Did not start Rserve (no new PID)";
            System.err.println("--- Create Rsession (" + confs[i] + ")");
            RserveSession s = new RserveSession(System.out, null, confs[i]);
            assert s.asDouble(s.eval("1+" + i)) == 1 + i : "Failed basic computation in Rserve";
            System.err.println("--- Destroy Rsession");
            s.end();

            System.err.println("--- Stop Rserve");
            daemons[i].stop();
            //Thread.sleep(5000);

            assert !haveRservePID(daemons[i].rserve.pid) : "Still alive !";

            //System.err.println("--- Get REMAINING Rserve PID");
            //int other_pid = StartRserve.getLastRservePID();
            //System.err.println("---  " + other_pid);
            //assert other_pid != pid : "Did not kill Rserve (still live PID)";      
        }

    }

    @Test
    public void testLockPort() throws InterruptedException {
        System.err.println("====================================== testLockPort");

        final int port = 5555;
        final Thread[] tests = new Thread[10];
        final ServerSocket[] locks = new ServerSocket[tests.length];

        for (int ii = 0; ii < tests.length; ii++) {
            final int i = ii;
            tests[i] = new Thread(new Runnable() {
                @Override
                public void run() {
                    try {
                        System.err.println("--- Lock port #" + i);
                        locks[i] = StartRserve.lockPort(port);
                    } catch (Exception e) {
                        assert false : e.getMessage();
                    }
                }
            });
            tests[i].start();
//            Thread.sleep(100);
        }

        for (int i = 0; i < tests.length; i++) {
            tests[i].join();
        }

        ServerSocket locked = null;
        for (int i = 0; i < tests.length; i++) {
            if (locks[i] != null) {
                if (locked != null) {
                    try {
                        locked.close();
                    } catch (IOException ex) {
                        assert false : "Failed to close!";
                    }
                    try {
                        locks[i].close();
                    } catch (IOException ex) {
                        assert false : "Failed to close!";
                    }
                    assert false : "Already locked !";
                } else {
                    locked = locks[i];
                }
            }
        }
        assert locked != null : "Did not lock any port!";

        try {
            locked.close();
        } catch (IOException ex) {
            assert false : "Already locked !";
        }
    }

    @Test
    public void testLockPortAgainstRserve() throws InterruptedException {
        System.err.println("====================================== testLockPortAgainstRserve");

        final int port = 7654;

        System.err.println("--- Lock port " + port);
        ServerSocket lock = StartRserve.lockPort(port);

        assert lock != null : "Could not lock port " + port;

        System.err.println("--- Check port is locked");
        assert StartRserve.lockPort(port) == null : "Port was not locked";

        RserverConf conf = new RserverConf(RserverConf.DEFAULT_RSERVE_HOST, port, null, null);
        RserveDaemon d = null;
        try {
            d = new RserveDaemon(conf, new RLogPrintStream(System.out));
        } catch (Exception ex) {
            assert false : ex.getMessage();
        }
        assert d != null : "No daemon";
        try {
            System.err.println("--- Try start Rserve (should fail)");
            d.start();
        } catch (Exception e) {
            assert true : "Did not reject Rserve on already locked port";
        }

        try {
            System.err.println("--- Unlock port " + port);
            lock.close();
        } catch (IOException ex) {
            assert false : "Could not close lock:" + ex.getMessage();
        }

        Thread.sleep(3000); // let time to effectively close port by system...

        try {
            System.err.println("--- Try start Rserve (should work now)");
            d.start();
        } catch (Exception e) {
            assert false : "Did not accept Rserve on unlocked port: " + e.getMessage();
        }

        d.stop();
    }

    @Test
    public void testfailRserveSamePort() throws InterruptedException {
        System.err.println("====================================== testfailRserveSamePort");

        final int port = 8888;

        System.err.println("--- Check port is not already locked");
        ServerSocket lock = StartRserve.lockPort(port);
        assert lock != null : "Port was already locked";
        try {
            lock.close();
        } catch (IOException ex) {
            assert false : "Cannot close port";
        }

        RserverConf conf = new RserverConf(RserverConf.DEFAULT_RSERVE_HOST, port, null, null);
        RserveDaemon d_ok = null;
        try {
            d_ok = new RserveDaemon(conf, new RLogPrintStream(System.out));
        } catch (Exception ex) {
            assert false : ex.getMessage();
        }
        assert d_ok != null : "No daemon";
        try {
            System.err.println("--- Try start Rserve (should work)");
            d_ok.start();
        } catch (Exception e) {
            assert false : "Did not accept Rserve on unlocked port: " + e.getMessage();
        }

        boolean failed = false;
        RserveDaemon d_fail = null;
        try {
            d_fail = new RserveDaemon(conf, new RLogPrintStream(System.out));
        } catch (Exception ex) {
            assert false : ex.getMessage();
        }
        assert d_fail != null : "No daemon";
        try {
            System.err.println("--- Try start Rserve (should NOT work)");
            d_fail.start();
        } catch (Exception e) {
            failed = true;
            e.printStackTrace();
        }
        if (!failed) {
            assert false : "Did not reject Rserve on already locked port";
        }

        d_ok.stop();
        //d_fail.stop();
    }

    public static boolean haveRservePID(int Rp) {
        int[] pids = StartRserve.getRservePIDs();
        for (int i = 0; i < pids.length; i++) {
            if (pids[i] == Rp) {
                return true;
            }
        }
        return false;
    }

    @Before
    public void setUp() {
        http_proxy_env = System.getenv("http_proxy");
        Properties prop = new Properties();
        if (http_proxy_env != null) {
            prop.setProperty("http_proxy", http_proxy_env);
        }

        File[] rout = new File(".").listFiles(new FilenameFilter() {

            @Override
            public boolean accept(File dir, String name) {
                return name.endsWith(".Rout");
            }
        });
        for (File f : rout) {
            System.err.println("delete " + f + ": " + f.delete());

        }
    }

    @After
    public void tearDown() {

    }
}
