package org.math.R;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.PrintStream;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.Set;
import java.util.logging.Logger;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import javax.script.ScriptEngine;
import javax.script.ScriptEngineManager;
import javax.script.ScriptException;
import jdk.nashorn.api.scripting.ScriptObjectMirror;
import jdk.nashorn.api.scripting.ScriptUtils;


/**
 * This class evaluate an R expression by parsing it in javascript expression and then
 * evaluate the javascript expression with the Java ScriptEngine. This class
 * uses external javascript library like mathjs to evaluate expressions.
 * 
 * 
 * ------------------------------ Supported and unsupported syntaxes ------------------------------------------------------------------
 * ## Basic syntaxes
 *      - affectation (=, &lt;-)
 *      - mathematical expression (pi, ^, **, &lt;, &lt;=, &gt;, &gt;=, !=, ==, ++, +=, -=, priority of operators, for, for in, if, else, range)
 * ## Functions
 *      - All syntaxes of functions in R
 *      - recursive functions
 *      - Mathematical functions ("abs", "acos", "asin", "atan", "atan2", "ceil", "cos", "exp", "floor", "log", "max", "min", "round", "sin", "sqrt", "tan" )
 *      - NOT SUPPORTED: - function with named argument (ex: "f(y=1.23,x=4.56)") (impossible to support in the ES5)
 *                 - multiple functions in one command (impossible just write multiple lines)
 * ## Arrays
 *      - definition of arrays (c(1,2,3), c(0:4), array(0.0,c(4,3)))
 *      - accessor: a[1], 
 *      - operations (+, -, *, /)
 *      - function: length
 *      - NOT SUPPORTED: %*%, %/% : create matrices instead of arrays to use theses operations
 * ## Matrices
 *      - definition of matrices with: nrow, ncol, byrow
 *      - operations (transpose, +, -, *, ^, %*%, %/%, %%)
 *      - column and row selection ([1,], [,1], [,], [c(1,2),])
 *      - function supported: determinant, solve, dim
 *      - TO SUPPORT: eigen
 * ## DataFrames
 *      - constructor: data.frame(first=a,second=b), data.frame('first'=a,'second'=b), data.frame(a,b)
 *      - accessor: '$', "[['element']]"
 *      NOT SUPPORTED: column extraction "[1:2,]"
 * ## Lists
 *      - constructor: list(first=a,second=b), list('first'=a,'second'=b), list(a,b)
 *      - accessor: '$', "[['element']]"
 *      NOT SUPPORTED: column extraction "[1:2,]", accessor "[[1]]"
 * ## R functions
 *      - write.csv
 *      - runif
 *      - save and load variables
 *      - ls
 *      - rm variables
 *      - cbind (on matrices only)
 *      - rbind (on matrices only)
 *      - file.exists
 *      - savels
 *      TO SUPPORT: rnorm, capture.output
 *      NOT SUPPORTED:  toPNG, asHTML, cbind (array and dataframe), rbind (array and dataframe), multiple imbricated functions
 * 
 * -----------------------------------------------------------------------------------------------------------------------------------
 * 
 * @author Nicolas Chabalier
 */
public class R2jsSession extends Rsession implements RLog {
        
    private static final String[] MATH_FUN_JS = { "abs", "acos", "asin", "atan", "atan2", "ceil", "cos", "exp", "floor",
        "log", "max", "min", "round", "sin", "sqrt", "tan", "sign", "sum","mean", "median", "std", "var" };
    private static final String[] MATH_CONST_JS = { "pi" };
    
    // JavaScript libraries used to evaluate expression
    private static final String MATH_JS_FILE = "org/math/R/math.js";
    private static final String R_JS_FILE = "org/math/R/R.js";
    private static final String RAND_JS_FILE = "org/math/R/rand.js";
    
    public ScriptEngine engine;
    
    private static final String JS_ENVIRONMENT_DEFAULT = "__r2js__";
    
    // The name of the object which store all variables defined in the current session
    private String jsEnvName;
    
    public static final String[] DISABLED_FUNCTIONS = new String[]{"png", "plot", "abline", "rgb", "hist", "pairs", "lines", "points"};

    // Set of global variables declared
    public Set<String> variablesSet;
    public Set<String> functionsSet;
    
    // List of quotes expression
    private List<String> quotesList;
    
    public static R2jsSession newInstance(final RLog console, Properties properties) {
        return new R2jsSession(console, properties);
    }
    
    public R2jsSession(RLog console, Properties properties) {
        this(console, properties, null);
    }

    /**
     * Default constructor
     *
     * Initialize the Javascript engine and load external js libraries
     *
     * @param console - console
     * @param properties - properties
     * @param environmentName - name of the environment
     */
    public R2jsSession(RLog console, Properties properties, String environmentName) {
        super(console);
        
        if(environmentName != null) {
            jsEnvName = environmentName;
        } else {
            jsEnvName = JS_ENVIRONMENT_DEFAULT;
        }
        
        variablesSet = new HashSet<>();
        functionsSet = new HashSet<>();
        
        TRY_MODE_DEFAULT = false;
        
        ScriptEngineManager manager = new ScriptEngineManager();
        engine = manager.getEngineByName("js");

        // Load external js libraries used by the engine to evaluate expressions
        try {
            loadJSLibraries();
            
            for (String f : DISABLED_FUNCTIONS)
                addReturnNullFunction(f);
            
            // Instantiate the variables storage object which store all variables defined in the current session
            engine.eval("var " + jsEnvName + " = {};");
        } catch (ScriptException ex) {
            Logger.getLogger(R2jsSession.class.getName()).log(java.util.logging.Level.SEVERE, null, ex);
        }
    }

    public R2jsSession(final PrintStream p, Properties properties) {
        this(p, properties, null);
    }
    
    public R2jsSession(final PrintStream p, Properties properties, String environmentName) {
        this(new RLog() {

            public void log(String string, Level level) {
                PrintStream pp = null;
                if (p != null) {
                    pp = p;
                } else {
                    pp = System.err;
                }

                if (level == Level.WARNING) {
                    pp.print("(!) ");
                } else if (level == Level.ERROR) {
                    pp.print("(!!) ");
                }
                pp.println(string);
            }

            public void closeLog() {
                if (p != null) {
                    p.close();
                }
            }
        }, properties, environmentName);
    }

    public void addReturnNullFunction(String name) throws ScriptException {
        engine.eval("function " + name + "(a,b,c,d,e,f) {return null;}");
        functionsSet.add(name);
    }

    public void addJSFunction(String name, String js) throws ScriptException {
        engine.eval("function " + name + (js.startsWith("function")?js.substring("function".length()):js));
        functionsSet.add(name);
    }
    
    /**
     * Load external js libraries to evaluate js expresions:
     * - 'math.js' : evaluate all mathematical expressions with numbers, arrays and matrices. 
     * - 'r.js': contains various function ( loading and saving files/variables, range function, ...)
     * 
     * @throws ScriptException 
     */
    private void loadJSLibraries() throws ScriptException {
        ClassLoader classLoader = Thread.currentThread().getContextClassLoader();
	
        // Loading math.JS
        InputStream mathInputStream = classLoader.getResourceAsStream(MATH_JS_FILE);
        engine.eval(new InputStreamReader(mathInputStream));
        engine.eval("var parser = math.parser();");
        // Change 'Matrix' mathjs config by 'Array'
        engine.eval("math.config({matrix: 'Array'})");

        engine.eval("var str = String.prototype;");

        // Loading rand.js
        InputStream randInputStream = classLoader.getResourceAsStream(RAND_JS_FILE);
        engine.eval(new InputStreamReader(randInputStream));
        engine.eval("rand = rand()");
        
        // Loading R.js
        InputStream RInputStream = classLoader.getResourceAsStream(R_JS_FILE);
        engine.eval(new InputStreamReader(RInputStream));
        engine.eval("R = R()");
    }

    static final String POINT_CHAR_JS_KEY = "__";

    /**
     * Convert an R expression in a Js expression WARNING: many R syntaxes are not supported yet
     *
     * @param e - the R expression
     * @return the js script expression
     */
    public static String nameRtoJs(String e) {
        // Replace "." char by a dedicated key
        if (e.contains(POINT_CHAR_JS_KEY)) {
            throw new IllegalArgumentException("Cannot use " + POINT_CHAR_JS_KEY + " in expression (reserved substring)");
        }
        e = e.replace(".", POINT_CHAR_JS_KEY);

        return e;
    }

    public static Properties R_TO_JS = new Properties();
    
    static {
        R_TO_JS.put("R.version.string", "'R2js'");
        R_TO_JS.put(".GlobalEnv", JS_ENVIRONMENT_DEFAULT);
        R_TO_JS.put("stop(", "throw new Error(");
        R_TO_JS.put("as.numeric(", "asNumeric(");
        R_TO_JS.put("as.integer(", "asInteger(");
        R_TO_JS.put("as.matrix(", "matrix(");
        R_TO_JS.put("as.array(", "array(");
        R_TO_JS.put("which.min(", "whichMin(");
        R_TO_JS.put("which.max(", "whichMax(");
        R_TO_JS.put("print(", "_print(");
        R_TO_JS.put("is.function(", "isFunction(");
        R_TO_JS.put("is.null(", "isNull(");
        R_TO_JS.put("Sys.sleep(", "SysSleep(");
        R_TO_JS.put("capture.output(", "_print("); //should use the output stream capture instead...
        R_TO_JS.put("NA", "null");
        R_TO_JS.put("new.env()", "{}");
        R_TO_JS.put("dev.off()", "");
        R_TO_JS.put("return()", "return(NULL)");
        R_TO_JS.put("...", "varargs");
    }

    final static String AW = "((\\A)|(\\W)|(\\())(";
    final static String Az = ")((\\W)|(\\z)|(\\)))";

    public boolean debug_js = false; 
    
    /**
     * Convert an R expression in a Js expression WARNING: many R syntaxes are not supported yet
     *
     * @param e - the R expression
     * @return the js script expression
     */
    private String convertRtoJs(String e) {
       
        String R = null;
        if (debug_js) R  =e;

        // remove ; at end of lines. We will re-add it later
        e = e.replaceAll(";+ *\\n", "\n");
        
        // non-regexp keys in R2js.propto replace
        if (R_TO_JS != null) {
            for (Object R_key : R_TO_JS.keySet()) {
                String var =  Pattern.quote(R_key.toString());
                String regexp = AW +var + (R_key.toString().endsWith("(")?")":Az);
                Matcher m = Pattern.compile(regexp).matcher(e);
                while (m.find()) {
                    String val = R_TO_JS.getProperty(R_key.toString());
                    e = e.replace(m.group(), m.group().replace(R_key.toString(), val));
                }
            }
        }
        
        //1E-8 -> 1*10^-8
        e = e.replaceAll("([\\d|\\.]+)[eE]+[+-]*(\\d)", "$1*10^-$2");

        // Get all expression in quote and replace them by variables to not
        // modify them in this function
        quotesList = replaceQuotesByVariables(e, 1);

        // Get the expression with replaced quotes (it's the first element of
        // the returned list)
        e = quotesList.get(0);

        //change variable names containing "." by "__", but avoid file names (ending with ')
        e = e.replaceAll("([a-zA-Z]+)\\.([a-zA-Z]+)", "$1__$2");
        
        // Replace Math functions
        for (String f : MATH_FUN_JS) {
            e = e.replaceAll("(\\b)" + f + "\\(", "$1 math." + f + "(");
        }
        
        // Replace Math constants
        for (String c : MATH_CONST_JS) {
            e = e.replaceAll("(\\b)" + c + "(\\b)", "$1 math." + c.toUpperCase() + "$2");
        }
        
        // Replace t(x) by math.transpose(x)
        e = e.replaceAll("(^|[^a-zA-Z\\d:])t\\(", "$1math.transpose(");
        
        // Replace determinant(x) by math.det(x)
        e = e.replaceAll("(^|[^a-zA-Z\\d:])determinant\\(", "$1math.det(");
        
        // Replace solve(A,B) by math.lusolve(A,B)
        e = e.replaceAll("(^|[^a-zA-Z\\d:])solve\\(", "$1math.lusolve(");
        
        // Replace dim(A) by r.dim(A)
        e = e.replaceAll("(^|[^a-zA-Z\\d\\.:])dim\\(", "$1R.dim(");
        
        // replace '->' by '='
        e = e.replaceAll("<<-", "=");
	e = e.replaceAll("<-", "=");
        
        // replace "+-" by "-"
        e = e.replaceAll("\\+ *-", "-");
        
        // replace 'f = function(x)' by 'function f(x)'
        //e = e.replaceAll("([\\w\\-]+) *= *function[(]([\\w\\-[^)]]*)[)]", "function $1($2)");
        /*Matcher matcherFunction = Pattern.compile("([\\w\\-]+) *= *function[(]([\\w\\-[^)]]*)[)](.*)").matcher(e);
        if (matcherFunction.find()) {
            matcherFunction.reset();
            StringBuffer sb = new StringBuffer("");
            while (matcherFunction.find()) {
                StringBuilder f = new StringBuilder();
                f.append("function ");
                String name = matcherFunction.group(1); 
                f.append(name);
                f.append("(");
                f.append(matcherFunction.group(2));
                f.append(")");
                f.append(matcherFunction.group(3));
                functionsSet.add(name);
                
                matcherFunction.appendReplacement(sb, f.toString());
            }
            matcherFunction.appendTail(sb);
            e = sb.toString();
        }   */     

        // replace the for expression
        e = e.replaceAll("[(]([^=\\s]+)\\s*in\\s*([\\w\\-]+)\\s*:\\s*([[\\w\\-][.][)][(]]+)[)]\\s*",
                "($1=$2; $1<=$3; $1++) ");
        
        // Add '{}' between the 'if' and the 'else'
        e = e.replaceAll("if( *[(][^)]*[)])(.[^}]*)else(.*)", "if$1{$2} else{$3}");
        
        // Add "{" and "}" if the function doesn't have them
        e = e.replaceAll("function([.[^)]]*[)]) *([a-zA-Z0-9].*)$", "function$1 {$2}");

        // Add return statement in function if there is no return yet
        // FIXME: multiple imbricated functions are not supported for the moment
        e = e.replaceAll("function([.[^)]]*[)]) *[{]\\s*(((?!return|function).)*)\\s*[}];*$", "function$1 {return $2}");
        // e = e.replaceAll("function([.[^)]]*[)])
        // *[{](((?!return|function).)*)[}] *;", "function$1 {return $2};");
        // e = e.replaceAll("function([.[^)]]*[)])
        // *[{](((?!return|function).)*)[}] *;", "function$1 {return $2};");
        // *[{](((?!return|function).)*)[}] *\n", "function$1 {return $2}\n");
        
        // replace operator '**' by '^'
        e = e.replaceAll("\\*\\*", "\\^");

        
        // Replace array indexing
        e = replaceIndexesSet(e);
        e = replaceIndexes(e);
        
        // replace the array in R defined by c(1, 2, ...) by array in js [1, 2, ...]
        // FIXME: this will not work with c(a(2), b) because of parenthesis (maybe treat c like other functions?)
        e = e.replaceAll("([^A-Za-z0-9]|^)c[(]([.[^):]]*)[)]", "$1[$2]");
        e = e.replaceAll("([^A-Za-z0-9]|^)c[(]([.[^)]]*)[)]", "$1$2");
        
        // replace "for (x in array) {...}" by: "var arrayLength = array.length;
        // for(var i = 0; i < arrayLength; i++) { x = array[i]; ...}"
        // We can't used the "for (x of array)" expression in javascript because
        // it's not supported by Java8 and his javascript evaluator
        e = e.replaceAll("for *[(]([\\w\\-]+) +in +([\\w\\-]+)[)] *[{]",
                "var $2Length = R.dim($2)[0]; for(var i = 0; i < $2Length; i++) {$1 = $2[i]; ");
        
        // Replace "TRUE" by "true" and "FALSE" by "false"
        // TODO: Check that 'TRUE' is not inside a variable ex: myTRUEvariable
        e = e.replaceAll("TRUE", "true");
        e = e.replaceAll("FALSE", "false");
        
        // TODO: Check that 'NULL' is not inside a variable ex: myNULLvariable
        e = e.replaceAll("NULL", "null");
        
        // Replace '++' operator by a=a+1
        e = e.replaceAll("([^ ]+)\\+\\+", "$1=$1+1"); 
        
        // Replace '+=' operator by a=a+x
        e = e.replaceAll("([^ ]+) *\\+\\=", "$1=$1+");
        
        // FIXME this doesn't support += ...
        // Replace operators (+, -, *, /, ...)
        e = replaceOperators(e);
        
        // Default parameter ("function(arg = defaultValue) {...}") is defined after the version ES6 of javascript
        // Java8 uses a previous version of javascript so we have to transform this expression in:
        // function(arg) { arg = typeof arg !== 'undefined' ? arg :
        // defaultValue; ...}
        Matcher matcher = Pattern.compile("(.*function)[(](.*=.*)[)] *[{]").matcher(e);
        if (matcher.find()) {
            matcher.reset();
            StringBuffer sb = new StringBuffer("");
            while (matcher.find()) {
                sb.append(matcher.group(1));
                String functionCorrected = replaceFunctionDefaultArguments(matcher.group(2));
                matcher.appendReplacement(sb, functionCorrected);
            }
            matcher.appendTail(sb);
            e = sb.toString();
        }

        // replace matrix expression
        e = createMatrix(e);
        
        // replace array expression
        e = createArray(e);

        // replace write csv expression
        e = createWriteCSV(e);
        
        // replace data.frame expression
        e = createDataFrame(e);
        
        // replace list expression
        e = e.replace("list()","{}");
        e = createList(e);

        // replace ls fct expression
        e = createLs(e);
        
        // replace save fct expression
        e = createSaveFunction(e);
        
        // replace load fct expression
        e = createLoadFunction(e);
        
        // replace length fct expression
        e = createLengthFunction(e);
        
        // replace file.exist fct expression
        e = createFileExistsFunction(e);
        
        e = createExistsFunction(e);
        e = createStopIfNotFunction(e);
        //e = createPredefinedFunction(e);

        // change for (x in X) {...} by for (x in R._in(X)) {...}, because in R in returns values for arrays (and keys for maps)
        e = e.replaceAll(" in ([^\\{]+)\\{", " in R._in($1){");
        
        // force regular 'if' to throw error when arg is null
        e = e.replaceAll("if \\(([^\\{\\n]+)\\)\\s*(\\{|(return))", "if (R._if($1)) $2");
        
        // replace line return (\n) by ";" if there is a "=" or a "return" in the line
        e = e.replaceAll("return(.*)\n", "return$1 ;\n");
        //e = e.replaceAll("=(.*)\n", "=$1 ;\n");
        e = e.replaceAll("(.[^\\n+-=/\\*]+)\n", "$1 ;\n");
                
        // Remove '+' at begining
        e = e.replaceAll("^ *\\+", "");
        
        // Remove unused ';' (after a bracket for instance)
        e = e.replaceAll("\\{ *;", "\\{");
        //e = e.replaceAll("\\} *;", "\\}"); No! otherwise, will replace 'a={}; \n b=2' by 'a={} \n b=2'
        e = e.replaceAll("\\[ *;", "\\[");
        e = e.replaceAll("\\( *;", "\\(");
        e = e.replaceAll("; *;", ";");

        
        e = e.replaceAll("(\\W*)is__array\\(", "$1Array.isArray(");

        
        e = e.replaceAll("(\\W*)ncol\\(", "$1R.ncol(");
        e = e.replaceAll("(\\W*)nrow\\(", "$1R.nrow(");
        e = e.replaceAll("(\\W*)names\\(((\\w|\\.)+)\\)\\s*=\\s*", "$1 $2.names = "); // names(X) = "abc"
        e = e.replaceAll("(\\W*)names\\(", "$1R.names(");
        e = e.replaceAll("(\\W*)length\\(", "$1R.length(");
        e = e.replaceAll("(\\W*)dim\\(", "$1R.dim(");
        e = e.replaceAll("(\\W*)rep\\(", "$1R.rep(");
        e = e.replaceAll("(\\W*)which\\(", "$1R.which(");
        e = e.replaceAll("(\\W*)whichMin\\(", "$1R.whichMin(");
        e = e.replaceAll("(\\W*)whichMax\\(", "$1R.whichMax(");
        e = e.replaceAll("(\\W*)_print\\(", "$1R._print(");
        e = e.replaceAll("(\\W*)getwd\\(", "$1R.getwd(");
        e = e.replaceAll("(\\W*)SysSleep\\(", "$1R.SysSleep(");
        e = e.replaceAll("(\\W*)isFunction\\(", "$1R.isFunction(");
        e = e.replaceAll("(\\W*)isNull\\(", "$1R.isNull(");
        e = e.replaceAll("(\\W*)apply\\(", "$1R.apply(");
        e = e.replaceAll("(\\W*)rbind\\(", "$1R.rbind(");
        e = e.replaceAll("(\\W*)cbind\\(", "$1R.cbind(");
        e = e.replaceAll("(\\W*)paste\\(", "$1R.paste(");
        e = e.replaceAll("(\\W*)paste0\\(", "$1R.paste0(");
        e = e.replaceAll("(\\W*)asNumeric\\(", "$1R.asNumeric(");
        e = e.replaceAll("(\\W*)asInteger\\(", "$1R.asInteger(");

        e = e.replaceAll("R\\.R\\.", "R.");
        
        e = e.replaceAll("(\\W*)runif\\(", "$1rand.runif(");
        e = e.replaceAll("(\\W*)rnorm\\(", "$1rand.rnorm(");
        e = e.replaceAll("(\\W*)rpois\\(", "$1rand.rpois(");
        e = e.replaceAll("(\\W*)rcauchy\\(", "$1rand.rcauchy(");
        e = e.replaceAll("(\\W*)rchisq\\(", "$1rand.rchisq(");

        e = e.replaceAll("rand\\.rand\\.", "rand.");
        
        
        try {
            e = convertFunction(e);
        } catch (ScriptException ex) {
            Logger.getLogger(R2jsSession.class.getName()).log(java.util.logging.Level.SEVERE, null, ex);
        }
        
        // Store global variables in the object containing all global variables
        storeGlobalVariables(e);
        
        // Replace variables by variableStorageObject.variable
        e = replaceVariables(e, variablesSet);

        // Finally replace "quotes variables" by their expressions associated
        e = replaceNameByQuotes(quotesList, e, false);

        // Replace '$' accessor of data.frame by a '.'
        e = e.replaceAll("\\$" + jsEnvName + "\\.", "\\$"); // Remove the JS variable if there is a '$' before
        e = e.replaceAll("\\$", ".");
        
        // R Comments
        e = e.replaceAll("#", "//");
        //e = e.replaceAll("^(.*)#(.*)$", "$1/*$2*/");
        
        
        if (debug_js) {
            String[] lines_R = R.split("\n");
            String[] lines_JS = e.split("\n");
            int w = maxLength(lines_R);
            System.err.println("------------------------------------------------------------------------------------");
            for (int i = 0; i < Math.max(lines_R.length, lines_JS.length); i++) {
                System.err.println(
                        rightPad((lines_R.length > i ? lines_R[i] : ""), w)
                        + " | " + rightPad((i +1)+ "", 3) + " | "
                        + (lines_JS.length > i ? lines_JS[i] : ""));
            }
            System.err.println("------------------------------------------------------------------------------------");
        }

        return e;
    }
    
    private String convertFunction(String expr) throws ScriptException {
        Pattern indexPattern = Pattern.compile("(^|[^\\.\\w])((?!function|if|for|while|switch|return)\\w+)[(]");
        Matcher indexMatcher = indexPattern.matcher(expr);
        
        if (indexMatcher.find()) {
            indexMatcher.reset();
            while (indexMatcher.find()) {
                String fctName = indexMatcher.group(2);
                // Ignore if the functions is already defined
                if(!this.variablesSet.contains(fctName)) {
                    // If the function is not defined yet in js environment
                    if(!this.asLogical(this.engine.eval("typeof "+ fctName +" !== 'undefined'"))) {
                         this.variablesSet.add(fctName);
                    }
                }
            }
        } else {
            return expr;
        }
        
        return expr;
    }

    int maxLength(String... s) {
        int ml = 0;
        for (String l : s) {
            ml = Math.max(ml, l.length());
        }
        return ml;
    }

    String rightPad(String s, int pad) {
        if (s == null) {
            s = "";
        }
        if (s.length() > pad) {
            return s.substring(0, pad);
        }
        StringBuffer sb = new StringBuffer(pad);
        for (int i = 0; i < pad - s.length(); i++) {
            sb.append(" ");
        }
        return s + sb.toString();
    }

    /**
     * Replace all variables by JS_VARIABLE_STORAGE_OBJECT.variable to access
     * variable define in this JS object
     *
     * WARNING: If two variables has the same name (because one is global and
     * the other is local for example), there will be an error.
     *
     * @param expr - the expression to replace
     * @param variables - the variables to replace
     * @return the expression with replaced variables
     */
    private String replaceVariables(String expr, Iterable<String> variables) {
        String result = expr;
        for (String variable : variables) {
            if (variable.length() > 0) {
                //result = result.replaceAll("(\\b)^((?!" + JS_VARIABLE_STORAGE_OBJECT + "\\.).)*(\\b)(" + variable + ")(\\b)", JS_VARIABLE_STORAGE_OBJECT + "." + variable);
                result = result.replaceAll("(\\b)(" + variable + ")(\\b)", jsEnvName + "." + variable);
                result = result.replaceAll(jsEnvName + "\\." + jsEnvName, jsEnvName);
            }
        }
        return result;

    }

    /**
     * Replace all quoted expression by variables to not parse them. All
     * expression in quotes will be replaced by QUOTE_EXPRESSION_ID with ID the
     * id of the quotes.
     *
     * The result is a list containing all quotes expression and at the first
     * index the global expression with quotes replaced by all variables
     *
     * @param expr
     *            - the expression with quotes
     * @param startIndex
     *            - the index i of the first "QUOTE_EXPRESSION_i_" replacement
     * @return a list containing all quotes expression
     */
    private static List<String> replaceQuotesByVariables(String expr, int startIndex) {
        
        Pattern quotesPattern = Pattern.compile("(\'[^\']*\')");
        Matcher quotesMatcher = quotesPattern.matcher(expr);
        
        List<String> quotesList = new ArrayList<>();
        quotesList.add(expr);
        StringBuffer sb = new StringBuffer();
        int cmp = startIndex;
        while (quotesMatcher.find()) {
            quotesList.add(quotesMatcher.group(1));
            quotesMatcher.appendReplacement(sb, "QUOTE_EXPRESSION_" + cmp+"_"); // need to finish with _ otherwise _1 will replace also _10
            cmp++;
        }
        quotesMatcher.appendTail(sb);
        quotesList.set(0, sb.toString());
        return quotesList;
    }
    
    /**
     * Replace variables: QUOTE_EXPRESSION_ID by their expression in quotesList
     *
     * @param quotesList
     *            - a list containing all quotes expression in the same order
     *            than the ID of variables. (WARNING: The first index of the
     *            list is not used)
     * @param expr
     *            - expression containing variables to replace by quotes
     *            expressions
     * @return the expression with variables replaced by quotes expressions
     */
    private static String replaceNameByQuotes(List<String> quotesList, String expr, boolean removeRoundingQuotes) {

        for (int i = 1; i < quotesList.size(); i++) {
            String quote = quotesList.get(i).trim();
            if(removeRoundingQuotes) {
                quote = quote.substring(1, quote.length()-1);
            }
            expr = expr.replaceAll("QUOTE_EXPRESSION_" + i+"_", quote);
        }
        
        return expr;
    }
    
    /**
     * LS function
     *
     * @param e - the expression containing the function to replace
     * @return the expression with replaced function
     */
    private String createLs(String expr) {
        
        String result = expr;
        
        RFunctionArgumentsDTO rFunctionArgumentsDTO = getFunctionArguments(expr, "ls");
        
        while (rFunctionArgumentsDTO != null) {
            
            int startIndex = rFunctionArgumentsDTO.getStartIndex();
            int endIndex = rFunctionArgumentsDTO.getStopIndex();
            Map<String, String> argumentsMap = rFunctionArgumentsDTO.getGroups();
            
            // Pattern to match
            String pattern = argumentsMap.get("pattern");
            
            // Build the mathjs expression to generate random uniform
            // distribution
            StringBuilder unifRandomSb = new StringBuilder();
            
            // If there is a regex pattern argument
            if (pattern != null) {
                unifRandomSb.append("R.removeMatching(Object.keys(");
                unifRandomSb.append(jsEnvName);
                unifRandomSb.append("), new RegExp(");
                unifRandomSb.append(pattern);
                unifRandomSb.append("))");
            } else {
                unifRandomSb.append("Object.keys(");
                unifRandomSb.append(jsEnvName);
                unifRandomSb.append(")");
            }
            
            // Replace the R matrix expression by the current matrix js
            // expression
            StringBuilder sb = new StringBuilder();
            sb.append(result.substring(0, startIndex));
            sb.append(unifRandomSb.toString());
            sb.append(result.substring(endIndex + 1));
            result = sb.toString();
            
            // Search the next "ls" fct
            rFunctionArgumentsDTO = getFunctionArguments(result, "ls");
        }
        
        return result;
    }
    
    @Override
    public String[] ls(boolean all) {
        List<String> variablesandfunctions = new LinkedList<>();
        variablesandfunctions.addAll(variablesSet);
        variablesandfunctions.addAll(functionsSet);
        variablesandfunctions.removeAll(Arrays.asList(DISABLED_FUNCTIONS));
        
        if (all) 
            return variablesandfunctions.toArray(new String[]{});
        else {
        List<String> notall = new LinkedList<>();
            for (String v : variablesandfunctions) {
                if (!v.startsWith("_")) notall.add(v);
            }
            return notall.toArray(new String[]{});
        }
    }
    
    
    /**
     * Split an array with the separator only if the separator string is not in parenthesis or bracket
     * @param expr - the expression containing the array to replace
     * @param sep - the separator between values in the array
     * @return the split String array
     */
    private static String[] splitString(String expr, String sep) {
        List<String> splitList = new ArrayList<>();
        
        int sepIndex = -1;
        int exprLength = expr.length();
        while (sepIndex < exprLength) {
            int nextIndex = getNextExpressionLastIndex(expr, sepIndex, sep) + 1;
            String argumentName = expr.substring(sepIndex+1, nextIndex).trim();
            splitList.add(argumentName);
            sepIndex = nextIndex;
        }
        
        return splitList.toArray(new String[0]);
    }
    
        static String arrayIfNeeded(String[] l) {
        if (l == null) {
            return null;
        }
        StringBuffer arrayif = new StringBuffer(l[0]);
        boolean truearray = false;
        if (l.length > 0) {
            for (int i = 1; i < l.length; i++) {
                if (l[i].trim().length() > 0) {
                    truearray = true;
                    arrayif.append(" , " + l[i]);
                }
            }
        }

        if (truearray) {
            return " " + arrayif.toString() + " ";
        } else {
            return arrayif.toString();
        }
    }
        
    // accept:
    //X[i]
    //X[[i]]
    //X[,i]
    //f(X)[i]
    // reject:
    //f(X[i]
    //X[Y[i]]
    //X[[Y[i]]]
   private static String index_pattern = "([\\w|\\$|\\.]+(\\([\\w|\\$|\\=|\\,|\\-|\\(|\\)|\\.]+\\))*[\\w|\\$|\\.]*)\\[+(.[^\\]]*)\\]+";
   // to get only one '[': "([\\w|\\$|\\.]+(\\([\\w|\\$|\\=|\\,|\\-|\\(|\\)|\\.]+\\))*[\\w|\\$|\\.]*)\\[([.[^\\[\\]]]*)\\]"
   
    /**
     * Replace indexes by mathjs indexes
     *
     * @param expr - the expression containing indexes to replace
     * @return the expression with replaced indexes
     */
    public static String replaceIndexes(String expr) {
        Matcher intricated = Pattern.compile(".*(\\[+)(.[^\\]]+)(\\[).*").matcher(expr);
        if (intricated.find()) {
            intricated.reset();
            List<String > found = new LinkedList<>();
            while(intricated.find()){
                found.add(intricated.group(1)+intricated.group(2)+intricated.group(3));
            }
            throw new UnsupportedOperationException("Intricated indexes 'abc[def[i]]' not supported at:"+String.join("\n", found));
        }
        
        Pattern indexPattern = Pattern.compile(index_pattern);
        Matcher indexMatcher = indexPattern.matcher(expr);

        if (indexMatcher.find()) {        
            indexMatcher.reset();
            StringBuffer sb = new StringBuffer("");
            while (indexMatcher.find()) {
                String arrayName = indexMatcher.group(1);
                String indexes =" " + indexMatcher.group(3) + " ";
                String[] indexesArray = splitString(indexes, ",");

                for (int i = 0; i < indexesArray.length; i++) {
                    if (indexesArray[i].trim().equals("")) { // If the index is empty, we create an range array to select all the line
                        int dim = i;
                        indexesArray[i] = "math.range(1, 1+math.subset(dim(" + arrayName + "), math.index(" + dim + ")))"; //range starting from 1, because R.r_index will apply -1
                    }
                }

                StringBuilder result = new StringBuilder();
                result.append("math.squeeze(math.subset(");
                result.append(arrayName);
                result.append(", R._index(");
                result.append(arrayIfNeeded(indexesArray));
                result.append(")))");
                
                indexMatcher.appendReplacement(sb, result.toString().replace("$", "\\$"));
            }
            indexMatcher.appendTail(sb);
            return sb.toString();
        } else {
            return expr;
        }

    }

    public static String replaceIndexesSet(String expr) {
        Matcher intricated = Pattern.compile(".*(\\[+)(.[^\\]]+)(\\[).*").matcher(expr);
        if (intricated.find()) {
            intricated.reset();
            List<String > found = new LinkedList<>();
            while(intricated.find()){
            found.add(intricated.group(1)+intricated.group(2)+intricated.group(3));
            }
            throw new UnsupportedOperationException("Intricated indexes 'abc[def[i]]' not supported at:"+String.join("\n", found));
        }
      
        Pattern indexPattern = Pattern.compile(index_pattern+"\\s*[\\=]{1}(.*)");
        Matcher indexMatcher = indexPattern.matcher(expr);

        if (indexMatcher.find()) {
            indexMatcher.reset();
            StringBuffer sb = new StringBuffer("");
            while (indexMatcher.find()) {
                String arrayName = indexMatcher.group(1);
                String indexes = " " + indexMatcher.group(3) + " ";
                String toset = " " + indexMatcher.group(4) + " ";
                String[] indexesArray = splitString(indexes, ",");

                for (int i = 0; i < indexesArray.length; i++) {
                    // If the index is empty, we create an range array to select all the line
                    if (indexesArray[i].trim().equals("")) {
                        int dim = i;
                        indexesArray[i] = "math.range(1, 1+math.subset(dim(" + arrayName + "), math.index(" + dim + ")))"; //range starting from 1, because R.r_index will apply -1
                    }
                }

                StringBuilder result = new StringBuilder();
                result.append(arrayName + " = math.subset(");
                result.append(arrayName);
                result.append(", R._index(");
                result.append(arrayIfNeeded(indexesArray));
                result.append(")," + toset + ")");

                indexMatcher.appendReplacement(sb, result.toString().replace("$", "\\$"));
            }
            indexMatcher.appendTail(sb);

            return sb.toString();
        } else {
            return expr;
        }

    }

    /**
     * Convert the R expression or write csv: write.csv(data, file) to js
     * expression: r.write(file, data)
     *
     *
     * @param expr - the expression containing the function to replace
     * @return the expression with replaced function
     */
    private static String createWriteCSV(String expr) {
        
        String result = expr;
        
        RFunctionArgumentsDTO rFunctionArgumentsDTO = getFunctionArguments(expr, "write__csv");
        
        while (rFunctionArgumentsDTO != null) {
            
            int startIndex = rFunctionArgumentsDTO.getStartIndex();
            int endIndex = rFunctionArgumentsDTO.getStopIndex();
            Map<String, String> argumentsMap = rFunctionArgumentsDTO.getGroups();
            
            // Data to write
            String data = argumentsMap.get("data");
            
            // File
            String file = argumentsMap.get("file");
            
            // Build the mathjs expression to generate random uniform
            // distribution
            StringBuilder unifRandomSb = new StringBuilder();
            unifRandomSb.append("R.write(");
            unifRandomSb.append(file);
            unifRandomSb.append(", ");
            unifRandomSb.append(data.trim());
            unifRandomSb.append(".toString())");
            
            // Replace the R matrix expression by the current matrix js
            // expression
            StringBuilder sb = new StringBuilder();
            sb.append(result.substring(0, startIndex));
            sb.append(unifRandomSb.toString());
            sb.append(result.substring(endIndex + 1));
            result = sb.toString();
            
            // Search the next "array"
            rFunctionArgumentsDTO = getFunctionArguments(result, "write__csv");
        }
        
        return result;
    }
    
    /**
     * Convert the R expression of data.frame to js object.
     *
     *
     * @param expr - the expression containing the function to replace
     * @return the expression with replaced function
     */
    private String createDataFrame(String expr) {
        
        String result = expr;
        
        RFunctionArgumentsDTO rFunctionArgumentsDTO = getFunctionArguments(expr, "data__frame");
        
        while (rFunctionArgumentsDTO != null) {
            
            int startIndex = rFunctionArgumentsDTO.getStartIndex();
            int endIndex = rFunctionArgumentsDTO.getStopIndex();
            
            StringBuilder dataFrameSb = new StringBuilder();
            dataFrameSb.append("{");
            
            Map<String, String> argumentsMap = rFunctionArgumentsDTO.getGroups();
            for (Map.Entry<String, String> entry : argumentsMap.entrySet()) {
                String key = entry.getKey();
                String value = entry.getValue();
                if(key.equals(value))
                    dataFrameSb.append("'");
                dataFrameSb.append(key);
                if(key.equals(value))
                    dataFrameSb.append("'");
                dataFrameSb.append(":");
                dataFrameSb.append(value);
                dataFrameSb.append(",");
            }
            
            dataFrameSb.replace(dataFrameSb.length()-1, dataFrameSb.length(), "}");

            
            // Replace the R matrix expression by the current matrix js
            // expression
            StringBuilder sb = new StringBuilder();
            sb.append(result.substring(0, startIndex));
            sb.append(dataFrameSb.toString());
            sb.append(result.substring(endIndex + 1));
            result = sb.toString();
            
            List<String> newQuoteList = replaceQuotesByVariables(result,quotesList.size());
            result = newQuoteList.get(0);
            for(int i=1; i<newQuoteList.size(); i++) {
                quotesList.add(newQuoteList.get(i));
            }
            
            // Search the next "array"
            rFunctionArgumentsDTO = getFunctionArguments(result, "data__frame");
        }
        
        return result;
    }
    
        /**
     * Convert the R expression of list to js object.
     *
     *
     * @param expr - the expression containing the function to replace
     * @return the expression with replaced function
     */
    private String createList(String expr) {
        String result = expr;
        
        RFunctionArgumentsDTO rFunctionArgumentsDTO = getFunctionArguments(expr, "list");
        
        while (rFunctionArgumentsDTO != null) {
            
            int startIndex = rFunctionArgumentsDTO.getStartIndex();
            int endIndex = rFunctionArgumentsDTO.getStopIndex();
            
            StringBuilder dataFrameSb = new StringBuilder();
            dataFrameSb.append("{");
            
            Map<String, String> argumentsMap = rFunctionArgumentsDTO.getGroups();
            for (Map.Entry<String, String> entry : argumentsMap.entrySet()) {
                String key = entry.getKey();
                String value = entry.getValue();
                if(key.equals(value))
                    dataFrameSb.append("'");
                dataFrameSb.append(key);
                if(key.equals(value))
                    dataFrameSb.append("'");
                dataFrameSb.append(":");
                dataFrameSb.append(value);
                dataFrameSb.append(",");
            }
            
            dataFrameSb.replace(dataFrameSb.length()-1, dataFrameSb.length(), "}");

            
            // Replace the R matrix expression by the current matrix js
            // expression
            StringBuilder sb = new StringBuilder();
            sb.append(result.substring(0, startIndex));
            sb.append(dataFrameSb.toString());
            sb.append(result.substring(endIndex + 1));
            result = sb.toString();
            
            List<String> newQuoteList = replaceQuotesByVariables(result,quotesList.size());
            result = newQuoteList.get(0);
            for(int i=1; i<newQuoteList.size(); i++) {
                quotesList.add(newQuoteList.get(i));
            }
            
            // Search the next "array"
            rFunctionArgumentsDTO = getFunctionArguments(result, "list");
        }
        if (result.startsWith("{") && result.endsWith("}")) 
            result = "("+result+")"; //hack to avoid passing "{a:1,b:2}" to javascript, which is then misinterpredted as an expression, and not as a list declaration.
        return result;
    }
    
    /**
     * @param expr - the expression containing the function to replace
     * @return the expression with replaced function
     */
    private static String createMatrix(String expr) {
        
        String result = expr;
        
        RFunctionArgumentsDTO rFunctionArgumentsDTO = getFunctionArguments(expr, "matrix");
        
        while (rFunctionArgumentsDTO != null) {
            
            int startIndex = rFunctionArgumentsDTO.getStartIndex();
            int endIndex = rFunctionArgumentsDTO.getStopIndex();
            Map<String, String> argumentsMap = rFunctionArgumentsDTO.getGroups();
            
            String data = argumentsMap.get("data");
            String nrow = argumentsMap.get("nrow");
            String ncol = argumentsMap.get("ncol");
            String byrow = argumentsMap.get("byrow");
            
            // Build the mathjs expression to create an array/matrix
            StringBuilder arraySb = new StringBuilder();
            
            if (nrow == null && ncol == null) {
                // We just create an array nrow and ncol are null
                nrow = "math.prod(dim(" + data + "))";
                ncol = "1";
            } else if (nrow != null) {
                if (ncol == null) {
                    // compute the number of columns
                    ncol = "math.ceil(math.dotDivide(math.prod(dim(" + data + ")), " + nrow + "))";
                }
            } else if (ncol != null) {
                // compute the number of rows
                nrow = "math.ceil(math.dotDivide(math.prod(dim(" + data + ")), " + ncol + "))";
            }
            
            // Create the array containing the dimension of the result matrix
            StringBuilder stringDimArraybuilder = new StringBuilder();
            stringDimArraybuilder.append("dim = [");
            stringDimArraybuilder.append(nrow);
            stringDimArraybuilder.append(", ");
            stringDimArraybuilder.append(ncol);
            stringDimArraybuilder.append("]");
            String stringDimArray = stringDimArraybuilder.toString();
            
            if (byrow == null || byrow.equals("false")) {
                arraySb.append("math.transpose(math.reshape(R.expendArray(");
                arraySb.append(data);
                arraySb.append(", math.prod(");
                arraySb.append(stringDimArray);
                arraySb.append(")), ");
                arraySb.append(stringDimArray);
                arraySb.append(".reverse()))");
            } else {
                arraySb.append("math.reshape(R.expendArray(");
                arraySb.append(data);
                arraySb.append(", math.prod(");
                arraySb.append(stringDimArray);
                arraySb.append(")), ");
                arraySb.append(stringDimArray);
                arraySb.append(")");
            }
            
            // Replace the R matrix expression by the current matrix js
            // expression
            StringBuilder sb = new StringBuilder();
            sb.append(result.substring(0, startIndex));
            sb.append(arraySb);
            sb.append(result.substring(endIndex + 1));
            result = sb.toString();
            
            // Search the next "array"
            rFunctionArgumentsDTO = getFunctionArguments(result, "matrix");
        }
        
        return result;
    }
    
    /**
     * Convert the R expression: array(c(1, 2, 3),dim = c(3,3,2)) to js array
     *
     * FIXME: this function doesn't work with more than 2 dimensions FIXME: this
     * function doesn't work recursively ( "array(array(...))") FIXME: this
     * function doesn't work with variables !!!
     *
     * @param expr - the expression containing the function to replace
     * @return the expression with replaced function
     */
    private static String createArray(String expr) {
        
        String result = expr;
        
        RFunctionArgumentsDTO rFunctionArgumentsDTO = getFunctionArguments(expr, "array");
        
        while (rFunctionArgumentsDTO != null) {
            
            int startIndex = rFunctionArgumentsDTO.getStartIndex();
            int endIndex = rFunctionArgumentsDTO.getStopIndex();
            Map<String, String> argumentsMap = rFunctionArgumentsDTO.getGroups();
            
            // Array containing data
            String stringArray = argumentsMap.get("data");
            
            // Array containing dimension
            String stringDimArray = argumentsMap.get("dim");
            
            // Build the mathjs expression to create an array/matrix
            StringBuilder arraySb = new StringBuilder();
            
            // If the field 'dim' is not null
            if (stringDimArray != null) {
                arraySb.append("math.transpose(math.reshape(R.expendArray(");
                arraySb.append(stringArray);
                arraySb.append(", math.prod(");
                arraySb.append(stringDimArray);
                arraySb.append(")), ");
                arraySb.append(stringDimArray);
                arraySb.append(".reverse()))");
            } else {
                // Else we flat the matrix
                arraySb.append("math.reshape(");
                arraySb.append(stringArray);
                arraySb.append(", math.multiply(math.ones(1),math.prod(dim(");
                arraySb.append(stringArray);
                arraySb.append("))))");
            }
            
            // Replace the R matrix expression by the current matrix js
            // expression
            StringBuilder sb = new StringBuilder();
            sb.append(result.substring(0, startIndex));
            sb.append(arraySb);
            sb.append(result.substring(endIndex + 1));
            result = sb.toString();
            
            // Search the next "array"
            rFunctionArgumentsDTO = getFunctionArguments(result, "array");
        }
        
        return result;
    }
    
    
    /**
     * This function replaces the R function save by JS equivalent
     * It writes in file the variable with its value sperated by ':' in the file (example: "variable:value")
     * WARNING the function works only if the variable to save is between quotes
     *
     * @param expr - the expression containing the function to replace
     * @return the expression with replaced function
     */
    private String createSaveFunction(String expr) {
        
        String result = expr;
        
        RFunctionArgumentsDTO rFunctionArgumentsDTO = getFunctionArguments(expr, "save");
        
        while (rFunctionArgumentsDTO != null) {
            
            int startIndex = rFunctionArgumentsDTO.getStartIndex();
            int endIndex = rFunctionArgumentsDTO.getStopIndex();
            Map<String, String> argumentsMap = rFunctionArgumentsDTO.getGroups();
            
            String listString = argumentsMap.get("list");
            String fileString = argumentsMap.get("file");
            
            String listStringUnquotted = listString.replace("\'", "");
            
            // Build the mathjs expression to create an array/matrix
            StringBuilder saveSb = new StringBuilder();
            
            saveSb.append("R.write(");
            saveSb.append(fileString);
            saveSb.append(", ");
            saveSb.append("R.createJsonString(");
            saveSb.append(listStringUnquotted);
            saveSb.append(", '");
            saveSb.append(jsEnvName);
            saveSb.append("'))");
            
            // Replace the R matrix expression by the current matrix js
            // expression
            StringBuilder sb = new StringBuilder();
            sb.append(result.substring(0, startIndex));
            sb.append(saveSb);
            sb.append(result.substring(endIndex + 1));
            result = sb.toString();
            
            // Search the next "save" in the expression
            rFunctionArgumentsDTO = getFunctionArguments(result, "save");
        }
        
        return result;
    }
    
    /**
     * Load variables in a json
     *
     * @param expr - the expression containing the function to replace
     * @return the expression with replaced function
     */
    private String createLoadFunction(String expr) {
        
        String result = expr;
        
        RFunctionArgumentsDTO rFunctionArgumentsDTO = getFunctionArguments(expr, "load");
        
        while (rFunctionArgumentsDTO != null) {
            
            int startIndex = rFunctionArgumentsDTO.getStartIndex();
            int endIndex = rFunctionArgumentsDTO.getStopIndex();
            Map<String, String> argumentsMap = rFunctionArgumentsDTO.getGroups();
            
            String fileString = argumentsMap.get("file");
            
            // Add all loaded variables to the java list of variables
            try {
                String readVariablesExpr = replaceNameByQuotes(quotesList,"R.readJsonVariables(" + fileString + ")", false);
                String[] loadedVariables = (String[])cast(engine.eval(readVariablesExpr));
                addGlobalVariables(loadedVariables);
            } catch (ScriptException ex) {
                Logger.getLogger(R2jsSession.class.getName()).log(java.util.logging.Level.SEVERE, null, ex);
            }
            
            // Build the mathjs expression to create load data
            StringBuilder loadSb = new StringBuilder();
            loadSb.append("R.loadJson(");
            loadSb.append(fileString);
            loadSb.append(", '");
            loadSb.append(jsEnvName);
            loadSb.append("')");
            
            // TODO add loaded function as global variables: storeGlobalVariables(String expr)
            
            // Replace the R load expression by the current load js
            // expression
            StringBuilder sb = new StringBuilder();
            sb.append(result.substring(0, startIndex));
            sb.append(loadSb);
            sb.append(result.substring(endIndex + 1));
            result = sb.toString();
            
            // Search the next "load"
            rFunctionArgumentsDTO = getFunctionArguments(result, "load");
        }
        
        return result;
    }
    
    /**
     * This function replaces the R function length by JS equivalent with "R.size()" 
     * function from library mathjs.
     * WARNING: "deparse.level", "make.row.names" and "stringsAsFactors" arguments are not supported yet
     *
     * @param expr - the expression containing the function to replace
     * @return the expression with replaced function
     */
    private static String createLengthFunction(String expr) {
        
        String result = expr;
        
        RFunctionArgumentsDTO rFunctionArgumentsDTO = getFunctionArguments(expr, "length");
        
        while (rFunctionArgumentsDTO != null) {
            
            int startIndex = rFunctionArgumentsDTO.getStartIndex();
            int endIndex = rFunctionArgumentsDTO.getStopIndex();
            Map<String, String> argumentsMap = rFunctionArgumentsDTO.getGroups();
            
            String values = argumentsMap.get("default");
            
            // Build the mathjs expression
            StringBuilder rbindSb = new StringBuilder();
            
            rbindSb.append("math.squeeze(math.subset(dim(");
            rbindSb.append(values);
            rbindSb.append("), math.index(0)))");
            
            // Replace the R cbind expression by the current js expression
            StringBuilder sb = new StringBuilder();
            sb.append(result.substring(0, startIndex));
            sb.append(rbindSb);
            sb.append(result.substring(endIndex + 1));
            result = sb.toString();
            
            // Search the next "cbind" in the expression
            rFunctionArgumentsDTO = getFunctionArguments(result, "length");
        }
        
        return result;
    }
    
    /**
     * This function replaces the R function file.exist in JavaScript
     *
     * @param expr - the expression containing the function to replace
     * @return the expression with replaced function
     */
    private static String createFileExistsFunction(String expr) {
        
        String result = expr;
        
        RFunctionArgumentsDTO rFunctionArgumentsDTO = getFunctionArguments(expr, "file__exists");
        
        while (rFunctionArgumentsDTO != null) {
            
            int startIndex = rFunctionArgumentsDTO.getStartIndex();
            int endIndex = rFunctionArgumentsDTO.getStopIndex();
            Map<String, String> argumentsMap = rFunctionArgumentsDTO.getGroups();
            
            String values = argumentsMap.get("default");
            
            // Build the mathjs expression
            StringBuilder fileExistSb = new StringBuilder();
            
            fileExistSb.append("R.fileExists(");
            fileExistSb.append(values);
            fileExistSb.append(")");
            
            // Replace the R file.exist expression by the current js expression
            StringBuilder sb = new StringBuilder();
            sb.append(result.substring(0, startIndex));
            sb.append(fileExistSb);
            sb.append(result.substring(endIndex + 1));
            result = sb.toString();
            
            // Search the next "file.exist" in the expression
            rFunctionArgumentsDTO = getFunctionArguments(result, "file__exists");
        }
        
        return result;
    }
    
    /**
     * This function replaces the R function exists in JavaScript
     * WARNING: arguments('where', 'envir', 'frame', 'mode' and 'inherits') are not supported yet and ignored
     *
     * @param expr - the expression containing the function to replace
     * @return the expression with replaced function
     */
    private String createExistsFunction(String expr) {
        
        String result = expr;
        
        RFunctionArgumentsDTO rFunctionArgumentsDTO = getFunctionArguments(expr, "exists");
        
        while (rFunctionArgumentsDTO != null) {
            
            int startIndex = rFunctionArgumentsDTO.getStartIndex();
            int endIndex = rFunctionArgumentsDTO.getStopIndex();
            Map<String, String> argumentsMap = rFunctionArgumentsDTO.getGroups();
            
            String variable = argumentsMap.get("default");
            
            // Build the mathjs expression
            StringBuilder fileExistSb = new StringBuilder();
            
            fileExistSb.append(jsEnvName);
            fileExistSb.append(".hasOwnProperty(");
            fileExistSb.append(variable);
            fileExistSb.append(")");
            
            // Replace the R exists expression by the current js expression
            StringBuilder sb = new StringBuilder();
            sb.append(result.substring(0, startIndex));
            sb.append(fileExistSb);
            sb.append(result.substring(endIndex + 1));
            result = sb.toString();
            
            // Search the next "exists" in the expression
            rFunctionArgumentsDTO = getFunctionArguments(result, "exists");
        }
        
        return result;
    }

    /**
     * This function replace stopifnot(...) by R.stopIfNot('...')
     *
     * @param expr - the expression containing the function to replace
     * @return the expression with replaced function
     */
    private static String createStopIfNotFunction(String expr) {
        
        String result = expr;
        
        RFunctionArgumentsDTO rFunctionArgumentsDTO = getFunctionArguments(expr, "stopifnot");
        
        while (rFunctionArgumentsDTO != null) {
            
            int startIndex = rFunctionArgumentsDTO.getStartIndex();
            int endIndex = rFunctionArgumentsDTO.getStopIndex();
            Map<String, String> argumentsMap = rFunctionArgumentsDTO.getGroups();
            
            String values = argumentsMap.get("default");
            
            // Build the mathjs expression
            StringBuilder fileExistSb = new StringBuilder();
            
            fileExistSb.append("R.stopIfNot(");
            fileExistSb.append(values);
            fileExistSb.append(",'");
            fileExistSb.append(values);
            fileExistSb.append("')");
            
            // Replace the R file.exist expression by the current js expression
            StringBuilder sb = new StringBuilder();
            sb.append(result.substring(0, startIndex));
            sb.append(fileExistSb);
            sb.append(result.substring(endIndex + 1));
            result = sb.toString();
            
            // Search the next "stopifnot" in the expression
            rFunctionArgumentsDTO = getFunctionArguments(result, "stopifnot");
        }
        
        return result;
    }
    
    /**
     * This function replace f = function(x=1, y=2) by 
     * f = function(x = typeof x != 'undefined' ? x : 1, y = typeof y != 'undefined' ? y : 2)
     *
     * 
     * @param expr - the expression containing the function to replace
     * @return the expression with replaced function
     */
    private static String createPredefinedFunction(String expr) {
        
        String result = expr;
        String functionReplacementString = "__FUNCTION_REPLACEMENT";
        
        RFunctionArgumentsDTO rFunctionArgumentsDTO = getFunctionArguments(expr, "function");
        
        while (rFunctionArgumentsDTO != null) {
            
            int startIndex = rFunctionArgumentsDTO.getStartIndex();
            int endIndex = rFunctionArgumentsDTO.getStopIndex();
            Map<String, String> argumentsMap = rFunctionArgumentsDTO.getGroups();
            
            
            // Build the mathjs expression
            StringBuilder functionSb = new StringBuilder();
            functionSb.append(functionReplacementString);
            functionSb.append("(");
            
            for(Map.Entry<String, String> entry : argumentsMap.entrySet()) {
                String key = entry.getKey();
                String value = entry.getValue();
                
                functionSb.append("(");
                functionSb.append(key);
                functionSb.append(" = typeof ");
                functionSb.append(key);
                functionSb.append(" !== 'undefined') ? ");
                functionSb.append(key);
                functionSb.append(" : ");
                functionSb.append(value);
                functionSb.append(",");
                
            }
            
            functionSb = functionSb.deleteCharAt(functionSb.length() -1);
            functionSb.append(");");
            
            // Replace the R file.exist expression by the current js expression
            StringBuilder sb = new StringBuilder();
            sb.append(result.substring(0, startIndex));
            sb.append(functionSb);
            sb.append(result.substring(endIndex + 1));
            result = sb.toString();
            
            // Search the next "function" in the expression
            rFunctionArgumentsDTO = getFunctionArguments(result, "function");
        }
        
        result = result.replaceAll(functionReplacementString, "function");
        
        return result;
    }
    
    
    /**
     * Get the beginning, the ending and arguments of a function. This function search for the first occurence
     * of "fctName" in the "expr" and return a DTO containing all these informations.
     * If an argument field is not informed, a default field will be affected.
     *
     * @param expr : the expression where we search the function
     * @param fctName : the name of the wanted function
     * @return a DTO containing: start index , end index and arguments of the function found
     */
    private static RFunctionArgumentsDTO getFunctionArguments(String expr, String fctName) {
        
        // Map containing possible arguments associated to each R functions
        Map<String, List<String>> argumentNamesByFunctions = new HashMap<>();
        argumentNamesByFunctions.put("array", Arrays.asList("data", "dim", "dimnames"));
        argumentNamesByFunctions.put("vector", Arrays.asList("mode", "length"));
        argumentNamesByFunctions.put("matrix", Arrays.asList("data", "nrow", "ncol", "byrow", "dimnames"));
        argumentNamesByFunctions.put("c", Arrays.asList("data", "dim", "dimnames"));
        argumentNamesByFunctions.put("ls", Arrays.asList("name", "pos", "envir", "all.names", "pattern", "sorted"));
        argumentNamesByFunctions.put("save", Arrays.asList("list", "file", "ascii", "all.names", "pattern", "sorted"));
        argumentNamesByFunctions.put("load", Arrays.asList("file", "envir", "verbose"));
        argumentNamesByFunctions.put("write__csv", Arrays.asList("data", "file", "row.names", "col.names", "sep", "na"));
        argumentNamesByFunctions.put("data__frame", new ArrayList<String>());
        argumentNamesByFunctions.put("list", new ArrayList<String>());
        argumentNamesByFunctions.put("cbind", Arrays.asList("default", "deparse.level", "make.row.names", "stringsAsFactors"));
        argumentNamesByFunctions.put("rbind", Arrays.asList("default", "deparse.level", "make.row.names", "stringsAsFactors"));
        argumentNamesByFunctions.put("length", Arrays.asList("default"));
        argumentNamesByFunctions.put("file__exists", Arrays.asList("default"));
        argumentNamesByFunctions.put("exists", Arrays.asList("default", "where", "envir", "mode", "frame","inherits"));
        argumentNamesByFunctions.put("stopifnot", Arrays.asList("default"));   
        argumentNamesByFunctions.put("function", Arrays.asList("default"));  

        RFunctionArgumentsDTO rFunctionArgumentsDTO = null;
        
        List<String> argumentNamesList = new ArrayList<>(argumentNamesByFunctions.get(fctName));
        
        Map<String, String> argumentNamesAndValues = new HashMap<>();
        
        Pattern pattern = Pattern.compile("(\\b)" + fctName + "\\(");
        Matcher matcher = pattern.matcher(expr);
        
        // If an occurrence has been found
        if (matcher.find()) {
            
            int startIndex = matcher.start();
            int currentIndex = startIndex + fctName.length() + 1;
            
            while (expr.charAt(currentIndex - 1) != ')') {
                int argumentEndIndex = getNextExpressionLastIndex(expr, currentIndex - 1, ",=");
                // Ignore if it is a comparison operator ( '!=', '<=', '>=' or '==')
                if (expr.charAt(argumentEndIndex + 1) == '=' && ("!=<>".contains(""+expr.charAt(argumentEndIndex)) || expr.charAt(argumentEndIndex + 2)=='=')) {
                    argumentEndIndex = getNextExpressionLastIndex(expr, argumentEndIndex+1, ",=");
                }                
		String argumentName = null;
                String argument = null;
                if (expr.charAt(argumentEndIndex + 1) == '=') {
                    argumentName = expr.substring(currentIndex, argumentEndIndex + 1).trim();
                    currentIndex = argumentEndIndex + 2;
                    argumentEndIndex = getNextExpressionLastIndex(expr, currentIndex - 1, ",=");
                }
                
                if (argumentName == null) {
                    if(argumentNamesList.size()>0) {
                        // If argument has no "name", we take the first of the list
                        argumentName = argumentNamesList.get(0);
                        // And we remove it from the list unless the name is "default"
                        if(!argumentName.equals("default"))
                            argumentNamesList.remove(0);
                    }
                } else {
                    // Remove the current argument name from the list
                    boolean removed = argumentNamesList.remove(argumentName);
                    if (!removed) {
                        Log.Err.println("Unknown argument:" + argumentName + " in function: " + fctName);
                    }
                }
                
                argument = expr.substring(currentIndex, argumentEndIndex + 1);
                
                // If there is not argument name and the list argumentNamesList is empty, the argumentName is the name of argument
                if(argumentName == null) {
                    argumentName = argument;
                }
                
                // If the map already contains the argumentName, we add the new argument after and separated with comma
                if(argumentNamesAndValues.containsKey(argumentName)){
                    argumentNamesAndValues.put(argumentName, argumentNamesAndValues.get(argumentName) + "," + argument);
                } else {
                    argumentNamesAndValues.put(argumentName, argument);
                }
                
                currentIndex = argumentEndIndex + 2;
            }
            rFunctionArgumentsDTO = new RFunctionArgumentsDTO(startIndex, currentIndex - 1, argumentNamesAndValues);
        }
        
        return rFunctionArgumentsDTO;
    }
    
    /**
     * Replace '+' operator by the math.add() operator. To do that we need to
     * find what are the expressions to add, they can contains '(' or ')' This
     * function start by replacing priority operators '*' and '/' and then
     * replace '+' and '-'
     *
     *
     * @param expr - the expression containing operators to replace
     * @return the expression with replaced operators
     */
    private static String replaceOperators(String expr) {
        
        String previousStoppingCharacters = "=*/^;%+:,>< \n";
        String nextStoppingCharacters = "=*+/^%;:,>< \n";
        
        expr = expr.replaceAll("(\\*|/) *-", "$1-");
        expr = expr.replaceAll("-", " -");
        
        Map<String, String> operatorsMap = new HashMap<>();
        operatorsMap.put(">", "R._gt");
        operatorsMap.put("<", "R._lt");
        operatorsMap.put(">=", "R._get");
        operatorsMap.put("<=", "R._let");
        operatorsMap.put("==", "R._eq");
        operatorsMap.put("+", "math.add");
        operatorsMap.put("-", "math.subtract");
        operatorsMap.put("*", "math.dotMultiply");
        operatorsMap.put("/", "math.dotDivide");
        operatorsMap.put("%*%", "math.multiply");
        operatorsMap.put("%/%", "math.floor(math.dotDivide");
        operatorsMap.put("%%", "math.mod");
        operatorsMap.put(":", "R.range");
        operatorsMap.put("^", "math.dotPow");

        String[] operators = new String[] {"^", "*/%:", "+-><" };

        // replace '^' first then replace '*','/','%' and ':' and finaly replace '+' and '-'
        int priority = 0;
        
        boolean continueReplacing = true;
        
        int i = 0;
        while (continueReplacing) {
            char currentChar = expr.charAt(i);
            
            // Ignore operators inside quotes
            if (currentChar == '\'') {
                i++;
                currentChar = expr.charAt(i);
                while (i < expr.length() && currentChar != '\'') {
                    currentChar = expr.charAt(i);
                    i++;
                }
            }
            
            // If the character is a supported operator
            if (operators[priority].indexOf(currentChar) >= 0) {
                
                // if the character is '%', it's a 3 character operator like
                // "%*%" or "%/%", or the mod operator "%%"
                if (currentChar == '%') {
                    
                    int nbChar = 3;
                    // If it is the mod operator "%%"
                    if (expr.charAt(i + 1) == '%') {
                        nbChar = 2;
                    }
                    
                    // Find the beginning of the left term
                    int startingIndex = getPreviousExpressionFirstIndex(expr, i, previousStoppingCharacters);
                    String prevExp = expr.substring(startingIndex, i);
                    
                    // If the left expression is not only whitespace (for
                    // example a = -4 or a = +4)
                    if (prevExp.trim().length() > 0) {
                        
                        // Find the end of the right term
                        int endingIndex = getNextExpressionLastIndex(expr, i + nbChar - 1, nextStoppingCharacters);
                        
                        String operatorName = operatorsMap.get(expr.substring(i, i + nbChar));
                        StringBuilder resultExpr = new StringBuilder();
                        resultExpr.append(operatorName);
                        resultExpr.append("(");
                        resultExpr.append(prevExp);
                        resultExpr.append(",");
                        resultExpr.append(expr.substring(i + nbChar, endingIndex + 1));
                        resultExpr.append(")");
                        
                        // Add a parenthesis if the operator is "%/%" because we
                        // add floor(..)
                        if (expr.charAt(i + 1) == '/') {
                            resultExpr.append(")");
                        }
                        
                        expr = expr.substring(0, startingIndex) + resultExpr
                                + expr.substring(endingIndex + 1, expr.length());
                        
                        // Decrement i to be sure to not miss an operator
                        i = startingIndex - 1;
                    }
                }
                // if the next character is not and operator or "=" (not supported yet)
                else if (!("+*/=".indexOf(expr.charAt(i + 1)) >= 0)) { // WHY not "-" also ?
                    
                    // Find the beginning of the left term
                    int startingIndex = getPreviousExpressionFirstIndex(expr, i, previousStoppingCharacters);
                    String prevExp = expr.substring(startingIndex, i);
                    
                    if(!prevExp.trim().equals("return")) {
                        
                        // If the left expression is not only whitespace (for
                        // example a = -4 or a = +4)
                        if (prevExp.trim().length() > 0) {

                            // Find the end of the right term
                            int endingIndex = getNextExpressionLastIndex(expr, i, nextStoppingCharacters);

                            String operatorName = operatorsMap.get(currentChar + "");
                            StringBuilder resultExpr = new StringBuilder();
                            // Add a "+" operator before:
                            // Example: 1*2 -5*6 will be
                            //          mult(1,2) + mult(-5,6) with a '+' between the two mult operators
                            resultExpr.append(" + "); 
                            resultExpr.append(operatorName);
                            resultExpr.append("(");
                            resultExpr.append(prevExp);
                            resultExpr.append(",");
                            resultExpr.append(expr.substring(i + 1, endingIndex + 1));
                            resultExpr.append(")");

                            expr = expr.substring(0, startingIndex) + resultExpr
                                    + expr.substring(endingIndex + 1, expr.length());

                            //Remove + operator if it is after a "return, if, else, (, [, {,),],},=,+,-
                            expr = expr.replaceAll("(return|if|else|\\(|\\{|\\|[\\|]|\\}|=|,|<|>) *\\+", "$1");
                            expr = expr.replaceAll("\\+ +\\+", "+");
                            expr = expr.replaceAll("\\- +\\+", "-");
                            expr = expr.replaceAll("\\* +\\+", "*");
                            expr = expr.replaceAll("\\/ +\\+", "/");
                            expr = expr.replaceAll("\\: +\\+", ":");
                            expr = expr.replaceAll("\\^ +\\+", "^");
                            expr = expr.replaceAll("^ *\\+", "");

                            // Decrement i to be sure to not miss an operator
                            i = startingIndex - 1;
                        }
                    }
                    
                } else {
                    i = i + 1;
                }
                
            }
            i = i + 1;
            
            if (i >= expr.length()) {
                if (priority < 2) {
                    priority++;
                    if(priority == 2)
                        nextStoppingCharacters+="-";
                    i = 0;
                } else {
                    continueReplacing = false;
                }
            }
        }
        
        return expr;
    }
    
    private void addGlobalVariables(String[] variables) {
        if (variablesSet == null) {
            variablesSet = new HashSet<String>();
        }
        for (String variable : variables) {
            variablesSet.add(variable);
        }
    }

    /**
     * Store global variables in the List variablesList.
     *
     * @param expr - the expression containing global variables
     */
    private void storeGlobalVariables(String expr) {
        
        if (variablesSet == null) {
            variablesSet = new HashSet<String>();
        }
        
        int equalIndex = getNextExpressionLastIndex(expr, -1, "=") + 1;
        int exprLength = expr.length();
        while (equalIndex < exprLength) {
            
            
            if((equalIndex>0 && expr.charAt(equalIndex-1)=='=') || (equalIndex<exprLength-1 && expr.charAt(equalIndex+1)=='=')) {
                // If it is a '==' we ignore it
                equalIndex+=1;
            } else {
                int startIndex = getPreviousExpressionFirstIndex(expr, equalIndex, "=*/^;%+,. ");
                String variableName = expr.substring(startIndex, equalIndex).trim();
                if (variableName.matches("\\w+")) {
                    variablesSet.add(variableName);
                }
            }

            // Get the next '=' character which is not in a parenthesis or
            // bracket
            equalIndex = getNextExpressionLastIndex(expr, equalIndex, "=") + 1;
        }
    }
    
    /**
     * Get the index of the beginning of an expression The function starts at
     * the given startIndex and return the index of the first character founded
     * in the stoppingCharacters string. This function ignore characters inside
     * brackets or parenthesis
     *
     * @param expr - the expression to check
     * @param startIndex
     *            - index to start with to search a stopping characters
     * @param stoppingCharacters
     *            - a String containing stopping characters.
     * @return the starting index of the previous expression
     */
    private static int getPreviousExpressionFirstIndex(String expr, int startIndex, String stoppingCharacters) {
        
        int firstIndex = 0;
        
        int parenthesis = 0; // '(' and ')'
        int brackets = 0; // '{' and '}'
        int brackets2 = 0; // '[' and ']'
        
        // Ignore space character at beginning
        int startingIndex = startIndex - 1;
        while (startingIndex > 0 && expr.charAt(startingIndex) == ' ') {
            startingIndex--;
        }
        
        // Stop at the first stopping character
        for (int i = startingIndex; i >= 0; i--) {
            char currentChar = expr.charAt(i);
            if (currentChar == ')') {
                parenthesis++;
            } else if (currentChar == '(') {
                parenthesis--;
                if (parenthesis < 0) {
                    return i + 1;
                }
            } else if (currentChar == '}') {
                brackets++;
            } else if (currentChar == '{') {
                brackets--;
                if (brackets < 0) {
                    return i + 1;
                }
            } else if (currentChar == ']') {
                brackets2++;
            } else if (currentChar == '[') {
                brackets2--;
                if (brackets2 < 0) {
                    return i + 1;
                }
            } else if (parenthesis == 0 && brackets == 0 && brackets2 == 0
                    && stoppingCharacters.indexOf(currentChar) >= 0) {
                // If it's a stopping character
                return i + 1;
            }
        }
        
        // If there is no stopping character, the expression start at the
        // beginning of the sentence
        return firstIndex;
    }
    
    /**
     * Get the index of the end of an expression The function starts at the
     * given startIndex and return the index of the first character founded in
     * the stoppingCharacters string. This function ignore characters inside
     * brackets or paranthesis
     *
     * @param expr - the expression to check
     * @param startIndex
     *            - index to start with to search a stopping characters
     * @param stoppingCharacters
     *            - a String containing stopping characters.
     * @return the last index of the next expression
     */
    private static int getNextExpressionLastIndex(String expr, int startIndex, String stoppingCharacters) {
        
        int lastIndex = expr.length() - 1;
        
        int parenthesis = 0; // '(' and ')'
        int brackets = 0; // '{' and '}'
        int brackets2 = 0; // '[' and ']'
        
        // Ignore space character at beginning
        int startingIndex = startIndex + 1;
        while (startingIndex < expr.length() && expr.charAt(startingIndex) == ' ') {
            startingIndex++;
        }
        
        // Stop at the first stopping character
        for (int i = startingIndex; i < expr.length(); i++) {
            char currentChar = expr.charAt(i);
            if (currentChar == '(') {
                parenthesis++;
            } else if (currentChar == ')') {
                parenthesis--;
                if (parenthesis < 0) {
                    return i - 1;
                }
            } else if (currentChar == '{') {
                brackets++;
            } else if (currentChar == '}') {
                brackets--;
                if (brackets < 0) {
                    return i - 1;
                }
            } else if (currentChar == '[') {
                brackets2++;
            } else if (currentChar == ']') {
                brackets2--;
                if (brackets2 < 0) {
                    return i - 1;
                }
            } else if (parenthesis == 0 && brackets == 0 && brackets2 == 0
                    && stoppingCharacters.indexOf(currentChar) >= 0) {
                // If it's a stopping character
                return i - 1;
            }
        }
        
        // If there is no stopping character, the expression stop at the
        // end of the sentence
        return lastIndex;
    }
    
    /**
     * This function replace default arguments of R function to interpret them
     * in javascript
     *
     * Default argument (in R: "function(arg = defaultValue) {...}") is defined
     * after the version ES6 of javascript Java8 uses a previous version of
     * javascript so we have to transform this expression to: function(arg) {
     * arg = typeof arg !== 'undefined' ? arg : defaultValue; ...}
     *
     * If an element haven't default value, it will be set to 'null'
     * automatically
     *
     * @param arguments - the R expression with default arguments
     * @return the javascript expression with default arguments
     */
    private static String replaceFunctionDefaultArguments(String arguments) {
        
        // Linked hash map to keep the same order of arguments
        Map<String, String> parametersAndValuesMap = new LinkedHashMap<>();
        
        // Put in map arguments with there values associated
        Matcher matcher = Pattern.compile("([\\w\\-]+) *=* *(([\\w\\-]+))?").matcher(arguments);
        while (matcher.find()) {
            parametersAndValuesMap.put(matcher.group(1), matcher.group(2));
        }
        
        // Result string of arguments (we remove '= value' statement)
        StringBuilder resultParameters = new StringBuilder();
        
        // Result string of inside function
        StringBuilder resultValues = new StringBuilder();
        
        // True if it is the first element of the map
        boolean first = true;
        
        for (Map.Entry<String, String> entry : parametersAndValuesMap.entrySet()) {
            String param = entry.getKey();
            String value = entry.getValue();
            
            if (!first) {
                resultParameters.append(",");
            } else {
                first = false;
            }
            resultParameters.append(param);
            
            resultValues.append(param);
            resultValues.append(" = typeof ");
            resultValues.append(param);
            resultValues.append(" !== 'undefined' ? ");
            resultValues.append(param);
            resultValues.append(" : ");
            resultValues.append(value);
            resultValues.append("; ");
        }
        
        String result = "(" + resultParameters + ") {" + resultValues;
        return result;
    }

    @Override
    public void end() {
        engine = null;       
        super.end();
    }
    
    @Override
    public boolean isAvailable() {
        return true;
    }
    
    @Override
    boolean isWindows() {
        return RserveDaemon.isWindows();
    }

    @Override
    boolean isMacOSX() {
        return RserveDaemon.isMacOSX();
    }

    @Override
    boolean isLinux() {
        return RserveDaemon.isLinux();
    }

    @Override
    protected boolean silentlyVoidEval(String expression, boolean tryEval) {

        String jsExpr = convertRtoJs(expression);
        try {
            this.engine.eval(jsExpr);
        } catch (ScriptException e) {
            String ls = "?";
            try {
                ls = (this.engine.eval("JSON.stringify(" + jsEnvName + ")")).toString();
            } catch (Exception ee) {
                ls = ee.getMessage();
            }
            log("Failed to evaluate code\n```{js}\n"+convertRtoJs(expression)+"\n```\n with variables: "+ls+"\n because: "+e.getMessage(),Level.ERROR);
            return false;
        }

        return true;
    }

    @Override
    protected Object silentlyRawEval(String expression, boolean tryEval) {

        Object result = null;
        String jsExpr = convertRtoJs(expression);
        if (jsExpr != null) {
            try {
                result = this.engine.eval(jsExpr);
            } catch (ScriptException e) {
                e.printStackTrace();
                String ls = "?";
                try {
                    ls = (String) this.engine.eval("JSON.stringify(" + jsEnvName + ")").toString();
                } catch (Exception ee) {
                    ls = ee.getMessage();
                }
                log("Failed to evaluate code\n```{js}\n"+convertRtoJs(expression)+"\n```\n with variables: "+ls+"\n because: "+e.getMessage(),Level.ERROR);
                return new RException("Failed to evaluate code\n```{js}\n"+convertRtoJs(expression)+"\n```\n with variables: "+ls+"\n because: "+e.getMessage());
            }
        }
        return result;
    }
    
    @Override
    public boolean set(String varname, double[][] data, String... names) throws RException {
        
        note_code(varname + " <- " + (data==null?"list()":toRcode(data)));
        note_code("names(" + varname + ") <- " + toRcode(names));
        note_code(varname + " <- data.frame(" + varname + ")");

        // RList list = buildRList(data, names);
        // log(HEAD_SET + varname + " <- " + list, Level.INFO);
        varname = nameRtoJs(varname);
        String allnames = "";
        for (int i = 0; i < names.length; i++) {
            names[i] = nameRtoJs(names[i]);
            allnames = allnames + ",'" + names[i] + "'";
        }
        allnames = allnames.substring(1);
        try {
            synchronized (engine) {
                String dim = "[" + data.length + "," + data[0].length + "]";
                String stringMatrix = Arrays.deepToString(data);
                engine.eval(varname + " = math.reshape(" + stringMatrix + ", " + dim + ")");

                engine.eval(jsEnvName + "." + varname + " = " + varname);
                engine.eval(jsEnvName + "." + varname + ".names = [" + allnames + "]");
                variablesSet.add(varname);
            }
        } catch (Exception e) {
            log(HEAD_ERROR + " " + e.getMessage(), Level.ERROR);
            return false;
        }

        return true;
    }

    /**
     * Set R object in R env.
     *
     * @param varname
     *            R object name
     * @param var
     *            R object value
     * @return succeeded ?
     */
    @Override
    public boolean set(String varname, Object var) {

        note_code(varname + " <- " + toRcode(var));
        
        varname = nameRtoJs(varname);
        try {
            synchronized (engine) {
                // FIXME: find a better solution than this
                // For 2d double array, we need to instanciate the matrix with the function "math.reshape"
                // I don't know why but the function math.matrix doesn't create the same js object than math.reshape and
                // the output ScriptMirrorObject is uncastable in java double[][] array and operations on a math.matrix
                // object don't work.
                if (var instanceof double[][]) {
                    double[][] var2DArray = (double[][]) var;
                    String dim = "[" + var2DArray.length + "," + var2DArray[0].length + "]";
                    String stringMatrix = Arrays.deepToString(var2DArray);
                    engine.eval(varname + " = math.reshape(" + stringMatrix + ", " + dim + ")");

                    engine.eval(jsEnvName + "." + varname + " = " + varname);
                    String allnames = "";
                    for (int i = 0; i < var2DArray[0].length; i++) {
                        allnames = allnames + ",'X" + (i + 1) + "'";
                    }
                    allnames = allnames.substring(1);
                    engine.eval(jsEnvName + "." + varname + ".names = [" + allnames + "]");
                    variablesSet.add(varname);
                } else if (var instanceof double[]) {
                    double[] var1DArray = (double[]) var;
                    String dim = "[" + var1DArray.length + ",1]";
                    String stringMatrix = Arrays.toString(var1DArray);
                    engine.eval(varname + " = "+Arrays.toString(var1DArray));

                    engine.eval(jsEnvName + "." + varname + " = " + varname);
                    variablesSet.add(varname);
                } else {
                    engine.put(varname, var);
                    engine.eval(jsEnvName + "." + varname + " = " + varname);
                    variablesSet.add(varname);
                }

            }
        } catch (Exception e) {
            log(HEAD_ERROR + " " + e.getMessage(), Level.ERROR);
            return false;
        }

        return true;
    }

    @Override
    public void source(File file) {
        if (!file.isFile()) {
            throw new IllegalArgumentException("File " + file + " is not reachable.");
        }
        StringBuilder sb = new StringBuilder();
        BufferedReader reader = null;
        InputStreamReader isr = null;
        FileInputStream fis = null;
        String line;

        try {
            fis = new FileInputStream(file);
            isr = new InputStreamReader(fis, "UTF-8");
            reader = new BufferedReader(isr);
            while ((line = reader.readLine()) != null) {
                sb.append(line);
                sb.append("\n");
            }
        } catch (Exception e) {
            e.printStackTrace();
        } finally {
            try {
                fis.close();
                isr.close();
                reader.close();
            } catch (Exception ee) {
                ee.printStackTrace();
            }
        }

        for (String expr : R2jsUtils.parse(sb.toString())) {
            silentlyVoidEval(expr, false);
        }
    }

    /**
     * delete R variables in R env.
     *
     * @param vars R objects names
     * @return well removed ?
     * @throws org.math.R.Rsession.RException Could not do rm
     */
    @Override
    public boolean rm(String... vars) throws RException {
        try {
            synchronized (engine) {
                for(String var : vars) {
                    engine.eval("delete " +jsEnvName + "." + var+ ";");
                    variablesSet.remove(var);
                    engine.eval("delete " + var + ";");
                }
            }
        } catch (Exception e) {
            log(HEAD_ERROR + " " + e.getMessage(), Level.ERROR);
            return false;
        }

        return true;
    }
    
    @Override
    public boolean rmAll() {
        try {
            synchronized (engine) {
                engine.eval("delete " + jsEnvName + ";");
                variablesSet.clear();
                engine.eval("var " + jsEnvName + " = {};");
            }
        } catch (Exception e) {
            log(HEAD_ERROR + " " + e.getMessage(), Level.ERROR);
            return false;
        }

        return true;
    }

    @Override
    public String loadPackage(String pack) {
        throw new UnsupportedOperationException("Cannot load any package in R2Js. Use 'source' for external static content loading.");
    }

    @Override
    public double asDouble(Object o) throws ClassCastException {
        return (double) ScriptUtils.convert(o, double.class);
    }

    @Override
    public double[] asArray(Object o) throws ClassCastException {
        return (double[]) ScriptUtils.convert(o, double[].class);
    }

    @Override
    public double[][] asMatrix(Object o) throws ClassCastException {
        if (o == null) {
            return null;
        }
        if (o instanceof double[][]) {
            return (double[][]) o;
        } else if (o instanceof double[]) {
            return t(new double[][]{(double[]) o});
        } else if (o instanceof Double) {
            return new double[][]{{(double) o}};
        } else /*if (o instanceof Map)*/ {
            double[][] vals = null;
            int i = 0;
            try {
                for (Object k : ((Map) o).keySet()) {
                    double[] v = null;
                    if (o instanceof ScriptObjectMirror)
                        try {
                            v = (double[]) ((ScriptObjectMirror) o).to(double[].class);
                        } catch (Exception ex) {
                            //ex.printStackTrace();
                            throw new ClassCastException("[asMatrix] Cannot cast ScriptObjectMirror list element to double[] " + ((Map) o).get(k) + " for key " + k + " in " + o);
                        }
                    else
                        try {
                            v = (double[]) ((Map) o).get(k);
                        } catch (Exception ex) {
                            //ex.printStackTrace();
                            throw new ClassCastException("[asMatrix] Cannot cast list element to double[] " + ((Map) o).get(k) + " for key " + k + " in " + o);
                        }
                    if (v == null) {
                        throw new ClassCastException("[asMatrix] Cannot get list element as double[] " + ((Map) o).get(k) + " for key " + k + " in " + o);
                    }
                    if (vals == null) {
                        vals = new double[v.length][((Map) o).size()];
                    }
                    for (int j = 0; j < v.length; j++) {
                        vals[j][i] = v[j];
                    }
                    i++;
                }
                return vals;
            } catch (Exception ex) {
                throw new ClassCastException("[asMatrix] Cannot cast Map to matrix: "+ex.getMessage());
            }
        }
    }

    @Override
    public String asString(Object o) throws ClassCastException {
        return (String) ScriptUtils.convert(o, String.class);
    }

    @Override
    public String[] asStrings(Object o) throws ClassCastException {
        return (String[]) ScriptUtils.convert(o, String[].class);
    }

    @Override
    public int asInteger(Object o) throws ClassCastException {
        return (int) ScriptUtils.convert(o, int.class);
    }

    @Override
    public int[] asIntegers(Object o) throws ClassCastException {
        return (int[]) ScriptUtils.convert(o, int[].class);
    }

    @Override
    public boolean asLogical(Object o) throws ClassCastException {
        return (boolean) ScriptUtils.convert(o, boolean.class);
    }

    @Override
    public boolean[] asLogicals(Object o) throws ClassCastException {
        return (boolean[]) ScriptUtils.convert(o, boolean[].class);
    }

    @Override
    public Map asList(Object o) throws ClassCastException {
        return (Map) ScriptUtils.convert(o, Map.class);
    }

    @Override
    public boolean isNull(Object o) {
        if (o==null) return true;
        return Arrays.asList(ls()).contains(o.toString());
    }

    @Override
    public String toString(Object o) {
        // TODO to test
        return o.toString();
    }

    @Override
    public Object cast(Object o) throws ClassCastException {
        // If it's a ScriptObjectMirror, it can be an array or a matrix
        if (o instanceof ScriptObjectMirror) {
            try {
//                System.err.println("// Casting of the ScriptObjectMirror to a double matrix");
                return ((ScriptObjectMirror) o).to(double[][].class);
            } catch (Exception e) {//e.printStackTrace();
            }

            try {
//                 System.err.println("// Casting of the ScriptObjectMirror to a string array");
                String[] stringArray = ((ScriptObjectMirror) o).to(String[].class);

//                 System.err.println("// Check if the String[] array can be cast to a double[] array");
                try {
                    for (String string : stringArray) {
                        Double.valueOf(string);
                    }
                } catch (Exception e) {//e.printStackTrace();
                    // It can't be cast to double[] so we return String[]
                    return stringArray;
                }

//                 System.err.println("// return double[] array");
                return ((ScriptObjectMirror) o).to(double[].class);

            } catch (Exception e) {//e.printStackTrace();
            }

            try {
//                 System.err.println("// Casting of the ScriptObjectMirror to a double array");
                return ((ScriptObjectMirror) o).to(double[].class);
            } catch (Exception e) {//e.printStackTrace();
            }

            try {
//                System.err.println(" // Casting of the ScriptObjectMirror to a list/map");
                Map m = ((ScriptObjectMirror) o).to(Map.class);
                try {
                    return asMatrix(m);
                } catch (ClassCastException c) {
                    //c.printStackTrace();
                    return m;
                }
            } catch (Exception e) {//e.printStackTrace();
            }

            throw new IllegalArgumentException("Impossible to cast object: ScriptObjectMirror");
        } else {
            return o;
        }
    }
    
    @Override
    public String getJsEnv() {return this.jsEnvName;}
    
    @Override
    public void setJsEnv(String jsEnvName) {this.jsEnvName = jsEnvName;};

}
