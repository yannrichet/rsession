package org.math.R;

import java.io.File;
import java.io.PrintStream;
import java.util.Arrays;
import java.util.Map;
import java.util.Properties;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;

/**
 *
 * @author richet
 */
public class BasicTest {

    PrintStream p = System.err;
    //RserverConf conf;
    RserveSession s;
    RenjinSession r;
    int rand = Math.round((float) Math.random() * 10000);
    File tmpdir = new File("tmp"/*System.getProperty("java.io.tmpdir")*/);

    public static void main(String args[]) {
        org.junit.runner.JUnitCore.main(BasicTest.class.getName());
    }

    @Before
    public void setUp() {
        RLog l = new RLog() {

            public void log(String string, RLog.Level level) {
                System.out.println("                              " + level + " " + string);
            }

            public void close() {
            }
        };/*RLogPanel();
         JFrame f = new JFrame("RLogPanel");
         f.setContentPane((RLogPanel) l);
         f.setSize(600, 600);
         f.setVisible(true);*/

        String http_proxy_env = System.getenv("http_proxy");
        Properties prop = new Properties();
        if (http_proxy_env != null) {
            prop.setProperty("http_proxy", http_proxy_env);
        }

        RserverConf conf = new RserverConf(null, -1, null, null, prop);
        s = RserveSession.newInstanceTry(l, conf);
        try {
            System.err.println(s.eval("R.version.string"));
        } catch (Exception ex) {
            ex.printStackTrace();
        }
        try {
            System.err.println("Rserve version " + s.eval("installed.packages()[\"Rserve\",\"Version\"]"));
        } catch (Exception ex) {
            ex.printStackTrace();
        }

        r = RenjinSession.newInstance(l, prop);

        System.out.println("tmpdir=" + tmpdir.getAbsolutePath());
    }

    @After
    public void tearDown() {
        try {
            Thread.sleep(1000);
        } catch (InterruptedException ex) {
            ex.printStackTrace();
        }
        s.close();
        //A shutdown hook kills all Rserve at the end.
        r.close();
    }

    @Test
    public void testCast_Rserve() throws Exception {
         System.err.println("====================================== Rserve");
       //cast
        assert ((Boolean) s.eval("TRUE")) == true;
        assert ((Double) s.eval("0.123")) == 0.123;
        assert ((Double) s.eval("pi")) - 3.141593 < 0.0001;
        //assert (castStrict(s.eval("0.123+a"))) == null : s.eval("0.123+a").toDebugString();
        assert ((Double) s.eval("0.123")) == 0.123 : s.eval("0.123").toString();
        assert ((Double) s.eval("(0.123)+pi")) - 3.264593 < 0.0001;
        assert ((double[]) s.eval("rnorm(10)")).length == 10;
        assert ((double[][]) s.eval("array(0.0,c(4,3))")).length == 4;
        assert ((double[]) s.eval("array(array(0.0,c(4,3)))")).length == 12;
        assert ((double[][]) s.eval("cbind(rnorm(10),rnorm(10))")).length == 10;
        assert ((Map) s.eval("list(aa=rnorm(10),bb=rnorm(10))")).size() == 2;
        assert ((String) s.eval("'abcd'")).equals("abcd");
        assert ((String[]) s.eval("c('abcd','sdfds')")).length == 2;
    }

    @Test
    public void testCast_Renjin() throws Exception {
        System.err.println("====================================== Renjin");
        //cast
        assert ((Boolean) r.eval("TRUE")) == true;
        assert ((Double) r.eval("0.123")) == 0.123;
        assert ((Double) r.eval("pi")) - 3.141593 < 0.0001;
        //assert (castStrict(s.eval("0.123+a"))) == null : s.eval("0.123+a").toDebugString();
        assert ((Double) r.eval("0.123")) == 0.123 : s.eval("0.123").toString();
        assert ((Double) r.eval("(0.123)+pi")) - 3.264593 < 0.0001;
        assert ((double[]) r.eval("rnorm(10)")).length == 10;
        assert ((double[][]) r.eval("array(0.0,c(4,3))")).length == 4;
        assert ((double[]) r.eval("array(array(0.0,c(4,3)))")).length == 12;
        assert ((double[][]) r.eval("cbind(rnorm(10),rnorm(10))")).length == 10;
        assert ((Map) r.eval("list(aa=rnorm(10),bb=rnorm(10))")).size() == 2;
        assert ((String) r.eval("'abcd'")).equals("abcd");
        assert ((String[]) r.eval("c('abcd','sdfds')")).length == 2;
    }

    @Test
    public void testSet_Rserve() throws Exception {
        System.err.println("====================================== Rserve");
        //set
        double c = Math.random();
        s.set("c", c);
        assert ((Double) s.eval("c")) == c;

        double[] C = new double[10];
        s.set("C", C);
        assert ((double[]) s.eval("C")).length == C.length;

        double[][] CC = new double[10][2];
        CC[9][1] = Math.random();
        s.set("CC", CC);
        //System.err.println("CC[9][1]="+((double[][]) Rcast(s.evalR("CC")))[9][1]);
        assert ((double[][]) s.eval("CC"))[9][1] == CC[9][1];
        assert ((double[]) s.eval("CC[1,]")).length == CC[0].length;

        System.err.println(s.cat(s.ls("C")));
        assert s.ls("C").length == 2 : "invalid ls(\"C\") : " + s.cat(s.ls("C"));

        String str = "abcd";
        s.set("s", str);
        assert ((String) s.eval("s")).equals(str);

        String[] Str = {"abcd", "cdef"};
        s.set("S", Str);
        assert ((String[]) s.eval("S")).length == Str.length;
        assert ((String) s.eval("S[1]")).equals(Str[0]);

        s.set("df", new double[][]{{1, 2, 3}, {4, 5, 6}, {7, 8, 9}, {10, 11, 12}}, "x1", "x2", "x3");
        assert (Double) (s.eval("df$x1[3]")) == 7;
    }

    @Test
    public void testSet_Renjin() throws Exception {
        System.err.println("====================================== Renjin");
        //set
        double c = Math.random();
        r.set("c", c);
        assert ((Double) r.eval("c")) == c;

        double[] C = new double[10];
        r.set("C", C);
        assert ((double[]) r.eval("C")).length == C.length;

        double[][] CC = new double[10][2];
        CC[9][1] = Math.random();
        r.set("CC", CC);
        //System.err.println("CC[9][1]="+((double[][]) Rcast(s.evalR("CC")))[9][1]);
        assert ((double[][]) r.eval("CC"))[9][1] == CC[9][1];
        assert ((double[]) r.eval("CC[1,]")).length == CC[0].length;

        System.err.println(r.cat(r.ls("C")));
        assert r.ls("C").length == 2 : "invalid ls(\"C\") : " + r.cat(r.ls("C"));

        String str = "abcd";
        r.set("s", str);
        assert ((String) r.eval("s")).equals(str);

        String[] Str = {"abcd", "cdef"};
        r.set("S", Str);
        assert ((String[]) r.eval("S")).length == Str.length;
        assert ((String) r.eval("S[1]")).equals(Str[0]);

        r.set("df", new double[][]{{1, 2, 3}, {4, 5, 6}, {7, 8, 9}, {10, 11, 12}}, "x1", "x2", "x3");
        assert (Double) (r.eval("df$x1[3]")) == 7;
    }

    @Test
    public void testIOFiles_Rserve() throws Exception {
        System.err.println("====================================== Rserve");
        //set
        double c = Math.random();
        s.set("c", c);
        s.set("z", 0.0);
        assert ((Double) s.eval("c")) == c;

        //get/put files
        String[] ls = (String[]) s.eval("ls()");
        Arrays.sort(ls);
        assert ls.length == 2 : "ls.length != 2 : " + Arrays.asList(ls);
        assert ls[0].equals("c") : s.toString(ls) + "[0]=" + ls[3];
        s.eval("save(file='c" + rand + ".Rdata',c)");
        s.rm("c");
        //ls = (String[]) castStrict(s.eval("ls()"));
        ls = s.ls();
        Arrays.sort(ls);
        assert !ls[0].equals("c") : s.toString(ls) + "[0]=" + ls[3];
        s.eval("load(file='c" + rand + ".Rdata')");
        p.println((s.eval("c")));

        File local = new File(tmpdir, "c" + rand + ".Rdata");
        s.getFile(local);
        assert local.exists();
        s.putFile(new File(tmpdir, "c" + rand + ".Rdata"));

        //save
        File f = new File(tmpdir, "save" + rand + ".Rdata");
        if (f.exists()) {
            f.delete();
        }
        s.save(f, "c");
        assert f.exists();

        p.println("ls=\n" + s.toString(s.eval("ls()")));
        //load
        ls = (String[]) s.eval("ls()");
        Arrays.sort(ls);
        assert ls[0].equals("c") : s.toString(ls) + "=" + Arrays.asList(ls);
        s.rm("c");
        assert s.eval("ls()") instanceof String : "More than 1 object in ls()";
        s.load(f);
        ls = (String[]) s.eval("ls()");
        Arrays.sort(ls);
        assert ls[0].equals("c") : s.toString(ls) + "=" + Arrays.asList(ls);

        //toJPEG
        File jpg = new File(tmpdir, "titi" + rand + ".jpg");
        s.toJPEG(jpg, 400, 400, "plot(rnorm(10))");
        assert jpg.exists();

        //to TXT
        String txt = s.print("summary(rnorm(100))");
        System.out.println(txt);
        assert txt.length() > 0;

//to HTML
        String html = s.asHTML("summary(rnorm(100))");
        System.out.println(html);
        assert html.length() > 0;
    }

    @Test
    public void testIOFiles_Renjin() throws Exception {
        System.err.println("====================================== Renjin");
        //set
        double c = Math.random();
        r.set("c", c);
        r.set("z", 0.0);
        assert ((Double) r.eval("c")) == c;

        //get/put files
        String[] ls = (String[]) r.eval("ls()");
        Arrays.sort(ls);
        assert ls.length == 2 : "ls.length != 2 : " + Arrays.asList(ls);
        assert ls[0].equals("c") : r.toString(ls) + "[0]=" + ls[3];
        r.eval("save(file='c" + rand + ".Rdata',c)");
        r.rm("c");
        //ls = (String[]) castStrict(s.eval("ls()"));
        ls = r.ls();
        Arrays.sort(ls);
        assert !ls[0].equals("c") : r.toString(ls) + "[0]=" + ls[3];
        r.eval("load(file='c" + rand + ".Rdata')");
        p.println((r.eval("c")));

        File local = new File(tmpdir, "c" + rand + ".Rdata");
        r.getFile(local);
        assert local.exists();
        r.putFile(new File(tmpdir, "c" + rand + ".Rdata"));

        //save
        File f = new File(tmpdir, "save" + rand + ".Rdata");
        if (f.exists()) {
            f.delete();
        }
        r.save(f, "c");
        assert f.exists();

        p.println("ls=\n" + r.toString(r.eval("ls()")));
        //load
        ls = (String[]) r.eval("ls()");
        Arrays.sort(ls);
        assert ls[0].equals("c") : r.toString(ls) + "=" + Arrays.asList(ls);
        r.rm("c");
        assert r.eval("ls()") instanceof String : "More than 1 object in ls()";
        r.load(f);
        ls = (String[]) r.eval("ls()");
        Arrays.sort(ls);
        assert ls[0].equals("c") : r.toString(ls) + "=" + Arrays.asList(ls);

        //toJPEG
        /*File jpg = new File(tmpdir, "titi" + rand + ".png");
         r.toPNG(jpg, 400, 400, "plot(rnorm(10))");
         assert jpg.exists();*/
        //toTXT
        String txt = r.print("summary(rnorm(100))");
        System.out.println(txt);
        assert txt.length() > 0;

        //toHTML
        String html = r.asHTML("summary(rnorm(100))");
        System.out.println(html);
        assert html.length() > 0;
    }
}
