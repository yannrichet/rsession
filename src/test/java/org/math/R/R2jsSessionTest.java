package org.math.R;

import java.io.File;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import java.util.Arrays;

import org.junit.Test;


/**
 * Test the converter r->js of the class {@link R2JsSession}
 * 
 * @author Nicolas Chabalier
 *
 */
public class R2jsSessionTest {

    // maximal epsilon wanted between actual and expected values
    final double epsilon = 1e-12;
    R2JsSession engine = R2JsSession.newInstance(null, null);

    @Test
    public void testBasicSyntaxes() {
        try {
            engine.voidEval("a = 1");
            assert (Integer) engine.eval("a")  == 1;
            engine.voidEval("a <- 1");
            assert (Integer) engine.eval("a")  == 1;
            assert (Double) engine.eval("a+1") == 2;
            assertEquals((Double)engine.eval("a+pi"),(1+Math.PI),epsilon);
            engine.eval("b <- (1)");
            assert (Integer) engine.eval("b")  == 1;
            
            assert Double.parseDouble( engine.eval("2 ^ 3").toString()) == 8;
            assert Double.parseDouble( engine.eval("2^3").toString())== 8;
            assert Double.parseDouble( engine.eval("2**3").toString())== 8;
            assert Double.parseDouble( engine.eval("2 ** 3").toString())== 8;
            assert Boolean.parseBoolean( engine.eval("1>2").toString())== false;
            assert Boolean.parseBoolean( engine.eval("1>1").toString()) == false;
            assert Boolean.parseBoolean( engine.eval("1>=2").toString()) == false;
            assert Boolean.parseBoolean( engine.eval("1>=1").toString()) == true;
            assert Boolean.parseBoolean( engine.eval("1<=2").toString()) == true;
            assert Boolean.parseBoolean( engine.eval("1<=1").toString()) == true;
            assert Boolean.parseBoolean( engine.eval("1==2").toString()) == false;
            assert Boolean.parseBoolean( engine.eval("1==1").toString()) == true;
            assert Boolean.parseBoolean( engine.eval("1!=2").toString()) == true;
            assert Boolean.parseBoolean( engine.eval("1!=1").toString()) == false;
            
            // Operators
            engine.voidEval("a = -4 * 10 -5* 100");
            assert (Double)engine.eval("a") == -540;

            engine.voidEval("a = -4*10-5*100");
            assert (Double)engine.eval("a") == -540;

            engine.voidEval("a = (-4 * 10) +(-5* 100)");
            assert (Double)engine.eval("a") == -540;

            engine.voidEval("a = -4 * -10 -5*-100 + 18 * (-5*(3+9))");
            assert (Double)engine.eval("a") == -540;

            engine.voidEval("a = (3 - 5) * (-6 + 5) / 2 ");
            assert (Double)engine.eval("a") == 1;
            
            assertEquals((Double) engine.eval("(5*6+12/15)/48*56+(5)-48"),(5.*6.+12./15.)/48.*56.+(5.)-48.,epsilon);
            assertEquals((Double) engine.eval("4-6-5+-7+(-5)-(-48+4)-(+56)"),4-6-5+-7+(-5)-(-48+4)-(+56), epsilon);
            assertEquals((Double) engine.eval("45.*0.2/-65.5*45.+(45.-5.*7.)*(+8.-4.-(-1.)+(-1.))/1.2/41./-5.*12.*0.1+4.*(48.+1.2/4.)/4."),45.*0.2/-65.5*45.+(45.-5.*7.)*(+8.-4.-(-1.)+(-1.))/1.2/41./-5.*12.*0.1+4.*(48.+1.2/4.)/4., epsilon);
            
        
        } catch (Exception e) {
            e.printStackTrace();
            assertTrue(false);
        }
    }
    
    @Test
    public void testFunctions() {
        try {
            engine.voidEval("f = function(x) {return(1+x)}");
            assert (Double) engine.eval("f(1.23)") == 2.23;
            assert (Double) engine.eval("f(x=1.23)") == 2.23;

            engine.voidEval("f = function(x) 1+x");
            assert (Double)engine.eval("f(1.23)") == 2.23;

            engine.voidEval("f = function(x) {\nreturn(1+x)\n}");
            assert (Double)engine.eval("f(1.23)") == 2.23;

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
            assertEquals((Double) engine.eval("f(1.23,4.56)"),-3.33,epsilon);
            assertEquals((Double) engine.eval("f(x=1.23,y=4.56)"),-3.33,epsilon);
            assertEquals((Double) engine.eval("f(x=4.56,y=1.23)"),3.33,epsilon);

            engine.voidEval("f <- function(x,y){return(x-y)}");
            assertEquals((Double) engine.eval("f(1.23,4.56)"),-3.33,epsilon);
            assertEquals((Double) engine.eval("f(x=1.23,y=4.56)"),-3.33,epsilon);
            assertEquals((Double) engine.eval("f(x=4.56,y=1.23)"),3.33,epsilon);

            // WARNING: named arguments work only with ES6 and higher version (java8 use a 
            // previous version of javascipt ES5, so named arguments are not supported)
            //assert Double.parseDouble( engine.eval(R2MathjsSession.R2js("f(y=1.23,x=4.56)")) == 3.33;

            engine.voidEval("f = function(x) {sin(x)}");
            assertEquals(Double.parseDouble( engine.eval(R2JsSession.convertRtoJs("f(1.23)")).toString()),Math.sin(1.23),epsilon);

            engine.voidEval("f = function(x) {asin(x)}");
            assertEquals(Double.parseDouble( engine.eval(R2JsSession.convertRtoJs("f(0.23)")).toString()),Math.asin(0.23),epsilon);

            engine.voidEval("f <- function(temp_F) {\ntemp_K <- ((temp_F - 32) * (5 / 9)) + 273.15\nreturn(temp_K)\n}");
            assert Double.parseDouble( engine.eval(R2JsSession.convertRtoJs("f(32)")).toString()) == 273.15;

            engine.voidEval("a=2; h <- function(x){1-x;};");
            assert Double.parseDouble( engine.eval(R2JsSession.convertRtoJs("h(1)")).toString()) == 0;
            
            engine.voidEval("a = c(1,2,3,12);");
            engine.voidEval("f = function(x) {res=0;\n for(y in x) {res+=y};\n return res;}");
            assert (Double) engine.eval("f(a)") == 18;
            
            engine.voidEval("f <- function(x=2,y=1) {return x+y}");
            assert (Double) engine.eval("f()") == 3;
            assert (Double) engine.eval("f(1.23)") == 2.23;
            assertEquals((Double) engine.eval("f(1.23, 4.56)"),5.79, epsilon);
            
            engine.voidEval("a <- c(0:4);");
            engine.voidEval("f = function(x) {res=0;\n for(y in x) {res+=y};\n return res;}");
            assert (Double) engine.eval("f(a)") == 10;


            engine.voidEval("recur_factorial <- function(n) {\n if(n <= 1) {\n return(1)\n } else { \n return(n * recur_factorial(n-1))\n }\n }");
            assert (Double) engine.eval("recur_factorial(3)") == 6;
            assert (Double) engine.eval("recur_factorial(5)") == 120;

            // FIXME: multiple function in one line don't work
            //engine.eval("g<-function(x){1+x;};\nh<-function(x){1-x;};");
            //assert (Double) engine.eval(" g(1)") == 2;
            //assert (Double) engine.eval("h(1)") == 0;
            //			
            //engine.eval(R2MathjsSession.R2js("fahrenheit_to_kelvin <- function(temp_F) {\n   temp_K <- ((temp_F - 32) * (5 / 9)) + 273.15\n   return(temp_K)\n };\n kelvin_to_celsius <- function(temp_K) {\n temp_C <- temp_K - 273.15\n   return(temp_C)\n };\n fahrenheit_to_celsius <- function(temp_F) {\n   temp_K <- fahrenheit_to_kelvin(temp_F)\n   temp_C <- kelvin_to_celsius(temp_K)\n   return(temp_C)\n };\n"));
            //assert Double.parseDouble( engine.eval("fahrenheit_to_celsius(32.0);\n").toString()) == 0;
            //assert Double.parseDouble( engine.eval("kelvin_to_celsius(fahrenheit_to_kelvin(32.0))").toString()) == 32;
            //assert Double.parseDouble( engine.eval("kelvin_to_celsius(0)").toString()) == -273.15;

            engine.eval("f <- function(x,bool1=TRUE,bool2=TRUE) {\n   if (!bool1) {\n     a <- 1; b <- 2\n   } else {\n     a <- 3; b <- 4\n   }\n   if (bool2) {\n     c<-a*1000 + b*100\n   } else if (!bool2) {\n     c<--a*1000 - b*100\n   }\n   result <- c + x\n   return(result)\n }");
            assert (Double) engine.eval("f(1, TRUE, TRUE)") == 3401;
            assert (Double) engine.eval("f(3)") == 3403;
            assert (Double) engine.eval("f(3, FALSE, FALSE)") == -1197;

        
        } catch (Exception e) {
            e.printStackTrace();
            assertTrue(false);
        }
    }

    @Test
    public void testArrays() {
        try {
            engine.voidEval("a1 = c(1,2,3,12);\n b_2 = 2;");
            assert (Integer)engine.eval("a1[1]")  == 1;
            assert (Integer)engine.eval("a1[4]") ==12;
            assert (Integer)engine.eval("a1[b_2]") ==2;
            assert (Integer)engine.eval("a1[b_2-1]") ==1;
            
            engine.voidEval("a <- c(0:4);");
            double[] m = new double[]{0,1,2,3, 4};
            double[] res = (double[])engine.eval("a");
            assert Arrays.equals(m, res);

            // Addition between arrays
            engine.voidEval("a = c(4,5,6,7)");
            engine.voidEval("b = c(1,2,1,2)");
            engine.voidEval("c = a + b");
            assert Arrays.equals((double[]) engine.eval("c"), new double[]{5,7,7,9});

            // Addiction between arrays in js function
            engine.voidEval("fsum <- function(x, y) {return x+y}");
            engine.voidEval("d = fsum(a , b)");
            assert Arrays.equals((double[]) engine.eval("d"), new double[]{5, 7, 7, 9});
            
            // ------ Array operations -----------------
            // Dot multiplication
            engine.voidEval("a <- c(1., 2. ,3.)");
            engine.voidEval("b <- c(2., 1. ,2.)");
            engine.voidEval("c <- a*b");
            assert Arrays.equals((double[]) engine.eval("c"), new double[]{2,2,6});

            // Dot division
            System.err.println(engine.eval("d <- a/b")); // should be voidEval instead
            assert Arrays.equals((double[]) engine.eval("d"), new double[]{0.5,2,1.5});

            // Substraction
            double[] e = (double[])engine.eval("e <- a-b");
            assert Arrays.equals((double[]) engine.eval("e"), new double[]{-1,1,1});

            // Addition
            engine.voidEval("f <- a+b");
            assert Arrays.equals((double[]) engine.eval("f"), new double[]{3,3,5});
            
            // Multiplication and substraction
            engine.voidEval("f <- a*b-b");
            assert Arrays.equals((double[]) engine.eval("f"), new double[]{0,1,4});

            // TODO  multiplication and division '%*%'
            //System.err.println(engine.eval(R2MathjsSession.R2js("bt <- t(b)")));
            //System.err.println(engine.eval("h <- a * bt"));
            //System.err.println(engine.eval(R2MathjsSession.R2js("h <- a %*% bt")));
            //assertTrue(engine.eval("h").toString().trim().equals("[todo]".trim()));
            
        } catch (Exception e) {
            e.printStackTrace();
            assertTrue(false);
        }
    }
    
    @Test
    public void testMatrices() {
        try {
            // Matrix transpose
            engine.voidEval("A = matrix( c(2, 4, 3, 1, 5, 7), nrow=3, ncol=2, byrow=TRUE)");
            engine.voidEval("B = t(A)");
            assert Arrays.deepEquals((double[][]) engine.eval("B"), new double[][]{{2,3,5},{4,1,7}});

            // Matrix dot multiplication
            System.err.println(engine.eval("C = A*A")); // should be voidEval instead
            assert Arrays.deepEquals((double[][]) engine.eval("C"), new double[][]{{4,16},{9,1}, {25,49}});

            // Matrix s
            System.err.println(engine.eval("D = A^2")); // should be voidEval instead
            assert Arrays.deepEquals((double[][]) engine.eval("D"), new double[][]{{4,16},{9,1}, {25,49}});

            // Matrix multiplication
            engine.voidEval("A = matrix( c(2, 4, 3.23, -1, -5.34, 7), nrow=3, ncol=2, byrow=TRUE)");
            System.err.println(engine.eval("MA = A%*%t(A)")); // should be voidEval instead
            assert Arrays.deepEquals((double[][]) engine.eval("MA"), new double[][]{{20, 2.46, 17.32}, {2.46, 11.4329, -24.2482}, {17.32, -24.2482, 77.5156}});

            // Matrix division
            engine.voidEval("A = matrix( c(2, 4, 3.5, 13.5, 23.56, 7.1), nrow=3, ncol=2, byrow=TRUE)");
            engine.voidEval("B = matrix( c(2.12, 2, 2.23, 2, 3, 10.4), nrow=3, ncol=2, byrow=TRUE)");
            System.err.println(engine.eval("DA = A%/%B")); // should be voidEval instead
            assert Arrays.deepEquals((double[][]) engine.eval("DA"), new double[][]{{0,2}, {1,6}, {7,0}});


            // Matrix mod
            engine.voidEval("A = matrix( c(2, 4, 3.5, 13.5, 23.56, 7.1), nrow=3, ncol=2, byrow=TRUE)");
            engine.voidEval("B = matrix( c(2.12, 2, 2.23, 2, 3, 10.4), nrow=3, ncol=2, byrow=TRUE)");
            System.err.println(engine.eval("ModA = A%%B")); // should be voidEval instead
            System.out.println(Arrays.deepToString((double[][]) engine.eval("ModA")));

            assert Arrays.deepEquals((double[][]) engine.eval("ModA"), new double[][]{{2,0}, {1.27,1.5}, {2.5599999999999987,7.1}});


            // Matrix indexing
            double[][] matrix11 = (double[][]) engine.eval("A = matrix(c(1:9), nrow = 3, byrow = TRUE)");


            System.out.println(Arrays.deepToString(matrix11));
            assert (Integer) engine.eval("A[2,2]") == 5;
            assert Arrays.equals((double[])engine.eval("A[,2]"), new double[]{2,5,8});
            assert Arrays.equals((double[])engine.eval("A[2,]"),  new double[]{4,5,6});
            assert Arrays.deepEquals((double[][])engine.eval("A[,]"),  new double[][]{{1.0,2.0,3.0}, {4.0,5.0,6.0}, {7.0,8.0,9.0}});
            assert Arrays.deepEquals((double[][])engine.eval("A[c(1,2),]"), new double[][]{{1,2,3}, {4,5,6}});    
            
            // Matrix creation
            engine.voidEval("A = matrix( c(2, 4, 3, 1, 5, 7), nrow=3, ncol=2, byrow=TRUE)");
            assert Arrays.deepEquals((double[][]) engine.eval("A"), new double[][]{{2,4},{3,1},{5,7}});

            engine.voidEval("A = matrix( c(2, 4, 3, 1, 5, 7), nrow=3, ncol=2)");
            assert Arrays.deepEquals((double[][]) engine.eval("A"), new double[][]{{2,1},{4,5},{3,7}});

            engine.voidEval("A = matrix( c(2, 4, 3, 1, 5, 7), nrow=3, ncol=2, byrow=FALSE)");
            assert Arrays.deepEquals((double[][]) engine.eval("A"), new double[][]{{2,1},{4,5},{3,7}});
            
            engine.voidEval("A = matrix( c(2, 4, 3, 1, 5, 7), nrow=2, ncol=3, byrow=TRUE)");
            engine.voidEval("B = matrix( c(1, 2, 3, 4, 5, 6), nrow=2, ncol=3, byrow=TRUE)");
            engine.voidEval("C = A + B");
            assert Arrays.deepEquals((double[][]) engine.eval("C"), new double[][]{{3,6,6},{5,10,13}});
            
            
            engine.voidEval("A = matrix( c(2, 4, 3, 1, 5, 7), nrow=2, ncol=3, byrow=TRUE)");
            engine.voidEval("B = matrix( c(1, 2, 3, 4, 5, 6), nrow=2, ncol=3, byrow=TRUE)");
            engine.voidEval("D = A - B");
            assert Arrays.deepEquals((double[][]) engine.eval("D"), new double[][]{{1,2,0},{-3,0,1}});
            
            // Test determinant
            engine.voidEval("A = matrix( c(1,2,3,-4,-5,6,-2,-5,-1), nrow=3, ncol=3, byrow=TRUE)");
            assert Arrays.deepEquals((double[][]) engine.eval("A"), new double[][]{{1,2,3},{-4,-5,6}, {-2,-5,-1}});
            engine.voidEval("b = determinant(A)");
            assert ((Double) engine.eval("b")) == 33.;
            
            // Test solve
            engine.voidEval("A <- matrix(nrow = 2, ncol = 2, data = c(-2, 3, 2, 1), byrow=TRUE)");
            engine.voidEval("B <- matrix(nrow = 2, ncol = 1, data = c(11,9), byrow=TRUE)");
            engine.voidEval("X <- solve(A, B)");
            assert Arrays.deepEquals((double[][]) engine.eval("X"), new double[][]{{2},{5}});
            assert Arrays.deepEquals((double[][]) engine.eval("A %*% X - B"), new double[][]{{0},{0}});
            
            // Test dim
            engine.voidEval("A <- matrix(nrow = 2, ncol = 2, data = c(-2, 3, 2, 1), byrow=TRUE)");
            engine.voidEval("B <- matrix(nrow = 2, ncol = 1, data = c(11,9), byrow=TRUE)");
            assert Arrays.equals((double[]) engine.eval("dim(A)"), new double[]{2,2});
            assert Arrays.equals((double[]) engine.eval(" dim(B)"), new double[]{2,1});
            
            
        } catch (Exception e) {
            e.printStackTrace();
            assertTrue(false);
        }
    }
    
    @Test
    public void testRbind() {
        try {
            engine.voidEval("A <- matrix(nrow = 2, ncol = 2, data = c(1,2,5,6), byrow=TRUE)");
            engine.voidEval("B <- matrix(nrow = 2, ncol = 2, data = c(3,4,7,8), byrow=TRUE)");
            engine.voidEval("C = rbind(A,B)");
            assert Arrays.deepEquals((double[][]) engine.eval("C"), new double[][]{{1,2},{5,6},{3,4},{7,8}});
        
        } catch (Exception e) {
            e.printStackTrace();
            assertTrue(false);
        }
    }
    
    @Test
    public void testCbind() {
        try {
            engine.voidEval("A <- matrix(nrow = 2, ncol = 2, data = c(1,2,5,6), byrow=TRUE)");
            engine.voidEval("B <- matrix(nrow = 2, ncol = 2, data = c(3,4,7,8), byrow=TRUE)");
            engine.voidEval("C = cbind(A,B)");
            assert Arrays.deepEquals((double[][]) engine.eval("C"), new double[][]{{1,2,3,4},{5,6,7,8}});
        
        } catch (Exception e) {
            e.printStackTrace();
            assertTrue(false);
        }
    }
    
    @Test
    public void testSaveAndLoad() {
        try {
            double rand = (double) Math.random();
            
            engine.set("s", "abcdef");
            File f = new File("R2Js" + rand + ".save");
            engine.save(f, "s");
            engine.load(f);
            assert engine.asString(engine.eval("s")).equals("abcdef") : "bad restore of s";
        
        } catch (Exception e) {
            e.printStackTrace();
            assertTrue(false);
        }
    }
    
    @Test
    public void testRmFunction() {
        try {
            engine.set("a1", "test1");
            engine.set("a2", "test2");
            engine.set("a3", "test3");
            engine.voidEval("a4 = 'test4'");
            engine.voidEval("a5 <- 'test5'");
            
            assert ((String) engine.eval("a1")).equals("test1");
            engine.rm("a1");
            try {
                engine.eval("a1");
            } catch (Rsession.RException ex) {
                assertTrue(true);
            } 
            
            engine.rm(new String[]{"a2", "a3"});
            try {
                engine.eval("a2");
            } catch (Rsession.RException ex) {
                assertTrue(true);
            }
            
            try {
                engine.eval("a3");
            } catch (Rsession.RException ex) {
                assertTrue(true);
            }
            
            assert ((String) engine.eval("a4")).equals("test4");
            engine.rm("a4");
            try {
                engine.eval("a4");
            } catch (Rsession.RException ex) {
                assertTrue(true);
            }
            
            assert ((String) engine.eval("a5")).equals("test5");
            engine.rm("a5");
            try {
                engine.eval("a5");
            } catch (Rsession.RException ex) {
                assertTrue(true);
            }
            
        } catch (Rsession.RException ex) {
            ex.printStackTrace();
            assertTrue(false);
        }
    }
    
    @Test
    public void testDataFrames() {
        try {
            engine.voidEval("a = c('aa','bb','cc')");
            engine.voidEval("b = c(11,22,33)");
            engine.voidEval("c = data.frame(first=a,second=b)");
            assertTrue(Arrays.equals((String[]) engine.eval("c$first") ,new String[]{"aa","bb","cc"}));
            assertTrue(Arrays.equals((String[]) engine.eval("c[['first']]") ,new String[]{"aa","bb","cc"}));
            assertTrue(Arrays.equals((double[]) engine.eval("c$second") ,new double[]{11,22,33}));
            assertTrue(Arrays.equals((double[]) engine.eval("c[['second']]") ,new double[]{11,22,33}));
            assert ((String) engine.eval("c$first[1]")).equals("bb");
            assert ((Integer)engine.eval("c$second[2]")).equals(33);
            
            engine.voidEval("a2 = c('aa','bb','cc')");
            engine.voidEval("b2 = c(11,22,33)");
            engine.voidEval("c2 = data.frame(\"first\"=a2,\"second\"=b2)");
            assertTrue(Arrays.equals((String[]) engine.eval("c2$first") ,new String[]{"aa","bb","cc"}));
            assertTrue(Arrays.equals((double[]) engine.eval("c2$second") ,new double[]{11,22,33}));
            assert ((String) engine.eval("c2$first[1]")).equals("bb");
            assert ((Integer)engine.eval("c2$second[2]")).equals(33);
            
            engine.voidEval("a3 = c('aa','bb','cc')");
            engine.voidEval("b3 = c(11,22,33)");
            engine.voidEval("c3 = c(FALSE,TRUE,FALSE)");
            engine.voidEval("d3 = data.frame(first=a3, second=b3, third=c3)");
            engine.voidEval("e3 = data.frame(first2=d3$first, third3=d3$third)");
            assertTrue(Arrays.equals((String[]) engine.eval("e3$first2") ,new String[]{"aa","bb","cc"}));
            assertTrue(Arrays.equals((String[]) engine.eval("e3$third3") ,new String[]{"false","true","false"}));
            assert ((String) engine.eval("e3$first2[1]")).equals("bb");
            assert !(boolean)engine.eval("e3$third3[2]");
            
            // Change value in a dataframe
            engine.voidEval("e3$first2[0]='hello'");
            assertTrue(Arrays.equals((String[]) engine.eval("e3$first2") ,new String[]{"hello","bb","cc"}));
            engine.voidEval("e3$third3[2]=TRUE");
            assert (boolean)engine.eval("e3$third3[2]");
            
            // Test data.frame without column name
            engine.voidEval("first = c('aa','bb','cc')");
            engine.voidEval("second = c(11,22,33)");
            engine.voidEval("c = data.frame(first,second)");
            assertTrue(Arrays.equals((String[]) engine.eval("c$first") ,new String[]{"aa","bb","cc"}));
            assertTrue(Arrays.equals((String[]) engine.eval("c[['first']]") ,new String[]{"aa","bb","cc"}));
            assertTrue(Arrays.equals((double[]) engine.eval("c$second") ,new double[]{11,22,33}));
            assertTrue(Arrays.equals((double[]) engine.eval("c[['second']]") ,new double[]{11,22,33}));
            assert ((String) engine.eval("c$first[1]")).equals("bb");
            assert ((Integer)engine.eval("c$second[2]")).equals(33);
            

        } catch (Rsession.RException ex) {
            ex.printStackTrace();
            assertTrue(false);
        }
    }
    
    @Test
    public void testList() {
        try {
            engine.voidEval("a = c('aa','bb','cc')");
            engine.voidEval("b = c(11,22,33)");
            engine.voidEval("c = list(first=a,second=b)");
            assertTrue(Arrays.equals((String[]) engine.eval("c$first") ,new String[]{"aa","bb","cc"}));
            assertTrue(Arrays.equals((String[]) engine.eval("c[['first']]") ,new String[]{"aa","bb","cc"}));
            assertTrue(Arrays.equals((double[]) engine.eval("c$second") ,new double[]{11,22,33}));
            assertTrue(Arrays.equals((double[]) engine.eval("c[['second']]") ,new double[]{11,22,33}));
            assert ((String) engine.eval("c$first[1]")).equals("bb");
            assert ((Integer)engine.eval("c$second[2]")).equals(33);
            
            engine.voidEval("a2 = c('aa','bb','cc')");
            engine.voidEval("b2 = c(11,22,33)");
            engine.voidEval("c2 = list(\"first\"=a2,\"second\"=b2)");
            assertTrue(Arrays.equals((String[]) engine.eval("c2$first") ,new String[]{"aa","bb","cc"}));
            assertTrue(Arrays.equals((double[]) engine.eval("c2$second") ,new double[]{11,22,33}));
            assert ((String) engine.eval("c2$first[1]")).equals("bb");
            assert ((Integer)engine.eval("c2$second[2]")).equals(33);
            
            engine.voidEval("a3 = c('aa','bb','cc')");
            engine.voidEval("b3 = c(11,22,33)");
            engine.voidEval("c3 = c(FALSE,TRUE,FALSE)");
            engine.voidEval("d3 = list(first=a3, second=b3, third=c3)");
            engine.voidEval("e3 = list(first2=d3$first, third3=d3$third)");
            assertTrue(Arrays.equals((String[]) engine.eval("e3$first2") ,new String[]{"aa","bb","cc"}));
            assertTrue(Arrays.equals((String[]) engine.eval("e3$third3") ,new String[]{"false","true","false"}));
            assert ((String) engine.eval("e3$first2[1]")).equals("bb");
            assert !(boolean)engine.eval("e3$third3[2]");
            
            // Test list without column name
            engine.voidEval("first = c('aa','bb','cc')");
            engine.voidEval("second = c(11,22,33)");
            engine.voidEval("c = list(first,second)");
            assertTrue(Arrays.equals((String[]) engine.eval("c$first") ,new String[]{"aa","bb","cc"}));
            assertTrue(Arrays.equals((String[]) engine.eval("c[['first']]") ,new String[]{"aa","bb","cc"}));
            assertTrue(Arrays.equals((double[]) engine.eval("c$second") ,new double[]{11,22,33}));
            assertTrue(Arrays.equals((double[]) engine.eval("c[['second']]") ,new double[]{11,22,33}));
            assert ((String) engine.eval("c$first[1]")).equals("bb");
            assert ((Integer)engine.eval("c$second[2]")).equals(33);
            

        } catch (Rsession.RException ex) {
            ex.printStackTrace();
            assertTrue(false);
        }
    }
    
    @Test
    public void testLength() {
        try {
            engine.voidEval("a <- c(1,2,3,4)");
            assert ((Integer) engine.eval("length(a)")) == 4;
        } catch (Rsession.RException ex) {
            ex.printStackTrace();
            assertTrue(false);
        }
    }
    
}
