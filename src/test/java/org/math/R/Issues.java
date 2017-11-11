package org.math.R;

import java.io.File;
import java.io.PrintStream;
import java.util.Properties;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;

/**
 *
 * @author richet
 */
public class Issues {

    PrintStream p = System.err;
    //RserverConf conf;
    RserveSession s;
    RenjinSession r;
    int rand = Math.round((float) Math.random() * 10000);
    File tmpdir = new File("tmp"/*System.getProperty("java.io.tmpdir")*/);

    public static void main(String args[]) {
        org.junit.runner.JUnitCore.main(Issues.class.getName());
    }

    @Before
    public void setUp() {
        RLog l = new RLog() {

            public void log(String string, RLog.Level level) {
                System.out.println("                               " + level + " " + string);
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
    public void Error_when_running_Rsession_eval_on_multiple_lines_string() throws Exception {
        Rsession s = RserveSession.newInstanceTry(System.out, null);
        //    RConnection s = new RConnection();

        String string;

        string = "x = 2 + 4" + "\n"
                + "x" + "\n"
                + "x + 2";

        String y;

        y = s.asString(s.rawEval(string));

        System.out.println(y);

        assert y.contains("8") : "Bad multi-line eval";

        s.end();
    }

    @Test
    public void code_snippet_does_not_compile() throws Exception {
        Rsession s = RserveSession.newInstanceTry(System.out, null);

        double[] rand = (double[]) s.eval("rnorm(10)"); //create java variable from R command

        //...
        s.set("c", Math.random()); //create R variable from java one

        s.save(new File("save.Rdata"), "c"); //save variables in .Rdata
        s.rm("c"); //delete variable in R environment
        s.load(new File("save.Rdata")); //load R variable from .Rdata

        //...
        s.set("df", new double[][]{{1, 2, 3}, {4, 5, 6}, {7, 8, 9}, {10, 11, 12}}, "x1", "x2", "x3"); //create data frame from given vectors
        double value = (double) (s.eval("df$x1[3]")); //access one value in data frame

        //...
        s.toJPEG(new File("plot.jpg"), 400, 400, "plot(rnorm(10))"); //create jpeg file from R graphical command (like plot)

        String html = s.asHTML("summary(rnorm(100))"); //format in html using R2HTML
        System.out.println(html);

        String txt = s.asString("summary(rnorm(100))"); //format in text
        System.out.println(txt);

        //...
        System.out.println(s.installPackage("sensitivity", true)); //install and load R package
        System.out.println(s.installPackage("wavelets", true));

        s.end();
    }
}
