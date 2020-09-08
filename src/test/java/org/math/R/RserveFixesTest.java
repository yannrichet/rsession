package org.math.R;

import java.io.File;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import static org.math.R.StartRserve.checkLocalRserve;
import org.rosuda.REngine.Rserve.RConnection;

/**
 *
 * @author richet
 */
public class RserveFixesTest {

    public static void main(String args[]) {
        org.junit.runner.JUnitCore.main(RserveFixesTest.class.getName());
    }
    
    RserveDaemon d;

    @Before
    public void setUp() throws Exception {
        String http_proxy_env = System.getenv("http_proxy");
        d = new RserveDaemon(new RserverConf("localhost", 6666, null, null), new RLogSlf4j());
        d.start();
    }

    @Test
    public void testCat_E127() {
        assert checkLocalRserve(6666) : "Rserve not available";

        try {
            RConnection c = new RConnection("localhost", 6666);
            c.eval("cat('123')");
            c.close();
            return;
        } catch (Exception x) {
            x.printStackTrace();
        }
        assert false;
    }

    @Test
    public void testFlushConsole_NoE127() {
        assert checkLocalRserve(6666) : "Rserve not available";

        File dir = null;
        try {
            RConnection c = new RConnection("localhost", 6666);
            //c.eval("cat('123')");
            dir = new File(c.eval("getwd()").asString());
            System.err.println("wd: " + dir);

            String http_proxy_env = System.getenv("http_proxy");
            if (http_proxy_env != null) {
                c.eval("Sys.setenv(http_proxy='" + http_proxy_env + "')");
                c.eval("Sys.setenv(https_proxy='" + http_proxy_env + "')");
            }

            c.eval("download.file(quiet=T,'http://cran.irsn.fr/',paste0(getwd(),'/log.txt'))");
            c.close();
        } catch (Exception x) {
            x.printStackTrace();
            assert false: "Exception:"+x;
        }

        assert (new File(dir, "log.txt").exists()) : "file NOT exists";
        assert (new File(dir, "log.txt").length() > 10) : "file EMPTY (error 127 ?)";

    }

    @After
    public void tearDown() {
        d.stop();
    }

}
