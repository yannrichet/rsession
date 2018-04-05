package org.math.R;

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
public class RenjinLibraryTest {

    RenjinScriptEngine R = null;

    public static void main(String args[]) {
        org.junit.runner.JUnitCore.main(RenjinLibraryTest.class.getName());
    }

    @Test
    public void testInstallPackage() throws Exception {
        R.eval("library('pso')");
    }

    @Before
    public void setUp() throws Exception {
        Session session = new SessionBuilder().bind(PackageLoader.class, new AetherPackageLoader()).build();
        R = new RenjinScriptEngineFactory().getScriptEngine(session);
        if (R == null) {
            throw new RuntimeException("Renjin Script Engine not found in the classpath.");
        }
    }
}
