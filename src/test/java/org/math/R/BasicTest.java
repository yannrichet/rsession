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
                System.out.println("                               " + level + " " + string);
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
            System.err.println("Rserve version " + s.eval("installed.packages(lib.loc='" + RserveDaemon.R_APP_DIR + "')[\"Rserve\",\"Version\"]"));
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
    public void testWriteCSVAnywhere_Rserve() throws Exception {
        String toto = "/tmp/toto.csv";
        File totof = new File(toto);
        if (totof.exists()) {
            assert totof.delete() : "Failed to delete " + totof;
        }
        assert !totof.exists() : "Indeed, did not deleted " + totof;
        s.voidEval("write.csv(runif(10),'" + toto + "')");
        assert totof.isFile() : "Failed to write file";
    }

    @Test
    public void testWriteCSVAnywhere_Renjin() throws Exception {
        String toto = "/tmp/toto.csv";
        File totof = new File(toto);
        if (totof.exists()) {
            assert totof.delete() : "Failed to delete " + totof;
        }
        assert !totof.exists() : "Indeed, did not deleted " + totof;
        r.voidEval("write.csv(runif(10),'" + toto + "')");
        assert totof.isFile() : "Failed to write file";
    }

    @Test
    public void testCast_Rserve() throws Exception {
        System.err.println("====================================== Rserve");
        //cast
        assert Double.isNaN((Double) s.eval("NaN")) : r.eval("NaN");
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
        assert Double.isNaN((Double) r.eval("NaN")) : r.eval("NaN");
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

        assert s.set("n", null) : "Failed to create NULL object";

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
    public void testMatrix_Renjin() throws Exception {
        System.err.println("====================================== Renjin");

        assert r.set("n", null, "a") : "Failed to create NULL matrix";
        assert r.eval("n").toString().equals("{a=null}") : "Bad print of object: " + r.eval("n").toString();

        double[][] m = new double[][]{{0, 1}, {2, 3}};
        r.set("m", m);
        assert Arrays.deepEquals(m, r.asMatrix(r.eval("m"))) : "Failed asMatrix: " + Arrays.deepToString(m) + " != " + Arrays.deepToString(r.asMatrix(r.eval("m")));

        double[] a = new double[]{0, 1};
        r.set("a", a);
        // !!! R used to put arrays in column matrix when as.matrix called
        assert Arrays.deepEquals(new double[][]{{a[0]}, {a[1]}}, r.asMatrix(r.eval("a"))) : "Failed asMatrix: " + Arrays.deepToString(new double[][]{{a[0]}, {a[1]}}) + " != " + Arrays.deepToString(r.asMatrix(r.eval("a")));

        double d = 0;
        r.set("d", d);
        assert Arrays.deepEquals(new double[][]{{d}}, r.asMatrix(r.eval("d"))) : "Failed asMatrix: " + Arrays.deepToString(new double[][]{{d}}) + " != " + Arrays.deepToString(r.asMatrix(r.eval("d")));

        assert r.set("l", new double[][]{{0, 1}}, "a", "b") : "Failed to create list";
        assert r.set("l", new double[][]{{0}, {1}}, "a") : "Failed to create list";

        assert r.set("lm", r.asMatrix(r.eval("m")), "m1", "m2") : "Failed to create list";
        assert r.print("lm").contains("m1 m2") && r.print("lm").contains("2  3") : "Bad print: " + r.print("lm");
        assert r.asDouble(r.eval("lm$m1[2]")) == 2.0 : "Bad values in list: " + r.eval("print(lm)");

        assert r.set("la", r.asMatrix(r.eval("a")), "a1") : "Failed to create list";
        assert r.print("la").contains("a1") && r.print("la").contains("2 1") : "Bad print: " + r.print("la");

        assert r.set("ld", r.asMatrix(r.eval("d")), "d1") : "Failed to create list";
        assert r.print("ld").contains("d1") && r.print("ld").contains("1 0") : "Bad print: " + r.print("ld");
    }

    @Test
    public void testMatrix_Rserve() throws Exception {
        System.err.println("====================================== Rserve");

        assert s.set("n", null, "a") : "Failed to create NULL matrix";
        assert s.eval("n").toString().equals("{a=null}") : "Bad print of object: " + s.eval("n").toString();

        double[][] m = new double[][]{{0, 1}, {2, 3}};
        s.set("m", m);
        assert Arrays.deepEquals(m, s.asMatrix(s.eval("m"))) : "Failed asMatrix: " + Arrays.deepToString(m) + " != " + Arrays.deepToString(s.asMatrix(s.eval("m")));

        double[] a = new double[]{0, 1};
        s.set("a", a);
        // !!! R used to put arrays in column matrix when as.matrix called
        assert Arrays.deepEquals(new double[][]{{a[0]}, {a[1]}}, s.asMatrix(s.eval("a"))) : "Failed asMatrix: " + Arrays.deepToString(new double[][]{{a[0]}, {a[1]}}) + " != " + Arrays.deepToString(s.asMatrix(s.eval("a")));

        double d = 0;
        s.set("d", d);
        assert Arrays.deepEquals(new double[][]{{d}}, s.asMatrix(s.eval("d"))) : "Failed asMatrix: " + Arrays.deepToString(new double[][]{{d}}) + " != " + Arrays.deepToString(s.asMatrix(s.eval("d")));

        assert s.set("l", new double[][]{{0, 1}}, "a", "b") : "Failed to create list";
        assert s.set("l", new double[][]{{0}, {1}}, "a") : "Failed to create list";

        assert s.set("lm", s.asMatrix(s.eval("m")), "m1", "m2") : "Failed to create list";
        assert s.print("lm").contains("m1 m2") && s.print("lm").contains("2  3") : "Bad print: " + s.print("lm");
        assert s.asDouble(s.eval("lm$m1[2]")) == 2.0 : "Bad values in list: " + s.eval("print(lm)");

        assert s.set("la", s.asMatrix(s.eval("a")), "a1") : "Failed to create list";
        assert s.print("la").contains("a1") && s.print("la").contains("2  1") : "Bad print: " + s.print("la");

        assert s.set("ld", s.asMatrix(s.eval("d")), "d1") : "Failed to create list";
        assert s.print("ld").contains("d1") && s.print("ld").contains("1  0") : "Bad print: " + s.print("ld");
    }

    @Test
    public void testSet_Renjin() throws Exception {
        System.err.println("====================================== Renjin");

        assert r.set("n", null) : "Failed to create NULL object";

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
    public void testSave_Rserve() throws Exception {
        String str = "abcd";
        s.set("s", str);
        assert ((String) s.eval("s")).equals(str);

        File f2 = new File("Rserve" + Math.random() + ".save");
        s.save(f2, (String[])null);        
        File f = new File("Rserve" + Math.random() + ".save");
        s.save(f, (String)null);
        assert !f.exists() : "Created empty save file !";
        s.save(f, "s");
        assert f.exists() : "Failed to create save file !";

        String ss = s.asString(s.eval("s"));
        assert ss.equals("abcd") : "bad eval of s";
        assert s.rm("s") : "Failed to delete s";
        s.load(f);
        assert s.asString(s.eval("s")).equals("abcd") : "bad restore of s";

        File fa = new File("Rserve" + Math.random() + ".all.save");
        s.savels(fa, "*");
        assert fa.exists() : "Failed to create save file !";
    }

    @Test
    public void testSave_Renjin() throws Exception {
        String str = "abcd";
        r.set("s", str);
        assert ((String) r.eval("s")).equals(str);

        File f2 = new File("Rserve" + Math.random() + ".save");
        r.save(f2, (String[])null);
        File f = new File("Rserve" + Math.random() + ".save");
        r.save(f, (String)null);
        assert !f.exists() : "Created empty save file !";
        r.save(f, "s");
        assert f.exists() : "Failed to create save file !";

        String ss = r.asString(r.eval("s"));
        assert ss.equals("abcd") : "bad eval of s";
        assert r.rm("s") : "Failed to delete s";
        r.load(f);
        assert r.asString(r.eval("s")).equals("abcd") : "bad restore of s";

        File fa = new File("Rserve" + Math.random() + ".all.save");
        r.savels(fa, "*");
        assert fa.exists() : "Failed to create save file !";
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
        assert f.exists(): "Could not find file "+f.getAbsolutePath();

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
