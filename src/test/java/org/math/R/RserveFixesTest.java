package org.math.R;

import java.io.File;
import java.util.Properties;
import org.junit.After;
import org.junit.Test;
import static org.math.R.StartRserve.checkLocalRserve;
import org.rosuda.REngine.Rserve.RConnection;
import org.rosuda.REngine.Rserve.RserveException;

/**
 *
 * @author richet
 */
public class RserveFixesTest {

    public static void main(String args[]) {
        org.junit.runner.JUnitCore.main(RserveFixesTest.class.getName());
    }

    @Test
    public void testCat_E127() {
        System.out.println("checkLocalRserve=" + checkLocalRserve());
        try {
            RConnection c = new RConnection();
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
        File dir = null;

        System.out.println("checkLocalRserve=" + checkLocalRserve());
        try {
            RConnection c = new RConnection();
            //c.eval("cat('123')");
            dir = new File(c.eval("getwd()").asString());
            System.err.println("wd: " + dir);

            String http_proxy_env = System.getenv("http_proxy");
            if (http_proxy_env != null) {
                c.eval("Sys.setenv(http_proxy='" + http_proxy_env + "')");
                c.eval("Sys.setenv(https_proxy='" + http_proxy_env + "')");
            }

            c.eval("download.file(quiet=T,'https://www.r-project.org/',paste0(getwd(),'/log.txt'))");
            c.close();
        } catch (Exception x) {
            x.printStackTrace();
        }

        assert (new File(dir, "log.txt").exists()) : "file NOT exists";
        assert (new File(dir, "log.txt").length() > 10) : "file EMPTY (error 127 ?)";

    }
    
    @After
    public void tearDown() throws RserveException {
         RConnection c = new RConnection();
         c.shutdown();
    }
    
}
