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

    //@Test
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

    //@Test
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

    //@Test
    public void sink_stack_is_full_exception_when_run_RSession() throws Rsession.RException {
        for (int i = 0; i < 25; i++) {
            RserverConf rconf = new RserverConf("127.0.0.1", 6311, "", "", new Properties());
            Rsession s = null;
            s = RserveSession.newInstanceTry(System.out, rconf);
            s.voidEval("warning2error <- function(code=\"\") { tryCatch(code, warning = function(e) stop(e)) }");
            try {
                s.eval("warning2error(warning(\'hahaha\'))", true);
                assert false : "Did not report error";
            } catch (Rsession.RException err) {
                assert true;
            }
            s.end();
        }
    }

    @Test
    public void Found_a_bug_in_isPackageInstalled() throws Rsession.RException {
        Rsession s = RserveSession.newInstanceTry(System.out, null);
        s.installPackage("atsd", false);

        if ((Boolean) s.eval("is.element(set=row.names(packs), el='zoo')")) {
            s.voidEval("remove.packages('zoo')");
        }

        assert (Boolean) s.eval("is.element(set=row.names(packs), el='zoo')") == false : "Package still here !";

        assert !s.isPackageInstalled("zoo", null) : "Package uninstalled !";
    }
}
