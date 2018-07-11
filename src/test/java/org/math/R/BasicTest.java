package org.math.R;

import java.io.File;
import java.io.IOException;
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
    R2JsSession r2jsSession;
    
    int rand = Math.round((float) Math.random() * 10000);
    File tmpdir = new File(System.getProperty("java.io.tmpdir"), "" + rand);

    public static void main(String args[]) {
        org.junit.runner.JUnitCore.main(BasicTest.class.getName());
    }

    @Before
    public void setUp() throws Exception {
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
        System.out.println("| R.version:\t" + s.eval("R.version.string"));
        System.out.println("| Rserve.version:\t" + s.eval("installed.packages(lib.loc='" + RserveDaemon.app_dir() + "')[\"Rserve\",\"Version\"]"));

        System.out.println("| tmpdir:\t" + tmpdir.getAbsolutePath());
        if (!(tmpdir.isDirectory() || tmpdir.mkdir())) {
            throw new IOException("Cannot access tmpdir=" + tmpdir);
        }

        System.out.println("| getwd():\t" + s.eval("getwd()"));
        System.out.println("| list.files(all.files=TRUE):\t" + Arrays.toString((String[]) s.eval("list.files(all.files=TRUE)")));
        System.out.println("| ls():\t" + Arrays.toString((String[]) s.ls(true)));

        r = RenjinSession.newInstance(l, prop);

        System.out.println("| getwd():\t" + r.eval("getwd()"));
        System.out.println("| list.files(all.files=TRUE):\t" + Arrays.toString((String[]) r.eval("list.files(all.files=TRUE)")));
        System.out.println("| ls():\t" + Arrays.toString((String[]) r.ls(true)));
        
        r2jsSession = R2JsSession.newInstance(l, null);
        
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
        
        r2jsSession.close();
    }

    @Test
    public void testWriteCSVAnywhere_Rserve() throws Exception {
        File totof = new File("..", "toto.csv");
        if (totof.exists()) {
            assert totof.delete() : "Failed to delete " + totof;
        }
        assert !totof.exists() : "Indeed, did not deleted " + totof;
        s.voidEval("write.csv(runif(10),'" + totof.getAbsolutePath().replace("\\", "/") + "')");
        assert totof.isFile() : "Failed to write file";
    }

    @Test
    public void testWriteCSVAnywhere_Renjin() throws Exception {
        File totof = new File("..", "toto.csv");
        if (totof.exists()) {
            assert totof.delete() : "Failed to delete " + totof;
        }
        assert !totof.exists() : "Indeed, did not deleted " + totof;
        r.voidEval("write.csv(runif(10),'" + totof.getAbsolutePath().replace("\\", "/") + "')");
        assert totof.isFile() : "Failed to write file";
    }
    
    @Test
    public void testWriteCSVAnywhere_R2Js() throws Exception {
        File totof = new File("..", "toto.csv");
        if (totof.exists()) {
            assert totof.delete() : "Failed to delete " + totof;
        }
        assert !totof.exists() : "Indeed, did not deleted " + totof;
        r2jsSession.voidEval("write.csv(runif(10),'" + totof.getAbsolutePath().replace("\\", "/") + "')");
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
    public void testCast_R2Js() throws Exception {
        System.err.println("====================================== R2Js");
        //cast
        assert Double.isNaN((Double) r2jsSession.eval("NaN"));
        assert ((Boolean) r2jsSession.eval("TRUE")) == true;
        assert ((Double) r2jsSession.eval("0.123")) == 0.123;
        assert ((Double) r2jsSession.eval("pi")) - 3.141593 < 0.0001;
        assert ((Double) r2jsSession.eval("0.123")) == 0.123 : s.eval("0.123").toString();
        assert ((Double) r2jsSession.eval("(0.123)+pi")) - 3.264593 < 0.0001;
        assert ((double[]) r2jsSession.eval("runif(10)")).length == 10;
        assert ((double[][]) r2jsSession.eval("array(0.0,c(4,3))")).length == 4;
        assert ((double[]) r2jsSession.eval("array(array(0.0,c(4,3)))")).length == 12;
        
        // TODO: uncomment theses tests
        //assert ((double[][]) r2jsSession.eval("cbind(runif(10),runif(10))")).length == 10;
        //assert ((Map) r2jsSession.eval("list(aa=rnorm(10),bb=rnorm(10))")).size() == 2;
        
        assert ((String) r2jsSession.eval("'abcd'")).equals("abcd");
        assert ((String[]) r2jsSession.eval("c('abcd','sdfds')")).length == 2;
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
    public void testMatrix_R2Js() throws Exception {
        System.err.println("====================================== R2Js");

        // TODO: support and uncomment these lines
        //assert r2jsSession.set("n", null, "a") : "Failed to create NULL matrix";
        //assert r2jsSession.eval("n").toString().equals("{a=null}") : "Bad print of object: " + r2jsSession.eval("n").toString();

        double[][] m = new double[][]{{0.0, 1.0}, {2.0, 3.0}};
        r2jsSession.set("m", m);
        assert Arrays.deepEquals(m, r2jsSession.asMatrix(r2jsSession.eval("m"))) : "Failed asMatrix: " + Arrays.deepToString(m) + " != " + Arrays.deepToString(r2jsSession.asMatrix(r2jsSession.eval("m")));

        double[] a = new double[]{0, 1};
        r2jsSession.set("a", a);
        // !!! R used to put arrays in column matrix when ar2jsSession.matrix called
        assert Arrays.deepEquals(new double[][]{{a[0]}, {a[1]}}, r2jsSession.asMatrix(r2jsSession.eval("a"))) : "Failed asMatrix: " + Arrays.deepToString(new double[][]{{a[0]}, {a[1]}}) + " != " + Arrays.deepToString(r2jsSession.asMatrix(r2jsSession.eval("a")));

        double d = 0;
        r2jsSession.set("d", d);
        assert Arrays.deepEquals(new double[][]{{d}}, r2jsSession.asMatrix(r2jsSession.eval("d"))) : "Failed asMatrix: " + Arrays.deepToString(new double[][]{{d}}) + " != " + Arrays.deepToString(r2jsSession.asMatrix(r2jsSession.eval("d")));

        assert r2jsSession.set("l", new double[][]{{0, 1}}, "a", "b") : "Failed to create list";
        assert r2jsSession.set("l", new double[][]{{0}, {1}}, "a") : "Failed to create list";

        assert r2jsSession.set("lm", r2jsSession.asMatrix(r2jsSession.eval("m")), "m1", "m2") : "Failed to create list";
        
        // TODO: support and uncomment these lines
        //assert r2jsSession.print("lm").contains("m1 m2") && r2jsSession.print("lm").contains("2  3") : "Bad print: " + r2jsSession.print("lm");
        //assert r2jsSession.asDouble(r2jsSession.eval("lm$m1[2]")) == 2.0 : "Bad values in list: " + r2jsSession.eval("print(lm)");

        assert r2jsSession.set("la", r2jsSession.asMatrix(r2jsSession.eval("a")), "a1") : "Failed to create list";
        
        // TODO: support and uncomment these lines
        //assert r2jsSession.print("la").contains("a1") && r2jsSession.print("la").contains("2  1") : "Bad print: " + r2jsSession.print("la");

        assert r2jsSession.set("ld", r2jsSession.asMatrix(r2jsSession.eval("d")), "d1") : "Failed to create list";
        
        // TODO: support and uncomment these lines
        //assert r2jsSession.print("ld").contains("d1") && r2jsSession.print("ld").contains("1  0") : "Bad print: " + r2jsSession.print("ld");
    }

    @Test
    public void testSet_R2Js() throws Exception {
        System.err.println("====================================== Renjin");

        assert r2jsSession.set("n", null) : "Failed to create NULL object";

        //set
        double c = Math.random();
        r2jsSession.set("c", c);
        assert ((Double) r2jsSession.eval("c")) == c;

        double[] C = new double[10];
        r2jsSession.set("C", C);
        assert ((double[]) r2jsSession.eval("C")).length == C.length;

        double[][] CC = new double[10][2];
        CC[9][1] = Math.random();
        r2jsSession.set("CC", CC);
        assert ((double[][]) r2jsSession.eval("CC"))[9][1] == CC[9][1];
        
        // TODO: support and uncomment these lines
        //assert ((double[]) r2jsSession.eval("CC[1,]")).length == CC[0].length;

        //System.err.println(r2jsSession.cat(r2jsSession.ls("C")));
        //assert r2jsSession.ls("C").length == 2 : "invalid ls(\"C\") : " + r2jsSession.cat(r2jsSession.ls("C"));

        String str = "abcd";
        r2jsSession.set("s", str);
        assert ((String) r2jsSession.eval("s")).equals(str);

        String[] Str = {"abcd", "cdef"};
        r2jsSession.set("S", Str);
        assert ((String[]) r2jsSession.eval("S")).length == Str.length;
        assert ((String) r2jsSession.eval("S[1]")).equals(Str[0]);

        // TODO: support and uncomment these lines
//        r2jsSession.set("df", new double[][]{{1, 2, 3}, {4, 5, 6}, {7, 8, 9}, {10, 11, 12}}, "x1", "x2", "x3");
//        assert (Double) (r2jsSession.eval("df$x1[3]")) == 7;
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
        s.save(f2, (String[]) null);
        File f = new File("Rserve" + Math.random() + ".save");
        s.save(f, (String) null);
        assert !f.exists() : "Created empty save file !";
        s.save(f, "s");
        assert f.exists() : "Failed to create save file !";

        String ss = s.asString(s.eval("s"));
        assert ss.equals("abcd") : "bad eval of s";
        assert s.rm("s") : "Failed to delete s";
        s.load(f);
        assert s.asString(s.eval("s")).equals("abcd") : "bad restore of s";

        File fa = new File("Rserve" + Math.random() + ".all.save");
        assert !fa.exists() : "Already created save file !";
        s.savels(fa, "*");
        assert fa.exists() : "Failed to create save file !";
    }

    @Test
    public void testSave_Renjin() throws Exception {
        String str = "abcd";
        r.set("s", str);
        assert ((String) r.eval("s")).equals(str);

        File f2 = new File("Rserve" + Math.random() + ".save");
        r.save(f2, (String[]) null);
        File f = new File("Rserve" + Math.random() + ".save");
        r.save(f, (String) null);
        assert !f.exists() : "Created empty save file !";
        r.save(f, "s");
        assert f.exists() : "Failed to create save file !";

        String ss = r.asString(r.eval("s"));
        assert ss.equals("abcd") : "bad eval of s";
        assert r.rm("s") : "Failed to delete s";
        r.load(f);
        assert r.asString(r.eval("s")).equals("abcd") : "bad restore of s";

        File fa = new File("Renjin" + Math.random() + ".all.save");
        assert !fa.exists() : "Already created save file !";
        r.savels(fa, "*");
        assert fa.exists() : "Failed to create save file !";
    }
    
    @Test
    public void testSave_R2Js() throws Exception {
        String str = "abcd";
        r2jsSession.set("s", str);
        assert ((String) r2jsSession.eval("s")).equals(str);

        File f2 = new File("R2Js" + Math.random() + ".save");
        r2jsSession.save(f2, (String[]) null);
        File f = new File("R2Js" + Math.random() + ".save");
        r2jsSession.save(f, (String) null);
        assert !f.exists() : "Created empty save file !";
        r2jsSession.save(f, "s");
        assert f.exists() : "Failed to create save file !";

        String ss = r2jsSession.asString(r2jsSession.eval("s"));
        assert ss.equals("abcd") : "bad eval of s";
        assert r2jsSession.rm("s") : "Failed to delete s";
        r2jsSession.load(f);
        assert r2jsSession.asString(r2jsSession.eval("s")).equals("abcd") : "bad restore of s";

        File fa = new File("R2Js" + Math.random() + ".all.save");
        assert !fa.exists() : "Already created save file !";
        r2jsSession.savels(fa, "*");
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
        assert local.exists() : "Cannot access file " + local.getAbsolutePath();
        s.putFile(new File(tmpdir, "c" + rand + ".Rdata"));

        //save
        File f = new File(tmpdir, "save" + rand + ".Rdata");
        if (f.exists()) {
            assert f.delete();
        }
        s.save(f, "c");
        assert f.exists() : "Could not find file " + f.getAbsolutePath();

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

        //save
        File f = new File(tmpdir, "save" + rand + ".Rdata");
        if (f.exists()) {
            f.delete();
        }
        r.save(f, "c");
        assert f.exists() : "Could not find file " + f.getAbsolutePath();

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
    
    @Test
    public void testIOFiles_R2Js() throws Exception {
        System.err.println("====================================== R2Js");
        //set
        double c = Math.random();
        r2jsSession.set("c", c);
        r2jsSession.set("z", 0.0);
        assert ((Double) r2jsSession.eval("c")) == c;

        //get/put files
        String[] ls = (String[]) r2jsSession.eval("ls()");
        Arrays.sort(ls);
        assert ls.length == 2 : "ls.length != 2 : " + Arrays.asList(ls);
        assert ls[0].equals("c") : r2jsSession.toString(ls) + "[0]=" + ls[3];
        r2jsSession.eval("save(file='c" + rand + ".Rdata',c)");
        r2jsSession.rm("c");
        //ls = (String[]) castStrict(s.eval("ls()"));
        ls = r2jsSession.ls();
        Arrays.sort(ls);
        assert !ls[0].equals("c") : r2jsSession.toString(ls) + "[0]=" + ls[3];
        r2jsSession.eval("load(file='c" + rand + ".Rdata')");
        p.println((r2jsSession.eval("c")));

        //save
        File f = new File(tmpdir, "save" + rand + ".Rdata");
        if (f.exists()) {
            f.delete();
        }
        r2jsSession.save(f, "c");
        assert f.exists() : "Could not find file " + f.getAbsolutePath();

        p.println("ls=\n" + r2jsSession.toString(r2jsSession.eval("ls()")));
        //load
        ls = (String[]) r2jsSession.eval("ls()");
        Arrays.sort(ls);
        assert ls[0].equals("c") : r2jsSession.toString(ls) + "=" + Arrays.asList(ls);
        r2jsSession.rm("c");
        assert r2jsSession.eval("ls()") instanceof String : "More than 1 object in ls()";
        r2jsSession.load(f);
        ls = (String[]) r2jsSession.eval("ls()");
        Arrays.sort(ls);
        assert ls[0].equals("c") : r2jsSession.toString(ls) + "=" + Arrays.asList(ls);

        //toJPEG
        /*File jpg = new File(tmpdir, "titi" + rand + ".png");
         r2jsSession.toPNG(jpg, 400, 400, "plot(rnorm(10))");
         assert jpg.exists();*/
        //toTXT
        String txt = r2jsSession.print("summary(rnorm(100))");
        System.out.println(txt);
        assert txt.length() > 0;

        //toHTML
        String html = r2jsSession.asHTML("summary(rnorm(100))");
        System.out.println(html);
        assert html.length() > 0;
    }
}
