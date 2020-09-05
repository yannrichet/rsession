package org.math.R;

import java.io.File;
import java.io.FileFilter;
import java.io.FilenameFilter;
import java.io.IOException;
import java.net.ServerSocket;
import java.util.Properties;
import java.util.logging.Level;
import java.util.logging.Logger;
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
    public void testDoInR() throws Exception {
        if (RserveDaemon.R_HOME == null || Rcmd == null) {
            testFindR_HOME();
        }

        String expr = ".libPaths(); 1+1==2";
        Process p = doInR(expr, Rcmd, "--vanilla -q", false);
        assert p != null : "Cannot ceate R process";

        try {
            StringBuffer result = new StringBuffer();
            // we need to fetch the output - some platforms will die if you don't ...
            StreamHog error = new StreamHog(p.getErrorStream(), true);
            StreamHog output = new StreamHog(p.getInputStream(), true);
            error.join();
            output.join();

            if (!RserveDaemon.isWindows()) /* on Windows the process will never return, so we cannot wait */ {
                p.waitFor();
            }
            result.append(output.getOutput());
            result.append(error.getOutput());

            System.err.println("results \n" + result);

            assert result.toString().contains("TRUE") : "Failed to eval " + expr + ": " + result;
        } catch (InterruptedException e) {
            assert false : e;
        }
    }

    // Replaced by local install instead... @Test
    public void testInstallRserve() throws Exception {
        if (RserveDaemon.R_HOME == null || Rcmd == null) {
            testFindR_HOME();
        }

        if (StartRserve.isRserveInstalled(Rcmd)) {
            System.err.println("Rserve is already installed. Removing...");
            Process p = doInR("remove.packages('Rserve')", Rcmd, "--vanilla -q", false);
            if (!RserveDaemon.isWindows()) /* on Windows the process will never return, so we cannot wait */ {
                p.waitFor();
            }
            assert p.exitValue() == 0 : "Could not remove package Rserve...";
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

        assert StartRserve.isRserveInstalled(Rcmd) : "Could not find package Rserve";
    }

    @Test
    public void testInstallRserveLocal() throws Exception {
        if (RserveDaemon.R_HOME == null || Rcmd == null) {
            testFindR_HOME();
        }

        if (StartRserve.isRserveInstalled(Rcmd)) {
            System.err.println("Rserve is already installed. Removing...");
            Process p = doInR("remove.packages('Rserve',lib='" + RserveDaemon.app_dir() + "');q(save='no')", Rcmd, "--vanilla --silent", false);
            if (!RserveDaemon.isWindows()) /* on Windows the process will never return, so we cannot wait */ {
                p.waitFor();
                assert p.exitValue() == 0 : "Could not remove package Rserve...";
            }

            int n = 10;
            while (StartRserve.isRserveInstalled(Rcmd) && (n--) > 0) {
                Thread.sleep(2000);
            }
            assert n > 1 : "Package Rserve was not removed !";
        } else {
            System.err.println("Rserve is not installed. Continue.");
        }

        ClassLoader classloader = Thread.currentThread().getContextClassLoader();
        assert classloader.getResource("org/math/R/Rsession.class") != null : "cannot access class resources...";
        assert classloader.getResource("org/math/R/Rserve_1.7-5.zip") != null : "cannot access resources...";

        boolean install = StartRserve.installRserve(Rcmd);

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

        assert StartRserve.isRserveInstalled(Rcmd) : "Could not find package Rserve";
    }

    /*boolean isRserveInstalled() {
        Process p = doInR("for (i in 1:length(.libPaths())) print(any(list.files(.libPaths()[i])=='Rserve'))", Rcmd, "--vanilla -q", false);
        boolean isWindows = System.getProperty("os.name") != null && System.getProperty("os.name").length() >= 7 && System.getProperty("os.name").substring(0, 7).equals("Windows");
        if (!isWindows) {
            p.waitFor();
        }
        assert p.exitValue() == 0 : "Could not find package Rserve...";
    }*/
    @Test
    public void testFindR_HOME() {
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
        RserverConf c = new RserverConf("localhost", 3600, "me", "whatever");
        System.err.println(c.toString());
        System.err.println(RserverConf.parse(c.toString()));
    }

    @Test
    public void testStartStopRserve() throws Exception {
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
        final int port = 6666;
        final Thread[] tests = new Thread[10];
        final ServerSocket[] locks = new ServerSocket[tests.length];

        for (int ii = 0; ii < tests.length; ii++) {
            final int i = ii;
            tests[i] = new Thread(new Runnable() {
                @Override
                public void run() {
                    try {
                        System.err.println("--- Lock port #" + i);
                        locks[i] = StartRserve.getPort(port);
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

        boolean locked = false;
        for (int i = 0; i < tests.length; i++) {
            if (locks[i] != null) {
                if (locked) {
                    assert false : "Already locked !";
                } else {
                    locked = true;
                }
            }
        }
        assert locked : "Did not lock any port!";
    }

    @Test
    public void testLockPortAgainstRserve() throws InterruptedException {
        final int port = 6666;

        System.err.println("--- Lock port " + port);
        ServerSocket lock = StartRserve.getPort(port);

        System.err.println("--- Check port is locked");
        assert StartRserve.getPort(port) == null : "Port was not locked";

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
            assert true : "Did not reject Rserrve on already locked port";
        }

        try {
            System.err.println("--- Unlock port " + port);
            lock.close();
        } catch (IOException ex) {
            assert false : "Could not close lock:" + ex.getMessage();
        }

        try {
            System.err.println("--- Try start Rserve (should work now)");
            d.start();
        } catch (Exception e) {
            assert false : "Did not accept Rserve on unlocked port";
        }

        d.stop();
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
