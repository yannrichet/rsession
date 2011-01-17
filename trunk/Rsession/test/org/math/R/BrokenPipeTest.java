/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */
package org.math.R;

import java.util.Properties;
import org.junit.After;
import org.junit.AfterClass;
import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.Test;
import org.rosuda.REngine.REXPMismatchException;

/**
 *
 * @author richet
 */
public class BrokenPipeTest {

    public BrokenPipeTest() {
    }

    public static void main(String args[]) {
        org.junit.runner.JUnitCore.main(BrokenPipeTest.class.getName());
    }

    @Test
    public void doit() {
        try {
            Properties p = new Properties();
            //p.setProperty("http_proxy", "'http://fernex-fre:Fred&Chris71@81.194.12.17:3128'");
            RserverConf conf = RserverConf.parse("R://localhost");
            //System.setProperty("R_HOME", "/Library/Frameworks/R.framework/Versions/2.11/Resources/");
            conf.properties = p;
            Rsession r = new Rsession(new Logger() {

                public void println(String string, Level level) {
                    System.out.println(string);
                }
            }, conf, true);
            double[] array = r.eval("rnorm(10)").asDoubles();
            for (double db : array) {
                System.out.println(db);
            }
            r.eval("library(Rserve)");
            String out = r.asString("rnorm(10)");
            System.out.println(out);
            r.eval("x11()");
            r.eval("png(file='toto.png',width=400,height=400)");
            r.eval("plot(rnorm(10))");
            r.eval("dev.off()");
            //r.toPNG(new File("test.png"),400,400,"plot(rnorm(10))");
        } catch (REXPMismatchException ex) {
            System.out.println("rien");
        }
    }

    @BeforeClass
    public static void setUpClass() throws Exception {
    }

    @AfterClass
    public static void tearDownClass() throws Exception {
    }

    @Before
    public void setUp() {
    }

    @After
    public void tearDown() {
    }
    // TODO add test methods here.
    // The methods must be annotated with annotation @Test. For example:
    //
    // @Test
    // public void hello() {}
}
