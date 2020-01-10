package org.math.R;

import java.awt.GraphicsEnvironment;
import java.io.File;
import java.io.IOException;
import java.io.PrintStream;
import java.util.Arrays;
import java.util.Properties;
import javax.swing.JFrame;
import javax.swing.JPanel;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.math.R.Rsession.RException;

/**
 *
 * @author richet
 */
public class RPanelsTest {

    PrintStream p = System.err;
    //RserverConf conf;
    RserveSession s;
    RenjinSession r;
    R2jsSession q;
    int rand = Math.round((float) Math.random() * 10000);
    File tmpdir = new File(System.getProperty("java.io.tmpdir"),""+rand);

    public static void main(String args[]) {
        if (GraphicsEnvironment.isHeadless()) {
            return;
        }
        org.junit.runner.JUnitCore.main(RPanelsTest.class.getName());
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

        RserverConf conf = new RserverConf(null, -1, null, null, prop);
        s = RserveSession.newInstanceTry(l, conf);
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
        //A shutdown hook kills all Rserve at the end.
        r.closeLog();

        q.closeLog();

        System.out.println("========================================================================");
        System.out.println(s.notebook());
        System.out.println(r.notebook());
        System.out.println(q.notebook());
        System.out.println("========================================================================");
    }


    void frame(JPanel p) {
        JFrame frame = new JFrame("User Details");
        frame.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);

        frame.getContentPane().add(p);
        // important!
        frame.pack();

        frame.setVisible(true);
    }

    @Test
    public void testRLogPanel_Rserve() throws Exception {
        System.err.println("====================================== testRLogPanel_Rserve");
        RLogPanel p = new RLogPanel();
        s.addLogger(p);
        frame(p);
        p.log("Rserve", RLog.Level.INFO);

        s.eval("1+1");

        p.flush();
        Thread.sleep(1000);

        s.eval("rnorm(10)");

        p.flush();
        Thread.sleep(1000);
    }

    @Test
    public void testRLogPanel_Renjin() throws Exception {
        System.err.println("====================================== testRLogPanel_Renjin");
        RLogPanel p = new RLogPanel();
        r.addLogger(p);
        frame(p);
        p.log("Renjin", RLog.Level.INFO);

        r.eval("1+1");

        p.flush();
        Thread.sleep(1000);

        r.eval("rnorm(10)");

        p.flush();
        Thread.sleep(1000);
    }
    
    @Test
    public void testRLogPanel_R2js() throws Exception {
        System.err.println("====================================== testRLogPanel_R2js");
        RLogPanel p = new RLogPanel();
        q.addLogger(p);
        frame(p);
        p.log("R2js", RLog.Level.INFO);

        q.eval("1+1");

        p.flush();
        Thread.sleep(1000);

        q.eval("rnorm(10)");

        p.flush();
        Thread.sleep(1000);
    }

    @Test
    public void testRLogPanel_RserveError() throws Exception {
        System.err.println("====================================== testRLogPanel_RserveError");
        RLogPanel p = new RLogPanel();
        s.addLogger(p);
        frame(p);

        p.log("RserveError", RLog.Level.INFO);
        try {
            s.eval("rnorm(10;");
        } catch (RException e) {
            assert e.getMessage().contains("';'") : "Bad error detected: " + e.getMessage();
        }

        p.flush();
        Thread.sleep(1000);
    }

    @Test
    public void testRLogPanel_RenjinError() throws Exception {
        System.err.println("====================================== testRLogPanel_RserveError");
        RLogPanel p = new RLogPanel();
        r.addLogger(p);
        frame(p);

        p.log("RserveError", RLog.Level.INFO);
        try {
            r.eval("rnorm(10;");
        } catch (RException e) {
            assert e.getMessage().contains("';'") : "Bad error detected: " + e.getMessage();
        }

        p.flush();
        Thread.sleep(1000);
    }
    
    
 @Test
    public void testRLogPanel_R2jsError() throws Exception {
        System.err.println("====================================== testRLogPanel_R2jsError");
        RLogPanel p = new RLogPanel();
        q.addLogger(p);
        frame(p);

        p.log("R2jsError", RLog.Level.INFO);
        try {
            q.eval("rnorm(10;");
        } catch (RException e) {
            assert e.getMessage().contains(";") : "Bad error detected: " + e.getMessage();
        }

        p.flush();
        Thread.sleep(1000);
    }

    @Test
    public void testRObjPanel_Renjin() throws Exception {
        System.err.println("====================================== testRObjPanel_Renjin");
        RObjectsPanel p = new RObjectsPanel(r);
        frame(p);

        r.set("a", 2.0);
        System.err.println(Arrays.asList(r.ls()));

        p.update();
        Thread.sleep(1000);

    }

    @Test
    public void testRObjPanel_Rsession() throws Exception {
        System.err.println("====================================== testRObjPanel_Rsession");
        RObjectsPanel p = new RObjectsPanel(s);
        frame(p);

        s.set("a", 2.0);
        System.err.println(Arrays.asList(s.ls()));

        p.update();
        Thread.sleep(1000);

    }  
    
    @Test
    public void testRObjPanel_R2js() throws Exception {
        System.err.println("====================================== testRObjPanel_R2js");
        RObjectsPanel p = new RObjectsPanel(q);
        frame(p);

        q.set("a", 2.0);
        System.err.println(Arrays.asList(q.ls()));

        p.update();
        Thread.sleep(1000);

    }

}
