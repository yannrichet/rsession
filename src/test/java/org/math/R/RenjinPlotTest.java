package org.math.R;

import java.io.File;
import java.io.IOException;
import java.util.logging.Level;
import java.util.logging.Logger;
import javax.script.ScriptException;
import org.junit.Before;
import org.junit.Test;
import org.renjin.aether.AetherPackageLoader;
import org.renjin.eval.Session;
import org.renjin.eval.SessionBuilder;
import org.renjin.primitives.packaging.PackageLoader;
import org.renjin.script.RenjinScriptEngine;
import org.renjin.script.RenjinScriptEngineFactory;

/**
 *
 * @author richet
 */
public class RenjinPlotTest {

    RenjinScriptEngine R = null;

    public static void main(String args[]) {
        org.junit.runner.JUnitCore.main(RenjinPlotTest.class.getName());
    }

    @Before
    public void setUp() throws Exception {
        Session session = new SessionBuilder().bind(PackageLoader.class, new AetherPackageLoader()).withDefaultPackages().build();
        R = new RenjinScriptEngineFactory().getScriptEngine(session);
        if (R == null) {
            throw new RuntimeException("Renjin Script Engine not found in the classpath.");
        }
    }

    @Test
    public void plotWindow() throws IOException {
        try {
            R.eval("graphics::barplot(c(1,2,3), main='Distribution', xlab='Number')");
        } catch (ScriptException ex) {
            Logger.getLogger(RenjinPlotTest.class.getName()).log(Level.SEVERE, null, ex);
        }
    }

    @Test
    public void plotPngFile() throws IOException {
        File test = null;
        try {
            R.eval("png('test.png')");
            R.eval("graphics::barplot(c(1,2,3), main='Distribution', xlab='Number')");
            R.eval("dev.off()");
            test = new File(R.eval("getwd()").toString(), "test.png");
        } catch (ScriptException ex) {
            Logger.getLogger(RenjinPlotTest.class.getName()).log(Level.SEVERE, null, ex);
        }
        assert test.isFile() : "Png file not created by plot()";
    }
}
