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
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Properties;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.rosuda.REngine.REXPMismatchException;
import org.rosuda.REngine.RList;
import org.rosuda.REngine.Rserve.RserveException;

import static org.math.R.Rsession.*;

/**
 *
 * @author richet
 */
public class RsessionTest {

    PrintStream p = System.err;
    RserverConf conf;
    Rsession s;
    int rand = Math.round((float) Math.random() * 10000);
    File tmpdir = new File("tmp"/*System.getProperty("java.io.tmpdir")*/);

    public static void main(String args[]) {
        org.junit.runner.JUnitCore.main(RsessionTest.class.getName());
    }

    //@Test
    public void testFileSize() throws REXPMismatchException {
        for (int i = 0; i < 20; i++) {
            int size = i * 10000;
            s.evalR("raw" + i + "<-rnorm(" + (size / 8) + ")");
            File sfile = new File("tmp", size + ".Rdata");
            s.save(sfile, "raw" + i);
            assert sfile.exists() : "Size " + size + " failed";
            p.println(sfile.length());
        }
    }

    @Test
    public void testPrint() throws REXPMismatchException {
        //cast
        String[] exp = {"TRUE", "0.123", "pi", "0.123+a", "0.123", "(0.123)+pi", "rnorm(10)", "cbind(rnorm(10),rnorm(10))", "data.frame(aa=rnorm(10),bb=rnorm(10))", "'abcd'", "c('abcd','sdfds')"};
        for (String string : exp) {
            p.println(string + " --> " + s.toString(Rcast(s.evalR(string))));
        }
    }

    @Test
    public void testEval() throws REXPMismatchException {

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
            System.out.println(e + " --> " + s.eval(e, vars));
        }
        assert Arrays.asList(s.ls()).contains("a") : "variable a disappeared";
        assert (Double) s.eval("a", null) == a : "variable a changed";
        assert Arrays.equals((double[]) s.eval("A", null), A) : "variable A changed";
    }

    @Test
    public void testRFileIO() throws REXPMismatchException {
        //get file test...
        String remoteFile1 = "get" + rand + ".csv";
        File localfile1 = new File(tmpdir, remoteFile1);
        System.out.println("GET :" + localfile1.getAbsolutePath());
        s.evalR("aa<-data.frame(A=c(1,2,3),B=c(4,5,6))");
        s.evalR("write.csv(file='" + remoteFile1 + "',aa)");
        InputStream is1 = null;
        OutputStream os1 = null;
        try {
            //System.out.println("openFile");
            is1 = s.connection.openFile(remoteFile1);
            //System.out.println("OK");
            os1 = new FileOutputStream(localfile1);
            byte[] buf = new byte[65536];
            try {
                s.connection.setSendBufferSize(buf.length);
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
        s.evalR("rm(aa)");
        //localfile1.delete();


        //put file test...
        String remoteFile2 = "put" + rand + ".csv";
        File localfile2 = new File(tmpdir, remoteFile2);
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
            os2 = s.connection.createFile(remoteFile2);
            //System.out.println("OK");
            is2 = new FileInputStream(localfile2);
            byte[] buf = new byte[65536];
            try {
                s.connection.setSendBufferSize(buf.length);
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
        s.evalR("ABC<-read.csv(file='" + remoteFile2 + "', header = TRUE,sep=',')");
        System.out.println(s.toString(s.Rcast(s.evalR("ABC"))));
        assert s.evalR("ABC$A").isNumeric();
        s.evalR("rm(ABC)");
        localfile2.delete();
    }

    @Test
    public void testCast() throws REXPMismatchException {
        //cast
        assert ((Boolean) Rcast(s.evalR("TRUE"))) == true;
        assert ((Double) Rcast(s.evalR("0.123"))) == 0.123;
        assert ((Double) Rcast(s.evalR("pi"))) - 3.141593 < 0.0001;
        assert (Rcast(s.evalR("0.123+a"))) == null;
        assert ((Double) Rcast(s.evalR("0.123"))) == 0.123;
        assert ((Double) Rcast(s.evalR("(0.123)+pi"))) - 3.264593 < 0.0001;
        assert ((double[]) Rcast(s.evalR("rnorm(10)"))).length == 10;
        assert ((double[][]) Rcast(s.evalR("array(0.0,c(4,3))"))).length == 4;
        assert ((double[]) Rcast(s.evalR("array(array(0.0,c(4,3)))"))).length == 12;
        assert ((double[][]) Rcast(s.evalR("cbind(rnorm(10),rnorm(10))"))).length == 10;
        assert ((RList) Rcast(s.evalR("data.frame(aa=rnorm(10),bb=rnorm(10))"))).size() == 2;
        assert ((String) Rcast(s.evalR("'abcd'"))).equals("abcd");
        assert ((String[]) Rcast(s.evalR("c('abcd','sdfds')"))).length == 2;
    }

    @Test
    public void testSet() throws REXPMismatchException {

        //set
        double c = Math.random();
        s.set("c", c);
        assert ((Double) Rcast(s.evalR("c"))) == c;

        double[] C = new double[10];
        s.set("C", C);
        assert ((double[]) Rcast(s.evalR("C"))).length == C.length;

        double[][] CC = new double[10][2];
        CC[9][1] = Math.random();
        s.set("CC", CC);
        //System.err.println("CC[9][1]="+((double[][]) Rcast(s.evalR("CC")))[9][1]);
        assert ((double[][]) Rcast(s.evalR("CC")))[9][1] == CC[9][1];
        assert ((double[]) Rcast(s.evalR("CC[1,]"))).length == CC[0].length;

        System.err.println(s.cat(s.ls("C")));
        assert s.ls("C").length == 2 : "invalid ls(\"C\") : " + s.cat(s.ls("C"));

        String str = "abcd";
        s.set("s", str);
        assert ((String) Rcast(s.evalR("s"))).equals(str);

        String[] Str = {"abcd", "cdef"};
        s.set("S", Str);
        assert ((String[]) Rcast(s.evalR("S"))).length == Str.length;
        assert ((String) Rcast(s.evalR("S[1]"))).equals(Str[0]);


        s.set("df", new double[][]{{1, 2, 3}, {4, 5, 6}, {7, 8, 9}, {10, 11, 12}}, "x1", "x2", "x3");
        assert (Double) (Rcast(s.evalR("df$x1[3]"))) == 7;

        //get/put files
        String[] ls = (String[]) Rcast(s.evalR("ls()"));
        Arrays.sort(ls);
        assert ls[3].equals("c") : s.toString(ls) + "[3]=" + ls[3];
        s.evalR("save(file='c" + rand + ".Rdata',c)");
        s.rm("c");
        //ls = (String[]) cast(s.eval("ls()"));
        ls = s.ls();
        Arrays.sort(ls);
        assert !ls[3].equals("c") : s.toString(ls) + "[3]=" + ls[3];
        s.evalR("load(file='c" + rand + ".Rdata')");
        p.println(s.toString(Rcast(s.evalR("c"))));

        File local = new File(tmpdir, "c" + rand + ".Rdata");
        s.receiveFile(local);
        assert local.exists();
        s.sendFile(new File(tmpdir, "c" + rand + ".Rdata"));

        //save
        File f = new File(tmpdir, "save" + rand + ".Rdata");
        if (f.exists()) {
            f.delete();
        }
        s.save(f, "C");
        assert f.exists();


        p.println("ls=\n" + s.toString(Rcast(s.evalR("ls()"))));
        //load
        ls = (String[]) Rcast(s.evalR("ls()"));
        Arrays.sort(ls);
        assert ls[0].equals("C") : s.toString(ls) + "[0]=" + ls[0];
        s.rm("C");
        ls = (String[]) Rcast(s.evalR("ls()"));
        Arrays.sort(ls);
        assert !ls[0].equals("C") : s.toString(ls) + "[0]=" + ls[0];
        s.load(f);
        ls = (String[]) Rcast(s.evalR("ls()"));
        Arrays.sort(ls);
        assert ls[0].equals("C") : s.toString(ls) + "[0]=" + ls[0];

        //toJPEG
        File jpg = new File(tmpdir, "titi" + rand + ".jpg");
        s.toJPEG(jpg, 400, 400, "plot(rnorm(10))");
        assert jpg.exists();

        //toHTML
        String html = s.asHTML("summary(rnorm(100))");
        System.out.println(html);
        assert html.length() > 0;

        //toHTML
        String txt = s.asString("summary(rnorm(100))");
        System.out.println(txt);
        assert txt.length() > 0;

        //final Rsession s2 = new Rsession(System.err);
        //p.println(toString(cast(s2.eval("0.123"))));

        //installPackage
        System.out.println(s.installPackage("sensitivity", true));
        System.out.println(s.installPackage("wavelets", true));

        //s.end();
        //s2.end();
    }

    /*@Test
    public void testPerformance() throws REXPMismatchException { //Performance eval
    long start = Calendar.getInstance().getTimeInMillis();
    System.out.println("tic");
    
    for (int i = 0; i < 10000; i++) {
    s.silentlyEval("rnorm(10)").asDoubles();
    }
    System.out.println("toc");
    long duration = Calendar.getInstance().getTimeInMillis() - start;
    System.out.println("Spent time:" + (duration) + " ms");
    }*/
    //@Test
    public void testConcurrency() throws InterruptedException {
        final Rsession r1 = Rsession.newLocalInstance(System.out, null);
        final Rsession r2 = Rsession.newLocalInstance(System.err, null);

        new Thread(new Runnable() {

            public void run() {
                try {
                    r1.evalR("a<-1");

                    double a = r1.evalR("a").asDouble();
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
                    r2.evalR("a<-2");

                    double a2 = r2.evalR("a").asDouble();
                    assert a2 == 2 : "a should be == 2 !";
                    System.out.println("2: OK");

                    double a1 = r1.evalR("a").asDouble();
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

    //@Test
    public void testHardConcurrency() throws REXPMismatchException, InterruptedException {
        final int[] A = {1, 2/*, 3, 4, 5/*, 6, 7, 8, 9, 10*/};
        final Rsession[] R = new Rsession[A.length];
        for (int i = 0; i < R.length; i++) {
            R[i] = Rsession.newLocalInstance(System.out, null);
        }

        for (int i = 0; i < A.length; i++) {
            final int ai = A[i];
            final Rsession ri = R[i];
            //new Thread(new Runnable() {
            //
            //   public void run() {
            try {
                ri.evalR("a<-" + ai);

                double ria = ri.evalR("a").asDouble();
                assert ria == ai : "a should be == " + ai + " !";
                System.out.println(ai + ": OK");

            } catch (Exception ex) {
                ex.printStackTrace();
            }
            //}
            //}).start();
        }

        //checking of each Rsession to verify values are ok.
        for (int i = 0; i < A.length; i++) {
            for (int j = 0; j < i; j++) {
                while (R[j].evalR("a") == null) {
                    Thread.sleep(1000);
                }
                //System.out.println("Checking " + (j + 1) + " : " + R[j]);
                double rja = R[j].evalR("a").asDouble();
                assert rja == A[j] : "a should be == " + A[j] + " !";
                System.out.println(A[i] + " " + A[j] + ": OK");
            }
        }

        for (int i = 0; i < R.length; i++) {
            R[i].end();
        }
    }

    @Before
    public void setUp() {
        p = System.err;
        String http_proxy_env = System.getenv("http_proxy");
        Properties prop = new Properties();
        if (http_proxy_env != null) {
            prop.setProperty("http_proxy", "\"" + http_proxy_env + "\"");
        }
        conf = new RserverConf(null/*"81.194.2.21"*/, -1/* RserverConf.RserverDefaultPort*/, null, null, prop);
        s = Rsession.newInstanceTry(p, conf);

        System.out.println("tmpdir=" + tmpdir.getAbsolutePath());
    }

    @After
    public void tearDown() {
        s.end();
    }
}
