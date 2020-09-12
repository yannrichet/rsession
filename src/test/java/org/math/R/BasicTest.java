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
    R2jsSession q;

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

            public void closeLog() {
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
        
        if (!(tmpdir.isDirectory() || tmpdir.mkdirs())) throw new IllegalArgumentException("Failed to create temp dir");

        s = new RserveSession(l, null,null);
        System.out.println("| R.version:\t" + s.eval("R.version.string"));
        System.out.println("| Rserve.version:\t" + s.eval("installed.packages(lib.loc='" + RserveDaemon.app_dir() + "')[\"Rserve\",\"Version\"]"));

        System.out.println("| getwd():\t" + s.eval("getwd()"));
        System.out.println("| list.files(all.files=TRUE):\t" + Arrays.toString((String[]) s.eval("list.files(all.files=TRUE)")));
        System.out.println("| ls():\t" + Arrays.toString((String[]) s.ls(true)));
//        System.out.println("| tmpdir:\t" + tmpdir.getAbsolutePath());
//        if (!(tmpdir.isDirectory() || tmpdir.mkdir())) {
//            throw new IOException("Cannot access tmpdir=" + tmpdir);
//        }

        System.out.println("| getwd():\t" + s.eval("getwd()"));
        System.out.println("| list.files(all.files=TRUE):\t" + Arrays.toString((String[]) s.eval("list.files(all.files=TRUE)")));
        System.out.println("| ls():\t" + Arrays.toString((String[]) s.ls(true)));

        r = RenjinSession.newInstance(l, prop);
        System.out.println("| R.version:\t" + r.eval("R.version.string"));

        System.out.println("| getwd():\t" + r.eval("getwd()"));
        System.out.println("| list.files(all.files=TRUE):\t" + Arrays.toString((String[]) r.eval("list.files(all.files=TRUE)")));
        System.out.println("| ls():\t" + Arrays.toString((String[]) r.ls(true)));

        q = R2jsSession.newInstance(l, null); 
        System.out.println("| R.version:\t" + q.eval("R.version.string"));

        System.out.println("| getwd():\t" + q.eval("getwd()"));
//        System.out.println("| list.files(all.files=TRUE):\t" + Arrays.toString((String[]) q.eval("list.files(all.files=TRUE)")));
        System.out.println("| ls():\t" + Arrays.toString((String[]) q.ls(true)));
    }

    @After
    public void tearDown() {
        try {
            Thread.sleep(1000);
        } catch (InterruptedException ex) {
            ex.printStackTrace();
        }
        s.closeLog();
        r.closeLog();
        q.closeLog();

        System.out.println("========================================================================");
        System.out.println(s.notebook());
        System.out.println(r.notebook());
        System.out.println(q.notebook());
        System.out.println("========================================================================");
        
        s.end();
        r.end();
        q.end();
    }

    @Test
    public void testWriteCSVAnywhere_Rserve() throws Exception {
        File totof = new File(new File("..").getAbsoluteFile(), "toto.csv");
        if (totof.exists()) {
            assert totof.delete() : "Failed to delete " + totof;
        }
        assert !totof.exists() : "Indeed, did not deleted " + totof;
        s.voidEval("write.csv(runif(10),'" + totof.getAbsolutePath().replace("\\", "/") + "')");
        assert totof.isFile() : "Failed to write file";
    }

    @Test
    public void testWriteCSVAnywhere_Renjin() throws Exception {
        File totof = new File(new File("..").getAbsoluteFile(), "toto.csv");
        if (totof.exists()) {
            assert totof.delete() : "Failed to delete " + totof;
        }
        assert !totof.exists() : "Indeed, did not deleted " + totof;
        r.voidEval("write.csv(runif(10),'" + totof.getAbsolutePath().replace("\\", "/") + "')");
        assert totof.isFile() : "Failed to write file";
    }

    @Test
    public void testWriteCSVAnywhere_R2Js() throws Exception {
        File totof = new File(new File("..").getAbsoluteFile(), "toto.csv");
        if (totof.exists()) {
            assert totof.delete() : "Failed to delete " + totof;
        }
        assert !totof.exists() : "Indeed, did not deleted " + totof;
        q.voidEval("write.csv(runif(10),'" + totof.getAbsolutePath().replace("\\", "/") + "')");
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
        assert Double.isNaN((Double) q.eval("NaN"));
        assert ((Boolean) q.eval("TRUE")) == true;
        assert ((Double) q.eval("0.123")) == 0.123 : ((Double) q.eval("0.123")) + " != " + 0.123;
        assert ((Double) q.eval("pi")) - 3.141593 < 0.0001;
        assert ((Double) q.eval("0.123")) == 0.123 : s.eval("0.123").toString();
        assert ((Double) q.eval("(0.123)+pi")) - 3.264593 < 0.0001;
        assert ((double[]) q.eval("runif(10)")).length == 10;
        assert ((double[][]) q.eval("array(0.0,c(4,3))")).length == 4;
        assert ((double[]) q.eval("array(array(0.0,c(4,3)))")).length == 12;
        assert ((double[][]) q.eval("cbind(runif(10),runif(10))")).length == 10: q.eval("cbind(runif(10),runif(10))");
        assert ((Map) q.eval("list(aa=rnorm(10),bb=rnorm(10))")).size() == 2;
        assert ((String) q.eval("'abcd'")).equals("abcd");
        assert ((String[]) q.eval("c('abcd','sdfds')")).length == 2;
    }
    
    @Test
    public void testEval_R2Js() throws Exception {
        System.err.println("====================================== R2Js");
       q.debug_js=true;
        assert q.eval("if (1<2) print('a') else print('b')").toString().equals("a"):q.eval("if (1<2) print('a') else print('b')");
        assert q.eval("if (1<2) print(\"a\") else print(\"b\")").toString().equals("a"):q.eval("if (1<2) print(\"a\"else print(\"b\")");
        assert q.eval("if (1>2) print(\"a\") else print(\"*\")").toString().equals("*"):q.eval("if (1<2) print(\"a\"else print(\"*\")");
        //assert q.eval("( if (1<2) print('a') else print('b') )").toString().equals("a"):q.eval("( if (1<2) print('a') else print('b') )");
    }
        
    @Test
    public void testEval_Renjin() throws Exception {
        System.err.println("====================================== R2Js");
        assert r.eval("if (1<2) print('a') else print('b')").toString().equals("a"):r.eval("if (1<2) print('a') else print('b')");
        //assert q.eval("( if (1<2) print('a') else print('b') )").toString().equals("a"):q.eval("( if (1<2) print('a') else print('b') )");
    }
        
    @Test
    public void testEval_Rserve() throws Exception {
        System.err.println("====================================== R2Js");
        assert s.eval("if (1<2) print('a') else print('b')").toString().equals("a"):s.eval("if (1<2) print('a') else print('b')");
        //assert q.eval("( if (1<2) print('a') else print('b') )").toString().equals("a"):q.eval("( if (1<2) print('a') else print('b') )");
    }
    
    
    @Test
    public void testSet_Rserve() throws Exception {
        System.err.println("====================================== Rserve");

        assert s.set("ddd", new double[3][0], "ddd.a", "ddd.b", "ddd.c") : "Failed to setup empty dataframe";

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
        assert r.print("lm").contains("m1 m2") && r.print("lm").contains("2  3") : "Bad print lm: " + r.print("lm");
        assert r.asDouble(r.eval("lm$m1[2]")) == 2.0 : "Bad values in list: " + r.eval("print(lm)");

        assert r.set("la", r.asMatrix(r.eval("a")), "a1") : "Failed to create list";
        assert r.print("la").contains("a1") && r.print("la").contains("2") && r.print("la").contains("1") : "Bad print la: " + r.print("la");

        assert r.set("ld", r.asMatrix(r.eval("d")), "d1") : "Failed to create list";
        assert r.print("ld").contains("d1") && r.print("ld").contains("1") && r.print("ld").contains("0") : "Bad print ld: " + r.print("ld");
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
        //assert q.set("n", null, "a") : "Failed to create NULL matrix";
        //assert q.eval("n").toString().equals("{a=null}") : "Bad print of object: " + q.eval("n").toString();
        double[][] m = new double[][]{{0.0, 1.0}, {2.0, 3.0}};
        q.set("m", m);
        assert Arrays.deepEquals(m, q.asMatrix(q.eval("m"))) : "Failed asMatrix: " + Arrays.deepToString(m) + " != " + Arrays.deepToString(q.asMatrix(q.eval("m")));

        double[] a = new double[]{0, 1};
        q.set("a", a);
        // !!! R used to put arrays in column matrix when aq.matrix called
        assert Arrays.deepEquals(new double[][]{{a[0]}, {a[1]}}, q.asMatrix(q.eval("a"))) : "Failed asMatrix: " + Arrays.deepToString(new double[][]{{a[0]}, {a[1]}}) + " != " + Arrays.deepToString(q.asMatrix(q.eval("a")));

        double d = 0;
        q.set("d", d);
        assert Arrays.deepEquals(new double[][]{{d}}, q.asMatrix(q.eval("d"))) : "Failed asMatrix: " + Arrays.deepToString(new double[][]{{d}}) + " != " + Arrays.deepToString(q.asMatrix(q.eval("d")));

        //assert q.set("l", new double[][]{{0, 1}}, "a", "b") : "Failed to create list";
        //assert q.set("l", new double[][]{{0}, {1}}, "a") : "Failed to create list";
        //assert q.set("lm", q.asMatrix(q.eval("m")), "m1", "m2") : "Failed to create list";
        // TODO: support and uncomment these lines
        //assert q.print("lm").contains("m1 m2") && q.print("lm").contains("2  3") : "Bad print: " + q.print("lm");
        //assert q.asDouble(q.eval("lm$m1[2]")) == 2.0 : "Bad values in list: " + q.eval("print(lm)");
        //assert q.set("la", q.asMatrix(q.eval("a")), "a1") : "Failed to create list";
        // TODO: support and uncomment these lines
        //assert q.print("la").contains("a1") && q.print("la").contains("2  1") : "Bad print: " + q.print("la");
        //assert q.set("ld", q.asMatrix(q.eval("d")), "d1") : "Failed to create list";
        // TODO: support and uncomment these lines
        //assert q.print("ld").contains("d1") && q.print("ld").contains("1  0") : "Bad print: " + q.print("ld");
    }

    @Test
    public void testEnv_R2Js() throws Exception {
        System.err.println("====================================== R2Js");
        q.debug_js = true;
        
        double v = 123.456;
        assert q.set("v", v);
        assert Arrays.deepEquals(q.ls(false), new String[]{"v"}) : Arrays.deepToString(q.ls(false));
        
        q.copyGlobalEnv("myenv");
        q.setGlobalEnv("myenv");
        assert q.getGlobalEnv().equals("myenv"):q.getGlobalEnv();
        assert Arrays.deepEquals(q.ls(false), new String[]{"v"}) : Arrays.deepToString(q.ls(false));

        q.setGlobalEnv("myenv2");
        assert Arrays.deepEquals(q.ls(false), new String[]{}) : Arrays.deepToString(q.ls(false));
        
        q.setGlobalEnv("myenv");
        assert Arrays.deepEquals(q.ls(false), new String[]{"v"}) : Arrays.deepToString(q.ls(false));
        
        System.err.println("# cleanup first env");
        // cleanup
        q.setGlobalEnv(null);
        assert q.rmAll();
        assert Arrays.deepEquals(q.ls(false), new String[]{}) : Arrays.deepToString(q.ls(false));

                System.err.println("# put f1 f2 in env");
        String f2 = "f2 = function(x) {return(1-x)}";
        String f1 = "f1 = function(x) {return(f2(x))}";
        assert q.voidEval(f2);
        assert q.voidEval(f1);
        assert Arrays.deepEquals(q.ls(false), new String[]{"f1", "f2"}) : Arrays.deepToString(q.ls(false));
        assert q.set("v", v);
        assert (Double) q.eval("f1(v)") == 1 - v : q.eval("f1(v)");

//        q.savels(new File("1.save"), "");
        System.err.println("# copy env to myenvf");
        q.copyGlobalEnv("myenvf");
        System.err.println("# cleanup previous env");
        assert q.rmAll();
        System.err.println("# switch env to myenvf");
        q.setGlobalEnv("myenvf");

//        q.savels(new File("2.save"), "");
        assert Arrays.deepEquals(q.ls(false), new String[]{"v", "f1", "f2"}) : Arrays.deepToString(q.ls(false));
        System.err.println(q.eval("print(f1)"));
        assert (Double) q.eval("f1(v)") == 1 - v : q.eval("f1(v)");

        System.err.println("# switch env to myenvf2");
        q.setGlobalEnv("myenvf2");
        assert Arrays.deepEquals(q.ls(false), new String[]{}) : Arrays.deepToString(q.ls(false));

        System.err.println("# switch env to myenvf");
        q.setGlobalEnv("myenvf");
        assert Arrays.deepEquals(q.ls(false), new String[]{"v", "f1", "f2"}) : Arrays.deepToString(q.ls(false));
        assert (Double) q.eval("f1(v)") == 1 - v : q.eval("f1(v)");
    }

    @Test
    public void testEnv_Renjin() throws Exception {
        System.err.println("====================================== Renjin");
        
        double v = 123.456;
        assert r.set("v", v);
        assert Arrays.deepEquals(r.ls(false), new String[]{"v"}) : Arrays.deepToString(r.ls(false));

        r.copyGlobalEnv("myenv");
        r.setGlobalEnv("myenv");
        assert r.getGlobalEnv().equals("myenv") : r.getGlobalEnv();
        assert Arrays.deepEquals(r.ls(false), new String[]{"v"}) : Arrays.deepToString(r.ls(false));

        r.setGlobalEnv("myenv2");
        assert Arrays.deepEquals(r.ls(false), new String[]{}) : Arrays.deepToString(r.ls(false));

        r.setGlobalEnv("myenv");
        assert Arrays.deepEquals(r.ls(false), new String[]{"v"}) : Arrays.deepToString(r.ls(false));
        
        System.err.println("# cleanup first env");
        // cleanup
        r.setGlobalEnv(null);
        assert r.rmAll();
        assert Arrays.deepEquals(r.ls(false), new String[]{}) : Arrays.deepToString(r.ls(false));

        System.err.println("# put f1 f2 in env");
        String f2 = "f2 = function(x) {return(1-x)}";
        String f1 = "f1 = function(x) {return(f2(x))}";
        assert r.voidEval(f2);
        assert r.voidEval(f1);
        assert Arrays.deepEquals(r.ls(false), new String[]{"f1", "f2"}) : Arrays.deepToString(r.ls(false));
        assert r.set("v", v);
        assert (Double) r.eval("f1(v)") == 1 - v : r.eval("f1(v)");

//        r.savels(new File("1.save"), "");
        System.err.println("# copy env to myenvf");
        r.copyGlobalEnv("myenvf");
        System.err.println("# cleanup previous env");
        assert r.rmAll();
        System.err.println("# switch env to myenvf");
        r.setGlobalEnv("myenvf");

//        r.savels(new File("2.save"), "");
        String[] ls = r.ls(false);
        Arrays.sort(ls);
        assert Arrays.deepEquals(ls, new String[]{"f1", "f2","v"}) : Arrays.deepToString(ls);
        System.err.println(r.eval("print(f1)"));
        assert (Double) r.eval("f1(v)") == 1 - v : r.eval("f1(v)");

        System.err.println("# switch env to myenvf2");
        r.setGlobalEnv("myenvf2");
        assert Arrays.deepEquals(r.ls(false), new String[]{}) : Arrays.deepToString(r.ls(false));

        System.err.println("# switch env to myenvf");
        r.setGlobalEnv("myenvf");
        ls = r.ls(false);
        Arrays.sort(ls);
        assert Arrays.deepEquals(ls, new String[]{"f1", "f2","v"}) : Arrays.deepToString(ls);
        assert (Double) r.eval("f1(v)") == 1 - v : r.eval("f1(v)");
    }
    
    @Test
    public void testEnv_Rserve() throws Exception {
        System.err.println("====================================== Rserve");

        double v = 123.456;
        assert s.set("v", v);
        assert Arrays.deepEquals(s.ls(false), new String[]{"v"}) : Arrays.deepToString(s.ls(false));

        s.copyGlobalEnv("myenv");
        s.setGlobalEnv("myenv");
        assert s.getGlobalEnv().equals("myenv") : s.getGlobalEnv();
        assert Arrays.deepEquals(s.ls(false), new String[]{"v"}) : Arrays.deepToString(s.ls(false));

        s.setGlobalEnv("myenv2");
        assert Arrays.deepEquals(s.ls(false), new String[]{}) : Arrays.deepToString(s.ls(false));

        s.setGlobalEnv("myenv");
        assert Arrays.deepEquals(s.ls(false), new String[]{"v"}) : Arrays.deepToString(s.ls(false));

        System.err.println("# cleanup first env");
        // cleanup
        s.setGlobalEnv(null);
        assert s.rmAll();
        assert Arrays.deepEquals(s.ls(false), new String[]{}) : Arrays.deepToString(s.ls(false));

        System.err.println("# put f1 f2 in env");
        String f2 = "f2 = function(x) {return(1-x)}";
        String f1 = "f1 = function(x) {return(f2(x))}";
        assert s.voidEval(f2);
        assert s.voidEval(f1);
        assert Arrays.deepEquals(s.ls(false), new String[]{"f1", "f2"}) : Arrays.deepToString(s.ls(false));
        assert s.set("v", v);
        assert (Double) s.eval("f1(v)") == 1 - v : s.eval("f1(v)");

//        s.savels(new File("1.save"), "");
        System.err.println("# copy env to myenvf");
        s.copyGlobalEnv("myenvf");
        System.err.println("# cleanup previous env");
        assert s.rmAll();
        System.err.println("# switch env to myenvf");
        s.setGlobalEnv("myenvf");

//        s.savels(new File("2.save"), "");
        String[] ls = s.ls(false);
        Arrays.sort(ls);
        assert Arrays.deepEquals(ls, new String[]{"f1", "f2","v"}) : Arrays.deepToString(ls);
        System.err.println(s.eval("print(f1)"));
        assert (Double) s.eval("f1(v)") == 1 - v : s.eval("f1(v)");

        System.err.println("# switch env to myenvf2");
        s.setGlobalEnv("myenvf2");
        assert Arrays.deepEquals(s.ls(false), new String[]{}) : Arrays.deepToString(s.ls(false));

        System.err.println("# switch env to myenvf");
        s.setGlobalEnv("myenvf");
        ls = s.ls(false);
        Arrays.sort(ls);
        assert Arrays.deepEquals(ls, new String[]{"f1", "f2","v"}) : Arrays.deepToString(ls);
        assert (Double) s.eval("f1(v)") == 1 - v : s.eval("f1(v)");
    }
    
    @Test
    public void testSet_R2Js() throws Exception {
        System.err.println("====================================== R2Js");

        //assert q.set("ddd", new double[3][0], "ddd.a", "ddd.b", "ddd.c") : "Failed to setup empty dataframe";

        assert q.set("n", null) : "Failed to create NULL object";

        //set
        double c = Math.random();
        q.set("c", c);
        assert ((Double) q.eval("c")) == c;

        double[] C = new double[10];
        q.set("C", C);
        assert ((double[]) q.eval("C")).length == C.length;

        double[][] CC = new double[10][2];
        CC[9][1] = Math.random();
        q.set("CC", CC);
        assert ((double[][]) q.eval("CC"))[9][1] == CC[9][1];
        
        assert ((double[]) q.eval("CC[1,]")).length == CC[0].length;

        //System.err.println(q.cat(q.ls("C")));
        //assert q.ls("C").length == 2 : "invalid ls(\"C\") : " + q.cat(q.ls("C"));

        String str = "abcd";
        q.set("s", str);
        assert ((String) q.eval("s")).equals(str);

        String[] Str = {"abcd", "cdef"};
        q.set("S", Str);
        assert ((String[]) q.eval("S")).length == Str.length;

        // TODO: support and uncomment these lines
//        assert ((String) q.eval("S[1]")).equals(Str[0]);
//        q.set("df", new double[][]{{1, 2, 3}, {4, 5, 6}, {7, 8, 9}, {10, 11, 12}}, "x1", "x2", "x3");
//        assert (Double) (q.eval("df$x1[3]")) == 7;
    }

    @Test
    public void testSet_Renjin() throws Exception {
        System.err.println("====================================== Renjin");

        assert r.set("ddd", new double[3][0], "ddd.a", "ddd.b", "ddd.c") : "Failed to setup empty dataframe";

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
    public void testSource_Rserve() throws Exception {
        s.source(new File("src/test/R/test.R"));
        assert s.asDouble(s.eval("a")) == 1;
        assert s.asDouble(s.eval("b")) == 2;
        double x = Math.random();

        assert s.asDouble(s.eval("f(" + x + ")")) == x + 1;
        assert s.asDouble(s.eval("g(" + x + ")")) == x + 2;
        assert s.asDouble(s.eval("h(" + x + ")")) == x + 3;
    }

    @Test
    public void testSource_Renjin() throws Exception {
        r.source(new File("src/test/R/test.R"));
        assert r.asDouble(r.eval("a")) == 1;
        assert r.asDouble(r.eval("b")) == 2;
        double x = Math.random();

        assert r.asDouble(r.eval("f(" + x + ")")) == x + 1;
        assert r.asDouble(r.eval("g(" + x + ")")) == x + 2;
        assert r.asDouble(r.eval("h(" + x + ")")) == x + 3;
    }

    @Test
    public void testSource_R2Js() throws Exception {
        q.source(new File("src/test/R/test.R"));
        assert q.asDouble(q.eval("a")) == 1;
        assert q.asDouble(q.eval("b")) == 2;
        double x = Math.random();

        assert q.asDouble(q.eval("f(" + x + ")")) == x + 1;
        assert q.asDouble(q.eval("g(" + x + ")")) == x + 2;
        assert q.asDouble(q.eval("h(" + x + ")")) == x + 3;
    }
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

        File f2 = new File("Renjin" + Math.random() + ".save");
        r.save(f2, (String[]) null);
        File f = new File("Renjin" + Math.random() + ".save");
        r.save(f, (String) null);
        assert !f.exists() : "Created empty save file !";
        r.save(f, "s");
        assert f.exists() : "Failed to create save file !: "+f.getAbsolutePath();

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
        q.set("s", str);
        assert ((String) q.eval("s")).equals(str);

        File f2 = new File("R2Js" + Math.random() + ".save");
        q.save(f2, (String[]) null);
        File f = new File("R2Js" + Math.random() + ".save");
        q.save(f, (String) null);
        assert !f.exists() : "Created empty save file !";
        q.save(f, "s");
        //using f instead of  new File(f.getAbsolutePath()) fails ! fs Sync issue ?
        assert f.exists() : "Failed to create save file !: "+f.getAbsolutePath();

        String ss = q.asString(q.eval("s"));
        assert ss.equals("abcd") : "bad eval of s";
        assert q.rm("s") : "Failed to delete s";
        q.load(f);
        assert q.asString(q.eval("s")).equals("abcd") : "bad restore of s";

        File fa = new File("R2Js" + Math.random() + ".all.save");
        assert !fa.exists() : "Already created save file !";
        q.savels(fa, ".*");
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
        s.toPNG(jpg, 400, 400, "plot(rnorm(10))");
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
        File jpg = new File(tmpdir, "titi" + rand + ".png");
        r.toPNG(jpg, 400, 400, "plot(rnorm(10))");
        assert jpg.exists();

        r.toPNG(jpg, 400, 400, "plot(rnorm(10))");
        assert jpg.exists();

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
        q.set("c", c);
        q.set("z", 0.0);
        assert ((Double) q.eval("c")) == c;

        //get/put files
        String[] ls = (String[]) q.eval("ls()");
        //System.err.println("ls ...> "+Arrays.asList(ls));
        Arrays.sort(ls);
        assert ls.length == 2 : "ls.length != 2 : " + Arrays.asList(ls);
        assert ls[0].equals("c") : q.toString(ls) + "[0]=" + ls[3];
        q.voidEval("save(file='c" + rand + ".Rdata','c')");
        q.rm("c");
        //ls = (String[]) castStrict(s.eval("ls()"));
        ls = q.ls();
        Arrays.sort(ls);
        assert !ls[0].equals("c") : q.toString(ls) + "[0]=" + ls[3];
        q.eval("load(file='c" + rand + ".Rdata')");
        p.println((q.eval("c")));

        //save
        File f = new File(tmpdir, "save" + rand + ".Rdata");
        if (f.exists()) {
            f.delete();
        }
        q.save(f, "c");
        assert new File(tmpdir, "save" + rand + ".Rdata").exists() : "Could not find file " + f.getAbsolutePath();

        p.println("ls=\n" + q.toString(q.eval("ls()")));
        //load
        ls = (String[]) q.eval("ls()");
        Arrays.sort(ls);
        assert ls[0].equals("c") : q.toString(ls) + "=" + Arrays.asList(ls);
        q.rm("c");
        assert ((String[])q.eval("ls()")).length == 1: "More than 1 object in ls()";
        q.load(f);
        ls = (String[]) q.eval("ls()");
        Arrays.sort(ls);
        assert ls[0].equals("c") : q.toString(ls) + "=" + Arrays.asList(ls);

//        //toJPEG
//        /*File jpg = new File(tmpdir, "titi" + rand + ".png");
//         q.toPNG(jpg, 400, 400, "plot(rnorm(10))");
//         assert jpg.exists();*/
//
//        //toTXT
//        String txt = q.print("summary(rnorm(100))");
//        System.out.println(txt);
//        assert txt.length() > 0;
//
//        //toHTML
//        String html = q.asHTML("summary(rnorm(100))");
//        System.out.println(html);
//        assert html.length() > 0;
    }
    
    // localOS  remoteOS  remoteWD  localpath       remotepath                  
    // Win      Win       C:\toto   C:\titi\tata.R  C:\toto\C_.__-_titi_-_tata.R
    // Win      Lin       /toto     C:\titi\tata.R  /toto/C_.__-_titi_-_tata.R  
    // Lin      Lin       /toto     /titi/tata.R    /toto/_-_titi_-_tata.R  
    // Lin      Win       C:\toto   /titi/tata.R    C:/toto/_-_titi_-_tata.R
    @Test
    public void testRemoteLocalFiles_R2Js() throws Exception {
        System.err.println("====================================== R2Js");
        
        File rel_l = new File("titi/tata.R");
        System.err.println(rel_l.getAbsolutePath());
        System.err.println("WD: "+q.getwd());
        File rel_re = q.local2remotePath(rel_l);
        System.err.println(rel_re.getAbsolutePath());
        File rel_l2 = q.remote2localPath(rel_re);
        System.err.println(rel_l2.getAbsolutePath());
        
        assert rel_l.getAbsolutePath().equals(rel_l2.getAbsolutePath()) : rel_l.getAbsolutePath() +" != "+rel_l2.getAbsolutePath();
    
        File l = new File("/titi/tata.R");
        System.err.println(l.getAbsolutePath());
        System.err.println("WD: "+q.getwd());
        File re = q.local2remotePath(l);
        System.err.println(re.getAbsolutePath());
        File l2 = q.remote2localPath(re);
        System.err.println(l2.getAbsolutePath());
        
        assert l.getAbsolutePath().equals(l2.getAbsolutePath()) : l.getAbsolutePath() +" != "+l2.getAbsolutePath();
    }
    
    @Test
    public void testRemoteLocalFiles_Renjin() throws Exception {
        System.err.println("====================================== Renjin");
        
        File rel_l = new File("titi/tata.R");
        System.err.println(rel_l.getAbsolutePath());
        System.err.println("WD: "+r.getwd());
        File rel_re = r.local2remotePath(rel_l);
        System.err.println(rel_re.getAbsolutePath());
        File rel_l2 = r.remote2localPath(rel_re);
        System.err.println(rel_l2.getAbsolutePath());
        
        assert rel_l.getAbsolutePath().equals(rel_l2.getAbsolutePath()) : rel_l.getAbsolutePath() +" != "+rel_l2.getAbsolutePath();

        File l = new File("/titi/tata.R");
        System.err.println(l.getAbsolutePath());
        System.err.println("WD: "+r.getwd());
        File re = r.local2remotePath(l);
        System.err.println(re.getAbsolutePath());
        File l2 = r.remote2localPath(re);
        System.err.println(l2.getAbsolutePath());
        
        assert l.getAbsolutePath().equals(l2.getAbsolutePath()) : l.getAbsolutePath() +" != "+l2.getAbsolutePath();
    }
        
    @Test
    public void testRemoteLocalFiles_Rserve() throws Exception {
        System.err.println("====================================== Rserve");
        
        File rel_l = new File("titi/tata.R");
        System.err.println(rel_l.getAbsolutePath());
        System.err.println("WD: "+s.getwd());
        File rel_re = s.local2remotePath(rel_l);
        System.err.println(rel_re.getAbsolutePath());
        File rel_l2 = s.remote2localPath(rel_re);
        System.err.println(rel_l2.getAbsolutePath());
        
        assert rel_l.getAbsolutePath().equals(rel_l2.getAbsolutePath()) : rel_l.getAbsolutePath() +" != "+rel_l2.getAbsolutePath();

        File l = new File("/titi/tata.R");
        System.err.println(l.getAbsolutePath());
        System.err.println("WD: "+s.getwd());
        File re = s.local2remotePath(l);
        System.err.println(re.getAbsolutePath());
        File l2 = s.remote2localPath(re);
        System.err.println(l2.getAbsolutePath());
        
        assert l.getAbsolutePath().equals(l2.getAbsolutePath()) : l.getAbsolutePath() +" != "+l2.getAbsolutePath();
    }
}
