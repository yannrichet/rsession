package org.math.R;

import java.io.File;
import static java.lang.Math.pow;
import java.util.Arrays;
import java.util.Properties;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.math.R.Rsession.RException;
import org.math.array.DoubleArray;

/**
 * @author richet
 */
public class GradientDescent1DTest {

    /*
# f <- function(X) matrix(Vectorize(function(x) {((x+5)/15)^3})(X),ncol=1)
# 
# options = list(iterations = 10, delta = 0.1, epsilon = 0.01, target=0, yminimization='true')
# gd = GradientDescent(options)
# 
# X0 = getInitialDesign(gd, input=list(x=list(min=-5,max=10)), NULL)
# Y0 = f(X0)
# Xi = X0
# Yi = Y0
# 
# finished = FALSE
# while (!finished) {
#     Xj = getNextDesign(gd,Xi,Yi)
#     if (is.null(Xj) | length(Xj) == 0) {
#         finished = TRUE
#     } else {
#         Yj = f(Xj)
#         Xi = rbind(Xi,Xj)
#         Yi = rbind(Yi,Yj)
#     }
# }
# 
# print(displayResults(gd,Xi,Yi))
     */
    static double f(double[] x) {
        return pow(((x[0] + 2.5) / 15), 2);
    }
    String[] Xnames = {"x"};

    public double[] F(double[][] X) {
        double[] Y = new double[X.length];
        for (int i = 0; i < Y.length; i++) {
            Y[i] = f(X[i]);
        }
        return Y;
    }

    public static void main(String args[]) {
        org.junit.runner.JUnitCore.main(BrentTest.class.getName());
    }

    /**
     * Intended to test for the algorithm over a Rsession instance
     */
    public void test() throws Exception {
        assert R != null : "No Rsession available";
        String result = null;
        try {
            R.source(new File("src/test/R/GradientDescent.R"));

            R.voidEval("options = list(iterations = 10, delta = 0.1, epsilon = 0.01, target=0, yminimization='true',x0=0.5)");
            R.voidEval("gd = GradientDescent(options)");

            double[][] X0 = R.asMatrix(R.eval("getInitialDesign(gd, list(x=list(min=-5,max=10)), NULL)"));
            System.err.println("X0=\n" + Arrays.deepToString(X0));
            double[] Y0 = F(X0);
            System.err.println("Y0=\n" + Arrays.toString(Y0));
            R.set("Xi", X0, "x");
            R.set("Yi", DoubleArray.columnVector(Y0));

            boolean finished = false;
            while (!finished) {
                double[][] Xj = R.asMatrix(R.eval("getNextDesign(gd,Xi,Yi)"));
                System.err.println("Xj=\n" + Arrays.deepToString(Xj));
                if (Xj == null || Xj.length == 0) {
                    finished = true;
                } else {
                    double[] Yj = F(Xj);
                    System.err.println("Yj=\n" + Arrays.toString(Yj));
                    R.set("Xj", Xj, "x");
                    R.set("Yj", DoubleArray.columnVector(Yj));
                    R.voidEval("Xi = rbind(Xi,Xj)");
                    R.voidEval("Yi = rbind(Yi,Yj)");
                }
            }

            result = R.asString(R.eval("displayResults(gd,Xi,Yi)"));
        } catch (RException r) {
            assert false : r;
        }
        
        assert result != null : "Null result !";
        assert result.contains("minimum is ") : "Bad convergence:" + result;
    }

    @Test
    public void testRserve() {
        RserverConf conf = new RserverConf(null, -1, null, null, prop);
        R = RserveSession.newInstanceTry(l, conf);
        try {
            System.err.println(R.eval("R.version.string"));
        } catch (Exception ex) {
            ex.printStackTrace();
        }
        try {
            System.err.println("Rserve version " + R.eval("installed.packages(lib.loc='" + RserveDaemon.app_dir() + "')[\"Rserve\",\"Version\"]"));
        } catch (Exception ex) {
            ex.printStackTrace();
        }

        try {
            test();
        } catch (Exception ex) {
            ex.printStackTrace();
            System.err.println(R.notebook());
        }
    }

    @Test
    public void testRenjin() {
        R = new RenjinSession(l, prop);
        try {
            System.err.println(R.eval("R.version.string"));
        } catch (Exception ex) {
            ex.printStackTrace();
        }

        try {
            test();
        } catch (Exception ex) {
            ex.printStackTrace();
            System.err.println(R.notebook());
        }
    }

    @Test
    public void testR2js() {
        R = new R2jsSession(l, prop);
        ((R2jsSession)R).debug_js = true;
        try {
            System.err.println(R.eval("R.version.string"));
        } catch (Exception ex) {
            ex.printStackTrace();
        }

        try {
            test();
        } catch (Exception ex) {
            ex.printStackTrace();
            System.err.println(R.notebook());
        }
    }

    RLog l;
    Properties prop;
    Rsession R;

    @Before
    public void setUp() {
        l = new RLog() {

            public void log(String string, RLog.Level level) {
                System.out.println("                              " + level + " " + string);
            }

            public void closeLog() {
            }
        };

        String http_proxy_env = System.getenv("http_proxy");
        prop = new Properties();
        if (http_proxy_env != null) {
            prop.setProperty("http_proxy", http_proxy_env);
        }
    }

    @After
    public void tearDown() {
        //uncomment following for sequential call.
        R.end();
        //A shutdown hook kills all Rserve in the end.
    }
}
