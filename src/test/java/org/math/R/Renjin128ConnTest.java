package org.math.R;

import org.junit.Before;
import org.junit.Test;
import org.renjin.script.RenjinScriptEngine;
import org.renjin.script.RenjinScriptEngineFactory;

/**
 *
 * @author richet
 */
public class Renjin128ConnTest {

    RenjinScriptEngine R = null;

    public static void main(String args[]) {
        org.junit.runner.JUnitCore.main(Renjin128ConnTest.class.getName());
    }

    @Test
    public void testLess128Connections() throws Exception {
        for (int i = 0; i < 100; i++) {
            R.eval("sink('toto.out',type='output')");
            R.eval("1+1");
            R.eval("sink(type='output')");
        }
    }

    // @Test
    public void testExceed128ConnectionsOld() throws Exception {
        try {
            for (int i = 0; i < 129; i++) {
                R.eval("sink('toto.out',type='output')");
                R.eval("1+1");
                R.eval("sink(type='output')");
            }
        } catch (Exception e) {
            assert e.getMessage().contains("maximum number of connections exceeded"): "Bad exception: "+e.getMessage();
            return;
        }
        assert false : "Did not failed as expected...";
    }
    
    @Test //Now fixed in Renjin
    public void testExceed128Connections() throws Exception {
            for (int i = 0; i < 129; i++) {
                R.eval("sink('toto.out',type='output')");
                R.eval("1+1");
                R.eval("sink(type='output')");
            }
    }

    @Before
    public void setUp() throws Exception {
        R = new RenjinScriptEngineFactory().getScriptEngine();
        if (R == null) {
            throw new RuntimeException("Renjin Script Engine not found in the classpath.");
        }
    }
}
