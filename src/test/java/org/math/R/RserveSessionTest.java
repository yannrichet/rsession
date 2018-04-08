package org.math.R;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.io.OutputStreamWriter;
import java.io.PrintStream;
import java.io.Reader;
import java.io.Writer;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;
import java.util.Properties;
import org.apache.commons.io.FileUtils;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.math.R.RLog.Level;

import org.rosuda.REngine.REXP;
import org.rosuda.REngine.REXPMismatchException;
import org.rosuda.REngine.Rserve.RserveException;

/**
 *
 * @author richet
 */
public class RserveSessionTest {

    PrintStream p = System.err;
    //RserverConf conf;
    RserveSession s;
    int rand = Math.round((float) Math.random() * 10000);
    File tmpdir = new File(System.getProperty("java.io.tmpdir"), "RserveTest" + rand);

    public static void main(String args[]) {
        org.junit.runner.JUnitCore.main(RserveSessionTest.class.getName());
    }

    @Test
    public void testExceed128Connections() throws Exception {
        for (int i = 0; i < 129; i++) {
            assert (boolean) s.eval("is.function(png)") : "Failed to call is.function";
        }
    }

    @Test
    public void testError() throws Exception {
        boolean error = false;
        try {
            s.eval("stop('!!!')");
        } catch (Exception e) {
            e.printStackTrace();
            error = true;
        }
        assert error : "Error not detected";
    }

    @Test
    public void testPrintIn() throws Exception {
        String str = s.eval("print('*')").toString();
        assert str.equals("*") : "Bad print: " + str;
    }

    @Test
    public void testInstallPackage() throws Exception {
        File dir = new File(tmpdir, "tmp/pso".replace("/", File.separator));
        if (dir.exists()) {
            FileUtils.deleteDirectory(dir);
            assert !dir.exists() : "Cannot delete " + dir;
        }

        s.eval(".libPaths('" + tmpdir.getAbsolutePath().replace("\\", "/") + "/tmp')");
        String ret = s.installPackage("pso", true);
        assert ret.equals(Rsession.PACKAGELOADED) : "Failed to install pso: " + ret;
        assert dir.exists() : "Package pso not well installed";
        if (dir.exists()) {
            FileUtils.deleteDirectory(dir);
        }
    }

    @Test
    public void testInstallPackages() {
        String out = s.installPackage("sensitivity", true);
        assert out.equals(Rsession.PACKAGELOADED) : "Failed to load package sensitivity: " + out;
        String out2 = s.installPackage("pso", true);
        assert out2.equals(Rsession.PACKAGELOADED) : "Failed to load package pso: " + out2;
    }

    @Test
    public void testObject() throws Exception {
        Object l = s.eval("list(x=3)");
        System.err.println("l: " + l);
        System.err.println("R: " + s.getLastOutput());
        System.err.println("R! " + s.getLastError());

        assert s.asStrings(s.eval("ls()")).length == 0 : "Not empty environment: " + Arrays.asList(s.asStrings(s.eval("ls()")));

        assert l != null : "Cannot eval l:" + s.getLastError();
        assert (l instanceof Map) : "Not a Map";
    }

    // @Test
    public void testObjectFun() throws Exception {
        s.voidEval("f <- function(x) x");
        System.err.println("R: " + s.getLastOutput());
        System.err.println("R! " + s.getLastError());

        assert s.asStrings(s.eval("ls()")).length == 1 : "Wrong environment: " + Arrays.asList(s.asStrings(s.eval("ls()")));

        assert s.rawEval("f") != null : "Cannot get function object";
    }

    // @Test
    public void testFun() throws Exception {
        Object fun = s.eval("function(x) {return(x)}");
        System.err.println("fun: " + fun);
        System.err.println("R: " + s.getLastOutput());
        System.err.println("R! " + s.getLastError());

        assert s.asStrings(s.eval("ls()")).length == 1 : "Not empty environment: " + Arrays.asList(s.asStrings(s.eval("ls()")));

        assert fun != null : "Cannot eval fun:" + s.getLastError();
        assert (fun instanceof Rsession.Function) : "Not a Function";
        assert (double) (((Rsession.Function) fun).evaluate(1.0)) == 1.0 : "Bad function behavior: 1.0 != " + (double) (((Rsession.Function) fun).evaluate(1.0));
    }

    // @Test
    public void testEnvir() throws Exception {
        Object e = s.eval("new.env()");
        System.err.println("e: " + e);
        System.err.println("R: " + s.getLastOutput());
        System.err.println("R! " + s.getLastError());

        assert s.asStrings(s.eval("ls()")).length == 0 : "Not empty global environment: " + Arrays.asList(s.asStrings(s.eval("ls()")));

        assert e != null : "Cannot eval e:" + s.getLastError();
    }

    @Test
    public void testEnd() {
        for (int i = 0; i < 10; i++) {
            new Thread(new Runnable() {

                public void run() {
                    RserveSession s1 = RserveSession.newLocalInstance(new RLogPanel(), null);
                    try {
                        System.err.println(s1.eval("runif(1)"));
                    } catch (Exception ex) {
                        ex.printStackTrace();
                    }

                    try {
                        Thread.sleep(2000);
                    } catch (InterruptedException ex) {
                    }
                    s1.end();
                    s1 = null;
                }
            }).start();
        }
        try {
            Thread.sleep(50000);
        } catch (InterruptedException ex) {
        }
    }

    @Test
    public void testNodename() throws REXPMismatchException {
        REXP rexp = (REXP) s.rawEval("Sys.info()[['nodename']]");

        assert rexp != null : "Cannot get nodename";
        assert rexp.asString().length() > 0 : "Cannot read nodename";

    }

    String f = "f <- function() {cat('cat');warning('warning');message('message');return(0)}";

    @Test
    public void testErrorNOSink() throws Exception {
        s.voidEval(f);

        s.SINK_OUTPUT = false;

        REXP maxsin = (REXP) s.rawEval("f()");

        assert maxsin != null : "Null eval";// will fail here with SIGPIPE

        REXP test = (REXP) s.rawEval("1+pi");
        assert s.asDouble(test) > 4 : "Failed next eval";
        s.SINK_OUTPUT = true;
    }

    // @Test
    public void testExplicitSink() throws Exception {
        s.SINK_OUTPUT = false;
        s.SINK_MESSAGE = false;

        s.voidEval(f);

        // without sink: SIGPIPE error
        if (new File(tmpdir, "output.txt").exists()) {
            assert new File(tmpdir, "output.txt").delete() : "Cannot delete output.txt";
        }

        if (new File(tmpdir, "message.txt").exists()) {
            assert new File(tmpdir, "message.txt").delete() : "Cannot delete message.txt";
        }

        s.voidEval("sink('" + tmpdir.getAbsolutePath() + "/output.txt',type='output')");
        s.voidEval("sink('" + tmpdir.getAbsolutePath() + "/message.txt',type='message')");
        REXP maxsin = (REXP) s.rawEval("f()");
        assert Arrays.asList((s.asStrings(s.rawEval("readLines('" + tmpdir.getAbsolutePath() + "/output.txt')")))).size() > 0 : "Empty output sinked";
        //still not working... assert Arrays.asList((s.asStrings(s.rawEval("readLines('" + tmpdir.getAbsolutePath() + "/message.txt')")))).size() > 0 : "Empty message sinked";
        s.voidEval("sink(type='output')");
        s.voidEval("sink(type='message')");

        assert maxsin != null : s.getLastLogEntry() + " - " + s.getLastError() + " - " + s.getLastOutput();
        assert s.asDouble(maxsin) == 0 : "Wrong eval";

        REXP test = (REXP) s.rawEval("1+pi");
        assert s.asDouble(test) > 4 : "Failed next eval";
        s.SINK_OUTPUT = true;
    }

    @Test
    public void testDefaultSink() throws Exception {
        s.voidEval(f);

        // without sink: SIGPIPE error
        REXP maxsin = (REXP) s.rawEval("f()");

        assert maxsin != null : s.getLastLogEntry();
        assert s.asDouble(maxsin) == 0 : "Wrong eval";
        assert s.getLastOutput().equals("cat") : "Wrong LastOutput: " + s.getLastOutput();
        assert s.getLastError().equals("message") : "Wrong LastError: " + s.getLastError();
        assert s.getLastLogEntry().contains("0.0") : "Wrong LastLogEntry: " + s.getLastLogEntry();

        REXP test = (REXP) s.rawEval("1+pi");
        assert test.asDouble() > 4 : "Failed next eval";
    }

    @Test
    public void testFileSize() throws Exception {
        for (int i = 1; i < 5; i++) {
            int size = (int) Math.pow(10.0, (double) (i + 1));
            System.err.println("Size " + size);
            s.rawEval("raw" + i + "<-rnorm(" + (size / 8) + ")");
            File sfile = new File("tmp", size + ".Rdata");
            s.save(sfile.getAbsoluteFile(), "raw" + i);
            assert sfile.exists() : "Size " + size + " failed: " + sfile.getAbsolutePath() + " size " + (sfile.length());
            sfile.delete();
        }
    }

    @Test
    public void testJPEGSize() throws Exception {
        s.rawEval("library(MASS)");
        for (int i = 1; i < 20; i++) {
            int size = i * 80;
            File sfile = new File("tmp", size + ".jpg");
            s.toJPEG(sfile, 600, 600, "plot(rnorm(" + (size / 8) + "))");
            assert sfile.exists() : "Size " + size + " failed";
            p.println(sfile.length());
        }
    }

    @Test
    public void testPrint() throws Exception {
        //cast
        String[] exp = {"TRUE", "0.123", "pi", /*"0.123+a",*/ "0.123", "(0.123)+pi", "rnorm(10)", "cbind(rnorm(10),rnorm(10))", "data.frame(aa=rnorm(10),bb=rnorm(10))", "'abcd'", "c('abcd','sdfds')"};
        for (String string : exp) {
            p.println(string + " --> " + s.eval(string));
        }
    }

    @Test
    public void testEval() throws Exception {

        double a = -0.123;
        s.set("a", a);
        s.set("b", -1.23);
        double[] A = new double[]{0, 1, 2, 3};
        s.set("A", A);

        HashMap<String, Object> vars = new HashMap<String, Object>();
        vars.put("a", 1.23);
        vars.put("A", new double[]{10, 11, 12, 13});

        String[] exp = {"TRUE", "0.123", "pi", "a", "A", "0.123+a", "0.123+b", "0.123", "(0.123)+pi", "rnorm(10)", "cbind(rnorm(10),rnorm(10))", "data.frame(aa=rnorm(10),bb=rnorm(10))", "'abcd'", "c('abcd','sdfds')"};
        for (String e : exp) {
            System.out.println(e + " --> " + s.proxyEval(e, vars));
        }
        assert Arrays.asList(s.ls()).contains("a") : "variable a disappeared";
        assert (Double) s.proxyEval("a", null) == a : "variable a changed";
        assert Arrays.equals((double[]) s.proxyEval("A", null), A) : "variable A changed";
    }

    @Test
    public void testNullEval() throws Exception {

        double a = -0.123;
        s.set("a", a);
        assert s.asDouble(s.rawEval("1+a")) == 1 + a : "error evaluating 1+a";
        s.set("a", Double.NaN);
        double res = s.asDouble(s.rawEval("1+a"));
        assert Double.isNaN(res) : "error evaluating 1+a: " + res;
        s.set("a", null);
        try {
            res = s.asDouble(s.rawEval("1+a"));
            throw new Exception("error evaluating 1+a: " + res);
        } catch (Exception e) {
            //Exception well raised, everything is ok.
        }

    }

    @Test
    public void testEvalError() throws Exception {
        String[] exprs = {"a <- 1.0.0", "f <- function(x){((}"};
        for (String expr : exprs) {
            System.err.println("trying expression " + expr);
            try {
                boolean done = s.voidEval(expr);
                assert !done : "error not found in " + expr;
            } catch (Exception e) {
                System.err.println("Well detected error in " + expr);
                //Exception well raised, everything is ok.
            }
        }

        String[] evals = {"(xsgsdfgd", "1.0.0"};
        for (String eval : evals) {
            System.err.println("trying evaluation " + eval);
            try {
                REXP e = (REXP) s.rawEval(eval);
                assert e == null : "error not found in " + eval;
            } catch (Exception e) {
                System.err.println("Well detected error in " + eval);
                //Exception well raised, everything is ok.
            }
        }
    }

    @Test
    public void testLibrary() {
        s.rawEval("library(lhs)");
        // this next call was failing with rserve 0.6-0
        s.rawEval("library(rgenoud)");
    }

    @Test
    public void testRFileIO() throws Exception {
        //get file test...
        String remoteFile1 = "get" + rand + ".csv";
        File localfile1 = new File(tmpdir.getParent(), remoteFile1);
        System.out.println("GET :" + localfile1.getAbsolutePath());
        s.voidEval("aa<-data.frame(A=c(1,2,3),B=c(4,5,6))");
        s.voidEval("write.csv(file='" + remoteFile1 + "',aa)");
        InputStream is1 = null;
        OutputStream os1 = null;
        try {
            //System.out.println("openFile");
            is1 = s.R.openFile(remoteFile1);
            //System.out.println("OK");
            os1 = new FileOutputStream(localfile1.getAbsolutePath());
            byte[] buf = new byte[65536];
            try {
                s.R.setSendBufferSize(buf.length);
            } catch (RserveException ex) {
                ex.printStackTrace();
            }
            int n = 0;
            while ((n = is1.read(buf)) > 0) {
                os1.write(buf, 0, n);
            }
            //os1.flush();
        } catch (IOException e) {
            e.printStackTrace();
        } finally {
            try {
                if (os1 != null) {
                    os1.close();
                }
                if (is1 != null) {
                    is1.close();
                }
            } catch (IOException ee) {
                ee.printStackTrace();
            }
        }
        assert localfile1.exists();

        //check csv file is written
        StringBuffer b = new StringBuffer();
        try {
            FileInputStream fis = new FileInputStream(localfile1);
            Reader r = new BufferedReader(new InputStreamReader(fis));
            int n = 0;
            while ((n = r.read()) > 0) {
                b.append((char) n);
            }
            r.close();
        } catch (IOException e) {
            e.printStackTrace();
        }
        assert b.charAt(4) == 'A' : b.charAt(4);
        s.rawEval("rm(aa)");
        //localfile1.delete();

        //put file test...
        String remoteFile2 = "put" + rand + ".csv";
        File localfile2 = new File(tmpdir.getParent(), remoteFile2);
        System.out.println("PUT :" + localfile2.getAbsolutePath());
        String content = "A,B,C\n1,2,3\n";
        try {
            FileOutputStream fos = new FileOutputStream(localfile2);
            Writer w = new BufferedWriter(new OutputStreamWriter(fos));
            w.write(content);
            w.flush();
            w.close();
        } catch (IOException e) {
            e.printStackTrace();
        }

        //check csv file is written
        try {
            FileInputStream fis = new FileInputStream(localfile2);
            Reader r = new BufferedReader(new InputStreamReader(fis));
            int n = 0;
            while ((n = r.read()) > 0) {
                System.out.print((char) n);
            }
            r.close();
        } catch (IOException e) {
            e.printStackTrace();
        }

        InputStream is2 = null;
        OutputStream os2 = null;

        try {
            //System.out.println("createFile");
            os2 = s.R.createFile(remoteFile2);
            //System.out.println("OK");
            is2 = new FileInputStream(localfile2);
            byte[] buf = new byte[65536];
            try {
                s.R.setSendBufferSize(buf.length);
            } catch (RserveException ex) {
                ex.printStackTrace();
            }
            int n = 0;
            while ((n = is2.read(buf)) > 0) {
                System.out.print(buf);
                os2.write(buf, 0, n);
            }
        } catch (IOException e) {
            e.printStackTrace();
        } finally {
            try {
                if (os2 != null) {
                    os2.close();
                }
                if (is2 != null) {
                    is2.close();
                }
            } catch (IOException ee) {
                ee.printStackTrace();
            }
        }
        s.rawEval("ABC<-read.csv(file='" + remoteFile2 + "', header = TRUE,sep=',')");
        System.out.println(s.toString(s.cast(s.rawEval("ABC"))));
        assert ((REXP) s.rawEval("ABC$A")).isNumeric();
        s.rawEval("rm(ABC)");
        localfile2.delete();
    }

    /*@Test
     public void testPerformance() throws REXPMismatchException { //Performance rawEval
     long start = Calendar.getInstance().getTimeInMillis();
     System.out.println("tic");
    
     for (int i = 0; i < 10000; i++) {
     s.silentlyRawEval("rnorm(10)").asDoubles();
     }
     System.out.println("toc");
     long duration = Calendar.getInstance().getTimeInMillis() - start;
     System.out.println("Spent time:" + (duration) + " ms");
     }*/
    @Test
    public void testConcurrentEval() throws Exception {
        s.voidEval("id <- function(x){return(x)}");
        assert (double) s.eval("id(1.0)") == 1.0 : "Failed to eval id";
        int n = 10;
        final boolean[] test = new boolean[n];
        final boolean[] done = new boolean[n];
        for (int i = 0; i < n; i++) {
            done[i] = false;
        }
        for (int i = 0; i < n; i++) {
            final int I = i;
            new Thread(new Runnable() {

                public void run() {
                    double x = Math.random();
                    try {
                        System.err.println("x= " + x);
                        double fx = -1;
                        synchronized (s) {
                            s.voidEval("x <- " + x);
                            Thread.sleep((long) (1000 + Math.random() * 1000));
                            fx = ((Double) s.eval("id(x)"));
                        }
                        System.err.println(fx + " =?= " + x);
                        boolean ok = Math.abs(fx - x) < 0.00001;
                        assert ok : fx + " != " + x;
                        synchronized (test) {
                            test[I] = ok;
                        }
                    } catch (Exception ex) {
                        synchronized (test) {
                            test[I] = false;
                        }
                    }
                    synchronized (done) {
                        done[I] = true;
                    }
                }
            }).start();
        }
        while (!alltrue(done)) {
            Thread.sleep(1000);
            System.err.print(".");
        }
        assert alltrue(test) : "At least one concurrent eval failed !";
    }

    static boolean alltrue(boolean[] a) {
        for (int i = 0; i < a.length; i++) {
            if (!a[i]) {
                return false;
            }
        }
        return true;
    }

    @Test
    public void testConcurrency() throws InterruptedException {
        final RserveSession r1 = RserveSession.newInstanceTry(new RLog() {

            public void log(String string, Level level) {
                if (level == Level.INFO) {
                    System.out.println("1 " + string);
                } else {
                    System.err.println("1 " + string);
                }
            }

            public void close() {
            }
        }, null);
        final RserveSession r2 = RserveSession.newInstanceTry(new RLog() {

            public void log(String string, Level level) {
                if (level == Level.INFO) {
                    System.out.println("2 " + string);
                } else {
                    System.err.println("2 " + string);
                }
            }

            public void close() {
            }
        }, null);

        new Thread(new Runnable() {

            public void run() {
                try {
                    r1.rawEval("a<-1");

                    double a = r1.asDouble(r1.rawEval("a"));
                    assert a == 1 : "a should be == 1 !";
                    System.out.println("1: OK");

                } catch (Exception ex) {
                    ex.printStackTrace();
                }
            }
        }).start();
        new Thread(new Runnable() {

            public void run() {
                try {
                    r2.rawEval("a<-2");

                    double a2 = r2.asDouble(r2.rawEval("a"));
                    assert a2 == 2 : "a should be == 2 !";
                    System.out.println("2: OK");

                    double a1 = r1.asDouble(r1.rawEval("a"));
                    assert a1 == 1 : "a should be == 1 !";
                    System.out.println("1: OK");

                } catch (Exception ex) {
                    ex.printStackTrace();
                }
            }
        }).start();

        Thread.sleep(5000);
        r1.end();
        r2.end();
    }

    @Test
    public void testHardConcurrency() throws REXPMismatchException, InterruptedException {
        final int[] A = {1, 2, 3, 4, 5, 6, 7, 8, 9, 10};
        final RserveSession[] R = new RserveSession[A.length];
        for (int i = 0; i < R.length; i++) {
            R[i] = RserveSession.newInstanceTry(new RLog() {

                public void log(String string, Level level) {
                    if (level == Level.INFO) {
                        System.out.println(string);
                    } else {
                        System.err.println(string);
                    }
                }

                public void close() {
                }
            }, null);
        }

        for (int i = 0; i < A.length; i++) {
            final int ai = A[i];
            final RserveSession ri = R[i];
            //new Thread(new Runnable() {
            //
            //   public void run() {
            try {
                ri.rawEval("a<-" + ai);

                double ria = ri.asDouble(ri.rawEval("a"));
                assert ria == ai : "a should be == " + ai + " !";
                System.out.println(ai + ": OK");

            } catch (Exception ex) {
                ex.printStackTrace();
            }
            //}
            //}).start();
        }

        //checking of each RserveSession to verify values are ok.
        for (int i = 0; i < A.length; i++) {
            for (int j = 0; j < i; j++) {
                while (R[j].rawEval("a") == null) {
                    Thread.sleep(1000);
                }
                //System.out.println("Checking " + (j + 1) + " : " + R[j]);
                double rja = R[j].asDouble(R[j].rawEval("a"));
                assert rja == A[j] : "a should be == " + A[j] + " !";
                System.out.println(A[i] + " " + A[j] + ": OK");
            }
        }

        for (int i = 0; i < R.length; i++) {
            R[i].end();
        }
    }

    @Before
    public void setUp() throws Exception {
        RLog l = new RLog() {

            public void log(String string, RLog.Level level) {
                System.out.println("                              " + level + " " + string);
            }

            public void close() {
            }
        };

        String http_proxy_env = System.getenv("http_proxy");
        Properties prop = new Properties();
        if (http_proxy_env != null) {
            prop.setProperty("http_proxy", http_proxy_env);
        }

        RserverConf conf = new RserverConf(null, -1, null, null, prop);
        s = RserveSession.newInstanceTry(l, conf);
        System.out.println("| R.version:\t" + s.eval("R.version.string"));
        System.out.println("| Rserve.version:\t" + s.eval("installed.packages(lib.loc='" + RserveDaemon.R_APP_DIR + "')[\"Rserve\",\"Version\"]"));

        System.out.println("| tmpdir:\t" + tmpdir.getAbsolutePath());
        if (!(tmpdir.isDirectory() || tmpdir.mkdir())) {
            throw new IOException("Cannot access tmpdir=" + tmpdir);
        }

        System.out.println("| getwd():\t" + s.eval("getwd()"));
        System.out.println("| list.files(all.files=TRUE):\t" + Arrays.toString((String[]) s.eval("list.files(all.files=TRUE)")));
        System.out.println("| ls():\t" + Arrays.toString((String[]) s.ls(true)));
    }

    @After
    public void tearDown() {
        try {
            //uncomment following for sequential call. 
            //s.end();
            Thread.sleep(1000);
        } catch (InterruptedException ex) {
            ex.printStackTrace();
        }
        s.end();
        //A shutdown hook kills all Rserve at the end.
    }
}
