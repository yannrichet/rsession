package org.math.R;

import java.util.Arrays;
import org.junit.Before;
import org.junit.Test;
import org.renjin.aether.AetherPackageLoader;
import org.renjin.eval.Session;
import org.renjin.eval.SessionBuilder;
import org.renjin.primitives.packaging.PackageLoader;
import org.renjin.script.RenjinScriptEngine;
import org.renjin.script.RenjinScriptEngineFactory;
import org.renjin.sexp.LogicalArrayVector;
import org.renjin.sexp.StringArrayVector;

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
    public void testLoadPackage() throws Exception {
        Object ret = R.eval("library('pso',logical.return=T,quietly=T,verbose=F)");
        System.err.println(">> " + ret + " (" + ret.getClass() + ")");
        assert ((LogicalArrayVector) ret).isElementTrue(0) : "Cannot load pso";
    }

    @Test
    public void testInstallPackage() throws Exception {
        Object ret = R.eval("library('pso')");
        System.err.println(">> " + ret + " (" + ret.getClass() + ")");
        String[] a = ((StringArrayVector) ret).toArray();
        assert Arrays.asList(a).contains("pso") : "Cannot find pso in " + Arrays.asList(a);
    }

    @Before
    public void setUp() throws Exception {
        Session session = new SessionBuilder().bind(PackageLoader.class, new AetherPackageLoader()).withDefaultPackages().build();
        R = new RenjinScriptEngineFactory().getScriptEngine(session);
        if (R == null) {
            throw new RuntimeException("Renjin Script Engine not found in the classpath.");
        }
    }
}
