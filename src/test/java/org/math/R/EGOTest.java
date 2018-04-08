package org.math.R;

import org.rosuda.REngine.REXP;
import org.math.array.DoubleArray;
import org.junit.After;
import org.junit.Before;

import java.io.File;
import java.io.PrintStream;
import java.util.Properties;

import org.junit.Test;

/**
 * Intended to reproduce the broken pipe failure.
 *
 * @author richet
 */
public class EGOTest {

    PrintStream p = System.err;
    //RserverConf conf;
    RserveSession R;
    int rand = Math.round((float) Math.random() * 10000);
    File tmpdir = new File("tmp"/*System.getProperty("java.io.tmpdir")*/);

    public static void main(String args[]) {
        org.junit.runner.JUnitCore.main(EGOTest.class.getName());
    }

    /*
     branin <- function(x) {
     x1 <- x[1]*15-5
     x2 <- x[2]*15
     (x2 - 5/(4*pi^2)*(x1^2) + 5/pi*x1 - 6)^2 + 10*(1 - 1/(8*pi))*cos(x1) + 10
     }
     */
    static double branin(double[] x) {
        double x1 = x[0] * 15 - 5;
        double x2 = x[1] * 15;
        return Math.pow(x2 - 5 / (4 * Math.PI * Math.PI) * (x1 * x1) + 5 / Math.PI * x1 - 6, 2) + 10 * (1 - 1 / (8 * Math.PI)) * Math.cos(x1) + 10;
    }
    String[] Xnames = {"x1", "x2"};
    // to emulate a noisy function

    public double[] f(double[] x) {
        return new double[]{branin(x) + Math.random() * 10.0, 7.0};
    }

    public double[][] F(double[][] X) {
        double[][] Y = new double[X.length][];
        for (int i = 0; i < Y.length; i++) {
            Y[i] = f(X[i]);
        }
        return Y;
    }

    void initR() throws Exception {
        R.installPackage("DiceKriging", true);
        R.installPackage("rgenoud", true);
        R.installPackage("lhs", true);
        R.installPackage("pso", true);
        R.installPackage("DiceView", true);

        R.voidEval("distXmin <- function (x, Xmin) \n"
                + "{\n"
                + "    return(min(sqrt(rowSums((Xmin - matrix(x, nrow = nrow(Xmin), \n"
                + "        ncol = ncol(Xmin), byrow = TRUE))^2))))\n"
                + "}\n"
                + "\n"
                + "EI <- function (x, model, plugin = NULL) \n"
                + "{\n"
                + "    if (is.null(plugin)) {\n"
                + "        if (model@noise.flag) \n"
                + "            plugin <- min(model@y - 2 * sqrt(model@noise.var))\n"
                + "        else plugin <- min(model@y)\n"
                + "    }\n"
                + "    m <- plugin\n"
                + "    if (!is.matrix(x)) \n"
                + "        x <- matrix(x, ncol = model@d)\n"
                + "    d <- ncol(x)\n"
                + "    if (d != model@d) {\n"
                + "        stop(\"x does not have the right number of columns (\", \n"
                + "            d, \" instead of \", model@d, \")\")\n"
                + "    }\n"
                + "    newdata <- x\n"
                + "    colnames(newdata) = colnames(model@X)\n"
                + "    predx <- predict.km(object = model, newdata = newdata, type = \"UK\", \n"
                + "        checkNames = FALSE)\n"
                + "    kriging.mean <- predx$mean\n"
                + "    kriging.sd <- predx$sd\n"
                + "    xcr <- (m - kriging.mean)/kriging.sd\n"
                + "    xcr.prob <- pnorm(xcr)\n"
                + "    xcr.dens <- dnorm(xcr)\n"
                + "    res <- (m - kriging.mean) * xcr.prob + kriging.sd * xcr.dens\n"
                + "    too.close = which(kriging.sd/sqrt(model@covariance@sd2) < \n"
                + "        1e-06)\n"
                + "    res[too.close] <- max(0, m - kriging.mean)\n"
                + "    return(res)\n"
                + "}\n"
                + "\n"
                + "generate_knots <- function (knots.number = NULL, d, lower = NULL, upper = NULL) \n"
                + "{\n"
                + "    if (is.null(lower)) \n"
                + "        lower <- rep(0, times = d)\n"
                + "    if (is.null(upper)) \n"
                + "        upper <- rep(1, times = d)\n"
                + "    if (is.null(knots.number)) \n"
                + "        return(NULL)\n"
                + "    if (length(knots.number) == 1) {\n"
                + "        if (knots.number > 1) {\n"
                + "            knots.number <- rep(knots.number, times = d)\n"
                + "        }\n"
                + "        else {\n"
                + "            return(NULL)\n"
                + "        }\n"
                + "    }\n"
                + "    if (length(knots.number) != d) {\n"
                + "        print(\"Error in function generate_knots. The size of the vector knots.number needs to be equal to d\")\n"
                + "        return(NULL)\n"
                + "    }\n"
                + "    knots.number <- pmax(1, knots.number)\n"
                + "    thelist <- NULL\n"
                + "    for (i in 1:d) {\n"
                + "        thelist[[i]] <- seq(from = lower[i], to = upper[i], length = knots.number[i])\n"
                + "    }\n"
                + "    return(thelist)\n"
                + "}\n"
                + "\n"
                + "max_EI <- function (model, lower, upper, control = NULL) \n"
                + "{\n"
                + "    d <- ncol(model@X)\n"
                + "    if (is.null(control$print.level)) \n"
                + "        control$print.level <- 1\n"
                + "    if (is.null(control$max.parinit.iter)) \n"
                + "        control$max.parinit.iter <- 10^d\n"
                + "    if (d <= 6) \n"
                + "        N <- 10 * 2^d\n"
                + "    else N <- 100 * d\n"
                + "    if (is.null(control$pop.size)) \n"
                + "        control$pop.size <- N\n"
                + "    if (is.null(control$solution.tolerance)) \n"
                + "        control$solution.tolerance <- 1e-15\n"
                + "    pars = NULL\n"
                + "    for (i in 1:d) pars = cbind(pars, matrix(runif(N, lower[i], \n"
                + "        upper[i]), ncol = 1))\n"
                + "    ei <- EI(pars, model)\n"
                + "    good_start = which(ei == max(ei, na.rm = T))\n"
                + "    par0 = matrix(pars[good_start[sample(1:length(good_start), \n"
                + "        1)], ], nrow = 1)\n"
                + "    o <- psoptim(par = par0, fn = function(x) {\n"
                + "        EI(x, model)\n"
                + "    }, lower = lower, upper = upper, control = list(fnscale = -1, \n"
                + "        trace = control$print.level, maxit = 10 * d))\n"
                + "    o$par <- t(as.matrix(o$par))\n"
                + "    colnames(o$par) <- colnames(model@X)\n"
                + "    o$value <- as.matrix(o$value)\n"
                + "    colnames(o$value) <- \"EI\"\n"
                + "    return(list(par = o$par, value = o$value, counts = o$counts, \n"
                + "        par.all = o$par.all))\n"
                + "}\n"
                + "\n"
                + "max_qEI <- function (model, npoints, L, lower, upper, control = NULL, ...) \n"
                + "{\n"
                + "    n1 <- nrow(model@X)\n"
                + "    for (s in 1:npoints) {\n"
                + "        oEGO <- max_EI(model = model, lower = lower, upper = upper, \n"
                + "            control, ...)\n"
                + "        if (distXmin(oEGO$par, model@X) <= prod(upper - lower) * \n"
                + "            1e-10) {\n"
                + "            warning(\"Proposed a point already in design !\")\n"
                + "            npoints = s - 1\n"
                + "            break\n"
                + "        }\n"
                + "        model@X <- rbind(model@X, oEGO$par)\n"
                + "        if (L == \"min\") \n"
                + "            l = min(model@y)\n"
                + "        else if (L == \"max\") \n"
                + "            l = max(model@y)\n"
                + "        else if (L == \"upper95\") \n"
                + "            l = predict.km(object = model, newdata = oEGO$par, \n"
                + "                type = \"UK\", light.return = TRUE)$upper95\n"
                + "        else if (L == \"lower95\") \n"
                + "            l = predict.km(object = model, newdata = oEGO$par, \n"
                + "                type = \"UK\", light.return = TRUE)$lower95\n"
                + "        else l = L\n"
                + "        model@y <- rbind(model@y, l, deparse.level = 0)\n"
                + "        model@F <- trendMatrix.update(model, Xnew = data.frame(oEGO$par))\n"
                + "        if (model@noise.flag) {\n"
                + "            model@noise.var = c(model@noise.var, 0)\n"
                + "        }\n"
                + "        newmodel = NULL\n"
                + "        try(newmodel <- computeAuxVariables(model))\n"
                + "        if (is.null(newmodel)) {\n"
                + "            warning(\"Unable to update model !\")\n"
                + "            npoints = s - 1\n"
                + "            break\n"
                + "        }\n"
                + "        model = newmodel\n"
                + "    }\n"
                + "    if (npoints == 0) \n"
                + "        return()\n"
                + "    return(list(par = model@X[(n1 + 1):(n1 + npoints), , drop = FALSE], \n"
                + "        value = model@y[(n1 + 1):(n1 + npoints), , drop = FALSE]))\n"
                + "}");
    }

    void initDesign() throws Exception {
        int seed = 1;
        R.voidEval("set.seed(" + seed + ")");
        R.voidEval("Xlhs <- maximinLHS(n=9,k=2)");
        double[][] X0 = R.asMatrix(R.rawEval("as.matrix(Xlhs)"));
        double[][] Xbounds = new double[][]{{0, 0}, {0, 1}, {1, 0}, {1, 1}};
        X0 = DoubleArray.insertRows(X0, 0, Xbounds);
        R.set("X" + currentiteration, X0, Xnames);
    }
    int currentiteration = -1;

    void run() throws Exception {
        double[][] X = R.asMatrix(R.rawEval("as.matrix(X" + currentiteration + ")"));
        double[][] Y = F(X);
        System.err.println(RserveSession.cat(Y));
        R.set("Y" + currentiteration, DoubleArray.getColumnsCopy(Y, 0), "y");
    }

    void nextDesign() throws Exception {
        double[][] X = R.asMatrix(R.rawEval("as.matrix(X" + currentiteration + ")"));
        double[][] Y = R.asMatrix(R.rawEval("as.matrix(Y" + currentiteration + ")"));

        double[][] ytomin = DoubleArray.getColumnsCopy(Y, 0);

        R.set("Y" + currentiteration, ytomin, "y");

        String nuggetnoise_str = "nugget.estim = FALSE, nugget = NULL, noise.var = ";

        double[] sdy = DoubleArray.fill(ytomin.length, 7.0);//getColumnCopy(Y, 1);
        nuggetnoise_str = nuggetnoise_str + "c(" + RserveSession.cat(sdy) + ")^2";

        nuggetnoise_str = nuggetnoise_str + ", ";

        R.savels(new File("XY" + currentiteration + ".Rdata"), "" + currentiteration);

        R.voidEval("km" + currentiteration + " <- km(y~1,"
                + "optim.method='gen',"
                + "penalty = NULL,"
                + "covtype='matern5_2',"
                + nuggetnoise_str
                + "design=X" + currentiteration + ","
                + "response=Y" + currentiteration + ","
                + "control=list(" + control_km + "))");

        REXP exists = (REXP) R.rawEval("exists('km" + currentiteration + "')");
        if (exists == null || !(exists.asInteger() == 1)) {
            R.log("No km object built:\n" + RserveSession.cat(",", R.ls()), RLog.Level.ERROR);
            return;
        }

        R.savels(new File("km" + (currentiteration) + ".Rdata"), "" + (currentiteration));

        R.voidEval("EGO" + currentiteration + " <- max_qEI(model=km" + currentiteration + ","
                + "npoints=10,"
                //+ "L=c(" + liar + "(" + (search_min ? "" : "-") + "Y" + currentiteration + "_" + hcode + "$y)," + liar_noise + "),"
                + "L=max(Y" + currentiteration + "$y),"
                + "lower=c(0,0),"
                + "upper=c(1,1),"
                + "control=list(" + control_ego + "))");

        /*REXP*/ exists = (REXP) R.rawEval("exists('EGO" + currentiteration + "')");
        if (exists == null || !(exists.asInteger() == 1)) {
            R.log("No EGO object built:\n" + RserveSession.cat(",", R.ls()), RLog.Level.ERROR);
            return;
        }

        R.savels(new File("EGO" + (currentiteration) + ".Rdata"), "" + (currentiteration));

        R.voidEval("X" + (currentiteration + 1) + " <- rbind(X" + currentiteration + ",EGO" + currentiteration + "$par)");

    }

    void cleanRdata() {
        new File("XY" + currentiteration + ".Rdata").delete();
        new File("km" + (currentiteration) + ".Rdata").delete();
        new File("EGO" + (currentiteration) + ".Rdata").delete();
        new File("sectionview." + (currentiteration) + ".png").delete();
    }
    String control_km = "trace=FALSE";
    String control_ego = "trace=FALSE";

    public String analyseDesign() throws Exception {
        String htmlout = "";
        StringBuilder dataout = new StringBuilder();
        try {
            if (currentiteration > 0) {
                double[][] ysdy = R.asMatrix(R.rawEval("as.matrix(Y" + currentiteration + ")"));;
                double[][] x = R.asMatrix(R.rawEval("as.matrix(X" + currentiteration + ")"));

                double[] y = DoubleArray.getColumnCopy(ysdy, 0);

                double[] sdy = DoubleArray.fill(y.length, 7.0);//DoubleArray.getColumnCopy(ysdy, 1);

                double y0 = Double.POSITIVE_INFINITY;
                int i = -1;
                for (int ii = 0; ii < x.length; ii++) {
                    if (y[ii] < y0) {
                        i = ii;
                        y0 = y[i];
                    }
                }

                htmlout = htmlout + "Minimum value is " + y0 + " (sd=" + sdy[i] + ")<br/>";
                htmlout = htmlout + "for<br/>";
                for (int j = 0; j < x[i].length; j++) {
                    htmlout = htmlout + Xnames[j] + " = " + x[i][j] + "<br/>";
                }

                R.voidEval("pred <- predict.km(object=km" + (currentiteration - 1) + ",newdata=EGO" + (currentiteration - 1) + "$par,type='UK')");
                double em_mean = R.asDouble(R.rawEval("min(pred$mean)"));
                double em_sd = R.asDouble(R.rawEval("pred$sd[pred$mean==" + em_mean + "]"));

                htmlout = htmlout + "<br/>Next expected minimum value may be " + em_mean + " (sd=" + em_sd + ")";

                File f = new File("sectionview." + (currentiteration - 1) + ".png");
                R.set("bestX_" + (currentiteration - 1), x[i]);
                R.toPNG(f, 600, 600, "sectionview.km(model=km" + (currentiteration - 1) + ",center=bestX_" + (currentiteration - 1) + ",type='UK', yscale = 1,yname='Y',Xname=" + RserveSession.buildListString(Xnames) + ")");
                htmlout += "\n<br/>\n<img src='" + f.getName() + "' width='600' height='600'/>";

            } else {
                htmlout = htmlout + "\n" + "Not enought results yet.";
            }
        } catch (Exception e) {
            e.printStackTrace();
            htmlout = htmlout + "\nError: <pre>" + e.getMessage() + "</pre>";
        }

        return "<HTML name='min'>\n" + htmlout + "\n</HTML>" + dataout.toString();
    }

    /**
     * Intended to test for an EGO algorithm of at least 500 points in 50 steps
     */
    @Test
    public void testEGO() throws Exception {
        initR();

        currentiteration = 0;
        initDesign();

        for (currentiteration = 0; currentiteration < 5; currentiteration++) {
            System.err.println("============================== iteration " + currentiteration);
            run();
            nextDesign();
            cleanRdata();
        }
    }

    @Before
    public void setUp() {
        RLog l = new RLog() {

            public void log(String string, RLog.Level level) {
                System.out.println("                              " + level + " " + string);
            }

            public void close() {
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

        RserverConf conf = new RserverConf(null, -1, null, null, prop);
        R = RserveSession.newInstanceTry(l, conf);
        try {
            System.err.println(R.eval("R.version.string"));
        } catch (Exception ex) {
            ex.printStackTrace();
        }
        try {
            System.err.println("Rserve version " + R.eval("installed.packages(lib.loc='" + RserveDaemon.R_APP_DIR + "')[\"Rserve\",\"Version\"]"));
        } catch (Exception ex) {
            ex.printStackTrace();
        }

        System.out.println("tmpdir=" + tmpdir.getAbsolutePath());
    }

    @After
    public void tearDown() {
        //uncomment following for sequential call.
        //s.end();
        R.end();
        //A shutdown hook kills all Rserve at the end.
    }
}
