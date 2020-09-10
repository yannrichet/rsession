package org.math.R;

import java.util.Arrays;
import java.util.List;
import org.junit.Test;

/**
 * This class test the function 'R2jsUtils.parse(expr)' that parse a R
 * expression in inline R sub-functions.
 *
 *
 * @author Nicolas Chabalier
 */
public class R2jsParserTest {

    @Test
    public void testQuoteExpr() {
        String text = "abcabcabc\"defdef\"abcabc'ghighi'";//abc\"'jkl'\"abc'\"mno\"'";
        System.err.println("text:        " + text);
        List vars = R2jsSession.replaceQuotesByVariables(text, 0);
        System.err.println("vars: " + vars);
        String quoted_text = R2jsSession.replaceNameByQuotes(vars, text, true);
        //System.err.println("quoted text: "+quoted_text);
        assert quoted_text.equals(text) : "Failed to quote/unqote text: " + text + " -> " + quoted_text;

        String paste = "html <- paste0(' <HTML name=\"Root\">in iteration number ',brent$i,'.<br/>',\n"
                + "            'the root approximation is ', X[nrow(X)-1, 1], '.<br/>',\n"
                + "            'corresponding to the value ', Y[nrow(X)-1, 1],'<br/>',\n"
                + "            '<img src=\"',  brent$files,  '\" width=\"600\" height=\"600\"/>',\n"
                + "            '<br/>Exit due to ', exit.txt, '<br/></HTML>')";
        System.err.println("text:        " + paste);
        List vars_paste = R2jsSession.replaceQuotesByVariables(paste, 0);
        System.err.println("vars: " + vars_paste);
        String quoted_paste = R2jsSession.replaceNameByQuotes(vars_paste, paste, true);
        //System.err.println("quoted text: "+quoted_text);
        assert quoted_paste.equals(paste) : "Failed to quote/unqote text: " + paste + " -> " + quoted_paste;

        String paste2 = "html=paste0(\"<HTML name='minimum'>minimum is \",m,\n"
                + "                \" found at \",\n"
                + "                paste0(paste(names(X),'=',x, collapse=';')),\n"
                + "                \"<br/><img src='\",\n"
                + "                algorithm$files,\n"
                + "                \"' width='600' height='600'/></HTML>\")";
        System.err.println("text:        " + paste2);
        List vars_paste2 = R2jsSession.replaceQuotesByVariables(paste2, 0);
        System.err.println("vars: " + vars_paste2);
        String quoted_paste2 = R2jsSession.replaceNameByQuotes(vars_paste2, paste2, true);
        //System.err.println("quoted text: "+quoted_text);
        assert quoted_paste2.equals(paste2) : "Failed to quote/unqote text: " + paste2 + " -> " + quoted_paste2;
    }

    @Test
    public void testParser() {

        String inputString = "a=1;b=2; c=3;"
                + "a; d\n"
                + "e";
        List<String> result = R2jsUtils.parse(inputString);
        String[] expected = new String[]{"a=1;", "b=2;", "c=3;", "a;", "d\n", "e"};
        assert Arrays.equals(result.toArray(), expected) : result.toString();
    }

    @Test
    public void testParser2() {

        String inputString = "# Comment\n"
                + "# Second; comment\n"
                + "f(x) = a\n"
                + "# comment\n"
                + "g(x) = 1; b=2";
        List<String> result = R2jsUtils.parse(inputString);
        String[] expected = new String[]{"f(x) = a", "g(x) = 1;", "b=2\n"};
        assert Arrays.equals(result.toArray(), expected) : result;
    }

    @Test
    public void testParser3() {

        String inputString = "#' @test stopifnot(g_to_kg(1234) - 1.234 < 1E-9)\n"
                + "g_to_kg <- function(x_g) {\n"
                + "    x_g/1000\n"
                + "};";
        List<String> result = R2jsUtils.parse(inputString);
        String[] expected = new String[]{"g_to_kg <- function(x_g) {;\n"
            + "x_g/1000;\n"
            + "};"};
        assert Arrays.equals(result.toArray(), expected) : result.toString() + "\n!=\n" + Arrays.asList(expected);
    }

    @Test
    public void testParser4() {

        String inputString = "#' @param x_kg mass in kg\n"
                + "#' @return mass in g\n"
                + "#' @test stopifnot(g_to_kg(kg_to_g(0.123)) - 0.123 < 1E-9)\n"
                + "kg_to_g <- function(x_kg) {\n"
                + "    1000*x_kg\n"
                + "}\n"
                + "\n"
                + "#' @param x_cm3 volume in cm^3\n"
                + "#' @return volume in liters (L)\n"
                + "#' @test stopifnot(cm3_to_L(1234) - 1.234 < 1E-9)\n"
                + "cm3_to_L <- function(x_cm3) {\n"
                + "    x_cm3/1000\n"
                + "}";
        List<String> result = R2jsUtils.parse(inputString);
        String[] expected = new String[]{"kg_to_g <- function(x_kg) {;\n"
            + "1000*x_kg;\n"
            + "}",
            "cm3_to_L <- function(x_cm3) {;\n"
            + "x_cm3/1000;\n"
            + "}"};
        assert Arrays.equals(result.toArray(), expected) : result.toString() + "\n!=\n" + Arrays.asList(expected);
    }

}
