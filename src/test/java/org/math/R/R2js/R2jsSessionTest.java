package org.math.R.R2js;

import java.io.*;
import java.net.InetAddress;
import java.net.UnknownHostException;
import java.nio.charset.Charset;
import java.text.DecimalFormat;
import java.text.DecimalFormatSymbols;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import javax.script.ScriptEngine;
import javax.script.ScriptEngineManager;
import javax.script.ScriptException;
//import org.graalvm.polyglot.*;

import org.junit.Test;
import org.math.R.RLog;
import org.math.R.RLogSlf4j;
import org.math.R.Rsession.RException;

import static org.junit.Assert.*;
import org.junit.Before;

/**
 * Test the converter r->js of the class {@link R2jsSession}
 *
 * @author Nicolas Chabalier
 *
 */
public class R2jsSessionTest {

    // maximal epsilon wanted between actual and expected values
    final double epsilon = 1e-12;
    R2jsSession engine = R2jsSession.newInstance(new RLogSlf4j(), null);

    public static void main(String args[]) {
        org.junit.runner.JUnitCore.main(R2jsSessionTest.class.getName());
    }

    @Before
    public void setUp() throws Exception {
        //engine.debug_js = true;
    }

    @Test
    public void test2Sessions() throws RException { // was failing for f <- function(){return(list(a=1,b=2))}; f()[['a']] called in _TWO_ engines
        engine.debug_js = true;

        R2jsSession engine2 = R2jsSession.newInstance(new RLogSlf4j(), null);
        engine2.debug_js = true;

        // ok for simple list
        engine.voidEval("l <- list(a=1,b=2)");
        assert engine.asDouble(engine.eval("l[['a']]")) == 1.0;
        engine2.voidEval("l <- list(a=1,b=2)");
        assert engine2.asDouble(engine2.eval("l[['a']]")) == 1.0;

        // ok for simple function
        engine.voidEval("f <- function(){return(1)}");
        assert engine.asDouble(engine.eval("f()")) == 1.0;
        engine2.voidEval("f <- function(){return(1)}");
        assert engine2.asDouble(engine2.eval("f()")) == 1.0;

        // ok when list returned from function, but used after affectation
        engine.voidEval("f <- function(){return(list(a=1,b=2))}");
        assert engine.asDouble(engine.eval("f()[['a']]")) == 1.0;
        engine2.voidEval("f <- function(){return(list(a=1,b=2))}");
        engine2.voidEval("ff = f()");
        assert engine2.asDouble(engine2.eval("ff[['a']]")) == 1.0;

        // ok when list returned from function with non empty argument and called directly
        engine.voidEval("f <- function(x){return(list(a=1,b=2))}");
        assert engine.asDouble(engine.eval("f(0)[['a']]")) == 1.0;
        engine2.voidEval("f <- function(x){return(list(a=1,b=2))}");
        System.err.println(engine2.eval("f(0)"));
        engine2.voidEval("ff = f(0)");
        System.err.println(engine2.eval("ff[['a']]"));
        assert engine2.asDouble(engine2.eval("f(0)[['a']]")) == 1.0;

        // fail when list returned from function without arg and called directly
        engine.voidEval("f <- function(){return(list(a=1,b=2))}");
        assert engine.asDouble(engine.eval("f()[['a']]")) == 1.0;
        engine2.voidEval("f <- function(){return(list(a=1,b=2))}");
        System.err.println(engine2.eval("f()"));
        engine2.voidEval("ff = f()");
        System.err.println(engine2.eval("ff[['a']]"));
        assert engine2.asDouble(engine2.eval("f()[['a']]")) == 1.0;
    }

    @Test
    public void testVarNames() throws RException {
        engine.debug_js = true;

        // check that R variable is usable
        engine.voidEval("R = 1");
        engine.voidEval("A = matrix( c(2, 4, 3, 1, 5, 7), nrow=3, ncol=2, byrow=TRUE)");

        // check that R variable is usable
        engine.voidEval("rand = 1");
        engine.voidEval("runif(1)");

        // check that math variable is usable: NO, math.js has a strange behavior ...
        //engine.voidEval("math = 1");
        //engine.voidEval("1+pi");
    }

    @Test
    public void testConvertWithEnv() throws RException, UnknownHostException {
        System.err.println("================= testConvertWithEnv ===============");
        engine.debug_js = true;
        engine.voidEval("__this__a = 123.5");
        assert engine.asDouble(engine.eval("__this__a+1")) == 124.5;
    }

    @Test
    public void testQuoteExprError() {
        System.err.println("================= testQuoteExprError ===============");
        engine.debug_js = true;
        try {
            engine.voidEval("l = c('a','b','c')");
            assert Arrays.equals(engine.ls(), new String[]{"l"}) : Arrays.toString(engine.ls());
            engine.voidEval("l2 = c('a,'b','c')");
        } catch (RException ex) {
            assert ex.getMessage().contains("Failed to evaluate") : ex.getMessage();
        }
        assert Arrays.equals(engine.ls(), new String[]{"l"}) : Arrays.toString(engine.ls());
    }

    @Test
    public void testConvert() throws RException, UnknownHostException {
        System.err.println("================= testConvert ===============");

        String[] es = new String[]{"abc", "123", "1e5", "1ee1"};
        for (String e : es) {

            System.err.println("e=" + e);
            DecimalFormat formatter = new DecimalFormat("#.#############", DecimalFormatSymbols.getInstance(Locale.ENGLISH));
            Matcher m = Pattern.compile("(\\d|\\d\\.)+[eE]+([+-])*(\\d*)").matcher(e);
            while (m.find()) {
                try {
                    e = e.replace(m.group(), formatter.format(Double.parseDouble(m.group()))); // direct eval within java
                } catch (Exception ex) {
                }
            }
            System.err.println("=> " + e);
        }

        engine.debug_js = true;
        engine.voidEval("a='1ee1'");
        engine.voidEval("Sys__info = function() {return(list('a'='1ee1'))}");

    }

    @Test
    public void testSys() throws RException, UnknownHostException {
        System.err.println("================= testSys ===============");
        System.err.println("java.version: " + System.getProperty("java.version"));
        engine.log("java.version: " + System.getProperty("java.version"), RLog.Level.INFO);
//        Map<String, String> infos = new HashMap<String, String>();
//        infos.put("nodename", "'" + InetAddress.getLocalHost().getHostName() + "'");
//        engine.voidEval("Sys__info = function() {return(" + toRcode(infos) + ")}");
//        String nodename = (String) engine.eval("Sys.info()[['nodename']]");
//        assert nodename != null && nodename.length() > 0 : "Cannot get nodename";

        String sysinfo = "Sys__info = function() {return(" + asRList(newMap(
                "nodename", InetAddress.getLocalHost().getHostName(),
                "sysname", System.getProperty("os.name"),
                "release", "?",
                "version", System.getProperty("os.version"),
                "user", System.getProperty("user.name")
        )) + ")}";
        System.err.println("will eval Sys.info: " + sysinfo);
        engine.voidEval(sysinfo);
        engine.voidEval("Sys__getenv = function(v) {env=list('R_HOME'='')\nreturn(env[v])}");//+toRcode(System.getenv())+")\nreturn(env[v])}");
        engine.voidEval("options = function() {return(" + asRList(newMap(
                "OutDec", DecimalFormatSymbols.getInstance().getDecimalSeparator()
        )) + ")}");

        String nodename = (String) engine.eval("Sys.info()[['nodename']]");
        assert nodename != null && nodename.length() > 0 : "Cannot get nodename";

        System.err.println("//////////////////// testSys //////////////////////");
    }

    public static Map newMap(Object... o) {
        Map m = new HashMap();
        for (int i = 0; i < o.length / 2; i++) {
            m.put(o[2 * i], o[2 * i + 1]);
        }
        return m;
    }

    String asRList(Map m) {
        if (m.isEmpty()) {
            return "list()";
        }
        String l = "list(";
        for (Object k : m.keySet()) {
            l = l + k + "='" + m.get(k) + "',";
        }
        return l.substring(0, l.length() - 1) + ")";
    }


    public void testThatEvaluationFail(String expr) {
        boolean testFail=false;
        try{
            System.out.println(engine.eval(expr));
        } catch (Exception e) {
            testFail=true;
        }
        assertTrue("Evaluation should have thrown an exception", testFail);
    }

    @Test
    public void testBasSyntaxes() throws RException {
        engine.debug_js = true;

        testThatEvaluationFail("c(1,2");
        testThatEvaluationFail(")c(1,2");
        testThatEvaluationFail("1,2+3");
        testThatEvaluationFail("1,2+3)");
        testThatEvaluationFail("()1,2+3");
        testThatEvaluationFail("1(,)2+3");
        testThatEvaluationFail("c)1,2(");
        testThatEvaluationFail("c1,2(");
        testThatEvaluationFail("c)1,2");
        testThatEvaluationFail("c(1,2))");
        testThatEvaluationFail("c((1,2");
        testThatEvaluationFail("(c(1,2())");
        testThatEvaluationFail("c[1,2");
        testThatEvaluationFail("]c[1,2");
        testThatEvaluationFail("1,2+3");
        testThatEvaluationFail("1,2+3]");
        testThatEvaluationFail("[]1,2+3");
        testThatEvaluationFail("1[,]2+3");
        testThatEvaluationFail("c]1,2[");
        testThatEvaluationFail("c1,2[");
        testThatEvaluationFail("c]1,2");
        testThatEvaluationFail("c[1,2]]");
        testThatEvaluationFail("c[[1,2");
        testThatEvaluationFail("[c[1,2[]]");
        testThatEvaluationFail(")c(1,2[]]");
        testThatEvaluationFail(")c(1,2[]()");
        testThatEvaluationFail(")c(1,2()[]");
        testThatEvaluationFail("c[(1,2])");
        testThatEvaluationFail("c]]1,2[[");
        testThatEvaluationFail("f{zee");
        testThatEvaluationFail("f[zee");
        testThatEvaluationFail("f(zee");
        //testThatEvaluationFail("c([1,2)]"); todo: should fail
    }

    @Test
    public void testBasicSyntaxes() throws RException {
        engine.debug_js = true;

        assert (Double) engine.eval("2*3+4*+5") == 26;
        assert (Double) engine.eval("2*+3+4*5") == 26;
        assert (Double) engine.eval("2*3+4*-5") == -14;
        assert (Double) engine.eval("2*-3+4*5") == 14;
        assert (Double) engine.eval("2  * 3 + 4* + 5") == 26;
        assert (Double) engine.eval("2 *+  3    +4  * 5") == 26;
        assert (Double) engine.eval("2 * 3+ 4 * -5" ) == -14;
        assert (Double) engine.eval("2 * -3  +4 * 5") == 14;
        assertEquals((Double) engine.eval("2/4+4/+5"), 1.3, epsilon);
        assertEquals((Double) engine.eval("2/+4+4/5"), 1.3, epsilon);
        assertEquals((Double) engine.eval("2/4+4/-5"), -0.3, epsilon);
        assertEquals((Double) engine.eval("2/-4+4/5"), 0.3, epsilon);
        assertEquals((Double) engine.eval("2**4+4**-5"), 16.0009765625, epsilon);
        assertEquals((Double) engine.eval("2**+4+4**+5"), 1040, epsilon);
        assertEquals((Double) engine.eval("2 /  4 + 4/  +5"), 1.3, epsilon);
        assertEquals((Double) engine.eval("2 /  +4  +4  /5 "), 1.3, epsilon);
        assertEquals((Double) engine.eval("2 /4 +4/-5"), -0.3, epsilon);
        assertEquals((Double) engine.eval("2 /-4 +4 /5"), 0.3, epsilon);
        assertEquals((Double) engine.eval("2 ** 4+4 **- 5"), 16.0009765625, epsilon);
        assertEquals((Double) engine.eval("2 **  + 4 +4** + 5"), 1040, epsilon);
        assertEquals((Double) engine.eval("2^4+4^-5"), 16.0009765625, epsilon);
        assertEquals((Double) engine.eval("2^+4+4^+5"), 1040, epsilon);
        assertEquals((Double) engine.eval("2 ^ 4+4 ^- 5"), 16.0009765625, epsilon);
        assertEquals((Double) engine.eval("2 ^  + 4 +4^ + 5"), 1040, epsilon);




        // Check infinity is available
        assert Double.isInfinite((Double) engine.eval("a <- Inf"));
        assert Double.isInfinite((Double) engine.eval("a <- -Inf"));

        engine.voidEval("a <- NaN");
        assert Double.isNaN((Double) engine.eval("a")) : engine.eval("a");
        assert Double.isNaN((Double) engine.eval("a+1")) : engine.eval("a");

        engine.voidEval("a = 1");
        assert (Double) engine.eval("a") == 1;
        engine.voidEval("a <- 1");
        assert (Double) engine.eval("a") == 1;
        assert (Double) engine.eval("a+1") == 2;
        assertEquals((Double) engine.eval("a+pi"), (1 + Math.PI), epsilon);
        engine.eval("b <- (1)");
        assert (Double) engine.eval("b") == 1;

        assert Double.parseDouble(engine.eval("1.23E-4").toString()) == 1.23E-4 : engine.eval("1.23E-4").toString();
        assert Double.parseDouble(engine.eval("1.23E-10").toString()) == 1.23E-10;
        assert Double.parseDouble(engine.eval("11.23E-4").toString()) == 11.23E-4;
        assert Double.parseDouble(engine.eval("1+1.23E-4").toString()) == 1.000123 : engine.eval("1+1.23E-4").toString();
        assert Double.parseDouble(engine.eval("2*1.23E-4").toString()) == 2 * 1.23E-4 : engine.eval("10*1.23E-4").toString();
        assert Double.parseDouble(engine.eval("sin(1.23E-4)").toString()) == Math.sin(1.23E-4);
        assert Double.parseDouble(engine.eval("1.23e-4").toString()) == 1.23E-4;
        assert Double.parseDouble(engine.eval("1.23E4").toString()) == 1.23E4;
        assert Double.parseDouble(engine.eval("1.23e4").toString()) == 1.23E4;
        assert Double.parseDouble(engine.eval("2E4").toString()) == 2E4;
        assert Double.parseDouble(engine.eval("2e4").toString()) == 2E4;
        assert Double.parseDouble(engine.eval("2.0e4").toString()) == 2E4;
        assert Double.parseDouble(engine.eval("2.0E4").toString()) == 2E4;

        // Test if power is relaced in variable name
        engine.voidEval("e235 = function(){return 12}");
        assert (Double) engine.eval("e235()") == 12;

        assert Double.parseDouble(engine.eval("2 ^ 3").toString()) == 8;
        assert Double.parseDouble(engine.eval("2^3").toString()) == 8;
        assert Double.parseDouble(engine.eval("2**3").toString()) == 8;
        assert Double.parseDouble(engine.eval("2 ** 3").toString()) == 8;
        assert Boolean.parseBoolean(engine.eval("1>2").toString()) == false;

        try {
            engine.voidEval("if (1<2) print('toto') else print('titi')");
            assert true;
        } catch (Exception e) {
            assert false : "Throw error: " + e;
        }

        assert !(engine.eval("1<NaN") instanceof Boolean) : "Sadly eval NaN test as boolean: " + engine.eval("1<NaN");
        try {
            engine.voidEval("if (1<NaN) print('toto') else print('titi')");
            assert false : "Did not raise error";
        } catch (Exception e) {
            assert e instanceof RException : "Bad error: " + e;
        }
        assert Boolean.parseBoolean(engine.eval("1>1").toString()) == false;
        assert Boolean.parseBoolean(engine.eval("1>=2").toString()) == false;
        assert Boolean.parseBoolean(engine.eval("1>=1").toString()) == true;
        assert Boolean.parseBoolean(engine.eval("1<=2").toString()) == true;
        assert Boolean.parseBoolean(engine.eval("1<=1").toString()) == true;
        assert Boolean.parseBoolean(engine.eval("1==2").toString()) == false;
        assert Boolean.parseBoolean(engine.eval("1==1").toString()) == true;
        assert Boolean.parseBoolean(engine.eval("1!=2").toString()) == true;
        assert Boolean.parseBoolean(engine.eval("1!=1").toString()) == false;

        // '++' operator
        engine.voidEval("a <- 1");
        engine.voidEval("a++");
        assert ((Double) engine.eval("a") == 2);

        // '+=' operator
        engine.voidEval("a <- 1");
        engine.voidEval("a+=3");
        assert ((Double) engine.eval("a") == 4);

        // Operators
        engine.debug_js = true;
        assert (Double) engine.eval("0-1-2") == -3;
        assert (Double) engine.eval("-1-2") == -3;

        engine.voidEval("a = 2^2 - 2^2");
        assert (Double) engine.eval("a") == 0 : (Double) engine.eval("a") + " != 2^2 - 2^2";

        engine.voidEval("a = 2^2-2^2");
        assert (Double) engine.eval("a") == 0 : (Double) engine.eval("a") + " != 2^2-2^2";

        engine.voidEval("a = -4 * 10 -5* 100");
        assert (Double) engine.eval("a") == -540 : (Double) engine.eval("a") + " != -540";

        engine.voidEval("a = -4 * -10 -5* -100");
        assert (Double) engine.eval("a") == 540 : (Double) engine.eval("a") + " != 540";

        engine.voidEval("a = -4*10-5*100");
        assert (Double) engine.eval("a") == -540;

        engine.voidEval("a = (-4 * 10) +(-5* 100)");
        assert (Double) engine.eval("a") == -540;

        engine.voidEval("a = -4 * -10 -5*-100 + 18 * (-5*(3+9))");
        assert (Double) engine.eval("a") == -540;

        engine.voidEval("a = (3 - 5) * (-6 + 5) / 2 ");
        assert (Double) engine.eval("a") == 1;

        assertEquals((Double) engine.eval("(5*6+12/15)/48*56+(5)-48"), (5. * 6. + 12. / 15.) / 48. * 56. + (5.) - 48., epsilon);
        assertEquals((Double) engine.eval("4-6-5+-7+(-5)-(-48+4)-(+56)"), 4 - 6 - 5 + -7 + (-5) - (-48 + 4) - (+56), epsilon);
        assertEquals((Double) engine.eval("45.*0.2/-65.5*45.+(45.-5.*7.)*(+8.-4.-(-1.)+(-1.))/1.2/41./-5.*12.*0.1+4.*(48.+1.2/4.)/4."), 45. * 0.2 / -65.5 * 45. + (45. - 5. * 7.) * (+8. - 4. - (-1.) + (-1.)) / 1.2 / 41. / -5. * 12. * 0.1 + 4. * (48. + 1.2 / 4.) / 4., epsilon);

        assert engine.asLogical(engine.eval("TRUE | TRUE")) == true : engine.eval("TRUE | TRUE");
        assert engine.asLogical(engine.eval("FALSE | TRUE")) == true;
        assert engine.asLogical(engine.eval("FALSE | FALSE")) == false;
        assert engine.eval("FALSE | NULL") == null;

        assert engine.asLogical(engine.eval("TRUE || TRUE")) == true : engine.eval("TRUE || TRUE");
        assert engine.asLogical(engine.eval("FALSE || TRUE")) == true;
        assert engine.asLogical(engine.eval("FALSE || FALSE")) == false;
        try {
            engine.eval("FALSE || NULL");
            assert false : "Did not raise error";
        } catch (Exception e) {
            assert e instanceof RException : "Bad error: " + e;
        }

        assert engine.asLogical(engine.eval("TRUE & TRUE")) == true : engine.eval("TRUE & TRUE");
        assert engine.asLogical(engine.eval("FALSE & TRUE")) == false;
        assert engine.asLogical(engine.eval("FALSE & FALSE")) == false;
        assert engine.eval("FALSE & NULL") == null;

        assert engine.asLogical(engine.eval("TRUE && TRUE")) == true : engine.eval("TRUE && TRUE");
        assert engine.asLogical(engine.eval("FALSE && TRUE")) == false;
        assert engine.asLogical(engine.eval("FALSE && FALSE")) == false;
        assert engine.asLogical(engine.eval("FALSE && NULL")) == false;
    }

    @Test
    public void testRand() throws RException {

        assert ((double[]) engine.eval("runif(10,0,1)")).length == 10;
        assert ((double[]) engine.eval("rnorm(10,0,1)")).length == 10;
        assert ((double[]) engine.eval("rpois(10,10)")).length == 10;
        assert ((double[]) engine.eval("rchisq(10,2)")).length == 10;
        assert ((double[]) engine.eval("rcauchy(10,1,1)")).length == 10;

    }

    @Test
    public void testPaste() throws RException {
        engine.debug_js = true;

        assert engine.eval("paste('a','b','c',sep='v')").equals("avbvc") : engine.eval("paste('a','b','c',sep='v')");
        assert engine.eval("paste0('a','b','c')").equals("abc") : engine.eval("paste0('a','b','c')");

        engine.voidEval("x = matrix(c(1,2,3,4,5,6),ncol=2)");
        assert engine.eval("paste(x[1,],collapse='v')").equals("1v4") : engine.eval("paste(x[1,],collapse='v')");
        assert engine.eval("paste0(x[1,],collapse='v')").equals("1v4") : engine.eval("paste0(x[1,],collapse='v')");

        engine.voidEval("x1 = matrix(c(1.23,2.23,3.23,4.23,5.23,6.23),ncol=2)");
        assert engine.eval("paste(x1[1,],collapse='v')").equals("1.23v4.23") : engine.eval("paste(x1[1,],collapse='v')");
        assert engine.eval("paste0(x1[1,],collapse='v')").equals("1.23v4.23") : engine.eval("paste0(x1[1,],collapse='v')");

        assert engine.eval("paste('a1','b2')").equals("a1 b2") : engine.eval("paste('a1','b2')");
        assert engine.eval("paste(c('a1','b2'),c('c3','d4'))").equals("a1 c3;b2 d4") : engine.eval("paste(c('a1','b2'),c('c3','d4'))");
        assert engine.eval("paste(c('a1','b2'),'d4')").equals("a1 d4;b2 d4") : engine.eval("paste(c('a1','b2'),'d4')");

        assert engine.eval("paste(sep='<br/>',\n"
                + "        paste('<HTML name=\"minimum\">minimum is ',0.1),\n"
                + "        paste(sep='',\n"
                + "            'found at ',\n"
                + "            paste(collapse='; ',paste(c('x1','x2'),'=',c(.5,.6))),\n"
                + "            '<br/><img src=\"',\n"
                + "            'files',\n"
                + "            '\" width=\"',600,'\" height=\"',600,\n"
                + "            '\"/></HTML>'))").toString().contains("\"minimum\"");

        assert engine.eval("paste0(\"<HTML name='minimum'>minimum is \",0.1,\n"
                + "                \" found at \",\n"
                + "                paste0(paste(c('x1','x2'),'=',c(.5,.6), collapse=';')),\n"
                + "                \"<br/><img src='\",\n"
                + "                \"files\",\n"
                + "                \"' width='600' height='600'/></HTML>\")").toString().contains("'minimum'");

    }

    @Test
    public void testZeros() throws RException {
        assert (Double) engine.eval("0.") == 0.;
        assert (Double) engine.eval("000.") == 0.;
        assert (Double) engine.eval("0.0") == 0.;
        assert (Double) engine.eval(".0") == 0.;
        assert (Double) engine.eval("0.+1") == 1.;
    }

    @Test
    public void testParseStrArray() throws RException {
        String expr0 = "strsplit(\"a,b,c\",\",\")";
        assert engine.eval("paste(" + expr0 + ",collapse=';')").equals("a;b;c") : engine.eval("paste(" + expr0 + ",collapse=';')");

        String expr1 = "unlist(strsplit(\"a,b,c\",\",\"))";
        assert engine.eval("paste(" + expr1 + ",collapse=';')").equals("a;b;c") : engine.eval("paste(" + expr1 + ",collapse=';')");

        String expr2 = "as.character(\"a,b,c\")";
        assert engine.eval(expr2).equals("a,b,c") : expr2 + " -> " + engine.eval(expr2);

        String expr3 = "as.integer(unlist(strsplit(as.character(\"1,2,3\"),\",\")))";
        engine.eval(expr3);
    }

    @Test
    public void testWrongSyntaxes() throws RException {

        try {
            // good syntax is 2*(1.2)
            engine.eval("2*(1,2)");
            assert false : "Should throw exception";
        } catch (Exception e) {
            assert true;
        }
    }

    @Test
    public void testIfElse() throws RException {

        engine.voidEval("x=12");

        assert (Double) engine.eval("if (x>1) 1+x else 1-x") == 13.;
        assert (Double) engine.eval("if(x>1) 1+x else 1-x") == 13.;
        assert (Double) engine.eval("if (x>1) {1+x} else {1-x}") == 13.;
        assert (Double) engine.eval("if  (x> 1)   {1+x}   else   {1- x}") == 13.;
        assert (Double) engine.eval("if(x>1){1+x}else{1-x}") == 13.;
        assert (Double) engine.eval("if(x>1)(1+x)else(1-x)") == 13.;
        assert (Double) engine.eval("if (x>1) (1+x) else (1-x)") == 13.;
        assert (Double) engine.eval("if(x>1){1+x}else(1-x)") == 13.;
        assert (Double) engine.eval("if(x>1)(1+x)else{1-x}") == 13.;
        assert (Double) engine.eval("if  (x>1) (1+x)   else {1-x}  ") == 13.;

        engine.voidEval("MFR_Pu=0");
        engine.voidEval("CPu_gL=2");
        engine.voidEval("CU_gL=3");
        assert (Double) engine.eval("if(MFR_Pu==1){CPu_gL}else(CU_gL)") == 3.0;
        assert (Double) engine.eval("if(MFR_Pu==1){CPu_gL}else (CU_gL)") == 3.0;
        engine.voidEval("MFR_Pu=1");
        assert (Double) engine.eval("if(MFR_Pu==1){CPu_gL}else (CU_gL)") == 2.0;
    }

    @Test
    public void testApply() throws RException {
        String apply_f = "apply(X,1,function (x) {\n"
                + "     x1 <- x[1] * 15 - 5\n"
                + "     x2 <- x[2] * 15\n"
                + "     return((x2 - 5/(4 * pi^2) * (x1^2) + 5/pi * x1 - 6)^2 + 10 * (1 - 1/(8 * pi)) * cos(x1) + 10)\n"
                + " })";

        engine.voidEval("X = matrix(c(0,1,0,1),ncol=2)");
        assert Arrays.equals((double[]) engine.eval(apply_f), new double[]{305.9563016021099, 152.01412645333116});

        engine.voidEval("X = matrix(c(0,0),nrow=1)");
        assert Arrays.equals((double[]) engine.eval(apply_f), new double[]{305.9563016021099});
    }

    @Test
    public void testFunctions() throws RException {

        engine.voidEval("height.cylinder_cm <- function(x,y,z) {return x*y*z}");
        engine.voidEval("h <- function() { height.cylinder_cm(r(),m(),p()) }");
        engine.voidEval("r <- function() {return 1}");
        engine.voidEval("m <- function() {return 2}");
        engine.voidEval("p <- function() {return 3}");
        assert (Double) engine.eval("h()") == 6;

        engine.voidEval("f = function(x) {return(1+x)}");
        assert (Double) engine.eval("f(1.23)") == 2.23;
        assert (Double) engine.eval("f(x=1.23)") == 2.23;

        engine.voidEval("f = function(x) 1+x");
        assert (Double) engine.eval("f(1.23)") == 2.23;

        engine.voidEval("f = function(x) {\nreturn(1+x)\n}");
        assert (Double) engine.eval("f(1.23)") == 2.23;

        engine.voidEval("f = function(x) {\n1+x;\n}");
        assert (Double) engine.eval("f(1.23)") == 2.23;

        engine.voidEval("f=function(x){\n1+x;\n}");
        assert (Double) engine.eval("f(1.23)") == 2.23;

        engine.voidEval("f =function(x){\n1+x;\n}");
        assert (Double) engine.eval("f(1.23)") == 2.23;

        engine.voidEval("f = function(x){1+x;}");
        assert (Double) engine.eval("f(1.23)") == 2.23;

        engine.voidEval("f = function(x){1+x}");
        assert (Double) engine.eval("f(1.23)") == 2.23;

        engine.voidEval("f = function(x) {\nif (x>1) return(1+x) else return(1-x)\n}");
        assert (Double) engine.eval("f(1.23)") == 2.23;
        assert (Double) engine.eval("f(0.23)") == 0.77;

        engine.voidEval("f = function(x) {\nif (x>1) {return(1+x)} else {return(1-x)}\n}");
        assert (Double) engine.eval("f(1.23)") == 2.23;
        assert (Double) engine.eval("f(0.23)") == 0.77;

        engine.voidEval("f = function(x) {\nif (x>1) return(1+x) \n else return(1-x)\n}");
        assert (Double) engine.eval("f(1.23)") == 2.23;
        assert (Double) engine.eval("f(0.23)") == 0.77;

        engine.voidEval("f = function(x) {\ny = 0\nfor (i in 1:floor(x)) y=y+i\nreturn(y)\n}");
        assert (Double) engine.eval("f(3.23)") == 6;

        engine.voidEval("f = function(x) {\ny=0\nfor(i in 1:floor(x))y=y+i\nreturn(y)\n}");
        assert (Double) engine.eval("f(3.23)") == 6;

        engine.voidEval("f = function(x) {\nif (x>1) {\ny = 0\nfor (i in 1:floor(x)) y=y+i\nreturn(y)\n} else return(1-x)\n}");
        assert (Double) engine.eval("f(1.23)") == 1;
        assert (Double) engine.eval("f(3.23)") == 6;

        engine.voidEval("f = function(x) {\nif (x>1) {return(1+x)} else {return(1-x)}\n}");
        assert (Double) engine.eval("f(1.23)") == 2.23;
        assert (Double) engine.eval("f(0.23)") == 0.77;

        engine.voidEval("f = function(x) {\nif (x>1) return(1+x) \n else return(1-x)\n}");
        assert (Double) engine.eval("f(1.23)") == 2.23;
        assert (Double) engine.eval("f(0.23)") == 0.77;

        engine.voidEval("f = function(x,y) {return(x-y)}");
        assertEquals((Double) engine.eval("f(1.23,4.56)"), -3.33, epsilon);
        assertEquals((Double) engine.eval("f(x=1.23,y=4.56)"), -3.33, epsilon);
        assertEquals((Double) engine.eval("f(x=4.56,y=1.23)"), 3.33, epsilon);

        engine.voidEval("f <- function(x,y){return(x-y)}");
        assertEquals((Double) engine.eval("f(1.23,4.56)"), -3.33, epsilon);
        assertEquals((Double) engine.eval("f(x=1.23,y=4.56)"), -3.33, epsilon);
        assertEquals((Double) engine.eval("f(x=4.56,y=1.23)"), 3.33, epsilon);

        // WARNING: named arguments work only with ES6 and higher version (java8 use a
        // previous version of javascipt ES5, so named arguments are not supported)
        //assert Double.parseDouble( js.eval(R2MathjsSession.R2js("f(y=1.23,x=4.56)")) == 3.33;
        engine.voidEval("f = function(x) {sin(x)}");
        assertEquals(Double.parseDouble(engine.eval("f(1.23)").toString()), Math.sin(1.23), epsilon);

        engine.voidEval("f = function(x) {asin(x)}");
        assertEquals(Double.parseDouble(engine.eval("f(0.23)").toString()), Math.asin(0.23), epsilon);

        engine.voidEval("f <- function(temp_F) {\ntemp_K <- ((temp_F - 32) * (5 / 9)) + 273.15\nreturn(temp_K)\n}");
        assert Double.parseDouble(engine.eval("f(32)").toString()) == 273.15;

        engine.voidEval("a=2; h <- function(x){1-x;};");
        assert Double.parseDouble(engine.eval("h(1)").toString()) == 0;

        engine.voidEval("a = c(1.0,2,3,12);");
        engine.voidEval("f = function(x) {res=0;\n for(y in x) {res+=y};\n return res;}");
        assert (Double) engine.eval("f(a)") == 18.0 : "Cannot eval as 18.0: " + engine.eval("f(a)");

        engine.voidEval("f <- function(x=2,y=1) {return x+y}");
        assert (Double) engine.eval("f()") == 3;
        assert (Double) engine.eval("f(1.23)") == 2.23;
        assertEquals((Double) engine.eval("f(1.23, 4.56)"), 5.79, epsilon);

        engine.voidEval("a <- c(0:4);");
        engine.voidEval("f = function(x) {res=0;\n for(y in x) {res+=y};\n return res;}");
        assert (Double) engine.eval("f(a)") == 10;

        engine.voidEval("recur_factorial <- function(n) {\n if(n <= 1) {\n return(1)\n } else { \n return(n * recur_factorial(n-1))\n }\n }");
        assert (Double) engine.eval("recur_factorial(3)") == 6;
        assert (Double) engine.eval("recur_factorial(5)") == 120;

        // FIXME: multiple function in one line don't work
        //engine.eval("g<-function(x){1+x;};\nh<-function(x){1-x;};");
        //assert (Double) js.eval(" g(1)") == 2;
        //assert (Double) js.eval("h(1)") == 0;
        //
        //engine.eval(R2MathjsSession.R2js("fahrenheit_to_kelvin <- function(temp_F) {\n   temp_K <- ((temp_F - 32) * (5 / 9)) + 273.15\n   return(temp_K)\n };\n kelvin_to_celsius <- function(temp_K) {\n temp_C <- temp_K - 273.15\n   return(temp_C)\n };\n fahrenheit_to_celsius <- function(temp_F) {\n   temp_K <- fahrenheit_to_kelvin(temp_F)\n   temp_C <- kelvin_to_celsius(temp_K)\n   return(temp_C)\n };\n"));
        //assert Double.parseDouble( js.eval("fahrenheit_to_celsius(32.0);\n").toString()) == 0;
        //assert Double.parseDouble( js.eval("kelvin_to_celsius(fahrenheit_to_kelvin(32.0))").toString()) == 32;
        //assert Double.parseDouble( js.eval("kelvin_to_celsius(0)").toString()) == -273.15;
        engine.eval("f <- function(x,bool1=TRUE,bool2=TRUE) {\n   if (!bool1) {\n     a <- 1; b <- 2\n   } else {\n     a <- 3; b <- 4\n   }\n   if (bool2) {\n     c<-a*1000 + b*100\n   } else if (!bool2) {\n     c<--a*1000 - b*100\n   }\n   result <- c + x\n   return(result)\n }");
        assert (Double) engine.eval("f(1, TRUE, TRUE)") == 3401;
        assert (Double) engine.eval("f(3)") == 3403;
        assert (Double) engine.eval("f(3, FALSE, FALSE)") == -1197;

        engine.voidEval("m_g <- function() { -1 }");
        assert (Double) engine.eval("m_g()") == -1;
    }

    @Test
    public void testArrays() throws RException {
        engine.voidEval("r = rep(3.14,1)");
        engine.voidEval("print(r)");
        assert engine.eval("r") instanceof double[];
        assert Arrays.equals((double[]) engine.eval("r"), new double[]{3.14});

        engine.voidEval("a1 = c(1,2,3,12);\n b_2 = 2;");
        assert (Double) engine.eval("a1[1]") == 1;
        assert (Double) engine.eval("a1[4]") == 12;
        assert (Double) engine.eval("a1[b_2]") == 2 : engine.eval("a1[b_2]");
        assert (Double) engine.eval("a1[b_2-1]") == 1 : engine.eval("a1[b_2-1]");

        engine.voidEval("a <- c(0:4);");
        double[] m = new double[]{0, 1, 2, 3, 4};
        double[] res = (double[]) engine.eval("a");
        assert Arrays.equals(m, res);

        // Addition between arrays
        engine.voidEval("a = c(4,5,6,7)");
        engine.voidEval("b = c(1,2,1,2)");
        engine.voidEval("c = a + b");
        assert Arrays.equals((double[]) engine.eval("c"), new double[]{5, 7, 7, 9});

        // Addiction between arrays in js function
        engine.voidEval("fsum <- function(x, y) {return x+y}");
        engine.voidEval("d = fsum(a , b)");
        assert Arrays.equals((double[]) engine.eval("d"), new double[]{5, 7, 7, 9});

        // ------ Array operations -----------------
        // Dot multiplication
        engine.voidEval("a <- c(1., 2. ,3.)");
        engine.voidEval("b <- c(2., 1. ,2.)");
        engine.voidEval("c <- a*b");
        assert Arrays.equals((double[]) engine.eval("c"), new double[]{2, 2, 6});

        // Dot division
        System.err.println(engine.eval("d <- a/b")); // should be voidEval instead
        assert Arrays.equals((double[]) engine.eval("d"), new double[]{0.5, 2, 1.5});

        // Substraction
        double[] e = (double[]) engine.eval("e <- a-b");
        assert Arrays.equals((double[]) engine.eval("e"), new double[]{-1, 1, 1});

        // Addition
        engine.voidEval("f <- a+b");
        assert Arrays.equals((double[]) engine.eval("f"), new double[]{3, 3, 5});

        // Multiplication and substraction
        engine.voidEval("f <- a*b-b");
        assert Arrays.equals((double[]) engine.eval("f"), new double[]{0, 1, 4});

        // TODO  multiplication and division '%*%'
        //System.err.println(js.eval(R2MathjsSession.R2js("bt <- t(b)")));
        //System.err.println(js.eval("h <- a * bt"));
        //System.err.println(js.eval(R2MathjsSession.R2js("h <- a %*% bt")));
        //assertTrue(js.eval("h").toString().trim().equals("[todo]".trim()));
    }

    @Test
    public void testMatrices() throws RException {
        engine.voidEval("A0 = matrix( c(123), nrow=1)");
        assert Arrays.deepEquals((double[][]) engine.eval("A0"), new double[][]{{123}});
        
        engine.voidEval("A0 = matrix( 123, nrow=1)");
        assert Arrays.deepEquals((double[][]) engine.eval("A0"), new double[][]{{123}});

        // Matrix transpose
        engine.voidEval("A = matrix( c(2, 4, 3, 1, 5, 7), nrow=3, ncol=2, byrow=TRUE)");
        assert Arrays.deepEquals((double[][]) engine.eval("A"), new double[][]{{2, 4}, {3, 1}, {5, 7}});

        engine.voidEval("AA = matrix( c(2, 4, 3, 1, 5, 7), nrow=3, ncol=2, byrow=FALSE)");
        assert Arrays.deepEquals((double[][]) engine.eval("AA"), new double[][]{{2, 1}, {4, 5}, {3, 7}});

        engine.voidEval("AAA = matrix( c(2, 4, 3, 1, 5, 7), nrow=3, ncol=2)");
        assert Arrays.deepEquals((double[][]) engine.eval("AAA"), new double[][]{{2, 1}, {4, 5}, {3, 7}});

        engine.voidEval("B = t(A)");
        assert Arrays.deepEquals((double[][]) engine.eval("B"), new double[][]{{2, 3, 5}, {4, 1, 7}});

        // Matrix dot multiplication
        System.err.println(engine.eval("C = A*A")); // should be voidEval instead
        assert Arrays.deepEquals((double[][]) engine.eval("C"), new double[][]{{4, 16}, {9, 1}, {25, 49}});

        // Matrix s
        System.err.println(engine.eval("D = A^2")); // should be voidEval instead
        assert Arrays.deepEquals((double[][]) engine.eval("D"), new double[][]{{4, 16}, {9, 1}, {25, 49}}) : Arrays.deepToString((double[][]) engine.eval("D"));

        // Matrix multiplication
        engine.voidEval("A = matrix( c(2, 4, 3.23, -1, -5.34, 7), nrow=3, ncol=2, byrow=TRUE)");
        System.err.println(engine.eval("MA = A%*%t(A)")); // should be voidEval instead
        assert Arrays.deepEquals((double[][]) engine.eval("MA"), new double[][]{{20, 2.46, 17.32}, {2.46, 11.4329, -24.2482}, {17.32, -24.2482, 77.5156}});

        // Matrix division
        engine.voidEval("A = matrix( c(2, 4, 3.5, 13.5, 23.56, 7.1), nrow=3, ncol=2, byrow=TRUE)");
        engine.voidEval("B = matrix( c(2.12, 2, 2.23, 2, 3, 10.4), nrow=3, ncol=2, byrow=TRUE)");
        System.err.println(engine.eval("DA = A%/%B")); // should be voidEval instead
        assert Arrays.deepEquals((double[][]) engine.eval("DA"), new double[][]{{0, 2}, {1, 6}, {7, 0}});

        // Matrix mod
        engine.voidEval("A = matrix( c(2, 4, 3.5, 13.5, 23.56, 7.1), nrow=3, ncol=2, byrow=TRUE)");
        engine.voidEval("B = matrix( c(2.12, 2, 2.23, 2, 3, 10.4), nrow=3, ncol=2, byrow=TRUE)");
        System.err.println(engine.eval("ModA = A%%B")); // should be voidEval instead
        System.out.println(Arrays.deepToString((double[][]) engine.eval("ModA")));

        assert Arrays.deepEquals((double[][]) engine.eval("ModA"), new double[][]{{2, 0}, {1.27, 1.5}, {2.5599999999999987, 7.1}});

        // Matrix indexing
        double[][] matrix11 = (double[][]) engine.eval("A = matrix(c(1:9), nrow = 3, byrow = TRUE)");

        System.out.println(Arrays.deepToString(matrix11));
        assert (Double) engine.eval("A[2,2]") == 5 : Arrays.deepToString((double[][]) engine.eval("A"));
        assert Arrays.equals((double[]) engine.eval("A[,2]"), new double[]{2, 5, 8}) : Arrays.toString((double[]) engine.eval("A[,2]"));

        engine.eval("i=2");
        assert Arrays.equals((double[]) engine.eval("A[,i]"), new double[]{2, 5, 8}) : Arrays.toString((double[]) engine.eval("A[,i]"));

        assert Arrays.equals((double[]) engine.eval("A[,2]+1"), new double[]{3, 6, 9}) : Arrays.toString((double[]) engine.eval("A[,2]+1"));
        assert Arrays.equals((double[]) engine.eval("1+A[,2] "), new double[]{3, 6, 9});

        engine.eval("AA = A");
        engine.eval("AA[,2] = c(1,1,1)");
        assert Arrays.equals((double[]) engine.eval("AA[,2]"), new double[]{1, 1, 1}) : Arrays.deepToString((double[][]) engine.eval("AA"));
        assert Arrays.equals((double[]) engine.eval("AA[,1]"), new double[]{1, 4, 7}) : Arrays.deepToString((double[][]) engine.eval("AA"));

        engine.eval("AA = A");
        engine.eval("AA[,2] = A[,2] + 1");
        assert Arrays.equals((double[]) engine.eval("AA[,2]"), new double[]{3, 6, 9}) : Arrays.deepToString((double[][]) engine.eval("AA"));
        assert Arrays.equals((double[]) engine.eval("A[2,]"), new double[]{4, 5, 6}) : Arrays.toString((double[][]) engine.eval("A[2,]"));

        assert Arrays.deepEquals((double[][]) engine.eval("A[,]"), new double[][]{{1.0, 2.0, 3.0}, {4.0, 5.0, 6.0}, {7.0, 8.0, 9.0}});
        assert Arrays.deepEquals((double[][]) engine.eval("A[c(1,2),]"), new double[][]{{1, 2, 3}, {4, 5, 6}}) : Arrays.deepToString((double[][]) engine.eval("A[c(1,2),]"));

        // Matrix creation
        engine.voidEval("A = matrix( c(2, 4, 3, 1, 5, 7), nrow=3, ncol=2, byrow=TRUE)");
        assert Arrays.deepEquals((double[][]) engine.eval("A"), new double[][]{{2, 4}, {3, 1}, {5, 7}});

        engine.voidEval("A = matrix( c(2, 4, 3, 1, 5, 7), nrow=3, ncol=2)");
        assert Arrays.deepEquals((double[][]) engine.eval("A"), new double[][]{{2, 1}, {4, 5}, {3, 7}});

        engine.voidEval("A = matrix( c(2, 4, 3, 1, 5, 7), nrow=3, ncol=2, byrow=FALSE)");
        assert Arrays.deepEquals((double[][]) engine.eval("A"), new double[][]{{2, 1}, {4, 5}, {3, 7}});

        engine.voidEval("A = matrix( c(2, 4, 3, 1, 5, 7), nrow=2, ncol=3, byrow=TRUE)");
        engine.voidEval("B = matrix( c(1, 2, 3, 4, 5, 6), nrow=2, ncol=3, byrow=TRUE)");
        engine.voidEval("C = A + B");
        assert Arrays.deepEquals((double[][]) engine.eval("C"), new double[][]{{3, 6, 6}, {5, 10, 13}});

        engine.voidEval("A = matrix( c(2, 4, 3, 1, 5, 7), nrow=2, ncol=3, byrow=TRUE)");
        engine.voidEval("B = matrix( c(1, 2, 3, 4, 5, 6), nrow=2, ncol=3, byrow=TRUE)");
        engine.voidEval("D = A - B");
        assert Arrays.deepEquals((double[][]) engine.eval("D"), new double[][]{{1, 2, 0}, {-3, 0, 1}});

        // Test determinant
        engine.voidEval("A = matrix( c(1,2,3,-4,-5,6,-2,-5,-1), nrow=3, ncol=3, byrow=TRUE)");
        assert Arrays.deepEquals((double[][]) engine.eval("A"), new double[][]{{1, 2, 3}, {-4, -5, 6}, {-2, -5, -1}});
        engine.voidEval("b = determinant(A)");
        assert ((Double) engine.eval("b")) == 33.;

        // Test solve
        engine.voidEval("A <- matrix(nrow = 2, ncol = 2, data = c(-2, 3, 2, 1), byrow=TRUE)");
        engine.voidEval("B <- matrix(nrow = 2, ncol = 1, data = c(11,9), byrow=TRUE)");
        engine.voidEval("X <- solve(A, B)");
        assert Arrays.deepEquals((double[][]) engine.eval("X"), new double[][]{{2}, {5}});
        assert Arrays.deepEquals((double[][]) engine.eval("A %*% X - B"), new double[][]{{0}, {0}});

        // Test dim
        engine.voidEval("A <- matrix(nrow = 2, ncol = 2, data = c(-2, 3, 2, 1), byrow=TRUE)");
        engine.voidEval("B <- matrix(nrow = 2, ncol = 1, data = c(11,9), byrow=TRUE)");
        assert Arrays.equals((double[]) engine.eval("dim(A)"), new double[]{2, 2});
        assert Arrays.equals((double[]) engine.eval(" dim(B)"), new double[]{2, 1});

    }

    @Test
    public void testInIndexNotSupported() {
        try {
            String repl = R2jsSession.replaceIndexes("abc[def[i]]");
            assert false : "Did not detect wrong index pattern: " + repl;
        } catch (UnsupportedOperationException e) {
            assert true;
            e.printStackTrace();
        }

        try {
            String repl = R2jsSession.replaceIndexes("abc[[i]]");
            assert true;
        } catch (UnsupportedOperationException e) {
            assert false : "Detect wrong index pattern";
        }
        try {
            String repl = R2jsSession.replaceIndexes("abc[i]");
            assert true;
        } catch (UnsupportedOperationException e) {
            assert false : "Detect wrong index pattern";
        }
        try {
            String repl = R2jsSession.replaceIndexes("abc[i][j]");
            assert true;
        } catch (UnsupportedOperationException e) {
            assert false : "Detect wrong index pattern";
        }

        try {
            String repl = R2jsSession.replaceIndexes("displayResults <- function(gradientdescent,X,Y) {\n"
                    + "    Y = Y[,1]\n"
                    + "    m = min(Y)\n"
                    + "    m.ix = which(Y==m)\n"
                    + "    x = as.matrix(X)[m.ix[1],]\n"
                    + "\n"
                    + "    resolution <- 600\n"
                    + "    d = dim(X)[2]\n"
                    + "}");
            assert false : "Did not detect wrong index pattern: " + repl;
        } catch (UnsupportedOperationException e) {
            assert true;
            e.printStackTrace();
        }
        try {
            String repl = R2jsSession.replaceIndexes("displayResults <- function(gradientdescent,X,Y) {\n"
                    + "    Y = Y[,1]\n"
                    + "    m = min(Y)\n"
                    + "    m.ix = which(Y==m)[1]\n"
                    + "    x = as.matrix(X)[m.ix,]\n"
                    + "\n"
                    + "    resolution <- 600\n"
                    + "    d = dim(X)[2]\n"
                    + "}");
            assert true;
        } catch (UnsupportedOperationException e) {
            assert false : "Detect wrong index pattern";
        }
    }

    @Test
    public void testRbind() throws RException {

        engine.voidEval("A <- matrix(nrow = 2, ncol = 2, data = c(1,2,5,6), byrow=TRUE)");
        engine.voidEval("B <- matrix(nrow = 2, ncol = 2, data = c(3,4,7,8), byrow=TRUE)");
        engine.voidEval("C = rbind(A,B)");
        assert engine.eval("C") instanceof double[][] : "Not double[][] : " + engine.eval("C").getClass();
        assert Arrays.deepEquals((double[][]) engine.eval("C"), new double[][]{{1, 2}, {5, 6}, {3, 4}, {7, 8}});

        engine.voidEval("A <- runif(10)");
        engine.voidEval("B <- runif(10)");
        engine.voidEval("C = rbind(A,B)");
        assert engine.eval("C") instanceof double[][] : "Not double[][] : " + engine.eval("C").getClass();
        double[][] C = (double[][]) engine.eval("C");
        assert C.length == 2 : "Bad nrow";
        assert C[0].length == 10 : "Bad ncol";

        double[][] A = new double[][]{{1, 2, 3}, {4, 5, 6}};
        engine.set("A", A, "x1", "x2", "x3");
        double[][] B = new double[][]{{11, 12, 13}, {14, 15, 16}};
        engine.set("B", B, "x1", "x2", "x3");
        engine.voidEval("C = rbind(A,B)");
        assert engine.eval("C") instanceof double[][] : "Not double[][] : " + engine.eval("C").getClass();
        assert Arrays.deepEquals((double[][]) engine.eval("C"), new double[][]{{1, 2, 3}, {4, 5, 6}, {11, 12, 13}, {14, 15, 16}}) : Arrays.deepToString((double[][]) engine.eval("C"));
        assert engine.eval("names(C)") instanceof String[] : "No names";
        assert Arrays.deepEquals((String[]) engine.eval("names(C)"), new String[]{"x1", "x2", "x3"}) : "Bad names";

        engine.voidEval("A <- 0.5");
        engine.voidEval("B <- 0.6");
        engine.voidEval("C = rbind(A,B)");
        assert engine.eval("C") instanceof double[][] : "Not double[][] : " + engine.eval("C").getClass();
        assert Arrays.deepEquals((double[][]) engine.eval("C"), new double[][]{{0.5}, {0.6}}) : Arrays.deepToString((double[][]) engine.eval("C"));
    }

    @Test
    public void testCbind() throws RException {

        engine.voidEval("A <- matrix(nrow = 2, ncol = 2, data = c(1,2,5,6), byrow=TRUE)");
        engine.voidEval("B <- matrix(nrow = 2, ncol = 2, data = c(3,4,7,8), byrow=TRUE)");
        engine.voidEval("C = cbind(A,B)");
        assert engine.eval("C") instanceof double[][] : "Not double[][] : " + engine.eval("C").getClass();
        assert Arrays.deepEquals((double[][]) engine.eval("C"), new double[][]{{1, 2, 3, 4}, {5, 6, 7, 8}}) : Arrays.deepToString((double[][]) engine.eval("C"));

        engine.voidEval("A <- runif(10)");
        engine.voidEval("B <- runif(10)");
        engine.voidEval("C = cbind(A,B)");
        assert engine.eval("C") instanceof double[][] : "Not double[][] : " + engine.eval("C").getClass();
        double[][] C = (double[][]) engine.eval("C");
        assert C.length == 10 : "Bad nrow";
        assert C[0].length == 2 : "Bad ncol";

        double[][] A = new double[][]{{1, 2, 3}, {4, 5, 6}};
        engine.set("A", A, "x1", "x2", "x3");
        double[][] B = new double[][]{{11, 12, 13}, {14, 15, 16}};
        engine.set("B", B, "x1", "x2", "x3");
        engine.voidEval("C = cbind(A,B)");
        assert engine.eval("C") instanceof double[][] : "Not double[][] : " + engine.eval("C").getClass();
        assert Arrays.deepEquals((double[][]) engine.eval("C"), new double[][]{{1, 2, 3, 11, 12, 13}, {4, 5, 6, 14, 15, 16}}) : Arrays.deepToString((double[][]) engine.eval("C"));
        assert engine.eval("names(C)") instanceof String[] : "No names: " + engine.eval("names(C)");
        assert Arrays.deepEquals((String[]) engine.eval("names(C)"), new String[]{"x1", "x2", "x3", "x1", "x2", "x3"}) : "Bad names";

        engine.voidEval("A <- 0.5");
        engine.voidEval("B <- 0.6");
        engine.voidEval("C = cbind(A,B)");
        assert engine.eval("C") instanceof double[][] : "Not double[][] : " + engine.eval("C").getClass();
        assert Arrays.deepEquals((double[][]) engine.eval("C"), new double[][]{{0.5, 0.6}}) : Arrays.deepToString((double[][]) engine.eval("C"));
    }

    @Test
    public void testSaveAndLoad() throws RException {
        engine.debug_js = true;

        double rand = (double) Math.random();

        engine.set("s", "abcdef");
        File f = new File("R2Js" + rand + ".save");
        System.out.println("Absolute path: " + f.getAbsolutePath());
        System.out.println("getwd() " + engine.getwd());
        engine.save(f, "s");
        engine.rm("s");
        engine.load(f);
        assert engine.asString(engine.eval("s")).equals("abcdef") : "bad restore of s";

        engine.set("a", "123");
        engine.set("b", "456");

        rand = (double) Math.random();
        File f2 = new File("R2Js" + rand + ".save");
        engine.save(f, "s", "a");
        engine.rm("s");
        engine.rm("a");
        engine.rm("b");
        engine.load(f);
        assert engine.asString(engine.eval("a")).equals("123") : "bad restore of s";
        assert engine.asString(engine.eval("s")).equals("abcdef") : "bad restore of s";

        try {
            Object b = engine.eval("b");
            assert false : b + "";
        } catch (Exception e) {
            e.printStackTrace();
            assertTrue(true);
        }

        engine.set("s", "abcdef");
        engine.set("a", "123");
        engine.set("b", "456");
        engine.savels(f, "");
        engine.rm("s");
        engine.rm("a");
        engine.rm("b");
        engine.load(f);
        assert Arrays.equals(engine.ls(), new String[]{"a", "b", "s"}) : Arrays.toString(engine.ls());

        assert engine.rmAll();
        assert Arrays.equals(engine.ls(), new String[]{}) : Arrays.toString(engine.ls());
        engine.eval("fun = function(x) {return(x)}");
        engine.set("a", "123");
        engine.set("b", "456");
        System.err.println(Arrays.toString(engine.ls()));
        assert Arrays.equals(engine.ls(), new String[]{"a", "b", "fun"}) : Arrays.toString(engine.ls());
        engine.savels(f, "");
        engine.rm("fun");
        engine.rm("a");
        engine.rm("b");
        assert Arrays.equals(engine.ls(), new String[]{}) : Arrays.toString(engine.ls());
        engine.load(f);
        assert Arrays.equals(engine.ls(), new String[]{"a", "b", "fun"}) : Arrays.toString(engine.ls());
        assert (double) engine.eval("fun(0.123)") == 0.123 : engine.eval("fun(0.123)");
    }

    @Test
    public void testSaveGlobalEnv() throws RException {
        engine.debug_js = true;

        engine.voidEval("l = list(a=1,b=2)");

        assert engine.ls(true).length == 1 : "Not expected env: " + Arrays.toString(engine.ls(true));

        engine.voidEval("for (n in names(l)) {\nprint(n);\n.GlobalEnv[[n]] = l[[n]]\n}");

        //assert js.ls(true).length == 3 : "Not expected env: " + Arrays.toString(js.ls(true));
        try {
            engine.savels(File.createTempFile("___", "Rdata"), "");
        } catch (Exception ex) {
            assert false : ex;
        }
    }

    @Test
    public void testRegexGlobalEnv() throws RException {
        engine.debug_js = true;

        engine.voidEval("f = function(x,y){\n print(x)\nif(is.null(y)) stop('null y')\nprint(y)\n}");
        engine.voidEval("l = list(a=1,b=2)");

        assert engine.ls(true).length == 2 : "Not expected env: " + Arrays.toString(engine.ls(true));

        try {
            System.err.println(engine.eval("f(.GlobalEnv,l)"));
        } catch (Exception ex) {
            assert false : ex;
        }

    }

    @Test
    public void testRmFunction() throws RException {

        engine.set("a1", "test1");
        engine.set("a2", "test2");
        engine.set("a3", "test3");
        engine.voidEval("a4 = 'test4'");
        engine.voidEval("a5 <- 'test5'");

        assert ((String) engine.eval("a1")).equals("test1") : engine.eval("a1");
        engine.rm("a1");
        try {
            engine.eval("a1");
        } catch (RException ex) {
            assertTrue(true);
        }

        engine.rm(new String[]{"a2", "a3"});
        try {
            engine.eval("a2");
        } catch (RException ex) {
            assertTrue(true);
        }

        try {
            engine.eval("a3");
        } catch (RException ex) {
            assertTrue(true);
        }

        assert ((String) engine.eval("a4")).equals("test4");
        engine.rm("a4");
        try {
            engine.eval("a4");
        } catch (RException ex) {
            assertTrue(true);
        }

        assert ((String) engine.eval("a5")).equals("test5");
        engine.rm("a5");
        try {
            engine.eval("a5");
        } catch (RException ex) {
            assertTrue(true);
        }

    }

    @Test
    public void testDataFrames() throws RException {

        engine.voidEval("a = c('aa','bb','cc')");
        engine.voidEval("b = c(11,22,33)");
        engine.voidEval("c = data.frame(first=a,second=b)");
        assertTrue(Arrays.equals((String[]) engine.eval("c$first"), new String[]{"aa", "bb", "cc"}));
        assertTrue(Arrays.toString((String[]) engine.eval("c[['first']]")), Arrays.equals((String[]) engine.eval("c[['first']]"), new String[]{"aa", "bb", "cc"}));
        assertTrue(Arrays.equals((double[]) engine.eval("c$second"), new double[]{11, 22, 33}));
        assertTrue(Arrays.equals((double[]) engine.eval("c[['second']]"), new double[]{11, 22, 33}));
        assert ((String) engine.eval("c$first[1]")).equals("aa");
        assert ((Double) engine.eval("c$second[2]")).equals(22.0) : engine.eval("c$second[2]");

        engine.voidEval("a2 = c('aa','bb','cc')");
        engine.voidEval("b2 = c(11,22,33)");
        engine.voidEval("c2 = data.frame(\"first\"=a2,\"second\"=b2)");
        assertTrue(Arrays.equals((String[]) engine.eval("c2$first"), new String[]{"aa", "bb", "cc"}));
        assertTrue(Arrays.equals((double[]) engine.eval("c2$second"), new double[]{11, 22, 33}));
        assert ((String) engine.eval("c2$first[1]")).equals("aa");
        assert ((Double) engine.eval("c2$second[2]")).equals(22.0);

        engine.voidEval("a3 = c('aa','bb','cc')");
        engine.voidEval("b3 = c(11,22,33)");
        engine.voidEval("c3 = c(FALSE,TRUE,FALSE)");
        engine.voidEval("d3 = data.frame(first=a3, second=b3, third=c3)");
        engine.voidEval("e3 = data.frame(first2=d3$first, third3=d3$third)");
        assertTrue(Arrays.equals((String[]) engine.eval("e3$first2"), new String[]{"aa", "bb", "cc"}));
        assertTrue(Arrays.equals((String[]) engine.eval("e3$third3"), new String[]{"false", "true", "false"}));
        assert ((String) engine.eval("e3$first2[1]")).equals("aa");
        assert (boolean) engine.eval("e3$third3[2]");

        // Change value in a dataframe
        engine.voidEval("e3$first2[1]='hello'");
        assertTrue(Arrays.deepToString((String[]) engine.eval("e3$first2")), Arrays.equals((String[]) engine.eval("e3$first2"), new String[]{"hello", "bb", "cc"}));
        engine.voidEval("e3$third3[2]=FALSE");
        assert !(boolean) engine.eval("e3$third3[2]");

        // Test data.frame without column name
        engine.voidEval("first = c('aa','bb','cc')");
        engine.voidEval("second = c(11,22,33)");
        engine.voidEval("c = data.frame(first,second)");
        assertTrue(Arrays.equals((String[]) engine.eval("c$first"), new String[]{"aa", "bb", "cc"}));
        assertTrue(Arrays.equals((String[]) engine.eval("c[['first']]"), new String[]{"aa", "bb", "cc"}));
        assertTrue(Arrays.equals((double[]) engine.eval("c$second"), new double[]{11, 22, 33}));
        assertTrue(Arrays.equals((double[]) engine.eval("c[['second']]"), new double[]{11, 22, 33}));
        assert ((String) engine.eval("c$first[1]")).equals("aa");
        assert ((Double) engine.eval("c$second[2]")).equals(22.0);

    }

    @Test
    public void testSysEnv() throws RException {
        engine.debug_js = true;

        assert engine.eval("Sys.getenv('toto')").equals("") : "Sys.getenv('toto') -> " + engine.eval("Sys.getenv('toto')");
        engine.voidEval("Sys.setenv(toto='titi')");
        assert engine.eval("Sys.getenv('toto')").equals("titi") : "Sys.getenv('toto') -> " + engine.eval("Sys.getenv('toto')");
        String HOME = System.getenv("HOME");
        if (HOME == null) {
            HOME = "";
        }
        assert engine.eval("Sys.getenv('HOME')").toString().equals(HOME) : "Sys.getenv('HOME') -> " + engine.eval("Sys.getenv('HOME')");
    }

    @Test
    public void testList() throws RException {
        engine.debug_js = true;

        engine.voidEval("a = c('aa','bb','cc')");
        engine.voidEval("b = c(11,22,33)");
        engine.voidEval("C = list(first=a,second=b)");
        assertTrue(Arrays.equals((String[]) engine.eval("C$first"), new String[]{"aa", "bb", "cc"}));
        assertTrue(Arrays.toString((String[]) engine.eval("C[['first']]")), Arrays.equals((String[]) engine.eval("C[['first']]"), new String[]{"aa", "bb", "cc"}));
        assertTrue(Arrays.equals((double[]) engine.eval("C$second"), new double[]{11, 22, 33}));
        assertTrue(Arrays.equals((double[]) engine.eval("C[['second']]"), new double[]{11, 22, 33}));
        assert ((String) engine.eval("C$first[1]")).equals("aa") : ((String) engine.eval("C$first[1]"));
        assert ((Double) engine.eval("C$second[2]")).equals(22.0) : engine.eval("C$second[2]");

        engine.voidEval("a2 = c('aa','bb','cc')");
        engine.voidEval("b2 = c(11,22,33)");
        engine.voidEval("c2 = list(\"first\"=a2,\"second\"=b2)");
        assertTrue(Arrays.equals((String[]) engine.eval("c2$first"), new String[]{"aa", "bb", "cc"}));
        assertTrue(Arrays.equals((double[]) engine.eval("c2$second"), new double[]{11, 22, 33}));
        assert ((String) engine.eval("c2$first[1]")).equals("aa");
        assert ((Double) engine.eval("c2$second[2]")).equals(22.0);

        engine.voidEval("a3 = c('aa','bb','cc')");
        engine.voidEval("b3 = c(11,22,33)");
        engine.voidEval("c3 = c(FALSE,TRUE,FALSE)");
        engine.voidEval("d3 = list(first=a3, second=b3, third=c3)");
        assertTrue(Arrays.equals((String[]) engine.eval("d3$first"), new String[]{"aa", "bb", "cc"}));
        engine.voidEval("e3 = list(first2=d3$first, first3=d3$first, third3=d3$third)"); 
        assertTrue(Arrays.equals((String[]) engine.eval("e3$first2"), new String[]{"aa", "bb", "cc"}));
        assertTrue(Arrays.equals((String[]) engine.eval("e3$third3"), new String[]{"false", "true", "false"}));
        assert ((String) engine.eval("e3$first2[1]")).equals("aa") : ((String) engine.eval("e3$first2[1]"));
        assert (boolean) engine.eval("e3$third3[2]") : engine.eval("e3$third3[2]");

        // Test list without column name
        engine.voidEval("first = c('aa','bb','cc')");
        engine.voidEval("second = c(11,22,33)");
        engine.voidEval("c = list(first,second)");
        assertTrue(Arrays.equals((String[]) engine.eval("c$first"), new String[]{"aa", "bb", "cc"}));
        assertTrue(Arrays.equals((String[]) engine.eval("c[['first']]"), new String[]{"aa", "bb", "cc"}));
        assertTrue(Arrays.equals((double[]) engine.eval("c$second"), new double[]{11, 22, 33}));
        assertTrue(Arrays.equals((double[]) engine.eval("c[['second']]"), new double[]{11, 22, 33}));
        assert ((String) engine.eval("c$first[1]")).equals("aa");
        assert ((Double) engine.eval("c$second[2]")).equals(22.0);

        assert Arrays.equals((String[]) engine.eval("c[['first']]"), new String[]{"aa", "bb", "cc"}) : Arrays.toString((String[]) engine.eval("c[['first']]"));
        engine.voidEval("f = 'first'");
        assert Arrays.equals((String[]) engine.eval("c[[f]]"), new String[]{"aa", "bb", "cc"}) : Arrays.toString((String[]) engine.eval("c[['first']]"));
    }

    @Test
    public void testLength() throws RException {
        engine.voidEval("a <- c(1,2,3,4)");
        try {
            assert ((Double) engine.eval("length(a)")) == 4 : "Cannot get length:" + engine.eval("length(a)");
        } catch (Exception e) {
            assert false : "Cannot get length:" + engine.eval("length(a)") + " : " + e.getMessage();
        }
    }

    @Test
    public void testPow() throws RException {

        assertEquals((Double) engine.eval("10**(1/3)"), 2.154434690031884, epsilon);
        assertEquals((Double) engine.eval("10^(1/3)"), 2.154434690031884, epsilon);
        engine.voidEval("a <- 123*3/(4*pi*(1/2)*(4567/1000))^(1/3)");
        assertEquals((Double) engine.eval("a"), 120.528404101305372397928, epsilon);
        engine.voidEval("b <- 123*3/(4*pi*(1/2)*(4567/1000))**(1/3)");
        assertEquals((Double) engine.eval("b"), 120.528404101305372397928, epsilon);
        assertEquals((Double) engine.eval("2^3"), 8, epsilon);
        assertEquals((Double) engine.eval("2**3"), 8, epsilon);
        assertEquals((Double) engine.eval("pi*1.161024^2*12*6.789"), 345.0001802967, epsilon);

    }

    @Test
    public void testExistsFunction() throws RException {
        engine.set("s", "abcdef");
        assertTrue(Arrays.deepToString(engine.ls()), (Boolean) engine.eval("exists('s')"));
        assertTrue(!(Boolean) engine.eval("exists('a')"));
    }

    @Test
    public void testIsFunction() throws RException {
        engine.voidEval("x1 <- function() { 0 }");
        engine.voidEval("x2 <- 2");
        assertTrue((boolean) engine.eval("is.function(x1)"));
        assertTrue(!(boolean) engine.eval("is.function(x2)"));
    }

    @Test
    public void testWhichMin() throws RException {
        engine.set("x", new double[]{1, 2, 4, 8, 0, 5, 6, -1, 10, 9});
        assert (double) engine.eval("min(x)") == -1 : "Failed to find min: " + engine.eval("min(x)");
        assert Arrays.equals((double[]) engine.eval("which.min(x)"), new double[]{8}) : "Failed to find which.min: " + engine.eval("which.min(x)");
    }

    @Test
    public void testNamedArgFunction() throws RException {
        engine.debug_js = true;
        engine.voidEval("f = function(x) {return(x+1)}");
        engine.set("x", 1);
        assert (double) engine.eval("x") == 1 : "Bad setting of x:" + engine.eval("x");
        assert (double) engine.eval("f(2)") == 3 : "Bad eval of f(2):" + engine.eval("f(2)");
        assert (double) engine.eval("f(x)") == 2 : "Bad eval of f(x):" + engine.eval("f(x)");
        assert (double) engine.eval("f(x=2)") == 3 : "Bad eval of f(x=2):" + engine.eval("f(x=2)");
        assert (double) engine.eval("x") == 1 : "Bad setting of x:" + engine.eval("x");
        assert (double) engine.eval("f(2)") == 3 : "Bad eval of f(2):" + engine.eval("f(2)");
        assert (double) engine.eval("f(x)") == 2 : "Bad eval of f(x):" + engine.eval("f(x)");
        assert (double) engine.eval("f(x=2)") == 3 : "Bad eval of f(x=2):" + engine.eval("f(x=2)");

        engine.voidEval("g = function(x,y) {return(x-y)}");
        engine.set("x", 1);
        assert (double) engine.eval("x") == 1 : "Bad setting of x:" + engine.eval("x");
        assert (double) engine.eval("g(2,1)") == 1 : "Bad eval of g(2,1):" + engine.eval("g(2,1)");
        assert (double) engine.eval("g(x,1)") == 0 : "Bad eval of g(x,1):" + engine.eval("g(x,1)");
        assert (double) engine.eval("g(x=2,1)") == 1 : "Bad eval of g(x=2,1):" + engine.eval("g(x=2,1)");
        assert (double) engine.eval("x") == 1 : "Bad setting of x:" + engine.eval("x");
        assert (double) engine.eval("g(2,1)") == 1 : "Bad eval of g(2,1):" + engine.eval("g(2,1)");
        assert (double) engine.eval("g(x,1)") == 0 : "Bad eval of g(x,1):" + engine.eval("g(x,1)");
        assert (double) engine.eval("g(x=2,1)") == 1 : "Bad eval of g(x=2,1):" + engine.eval("g(x=2,1)");
// FIXME : unorder arguments in function are not properly re-sorted/named
//        assert (double) engine.eval("g(y=1,x=2)") == 1 : "Bad eval of g(y=1,x=2):" + engine.eval("g(y=1,x=2)");
//        assert (double) engine.eval("g(1,x=2)") == 1 : "Bad eval of g(1,x=2):" + engine.eval("g(1,x=2)");

        assert (double) engine.eval("a=g(x=2,1); a") == 1 : "Bad eval of a=g(x=2,1); a:" + engine.eval("a=g(x=2,1); a");
        assert (double) engine.eval("gh=g(x=2,1); gh") == 1 : "Bad eval of gg=g(x=2,1); gg:" + engine.eval("gg=g(x=2,1); gg");
    }

    @Test
    public void testStopIfNot() throws RException {
        engine.set("a", 1);
        engine.set("b", 2);
        engine.voidEval("stopifnot(a!=b)");
        try {
            engine.voidEval("stopifnot(a==b)");
            assertTrue(false);
        } catch (RException ex) {
            assertTrue(true);
        }

        engine.voidEval("stopifnot(a<b)");
        try {
            engine.voidEval("stopifnot(a>b)");
            assertTrue(false);
        } catch (RException ex) {
            assertTrue(true);
        }

        engine.voidEval("stopifnot(a<=b)");
        try {
            engine.voidEval("stopifnot(a>=b)");
            assertTrue(false);
        } catch (RException ex) {
            assertTrue(true);
        }

        engine.voidEval("stopifnot((a)<=(b))");
        try {
            engine.voidEval("stopifnot((a)>=(b))");
            assertTrue(false);
        } catch (RException ex) {
            assertTrue(true);
        }

        engine.voidEval("stopifnot((a)!=(b))");
        try {
            engine.voidEval("stopifnot((a)==(b))");
            assertTrue(false);
        } catch (RException ex) {
            assertTrue(true);
        }

    }

    @Test
    public void testImbricatedFunctions() throws RException {
        // Test when the function is used before its definition
        engine.voidEval("a <- function() { return b()}");
        engine.voidEval("b <- function() { return 12.0}");
        assertEquals((Double) engine.eval("b()"), 12.0, epsilon);
    }

    @Test
    public void testSetWDFunctions() throws RException {

        engine.debug_js = true;
        // Test when the function is used before its definition
        String initialWD = (String) engine.eval("getwd()");
        engine.voidEval("setwd('" + initialWD + "/test')");
        String otherWD = (String) engine.eval("getwd()");
        assert !initialWD.equals(otherWD) : "initial wd=" + initialWD + " otherwd=" + otherWD;
        engine.voidEval("setwd('" + initialWD + "')");
        String initialWD2 = (String) engine.eval("getwd()");
        assert initialWD.equals(initialWD2) : "initial wd=" + initialWD + " otherwd=" + initialWD2;
    }

    @Test
    public void testIfFunction() throws RException, ScriptException {
        engine.debug_js = true;
        engine.voidEval("f1 <- function() { return 4}");
        assertEquals((Double) engine.eval("if(f1() == 2) f1() else f1()+1"), 5, epsilon);
        assertEquals((Double) engine.eval("if(f1() >2) { 3 } else { 4 }"), 3, epsilon);
        assertEquals((Double) engine.eval("if(f1()>2) 3 else 4"), 3, epsilon);
        assertEquals((Double) engine.eval("if(f1()>2) f1() else f1()+1"), 4, epsilon);
        assertEquals((Double) engine.eval("if(f1()==2) f1() else f1()+1"), 5, epsilon);
        assertEquals((Double) engine.eval("if(f1()>2) f1()"), 4, epsilon);
    }

    @Test
    public void testReturnIfFunction() throws RException, ScriptException {
        engine.debug_js = true;
        engine.voidEval("f1 <- function() { return if(1==2) {12.0} else {13.0}}");
        assertEquals((Double) engine.eval("f1()"), 13.0, epsilon);
        engine.voidEval("f2 <- function() { return if(1==1) {12.0}}");
        assertEquals((Double) engine.eval("f2()"), 12.0, epsilon);
        engine.voidEval("compare_function <- function(x,y) { return if(x==y) {'equals'} else {if(x>y) {'superior'} else {'inferior'}}}");
        assertTrue(((String) engine.eval("compare_function(12,12)")).equals("equals"));
        assertTrue(((String) engine.eval("compare_function(12,13)")).equals("inferior"));
        assertTrue(((String) engine.eval("compare_function(13,12)")).equals("superior"));

        engine.voidEval("compare_function2 <- function(x,y) { return if(x>=y) {if(x==y) {'equals'} else {'superior'}} else {'inferior'}}");
        assertTrue(((String) engine.eval("compare_function2(12,12)")).equals("equals"));
        assertTrue(((String) engine.eval("compare_function2(12,13)")).equals("inferior"));
        assertTrue(((String) engine.eval("compare_function2(13,12)")).equals("superior"));
    }

    @Test
    public void testArgsFunction() throws RException, ScriptException {
        engine.debug_js = true;
        engine.voidEval("var1 <- 2");
        engine.voidEval("f1 <- function(var1) { var1 + 1}");
        assertEquals((Double) engine.eval("f1(3)"), 4.0, epsilon);

        engine.voidEval("var2 <- 2");
        engine.voidEval("f2 <- function(var2) { var2 <- var2+1; return (var2)}");
        assertEquals((Double) engine.eval("var2"), 2.0, epsilon);
        assertEquals((Double) engine.eval("f2(var2)"), 3.0, epsilon);
        assertEquals((Double) engine.eval("f2(4)"), 5.0, epsilon);
        assertEquals((Double) engine.eval("var2"), 2.0, epsilon);

        engine.voidEval("var3 <- 2");
        engine.voidEval("var4 <- 3");
        engine.voidEval("var5 <- 4");
        engine.voidEval("f3 <- function(var3, var4) { var5 * (var3 + var4)}");
        assertEquals((Double) engine.eval("f3(1, 2)"), 12.0, epsilon);
        assertEquals((Double) engine.eval("f3(var5, var3)"), 24.0, epsilon);
        engine.voidEval("var5 <- 5");
        assertEquals((Double) engine.eval("f3(1, 2)"), 15.0, epsilon);
    }

    @Test
    public void testFunctionNames() throws RException, ScriptException {
        engine.debug_js = true;

        List<String> names = Arrays.asList("array", "paste0", "paste", "vector", "matrix", "c", "ls", "save", "load", "write__csv",
                "data", "data__frame", "list", "length", "file__exists", "exists", "stopifnot", "returnif", "default");

        for (String name : names) {
            engine.voidEval("__this__." + name + " <- function() {12}");
            assertEquals((Double) engine.eval("__this__." + name + "()"), 12, epsilon);
        }
    }

    @Test
    public void testNumeralSystem() throws RException, ScriptException {
        // Test to prevent octal conversion
        assertEquals((Double) engine.eval("051+1"), 52.0, epsilon);
        assertEquals((Double) engine.eval("051+1"), 52.0, epsilon);
        assertEquals((Double) engine.eval("0051+1"), 52.0, epsilon);
        assertEquals((Double) engine.eval("051-01"), 50.0, epsilon);
        assertEquals((Double) engine.eval("051.1+1"), 52.1, epsilon);
        assertEquals((Double) engine.eval("051.2*010"), 512.0, epsilon);
        assertEquals((Double) engine.eval("051.2/010"), 5.12, epsilon);
        assertEquals((Double) engine.eval("051.02/010"), 5.102, epsilon);
        assertEquals((Double) engine.eval("051.02/0.10"), 510.2, epsilon);
    }

    @Test
    public void testWrongExpression() throws RException, ScriptException {
        engine.debug_js = true;
        engine.eval("x <- 12");
        assertEquals((Double) engine.eval("2"), 2., epsilon);
        try {
            engine.eval("1+ x)*2");
            assertTrue("Evaluation should return exception", false);
        } catch (RException e) {
        }
        assertEquals((Double) engine.eval("2"), 2., epsilon);
        assertEquals((Double) engine.eval("2+1"), 3., epsilon);
        try {
            engine.eval("$12");
            assertTrue("Evaluation should return exception", false);
        } catch (RException e) {
        }

    }

    @Test
    public void testDecimalPoints() throws RException {
        assert (Double) engine.eval("1e-27") == 1e-27;
        assert (Double) engine.eval("1e-31+1e-32") == 1.1e-31;
        assertEquals((Double) engine.eval("1e-31*1e-32"),  1e-63, 1e-75); //"normal" floating error of 1e-15 on multiplication
    }

    @Test
    public void testEvaluationTime() throws RException, ScriptException, IOException {
        String MATH_JS_FILE = "/org/math/R/math.js";
        InputStream mathInputStream = this.getClass().getResourceAsStream(MATH_JS_FILE);
//
//
//        StringBuilder textBuilder = new StringBuilder();
//        try (Reader reader = new BufferedReader(new InputStreamReader
//                (mathInputStream, StandardCharsets.UTF_8))) {
//            int c = 0;
//            while ((c = reader.read()) != -1) {
//                textBuilder.append((char) c);
//            }
//        }
//
//        Context polyglotContext = Context.create();

//
//        //polyglotContext.eval("js", textBuilder);


        // POLYGLOT
//        long time1 = System.currentTimeMillis();
//        Source scriptSource = Source.newBuilder("js",new File("/home/chabs/Documents/workspaces/rsession/src/main/resources/org/math/R/math.js")).build();
//        Engine engine = Engine.create();
//        Context ctx = Context.newBuilder()
//                .engine(engine)
//                .build();
//        long time2 = System.currentTimeMillis();
//
//        Value result = ctx.eval(scriptSource);
//        //Value math = ctx.getBindings("js").getMember("math");
//        System.out.println("Res=" + ctx.eval("js", "math.add(1,2)"));
//
//        long time3 = System.currentTimeMillis();
//
//        //Context newContext = Context.newBuilder("js").(true).option("js.foreign-object-prototype", Boolean.TRUE.toString()).build();
//        Context newContext = Context.newBuilder()
//                .engine(engine)
//                .build();
//        newContext.eval(scriptSource);
//        //newContext.getBindings("js").putMember("math", math);
//
//        System.out.println("Res=" + newContext.eval("js", "math.add(1,11)"));
//
//        long time4 = System.currentTimeMillis();
//
//        System.out.println("Evaluation time polyglot:" + (time2 - time1) + " ms");
//        System.out.println("Evaluation time polyglot:" + (time3 - time2) + " ms");
//        System.out.println("Evaluation time polyglot:" + (time4 - time3) + " ms");



        long time1 = System.currentTimeMillis();
        ScriptEngineManager manager = new ScriptEngineManager(null);
        long time11 = System.currentTimeMillis();
        ScriptEngine js = manager.getEngineByName("JavaScript");
        if (js==null) js = manager.getEngineByName("js");
        if (js==null) js = manager.getEngineByExtension("js");
        if (js==null) js = manager.getEngineByName("nashorn");
        if (js==null) js = manager.getEngineByName("Nashorn");
        //if (js==null) js = new jdk.nashorn.api.scripting.NashornScriptEngineFactory().getScriptEngine();
        if (js==null) throw new IllegalArgumentException("Could not load JavaScript ScriptEngine: "+manager.getEngineFactories());

        long time2 = System.currentTimeMillis();
        js.eval(new InputStreamReader(mathInputStream, Charset.forName("UTF-8")));
        System.out.println("Res="+ js.eval("math.add(1,2)"));
        Object math = js.get("math");
        long time3 = System.currentTimeMillis();
        ScriptEngine js2 = manager.getEngineByName("JavaScript");
        //System.out.println("Res="+ js2.eval("math.add(1,11)"));
        js2.put("math", math);
        System.out.println("Res="+ js2.eval("math.add(1,11)"));
        long time4 = System.currentTimeMillis();

        System.out.println("Evaluation time polyglot---:" + (time11 - time1) + " ms");
        System.out.println("Evaluation time polyglot:" + (time2 - time1) + " ms");
        System.out.println("Evaluation time polyglot:" + (time3 - time2) + " ms");
        System.out.println("Evaluation time polyglot:" + (time4 - time3) + " ms");

    }

}
