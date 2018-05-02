package net.gregseth.rsession;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import jdk.nashorn.internal.runtime.regexp.joni.Regex;

/**
 *
 * @author richet
 */
public class R2jsBasic {

    static String[] Rexpressions = {"a = 1", "a <- 1", "a = 1;",
        "a", "a+1", "a+pi",
        "f = function(x) {return(1+x)}", "f(1.23)", "f(x=1.23)", "f = function(x) {1+x}", "f(1.23)", "f = function(x) 1+x", "f(1.23)",
        "f = function(x) {\nreturn(1+x)\n}", "f(1.23)", "f = function(x) {\nreturn(1+x);\n}", "f(1.23)", "f = function(x) {\n1+x;\n}", "f(1.23)",
        "f = function(x) {\nif (x>1) return(1+x) else return(1-x)\n}", "f(1.23)", "f(0.23)", "f = function(x) {\nif (x>1) {return(1+x)} else {return(1-x)}\n}", "f(1.23)", "f(0.23)", "f = function(x) {\nif (x>1) return(1+x) \n else return(1-x)\n}", "f(1.23)", "f(0.23)",
        "f = function(x) {\nfor (i=1:floor(x)) y=y+i\nreturn(y)}", "f(1.23)",
        "f = function(x,y) {return(x-y)}", "f(1.23,4.56)", "f(x=1.23,y=4.56)", "f(y=1.23,x=4.56)",
        "f = function(x) {sin(x)}", "f(1.23)",
        "f = function(x) {dsin(x)}", "f(1.23)"};

    static String[] MATH_FUN_js = {"abs", "acos", "asin", "atan", "atan2", "ceil", "cos", "exp", "floor", "log", "max", "min", "round", "sin", "sqrt", "tan"};
    static String[] MATH_CONST_js = {"pi"};

    static String FOR = "for\\s\\(";
    static String COLUMN = ":";
    static String IN = "\\bin\\b";
    static String IF = "if\\s\\(";
    static String ELSE = "else";

    static boolean hasBalancedBrackets(String expression) {
        int par = 0, squ = 0, cur = 0, sqt = 0, dqt = 0;

        for (int i=0; i<expression.length(); ++i) {
            char c = expression.charAt(i);
            switch (c) {
                case '(': par++;    break;
                case ')': par--;    break;
                case '[': squ++;    break;
                case ']': squ--;    break;
                case '{': cur++;    break;
                case '}': cur--;    break;
                case '\'':sqt++;    break;
                case '"': dqt++;    break;
                default:            break;
            }
        }

        return par == 0 && squ == 0 && cur == 0 && sqt%2 == 0 && dqt%2 == 0;
    }

    static List<String> splitFirstLevel(String expression, String separator) {
        List<String> lst = new ArrayList<>();
        String needle = "";
        for (String item : expression.split(separator)) {
            needle += item;
            if (!hasBalancedBrackets(needle)) {
                needle += ",";
                continue;
            }
            lst.add(needle);
            needle = "";
        }
        return lst;
    }

    static String extractBalancedUntil(String expression, String stop) {
        String needle = "";
        for (String item : expression.split(Pattern.quote(stop))) {
            needle += item;
            if (hasBalancedBrackets(needle)) {
                break;
            }
            needle += stop;
        }
        return needle;
    }

    static String replaceIter(String expression) {
        return expression.replaceAll("([^=\\s]+)\\s*=\\s*([a-zA-Z0-9]+)\\s*:\\s*([a-zA-Z0-9]+)\\s*", "$1=$2; $1<$3; $1++");
    }
    static String replaceForIn(String expression, String array, String index) {
        return expression.replaceAll(index, array+"["+index+"]");
    }

    static String reindexArray(String arrayIndex) {
        List<String> lst = new ArrayList<>();
        for (String s : splitFirstLevel(arrayIndex, ",")) {
            lst.add(s+"-1");
        }
        return String.join("][", lst);
    }

    static String replaceFors(String expression) {
        StringBuilder replaced = new StringBuilder();
        Regex re = new Regex(FOR);
        Pattern p = Pattern.compile(FOR);
        Matcher m = p.matcher(expression);
        int start = 0;


        while (m.find()) {
            replaced.append(expression.substring(start, m.end()));
            String forCondition = extractBalancedUntil(expression.substring(m.end()-1), ")");
            if (forCondition.contains(":")) {
                forCondition = replaceIter(forCondition);
            }
            replaced.append("(").append(forCondition).append(")");
            String forBody = extractBalancedUntil(expression.substring(m.end()+forCondition.length()+2), "}");
            forBody = replaceFors(forBody);
        }

        return replaced.toString();
    }

    static String R2js(String e) {
        for (String f : MATH_FUN_js) {
            e = e.replaceAll("(\\b)" + f + "\\(", "$1 Math." + f + "(");
        }
        for (String c : MATH_CONST_js) {
            e = e.replaceAll("(\\b)" + c + "(\\b)", "$1 Math." + c.toUpperCase() + "$2");
        }
        e = e.replaceAll("(\\b)<-(\\b)", "$1 = $2");
        e = e.replaceAll("([a-zA-Z0-9]+) = function(\\b)", "function $1");


        for (int i=0;i<e.length(); ++i) {

        }
        
        return e;
    }

    public static void main(String[] args) {
        /*
        ScriptEngineManager engineManager = new ScriptEngineManager();
        ScriptEngine engine = engineManager.getEngineByName("javascript");
        engineManager.getEngineFactories();
        try {
            System.out.println(engine.eval("a=0; iterable = [3, 5, 7];for (i of iterable) { a += iterable[i]; }; a;"));
        } catch (ScriptException ex) {
            System.err.println(ex);
        }
        for (String e : Rexpressions) {
            System.out.println("| " + e.replace("\n", "\n| "));
            e = R2js(e);
            System.out.println(": " + e.replace("\n", "\n: "));
            try {
                System.out.println("> " + engine.eval(e).toString().replace("\n", "\n> "));
            } catch (ScriptException ex) {
                System.err.println("! " + ex.getMessage());
            } catch (Exception ex) {
                System.err.println("? " + ex.getMessage());
            }
        }
        */
        //System.out.println(splitFirstLevel("a[f(x,y),g(b[i,j],c),h]", ","));
        //System.out.println(extractBalancedUntil("[f(x,y),g(b[i,j],c),h]", "]"));
        //System.out.println(extractBalancedUntil(" a += c } else { c += d }", "}"));
        //System.out.println(extractBalancedUntil(" a+b, g(c-d), e(i,j))", ")"));
        System.out.println(replaceIter("ave = 5 : 10 "));
        System.out.println(replaceForIn("a=boz +1; boz--;","zob", "boz"));
        System.out.println(reindexArray("i,j,k"));

        System.out.println(reindexArray("f(i,j,k),j-5,b[k]"));
    }

}
