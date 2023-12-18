package org.math.R;

import java.io.File;
import java.util.Arrays;
import java.util.Properties;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.math.R.R2js.R2jsSession;
import org.math.R.Rsession.RException;
import org.math.array.DoubleArray;

/**
 * @author richet
 */
public class GradientDescentTest {

    /*
# f <- function(X) matrix(apply(X,1,function (x) {
#     x1 <- x[1] * 15 - 5
#     x2 <- x[2] * 15
#     (x2 - 5/(4 * pi^2) * (x1^2) + 5/pi * x1 - 6)^2 + 10 * (1 - 1/(8 * pi)) * cos(x1) + 10
# }),ncol=1)
#
# options = list(iterations = 10, delta = 0.1, epsilon = 0.01, target=0)
# gd = GradientDescent(options)
#
# X0 = getInitialDesign(gd, input=list(x1=list(min=0,max=1),x2=list(min=0,max=1)), NULL)
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
        double x1 = x[0];// * 15 - 5;
        double x2 = x[1];// * 15;
        double y = Math.pow((x2 - 5 / (4 * Math.PI * Math.PI) * (x1 * x1) + 5 / Math.PI * x1 - 6), 2) + 10 * (1 - 1 / (8 * Math.PI)) * Math.cos(x1) + 10;
        //System.err.println("f(" + x1 + "," + x2 + ") = " + y);
        return (y);
    }
    String[] Xnames = {"x1", "x2"};

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

            double[][] X0 = R.asMatrix(R.eval("getInitialDesign(gd, list(x1=list(min=-5,max=10),x2=list(min=0,max=15)), NULL)"));
            System.err.println("X0=" + Arrays.deepToString(X0));
            double[] Y0 = F(X0);
            System.err.println("Y0=" + Arrays.toString(Y0));
            R.set("Xi", X0, "x1","x2");
            R.set("Yi", DoubleArray.columnVector(Y0));

            boolean finished = false;
            while (!finished) {
                double[][] Xj = R.asMatrix(R.eval("getNextDesign(gd,Xi,Yi)"));
                System.err.println("Xj=" + Arrays.deepToString(Xj));
                if (Xj == null || Xj.length == 0) {
                    finished = true;
                } else {
                    double[] Yj = F(Xj);
                    System.err.println("Yj=" + Arrays.toString(Yj));
                    R.set("Xj", Xj, "x1","x2");
                    R.set("Yj", DoubleArray.columnVector(Yj));
                    R.voidEval("Xi = rbind(Xi,Xj)");
                    R.voidEval("Yi = rbind(Yi,Yj)");
                }
            }

            result = R.asString(R.eval("displayResults(gd,Xi,Yi)"));
        } catch (RException r) {
            assert false : r;
        }
        
        System.err.println("result "+result);
        assert result != null : "Null result !";
        assert result.contains("minimum is ") : "Bad convergence:" + result;
    }

    @Test
    public void testRserve() {
        R = new RserveSession(l, prop,null);
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

    public void testFail() throws Exception {
        assert R != null : "No Rsession available";
        String result = null;
        try {
            R.source(new File("src/test/R/GradientDescent.R"));

            R.voidEval("options = list(iterations = 'NaN', delta = 0.1, epsilon = 0.01, target=0)");
            R.voidEval("gd = GradientDescent(options)");

            double[][] X0 = R.asMatrix(R.eval("getInitialDesign(gd, list(x1=list(min=-5,max=10),x2=list(min=0,max=15)), NULL)"));
            System.err.println("X0=" + Arrays.deepToString(X0));
            double[] Y0 = F(X0);
            System.err.println("Y0=" + Arrays.toString(Y0));
            R.set("Xi", X0, "x1","x2");
            R.set("Yi", DoubleArray.columnVector(Y0));

            boolean finished = false;
            while (!finished) {
                double[][] Xj = R.asMatrix(R.eval("getNextDesign(gd,Xi,Yi)"));
                
                assert false : "Did not correctly failed !";

                System.err.println("Xj=" + Arrays.deepToString(Xj));
                if (Xj == null || Xj.length == 0) {
                    finished = true;
                } else {
                    double[] Yj = F(Xj);
                    System.err.println("Yj=" + Arrays.toString(Yj));
                    R.set("Xj", Xj, "x1","x2");
                    R.set("Yj", DoubleArray.columnVector(Yj));
                    R.voidEval("Xi = rbind(Xi,Xj)");
                    R.voidEval("Yi = rbind(Yi,Yj)");
                }
            }

            result = R.asString(R.eval("displayResults(gd,Xi,Yi)"));
        } catch (RException r) {
            assert true;
            return;
        }
        assert false : "Did not correctly failed !";
    }

    @Test
    public void testFailRserve() {
        R = new RserveSession(l, prop,null);
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
            testFail();
        } catch (Exception ex) {
            ex.printStackTrace();
            System.err.println(R.notebook());
        }
    }

    @Test
    public void testFailRenjin() {
        R = new RenjinSession(l, prop);
        try {
            System.err.println(R.eval("R.version.string"));
        } catch (Exception ex) {
            ex.printStackTrace();
        }

        try {
            testFail();
        } catch (Exception ex) {
            ex.printStackTrace();
            System.err.println(R.notebook());
        }
    }

    @Test
    public void testFailR2js() {
        R = new R2jsSession(l, prop);
        ((R2jsSession)R).debug_js = true;
        try {
            System.err.println(R.eval("R.version.string"));
        } catch (Exception ex) {
            ex.printStackTrace();
        }

        try {
            testFail();
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
