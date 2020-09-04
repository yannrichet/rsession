package org.math.R;

import java.io.File;
import static java.lang.Math.pow;
import java.util.Arrays;
import java.util.Properties;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.math.array.DoubleArray;
import org.math.array.LinearAlgebra;

/**
 * @author richet
 */
public class BrentTest {

    /*
# f <- function(X) matrix(Vectorize(function(x) {((x+5)/15)^3})(X),ncol=1)
# 
# options = list(ytarget=0.3,ytol=3.e-8,xtol=1.e-8,max_iterations=100)
# b = Brent(options)
# 
# X0 = getInitialDesign(b, input=list(x=list(min=-5,max=10)), NULL)
# Y0 = f(X0)
# Xi = X0
# Yi = Y0
# 
# finished = FALSE
# while (!finished) {
#     Xj = getNextDesign(b,Xi,Yi)
#     if (is.null(Xj) | length(Xj) == 0) {
#         finished = TRUE
#     } else {
#         Yj = f(Xj)
#         Xi = rbind(Xi,Xj)
#         Yi = rbind(Yi,Yj)
#     }
# }
# 
# print(displayResults(b,Xi,Yi))
     */
    static double f(double[] x) {
        return pow(((x[0] + 5) / 15), 3);
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

    public void from01() throws Exception {
        assert R.voidEval("input <- list(x1=list(min=0,max=1),x2=list(min=5,max=10))");
        System.err.println(R.eval("input"));
        double[][] X01 = DoubleArray.random(3, 2);
        assert R.set("X01", X01, "x1", "x2");

        System.err.println(R.eval("X01"));
        System.err.println(R.eval("ncol(X01)"));

        System.err.println(R.eval("ls()"));
        double[][] X = DoubleArray.add(new double[][]{{0, 5}, {0, 5}, {0, 5}}, LinearAlgebra.times(X01, new double[][]{{1, 0}, {0, 5}}));
        // System.err.println(R.voidEval("input[[ names(X01)[2] ]]$max"));

        String from01 = "\n"
                + "X = X01\n"
                + "print(X)\n"
                + "#from01 = function(X, input) {\n"
                + "        print(ncol(X01))\n"
                + "    for (i in 1:ncol(X01)) {\n"
                + "        print(\"_i_ \");print(i)\n"
                + "        namei = names(X01)[i]\n"
                + "        print(\"_name_i \");print(namei)\n"
                //                + "        boundsi = input[[ namei ]]\n"
                //                + "        print(\"_bounds_i \");print(boundsi)\n"
                //                + "        X01[,i] = X01[,i] * (boundsi$max-boundsi$min) + boundsi$min\n"
                + "        print(X[,i])\n"
                + "        print(X01[,i] * (input[[ namei ]]$max-input[[ namei ]]$min) + input[[ namei ]]$min)\n"
                + "        X[,i] = X01[,i] * (input[[ namei ]]$max-input[[ namei ]]$min) + input[[ namei ]]$min\n"
                + "        print(X[,i])\n"
                + "    }\n"
                + "    # colnames(X) <- names(input)\n"
                + "#    return(X)\n"
                + "#}\n"
                + "print(X)\n";

        assert R.voidEval(from01);
//        R.eval("X = from01(X01,input)");
        assert Arrays.deepEquals((double[][]) R.eval("X"), X) : Arrays.deepToString((double[][]) R.eval("X"));

    }

    @Test
    public void testfrom01R2js() {
        R = new R2jsSession(l, prop);
        try {
            System.err.println(R.eval("R.version.string"));
        } catch (Exception ex) {
            ex.printStackTrace();
        }

        try {
            from01();
        } catch (Exception ex) {
            ex.printStackTrace();
            assert false:R.notebook();
        }
    }

    public void from01Fun() throws Exception {
        String from01 = "\n"
                + "from01 = function(x, inp) {\n"
                + "    for (i in 1:ncol(x)) {\n"
                + "        namei = names(x)[i]\n"
                + "        x[,i] = x[,i] * (inp[[ namei ]]$max-inp[[ namei ]]$min) + inp[[ namei ]]$min\n"
                + "    }\n"
                + "    names(x) <- names(inp)\n"
                + "    print(x)\n"
                + "    return(x)\n"
                + "}\n";
        
        assert R.voidEval(from01);

        assert R.voidEval("input <- list(x1=list(min=0,max=1),x2=list(min=5,max=10))");
        double[][] X01 = DoubleArray.random(3, 2);
        assert R.set("X01", X01, "x1", "x2");
        System.err.println(R.voidEval("print(X01)"));
        assert Arrays.deepEquals((double[][]) R.eval("X01"), X01) : Arrays.deepToString((double[][]) R.eval("X01"));

        double[][] X = DoubleArray.add(new double[][]{{0, 5}, {0, 5}, {0, 5}}, LinearAlgebra.times(X01, new double[][]{{1, 0}, {0, 5}}));
        assert R.voidEval("X = from01(X01,input)");

        assert Arrays.deepEquals((double[][]) R.eval("X"), X) : Arrays.deepToString((double[][]) R.eval("X"));
    }

    //@Test
    public void testfrom01FunR2js() {
        R = new R2jsSession(l, prop);
        ((R2jsSession)R).debug_js=true;
        try {
            System.err.println(R.eval("R.version.string"));
        } catch (Exception ex) {
            ex.printStackTrace();
        }

        try {
            from01Fun();
        } catch (Exception ex) {
            ex.printStackTrace();
            assert false:R.notebook();
        }
    }

    public void getInitialDesign() throws Exception {
        assert R.voidEval("input <- list(x=list(min=0,max=10))");
        assert R.voidEval("brent = list()");

        String from01 = "\n"
                + "from01 = function(X, inp) {\n"
                + "    for (i in 1:ncol(X)) {\n"
                + "        namei = names(X)[i]\n"
                + "        X[,i] = X[,i] * (inp[[ namei ]]$max-inp[[ namei ]]$min) + inp[[ namei ]]$min\n"
                + "    }\n"
                + "    return(X)\n"
                + "}\n";

        assert R.voidEval(from01);

        String getInitialDesign = "\n"
                + "#getInitialDesign <- function(brent, input, output) {\n"
                + "    if (length(input)!=1) stop(\"Cannot find root of >1D function\")\n"
                + "    brent$i <- 0\n"
                + "    brent$input <- input\n"
                + "    brent$exit <- -1    # Reason end of algo\n"
                + "    x = matrix(c(0, 1, 1),ncol=1)\n"
                + "    names(x) <- names(input)\n"
                + "#    return(from01(x,brent$input))\n"
                + "#}\n"
                //+ "print(x)\n";
                + "print(from01(x,brent$input))\n";

        System.err.println(R.eval(getInitialDesign));
    }

    @Test
    public void testgetInitialDesignR2js() {
        R = new R2jsSession(l, prop);
        try {
            System.err.println(R.eval("R.version.string"));
        } catch (Exception ex) {
            ex.printStackTrace();
        }

        try {
            getInitialDesign();
        } catch (Exception ex) {
            ex.printStackTrace();
            assert false:R.notebook();
        }
    }

    public void getInitialDesignFun() throws Exception {
        String from01 = "\n"
                + "from01 = function(X, inp) {\n"
                + "    for (i in 1:ncol(X)) {\n"
                + "        namei = names(X)[i]\n"
                + "        X[,i] = X[,i] * (inp[[ namei ]]$max-inp[[ namei ]]$min) + inp[[ namei ]]$min\n"
                + "    }\n"
                + "    return(X)\n"
                + "}\n";

        assert R.voidEval(from01);

        String getInitialDesign = "\n"
                + "getInitialDesign <- function(b, inp, outp) {\n"
                + "    if (length(inp)!=1) stop(\"Cannot find root of >1D function\")\n"
                + "    b$i <- 0\n"
                + "    b$input <- inp\n"
                + "    b$exit <- -1    # Reason end of algo\n"
                + "    x = matrix(c(0, 1, 1),ncol=1)\n"
                + "    names(x) <- names(inp)\n"
                + "    return(from01(x,b$input))\n"
                + "}";

        assert R.voidEval(getInitialDesign);

        assert R.voidEval("input <- list(x=list(min=0,max=10))");
        assert R.eval("names(input)[1]").equals("x");
        assert R.voidEval("brent <- list()");

        assert R.voidEval("X = getInitialDesign(brent,input,NULL)");

        System.err.println(Arrays.deepToString((double[][]) R.eval("X")));
    }

    @Test
    public void testgetInitialDesignFunR2js() {
        R = new R2jsSession(l, prop);
        try {
            System.err.println(R.eval("R.version.string"));
        } catch (Exception ex) {
            ex.printStackTrace();
        }

        try {
            getInitialDesignFun();
        } catch (Exception ex) {
            ex.printStackTrace();
            assert false:R.notebook();
        }
    }

//    public void getNextDesignR2js() throws Exception {
//        String from01 = "\n"
//                + "from01 = function(X, inp) {\n"
//                + "    for (i in 1:ncol(X)) {\n"
//                + "        namei = names(X)[i]\n"
//                + "        X[,i] = X[,i] * (inp[[ namei ]]$max-inp[[ namei ]]$min) + inp[[ namei ]]$min\n"
//                + "    }\n"
//                + "    return(X)\n"
//                + "}\n";
//
//        assert R.voidEval(from01);
//
//        String to01 = "\n"
//                + "to01 = function(X, inp) {\n"
//                + "    for (i in 1:ncol(X)) {\n"
//                + "        namei = names(X)[i]\n"
//                + "        X[,i] = (X[,i] - inp[[ namei ]]$min) / (inp[[ namei ]]$max-inp[[ namei ]]$min)\n"
//                + "    }\n"
//                + "    return(X)\n"
//                + "}";
//
//        assert R.voidEval(to01);
//
//        String getInitialDesign = "\n"
//                + "getInitialDesign <- function(b, inp, outp) {\n"
//                + "    if (length(inp)!=1) stop(\"Cannot find root of >1D function\")\n"
//                + "    b$i <- 0\n"
//                + "    b$input <- inp\n"
//                + "    b$exit <- -1    # Reason end of algo\n"
//                + "    x = matrix(c(0, 1, 1),ncol=1)\n"
//                + "    names(x) <- names(inp)\n"
//                + "    return(from01(x,b$input))\n"
//                + "}";
//
//        assert R.voidEval(getInitialDesign);
//
//        String Brent = "Brent <- function(options) {\n"
//                + "    b = new.env()\n"
//                + "    b$ytol <- as.numeric(options$ytol)\n"
//                + "    b$xtol <- as.numeric(options$xtol)\n"
//                + "    b$ytarget <- as.numeric(options$ytarget)\n"
//                + "    b$max_iterations <- as.integer(options$max_iterations)\n"
//                + "    b$i = NA\n"
//                + "    return(b)\n"
//                + "}";
//
//        assert R.voidEval(Brent);
//
//        assert R.voidEval("input <- list(x=list(min=0,max=10))");
//        assert R.eval("names(input)[1]").equals("x");
//        assert R.voidEval("brent <- Brent(list(ytarget=0.3,ytol=3.e-8,xtol=1.e-8,max_iterations=100))");
//        
//        System.err.println("############################################"
//                + R.eval("JSON.stringify(" + R2JsSession.JS_VARIABLE_STORAGE_OBJECT + ")")
//                + "############################################");
//        
//        assert R.voidEval("X = getInitialDesign(brent,input,NULL)");
//        double[][] X = R.asMatrix(R.eval("X"));
//        double[] Y = F(X);
//        R.set("Y", Y);
//
//        System.err.println("############################################"
//                + R.eval("JSON.stringify(" + R2JsSession.JS_VARIABLE_STORAGE_OBJECT + ")")
//                + "############################################");
//
//        String getNextDesign1 = "#getNextDesign <- function(brent, X, Y) {\n"
//                + "    names(X) = names(brent$input)\n"
//                + "    X = to01(X,brent$input)\n"
//                + "    Y = matrix(Y,ncol=1) - brent$ytarget\n"
//                + "    if (brent$i >= brent$max_iterations) {\n"
//                + "        brent$exit <- 2\n"
//                + "        print(\"return(NULL)\")\n"
//                + "    }\n"
//                + "    brent$i <- brent$i + 1\n"
//                + "\n"
//                + "    a <- as.numeric(X[length(X) - 2, 1])\n"
//                + "    b <- as.numeric(X[length(X) - 1, 1])\n"
//                + "    c <- as.numeric(X[length(X), 1])\n"
//                + "    fa <- as.numeric(Y[length(Y) - 2, 1])\n"
//                + "    fb <- as.numeric(Y[length(Y) - 1, 1])\n"
//                + "    fc <- as.numeric(Y[length(Y), 1])\n"
//                + "    if ((brent$i == 1) && (fa * fb > 0)) {\n"
//                + "        # root must be bracketed for Brent\n"
//                + "        brent$exit <- 1\n"
//                + "        print(\"return(NULL)\")\n"
//                + "    }\n";
//
//        assert R.voidEval(getNextDesign1): "Failed to eval "+getNextDesign1;
//
//             System.err.println("############################################"+
//                R.eval("JSON.stringify("+R2JsSession.JS_VARIABLE_STORAGE_OBJECT+")")+
//                "############################################");
//             
//        String getNextDesign2 = "\n"
//                + "    if (fb * fc > 0) {\n"
//                + "        #Rename a, b, c and adjust bounding interval d\n"
//                + "        c <- a\n"
//                + "        fc <- fa\n"
//                + "        d <- b - a\n"
//                + "        e <- d\n"
//                + "    }\n"
//                + "    #else { d = c-b ; e = d}\n"
//                + "    if (abs(fc) < abs(fb)) {\n"
//                + "        # b stand for the best approx of the root which will lie between b and c\n"
//                + "        a = b\n"
//                + "        b = c\n"
//                + "        c = a\n"
//                + "        fa = fb\n"
//                + "        fb = fc\n"
//                + "        fc = fa\n"
//                + "    }\n"
//                + "\n";
//
//        assert R.voidEval(getNextDesign2): "Failed to eval "+getNextDesign2;
//
//                     System.err.println("############################################"+
//                R.eval("JSON.stringify("+R2JsSession.JS_VARIABLE_STORAGE_OBJECT+")")+
//                "############################################");
//                     
//        String getNextDesign30 =
//                "    tol1 = 2. * brent$ytol * abs(b) + 0.5 * brent$xtol # Convergence check tolerance.\n";
//    assert R.voidEval(getNextDesign30): "Failed to eval "+getNextDesign30;
//
//                     System.err.println("############################################"+
//                R.eval("JSON.stringify("+R2JsSession.JS_VARIABLE_STORAGE_OBJECT+")")+
//                "############################################");
//
//                     String getNextDesign3 =
//                 "    xm = .5 * (c - b)\n"
//                + "    if (abs(xm) <= tol1 | fb == 0) {\n"
//                + "        # stop if fb = 0 return root b or tolerance reached\n"
//                + "        brent$exit <- 0\n"
//                + "        print(\"return(NULL)\")\n"
//                + "    }\n"
//                + "    if ((abs(e) >= tol1) & (abs(fa) > abs(fb))) {\n"
//                + "        s = fb / fa\n"
//                + "        if (a == c) {\n"
//                + "            #Attempt linear interpolation\n"
//                + "            #print(\"Alinear\")\n"
//                + "            p = 2. * xm * s\n"
//                + "            q = 1. - s\n"
//                + "        } else {\n"
//                + "            #Attempt inverse quadratic interpolation.\n"
//                + "            #print(\"Aquadratic\")\n"
//                + "            q = fa / fc\n"
//                + "            r = fb / fc\n"
//                + "            p = s * (2. * xm * q * (q - r) - (b - a) * (r - 1.))\n"
//                + "            q = (q - 1.) * (r - 1.) * (s - 1.)\n"
//                + "        }\n"
//                + "\n"
//                + "        if (p > 0) {\n"
//                + "            q = -q # Check whether in bounds.\n"
//                + "        }\n"
//                + "        p = abs(p)\n"
//                + "        if (2. * p < min(3. * xm * q - abs(tol1 * q), abs(e * q))) {\n"
//                + "            #print(\"confirmInterpol\")\n"
//                + "            e <- d #Accept interpolation.\n"
//                + "            d <- p / q\n"
//                + "        } else {\n"
//                + "            #print(\"bisection1\")\n"
//                + "            d <- xm #Interpolation failed, use bisection.\n"
//                + "            e <- d\n"
//                + "        }\n"
//                + "    } else {\n"
//                + "        # Bounds decreasing too slowly, use bisection.\n"
//                + "        #print(\"bisection2\")\n"
//                + "        d = xm\n"
//                + "        e <- d\n"
//                + "    }\n"
//                + "    a = b #Move last best guess to a.\n"
//                + "    fa = fb\n"
//                + "    if (abs(d) > tol1) {\n"
//                + "        #then Evaluate new trial root.\n"
//                + "        b = b + d\n"
//                + "    } else {\n"
//                + "        b = b + sign(xm) * tol1\n"
//                + "    }\n"
//                + "    Xnext = c(a, b, c)\n"
//                + "    #return(from01(matrix(Xnext, ncol = 1),brent$input))\n"
//                + "#}";
//                     
//        assert R.voidEval(getNextDesign3): "Failed to eval "+getNextDesign3;
//
//             System.err.println("############################################"+
//                R.eval("JSON.stringify("+R2JsSession.JS_VARIABLE_STORAGE_OBJECT+")")+
//                "############################################");
//   
//        System.err.println(Arrays.deepToString((double[][]) R.eval("Xnext")));
//    }
//
//    @Test
//    public void testgetNextDesignFunR2js() {
//        R = new R2JsSession(l, prop);
//        try {
//            System.err.println(R.eval("R.version.string"));
//        } catch (Exception ex) {
//            ex.printStackTrace();
//        }
//
//        try {
//            getNextDesignR2js();
//        } catch (Exception ex) {
//            ex.printStackTrace();
//            System.err.println(R.notebook());
//        }
//    }
    public void errorFun() throws Exception {
        //((R2jsSession)R).debug_js=true;
        String getInitialDesign = "\n" + "getInitialDesign <- function(brent, inp, outp) {\n"
                + " print(length(inp))\n"
                + "    if (length(inp)!=1) stop(\"Cannot find root of >1D function\")\n"
                + "    brent$i <- 0\n"
                + "    brent$input <- inp\n"
                + "    brent$exit <- -1    # Reason end of algo\n"
                + "    x = c(0, 1, 1)\n"
                + "    return(from01(x,brent$input))\n"
                + "}";

        R.voidEval(getInitialDesign);

        R.voidEval("input <- list(x1=list(min=0,max=1),x2=list(min=5,max=10))");
        assert R.set("b", "list()");

        try {
            R.eval("X = getInitialDesign(b,input,NULL)");
        } catch (Exception e) {
            assert true;
            return;
        }
        assert false : "Did not raise error:" + Arrays.deepToString((double[][]) R.eval("X"));
    }

    @Test
    public void testErrorFunR2js() {
        R = new R2jsSession(l, prop);
        try {
            System.err.println(R.eval("R.version.string"));
        } catch (Exception ex) {
            ex.printStackTrace();
        }

        try {
            errorFun();
        } catch (Exception ex) {
            ex.printStackTrace();
            assert false:R.notebook();
        }
    }

    /**
     * Intended to test for the algorithm over a Rsession instance
     */
    public void test() throws Exception {
        assert R != null : "No Rsession available";
        String result = null;
        try {
            R.source(new File("src/test/R/Brent.R"));

            R.voidEval("options = list(ytarget=0.3,ytol=3.e-8,xtol=1.e-8,max_iterations=100)");
            R.voidEval("b = Brent(options)");

            assert R.eval("names(list(x=list(min=-5,max=10)))[1]").equals("x") : Arrays.toString((String[]) R.eval("names(list(x=list(min=-5,max=10)))"));

            double[][] X0 = R.asMatrix(R.eval("getInitialDesign(b, list(x=list(min=-5,max=10)), NULL)"));
            System.err.println("X0=\n" + Arrays.deepToString(X0));
            double[] Y0 = F(X0);
            System.err.println("Y0=\n" + Arrays.toString(Y0));
            R.set("Xi", X0, "x");
            assert R.eval("names(Xi)[1]").equals("x") : Arrays.toString((String[]) R.eval("names(Xi)"));

            R.set("Yi", DoubleArray.columnVector(Y0));

            double[][] Xj = X0;
            boolean finished = false;
            while (!finished) {
                System.err.println("Xj=\n" + Arrays.deepToString(Xj));
                Xj = R.asMatrix(R.eval("getNextDesign(b,Xi,Yi)"));
                System.err.println("   Xj=\n" + Arrays.deepToString(Xj));
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

            result = R.asString(R.eval("displayResults(b,Xi,Yi)"));
        } catch (Rsession.RException r) {
            assert false : r;
        }

        assert result != null : "Null result !";
        assert result.contains("the root approximation is 5") : "Bad convergence:" + result;
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
            assert false:R.notebook();
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
            assert false:R.notebook();
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
            assert false:R.notebook();
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
