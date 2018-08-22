package org.math.R;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import java.io.FileNotFoundException;
import java.util.Arrays;
import java.util.Collection;

import javax.script.ScriptEngine;
import javax.script.ScriptEngineManager;
import javax.script.ScriptException;

import org.junit.Test;

import jdk.nashorn.api.scripting.ScriptObjectMirror;
import jdk.nashorn.api.scripting.ScriptUtils;

/**
 * Test the converter r->js of the class {@link R2JsSession}
 * 
 * @author Nicolas Chabalier
 *
 */
public class R2jsSessionTest {

	@Test 
	public void testRToJsConverter() {

		// maximal epsilon wanted between actual and expected values
		final double epsilon = 1e-12;

		R2JsSession engine = R2JsSession.newInstance(null, null);


		try {
			
			
			assert (Integer) engine.eval("a = 1") == 1; // should be voidEval instead
			assert (Integer) engine.eval("a <- 1")  == 1; // should be voidEval instead
			assert (Integer) engine.eval("a")  == 1;
			assert (Double) engine.eval("a+1") == 2;
			assertEquals((Double)engine.eval("a+pi"),(1+Math.PI),epsilon);
			assert (Integer)engine.eval("b <- (1)") == 1; // should be voidEval instead

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

			assert Double.parseDouble( engine.eval("c = 2 ^ 3").toString()) == 8;
			assert Double.parseDouble( engine.eval("c=2^3").toString())== 8;
			assert Double.parseDouble( engine.eval("c=2**3").toString())== 8;
			assert Double.parseDouble( engine.eval("d = 2 ** 3").toString())== 8;
			assert Boolean.parseBoolean( engine.eval("e=1>2").toString())== false;
			assert Boolean.parseBoolean( engine.eval("e=d>d").toString()) == false;
			assert Boolean.parseBoolean( engine.eval("e=1>=2").toString()) == false;
			assert Boolean.parseBoolean( engine.eval("e=d>=d").toString()) == true;
			assert Boolean.parseBoolean( engine.eval("e=1<=2").toString()) == true;
			assert Boolean.parseBoolean( engine.eval("e=d<=d").toString()) == true;
			// FIXME
//			assert Boolean.parseBoolean( engine.eval("e=1==2").toString()) == false;
//			assert Boolean.parseBoolean( engine.eval("e=d==d").toString()) == true;
			assert Boolean.parseBoolean( engine.eval("e=1!=2").toString()) == true;
			assert Boolean.parseBoolean( engine.eval("e=d!=d").toString()) == false;

			engine.voidEval("a = c(1,2,3,12);");
			engine.voidEval("f = function(x) {res=0;\n for(y in x) {res+=y};\n return res;}");
			assert (Double) engine.eval("f(a)") == 18;

			engine.voidEval("a1 = c(1,2,3,12);\n b_2 = 2;");
			assert (Integer)engine.eval("a1[1]")  == 1;
			assert (Integer)engine.eval("a1[4]") ==12;
			assert (Integer)engine.eval("a1[b_2]") ==2;
			assert (Integer)engine.eval("a1[b_2-1]") ==1;

			engine.voidEval("f <- function(x=2,y=1) {return x+y}");
			assert (Double) engine.eval("f()") == 3;
			assert (Double) engine.eval("f(1.23)") == 2.23;
			assertEquals((Double) engine.eval("f(1.23, 4.56)"),5.79, epsilon);

			// FIXME: this test bug because: multiplication should have the priority on addition  
			//			engine.eval(R2MathjsSession.R2js("f <- function(x,bool1=TRUE,bool2=TRUE) {\n   if (!bool1) {\n     a <- 1; b <- 2\n   } else {\n     a <- 3; b <- 4\n   }\n   if (bool2) {\n     c<-a*1000 + b*100\n   } else if (!bool2) {\n     c<--a*1000 - b*100\n   }\n   result <- c + x\n   return(result)\n }"));
			//			assert Double.parseDouble( engine.eval(R2MathjsSession.R2js("f(1, TRUE, TRUE)")).toString()) == 3401;
			//			assert Double.parseDouble( engine.eval(R2MathjsSession.R2js("f(3)")).toString()) == 3403;
			//			assert Double.parseDouble( engine.eval(R2MathjsSession.R2js("f(3, FALSE, FALSE)")).toString()) == -1197;

			engine.voidEval("a <- c(0:4);");
	        double[] m = new double[]{0,1,2,3, 4};
	        double[] res = (double[])engine.eval("a");
	        assert Arrays.equals(m, res);


			engine.voidEval("f = function(x) {res=0;\n for(y in x) {res+=y};\n return res;}");
			assert (Double) engine.eval("f(a)") == 10;


			engine.voidEval("recur_factorial <- function(n) {\n if(n <= 1) {\n return(1)\n } else { \n return(n * recur_factorial(n-1))\n }\n }");
			assert (Double) engine.eval("recur_factorial(3)") == 6;
			assert (Double) engine.eval("recur_factorial(5)") == 120;

//			engine.eval(R2MathjsSession.R2js("g<-function(x){1+x;};\nh<-function(x){1-x;};"));
//			assert Double.parseDouble( engine.eval(" g(1)").toString()) == 2;
//			assert Double.parseDouble( engine.eval("h(1)").toString()) == 0;
//			
//			engine.eval(R2MathjsSession.R2js("fahrenheit_to_kelvin <- function(temp_F) {\n   temp_K <- ((temp_F - 32) * (5 / 9)) + 273.15\n   return(temp_K)\n };\n kelvin_to_celsius <- function(temp_K) {\n temp_C <- temp_K - 273.15\n   return(temp_C)\n };\n fahrenheit_to_celsius <- function(temp_F) {\n   temp_K <- fahrenheit_to_kelvin(temp_F)\n   temp_C <- kelvin_to_celsius(temp_K)\n   return(temp_C)\n };\n"));
//			assert Double.parseDouble( engine.eval("fahrenheit_to_celsius(32.0);\n").toString()) == 0;
//			assert Double.parseDouble( engine.eval("kelvin_to_celsius(fahrenheit_to_kelvin(32.0))").toString()) == 32;
//			assert Double.parseDouble( engine.eval("kelvin_to_celsius(0)").toString()) == -273.15;

			// Addition between arrays
			engine.voidEval("a = c(4,5,6,7)");
			engine.voidEval("b = c(1,2,1,2)");
			engine.voidEval("c = a + b");
			assert Arrays.equals((double[]) engine.eval("c"), new double[]{5,7,7,9});

			// Addiction between arrays in js function
			engine.voidEval("fsum <- function(x, y) {return x+y}");
			engine.voidEval("d = fsum(a , b)");
			assert Arrays.equals((double[]) engine.eval("d"), new double[]{5, 7, 7, 9});

			// Matrix creation
			engine.voidEval("A = matrix( c(2, 4, 3, 1, 5, 7), nrow=3, ncol=2, byrow=TRUE)");
			assert Arrays.deepEquals((double[][]) engine.eval("A"), new double[][]{{2,4},{3,1},{5,7}});

			engine.voidEval("A = matrix( c(2, 4, 3, 1, 5, 7), nrow=3, ncol=2)");
			assert Arrays.deepEquals((double[][]) engine.eval("A"), new double[][]{{2,1},{4,5},{3,7}});

			engine.voidEval("A = matrix( c(2, 4, 3, 1, 5, 7), nrow=3, ncol=2, byrow=FALSE)");
			assert Arrays.deepEquals((double[][]) engine.eval("A"), new double[][]{{2,1},{4,5},{3,7}});

			// Operators
			System.err.println(engine.eval("a = -4 * 10 -5* 100")); // should be voidEval instead
			assert (Double)engine.eval("a") == -540;

			System.err.println(engine.eval("a =-4*10-5*100")); // should be voidEval instead
			assert (Double)engine.eval("a") == -540;

			System.err.println(engine.eval("a = (-4 * 10) +(-5* 100)")); // should be voidEval instead
			assert (Double)engine.eval("a") == -540;

			System.err.println(engine.eval("a = -4 * -10 -5*-100 + 18 * (-5*(3+9))")); // should be voidEval instead
			assert (Double)engine.eval("a") == -540;

			System.err.println(engine.eval("a = (3 - 5) * (-6 + 5) / 2 ")); // should be voidEval instead
			assert (Double)engine.eval("a") == 1;

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
			System.err.println(engine.eval("e <- a-b")); // should be voidEval instead
			// TODO: pass this test
			//assert Arrays.equals((double[]) engine.eval("e"), new double[]{-1,1,1});

			// Addition
			engine.voidEval("f <- a+b");
			assert Arrays.equals((double[]) engine.eval("f"), new double[]{3,3,5});

			// TODO  multiplication and division '%*%'
			//System.err.println(engine.eval(R2MathjsSession.R2js("bt <- t(b)")));
			//System.err.println(engine.eval("h <- a * bt"));
			//System.err.println(engine.eval(R2MathjsSession.R2js("h <- a %*% bt")));
			//assertTrue(engine.eval("h").toString().trim().equals("[todo]".trim()));

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

		} catch (Exception e) {
			e.printStackTrace();
			assertTrue(false);
		}
	}

}
