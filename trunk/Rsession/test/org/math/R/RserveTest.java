/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */
package org.math.R;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.util.Properties;
import org.junit.After;
import org.junit.AfterClass;
import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.Test;
import org.rosuda.REngine.REXP;
import org.rosuda.REngine.REXPMismatchException;
import org.rosuda.REngine.Rserve.RConnection;
import org.rosuda.REngine.Rserve.RFileOutputStream;
import org.rosuda.REngine.Rserve.RserveException;

/**
 *
 * @author richet
 * tried to raise Broken pipe exception. For now it does not work...
 */
public class RserveTest {

    RConnection c;

    public RserveTest() {
    }

    @BeforeClass
    public static void setUpClass() throws Exception {
    }

    @AfterClass
    public static void tearDownClass() throws Exception {
    }

    @Before
    public void setUp() throws RserveException {
        c = new RConnection();

        Logger l = new Logger() {

            public void println(String string, Level level) {
                if (level == Level.INFO) {
                    System.out.println("1 " + string);
                } else {
                    System.err.println("1 " + string);
                }
            }

            public void close() {
            }
        };


        try {
            System.err.println(silentlyEval("R.version.string", false, c).asString());
        } catch (REXPMismatchException ex) {
            ex.printStackTrace();
        }
        try {
            System.err.println("Rserve version " + silentlyEval("installed.packages()[\"Rserve\",\"Version\"]", false, c).asString());
        } catch (REXPMismatchException ex) {
            ex.printStackTrace();
        }
    }

    @After
    public void tearDown() {
    }

    @Test
    public void hello() throws RserveException, REXPMismatchException {
        c.voidEval("library(lhs)");
        c.voidEval("library(DiceOptim)");
        c.voidEval("library(DiceKriging)");


        FileInputStream is = null;
        RFileOutputStream os = null;
        try {
            os = c.createFile("XY.Rdata");
            is = new FileInputStream(new File("EGO5_467120674.Rdata"));
            byte[] buf = new byte[512];
            try {
                c.setSendBufferSize(buf.length);
            } catch (RserveException ex) {
                ex.printStackTrace();
            }
            int n = 0;
            while ((n = is.read(buf)) > 0) {
                os.write(buf, 0, n);
            }
        } catch (IOException e) {
            e.printStackTrace();
            return;
        } finally {
            try {
                if (os != null) {
                    os.close();
                }
                if (is != null) {
                    is.close();
                }
            } catch (IOException ee) {
                ee.printStackTrace();
            }
        }

        for (int i = 0; i < 10000; i++) {
            c.voidEval("load('XY.Rdata')");
            //c.voidEval("km5_467120674 <- km(y~1,optim.method='gen',penalty = NULL,covtype='matern5_2',noise.var = rep(.1,dim(Y5_467120674)[1])^2, design=X5_467120674,response=Y5_467120674,control=list(trace=FALSE))");
            //c.voidEval("EGO5_467120674 <- max_qEI.CL(model=km5_467120674,npoints=10,L=c(min(-Y5_467120674$y),.1),lower=c(0.0,0.0),upper=c(1.0,1.0),control=list(trace=FALSE,debug=TRUE,MemoryMatrix=FALSE))");

            /*c.voidEval("X <- maximinLHS(n=100,k=2)");
            c.voidEval("Y <- apply(FUN=branin,X=X,MARGIN=1)");
            c.voidEval("set.seed(1)");
            c.eval("k <- km(lower=c(0,0),upper=c(1,1),response=((Y)),design=as.data.frame(X),formula=~1,covtype='matern3_2',nugget=100)");
            c.eval("try(EGO <- max_qEI.CL(upper=c(1,1),lower=c(0,0),L=min(Y),model=k,npoints=10))");*/
            String[] ls = c.eval("ls()").asStrings();
            System.out.println(cat(ls));
            for (String l : ls) {
                System.out.println("  " + l);
                try {
                    System.out.println("    " + cat(silentlyEval("print(" + l + ")", true, c).asString()));
                } catch (Exception re) {
                }
            }
        }
    }

    public static REXP silentlyEval(String expression, boolean tryEval, RConnection connection) {
        if (expression == null) {
            return null;
        }
        if (expression.trim().length() == 0) {
            return null;
        }
        /*for (EvalListener b : eval) {
        b.eval(expression);
        }*/
        REXP e = null;
        try {
            synchronized (connection) {
                e = connection.parseAndEval((tryEval ? "try(" : "") + expression + (tryEval ? ",silent=TRUE)" : ""));
            }
        } catch (Exception ex) {
            System.err.println("HEAD_EXCEPTION" + ex.getMessage() + "\n  " + expression);
            //log(HEAD_EXCEPTION + ex.getMessage() + "\n  " + expression);
            synchronized (connection) {
                try {
                    System.err.println("    " + connection.parseAndEval("geterrmessage()").toString());
                    //log("    " + connection.parseAndEval("geterrmessage()").toString());
                } catch (Exception ex1) {
                    System.err.println("HEAD_ERROR" + ex1.getMessage() + "\n  " + expression);
                    //log(HEAD_ERROR + ex1.getMessage() + "\n  " + expression);
                }
            }
        }

        if (tryEval && e != null) {
            try {
                /*REXP r = c.parseAndEval("try("+myCode+",silent=TRUE)");
                if (r.inherits("try-error")) System.err.println("Error: "+r.asString());
                else { // success ... }*/
                if (e.inherits("try-error")/*e.isString() && e.asStrings().length > 0 && e.asString().toLowerCase().startsWith("error")*/) {
                    System.err.println("HEAD_ERROR" + e.asString() + "\n  " + expression);
                    //log(HEAD_ERROR + e.asString() + "\n  " + expression);
                    e = null;
                }
            } catch (REXPMismatchException ex) {
                System.err.println("HEAD_ERROR" + ex.getMessage() + "\n  " + expression);
                //log(HEAD_ERROR + ex.getMessage() + "\n  " + expression);
                return null;
            }
        }
        return e;
    }

    static String cat(String... S) {
        String cat = "";
        for (String s : S) {
            cat = cat + "," + s;
        }
        return cat.substring(1);
    }

    public static void main(String args[]) {
        org.junit.runner.JUnitCore.main(RserveTest.class.getName());
    }
}
