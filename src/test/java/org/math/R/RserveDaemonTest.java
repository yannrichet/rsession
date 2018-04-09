package org.math.R;

import java.io.File;
import java.io.FileFilter;
import java.io.FilenameFilter;
import java.io.IOException;
import java.util.Properties;
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
        assert classloader.getResource("org/math/R/Rserve_1.7-3.zip") != null : "cannot access resources...";

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

    @Before
    public void setUp() {
        http_proxy_env = System.getenv("http_proxy");
        Properties prop = new Properties();
        if (http_proxy_env != null) {
            prop.setProperty("http_proxy", http_proxy_env);
        }

        File[] rout = new File(".").listFiles(
                new FilenameFilter() {

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
