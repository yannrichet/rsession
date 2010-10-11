package org.math.R;

import java.util.Properties;
import org.junit.Before;
import org.junit.Test;
import org.rosuda.REngine.REXPMismatchException;

/**
 *
 * @author richet
 */
public class SystemAccessTest {

    RserverConf conf;
    Rsession s1, s2;

    @Test
    public void testIO() {
        new Thread(new Runnable() {

            public void run() {
                s2.eval("while(!file.exists('/tmp/test')){Sys.sleep(1)}");
                try {
                    System.err.println(">>>>>>   " + s2.eval("read.csv('/tmp/test')[1,2]").asDouble());
                } catch (REXPMismatchException ex) {
                    ex.printStackTrace();
                }
            }
        }).start();

        s1.eval("write.csv(file='/tmp/test',rnorm(10))");

    }

    @Before
    public void setUp() {
        String http_proxy_env = System.getenv("http_proxy");
        Properties prop = new Properties();
        if (http_proxy_env != null) {
            prop.setProperty("http_proxy", "\"" + http_proxy_env + "\"");
        }
        conf = new RserverConf("localhost", -1/* RserverConf.RserverDefaultPort*/, null, null, prop);
        new Rdaemon(conf, new Logger() {

            public void println(String string) {
                System.err.println(">> "+string);            }
        }).start(null);
        s1 = Rsession.newLocalInstance(System.out, prop);
        s2 = Rsession.newLocalInstance(System.err, prop);
    }

    public static void main(String args[]) {
        org.junit.runner.JUnitCore.main(SystemAccessTest.class.getName());
    }
}
