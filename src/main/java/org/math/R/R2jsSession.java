package org.math.R;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.PrintStream;
import java.lang.reflect.Field;
import java.nio.charset.Charset;
import java.text.DecimalFormat;
import java.text.DecimalFormatSymbols;
import java.util.*;
import java.util.logging.Logger;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import javax.script.ScriptEngine;
import javax.script.ScriptEngineManager;
import javax.script.ScriptException;
import jdk.nashorn.api.scripting.ScriptObjectMirror;
import jdk.nashorn.api.scripting.ScriptUtils;
import org.apache.commons.io.FileUtils;

/**
 * This class evaluate an R expression by parsing it in javascript expression
 * and then evaluate the javascript expression with the Java ScriptEngine. This
 * class uses external javascript library like mathjs to evaluate expressions.
 *
 *
 * ------------------------------ Supported and unsupported syntaxes
 * ------------------------------------------------------------------ ## Basic
 * syntaxes - affectation (=, &lt;-) - mathematical expression (pi, ^, **, &lt;,
 * &lt;=, &gt;, &gt;=, !=, ==, ++, +=, -=, priority of operators, for, for in,
 * if, else, range) ## Functions - All syntaxes of functions in R - recursive
 * functions - Mathematical functions ("abs", "acos", "asin", "atan", "atan2",
 * "ceil", "cos", "exp", "floor", "log", "max", "min", "round", "sin", "sqrt",
 * "tan" ) - NOT SUPPORTED: - function with named argument (ex:
 * "f(y=1.23,x=4.56)") (impossible to support in the ES5) - multiple functions
 * in one command (impossible just write multiple lines) ## Arrays - definition
 * of arrays (c(1,2,3), c(0:4), array(0.0,c(4,3))) - accessor: a[1], -
 * operations (+, -, *, /) - function: length - NOT SUPPORTED: %*%, %/% : create
 * matrices instead of arrays to use theses operations ## Matrices - definition
 * of matrices with: nrow, ncol, byrow - operations (transpose, +, -, *, ^, %*%,
 * %/%, %%) - column and row selection ([1,], [,1], [,], [c(1,2),]) - function
 * supported: determinant, solve, dim - TO SUPPORT: eigen ## DataFrames -
 * constructor: data.frame(first=a,second=b), data.frame('first'=a,'second'=b),
 * data.frame(a,b) - accessor: '$', "[['element']]" NOT SUPPORTED: column
 * extraction "[1:2,]" ## Lists - constructor: list(first=a,second=b),
 * list('first'=a,'second'=b), list(a,b) - accessor: '$', "[['element']]" NOT
 * SUPPORTED: column extraction "[1:2,]", accessor "[[1]]" ## R functions -
 * write.csv - runif - save and load variables - ls - rm variables - cbind (on
 * matrices only) - rbind (on matrices only) - file.exists - savels TO SUPPORT:
 * rnorm, capture.output NOT SUPPORTED: toPNG, asHTML, cbind (array and
 * dataframe), rbind (array and dataframe), multiple imbricated functions
 *
 * -----------------------------------------------------------------------------------------------------------------------------------
 *
 * @author Nicolas Chabalier
 */
public class R2jsSession extends Rsession implements RLog {

    File wdir;

    private static final String[] MATH_FUN_JS = {"abs", "acos", "asin", "atan", "atan2", "ceil", "cos", "exp", "floor",
        "log", "max", "min", "round", "sin", "sqrt", "tan", "sign", "sum", "mean", "median", "std", "var"};
    private static final String[] MATH_CONST_JS = {"pi"};

    // JavaScript libraries used to evaluate expression
    private static final String MATH_JS_FILE = "/org/math/R/math.js";
    private static final String R_JS_FILE = "/org/math/R/R.js";
    private static final String RAND_JS_FILE = "/org/math/R/rand.js";
//    private static final String PLOT_JS_FILE = "/org/math/R/plotly.js";

    public ScriptEngine js;

    private static final String ENVIRONMENT_DEFAULT = "__r2js__";
    private static final String THIS_ENVIRONMENT = "__this__";

    public static final String[] DISABLED_FUNCTIONS = new String[]{"png", "plot", "abline", "rgb", "hist", "pairs", "lines", "points"};

    // Set of global variables declared
    public Set<String> variablesSet;
    public Set<String> functionsSet;

    // List of quotes expression
    private List<String> quotesList;

    // Map containing js libraries already loaded (to not reload them at each instance of R2jsSession)
    private final static Map<String, Object> jsLibraries = new HashMap<>();

    public static R2jsSession newInstance(final RLog console, Properties properties) {
        return new R2jsSession(console, properties);
    }

    public R2jsSession(RLog console, Properties properties) {
        this(console, properties, null);
    }

    /**
     * Default constructor
     *
     * Initialize the Javascript js and load external js libraries
     *
     * @param console - console
     * @param properties - properties
     * @param environmentName - name of the environment
     */
    @SuppressWarnings({"removal","deprecation"})
    public R2jsSession(RLog console, Properties properties, String environmentName) {
        super(console);
        if (environmentName != null) {
            envName = "__" + environmentName + "__";
        } else {
            envName = ENVIRONMENT_DEFAULT;
        }

        variablesSet = new HashSet<>();
        functionsSet = new HashSet<>();

        TRY_MODE_DEFAULT = false;

        ScriptEngineManager manager = new ScriptEngineManager(null);
        js = manager.getEngineByName("JavaScript");
        if (js==null) js = manager.getEngineByName("js");
        if (js==null) js = manager.getEngineByExtension("js");
        if (js==null) js = manager.getEngineByName("nashorn");
        if (js==null) js = manager.getEngineByName("Nashorn");
        if (js==null) js = new jdk.nashorn.api.scripting.NashornScriptEngineFactory().getScriptEngine();
        if (js==null) throw new IllegalArgumentException("Could not load JavaScript ScriptEngine: "+manager.getEngineFactories());

        // Load external js libraries used by the js to evaluate expressions
        try {
            loadJSLibraries();

            for (String f : DISABLED_FUNCTIONS) {
                addReturnNullFunction(f);
            }

            // Instantiate the variables storage object which store all variables defined in the current session
            js.eval("var " + envName + " = math.clone({});");
            //js.eval("var " + envName + " = {};");
            js.eval(THIS_ENVIRONMENT + " = " + envName);
        } catch (ScriptException ex) {
            Logger.getLogger(R2jsSession.class.getName()).log(java.util.logging.Level.SEVERE, null, ex);
        }

        try {
            int rand = Math.round((float) Math.random() * 10000);
            wdir = new File(new File(System.getProperty("RSESSION_HOME",FileUtils.getTempDirectoryPath()), ".R2js"), "" + rand);
            if (!wdir.mkdirs()) {
                wdir = new File(new File(FileUtils.getUserDirectory(), ".R2js"), "" + rand);
                if (!wdir.mkdirs()) {
                    throw new IOException("Could not create directory " +
                    new File(new File(System.getProperty("RSESSION_HOME",FileUtils.getTempDirectoryPath()), ".Renjin"), "" + rand) + 
                    "\n or " + 
                    new File(new File(FileUtils.getUserDirectory(), ".Renjin"), "" + rand));
                }
            }
            eval("setwd('" + toRpath(wdir.getAbsolutePath()) + "')");
            wdir.deleteOnExit();
        } catch (Exception ex) {
            log("Could not use directory: " + wdir + "\n" + ex.getMessage(), Level.ERROR);
        }

        setenv(properties);
    }

    @Override
    void setenv(Properties properties) {
        if (properties != null) {
            for (String p : properties.stringPropertyNames()) {
                if (p != null) {
                    try {
                        log("Setting environment " + p + ": '" + properties.getProperty(p).replaceAll("\\:([^/])(.*)\\@", ":???@") + "'", Level.INFO);
                        boolean done = set(p, properties.getProperty(p));
                        if (!done) {
                            log("Failed setting environment " + p, Level.WARNING);
                        }
                    } catch (Exception ex) {
                        log(ex.getMessage(), Level.WARNING);
                        ex.printStackTrace();
                    }
                }
            }
        }
    }

    public R2jsSession(final PrintStream p, Properties properties) {
        this(p, properties, null);
    }

    public R2jsSession(final PrintStream p, Properties properties, String environmentName) {
        this(new RLogPrintStream(p), properties, environmentName);
    }

    public void addReturnNullFunction(String name) throws ScriptException {
        js.eval("function " + name + "(a,b,c,d,e,f) {return null;}");
        functionsSet.add(name);
    }

    public void addJSFunction(String name, String js) throws ScriptException {
        this.js.eval("function " + name + (js.startsWith("function") ? js.substring("function".length()) : js));
        functionsSet.add(name);
    }

    /**
     * Load external js libraries to evaluate js expresions: - 'math.js' :
     * evaluate all mathematical expressions with numbers, arrays and matrices.
     * - 'r.js': contains various function ( loading and saving files/variables,
     * range function, ...)
     *
     * @throws ScriptException
     */
    private synchronized void loadJSLibraries() throws ScriptException {

        // Loading math.JS
        if (!jsLibraries.containsKey("math")) {
            InputStream mathInputStream = this.getClass().getResourceAsStream(MATH_JS_FILE);
            js.eval(new InputStreamReader(mathInputStream, Charset.forName("UTF-8")));
            jsLibraries.put("math", js.get("math"));
        } else {
            js.put("math", jsLibraries.get("math"));
        }

        js.eval("var parser = math.parser();");
        // Change 'Matrix' mathjs config by 'Array'
        js.eval("math.config({matrix: 'Array'})");
        js.eval("var str = String.prototype;");

        // Loading rand.js
        if (!jsLibraries.containsKey("rand")) {
            InputStream randInputStream = this.getClass().getResourceAsStream(RAND_JS_FILE);
            js.eval(new InputStreamReader(randInputStream, Charset.forName("UTF-8")));
            js.eval("__rand = rand()");
            jsLibraries.put("__rand", js.get("rand"));
        } else {
            js.put("__rand", jsLibraries.get("rand"));
        }

        // Loading plotly.js
//        InputStream RInputStream = this.getClass().getResourceAsStream(PLOT_JS_FILE);
//        js.eval(new InputStreamReader(RInputStream));
//        js.eval("Plotly = Plotly()");
        // Loading R.js
        if (!jsLibraries.containsKey("R")) {
            InputStream RInputStream = this.getClass().getResourceAsStream(R_JS_FILE);
            js.eval(new InputStreamReader(RInputStream, Charset.forName("UTF-8")));
            js.eval("__R = R()");
            jsLibraries.put("__R", js.get("R"));
        } else {
            js.put("__R", jsLibraries.get("R"));
        }
    }

    static final String POINT_CHAR_JS_KEY = "__";

    /**
     * Convert an R expression in a Js expression WARNING: many R syntaxes are
     * not supported yet
     *
     * @param e - the R expression
     * @return the js script expression
     */
    public static String nameRtoJs(String e) {

        // Replace "." char by a dedicated key
        if (e.contains(POINT_CHAR_JS_KEY)) {
            throw new IllegalArgumentException("Cannot use " + POINT_CHAR_JS_KEY + " in expression (reserved substring): "+e);
        }
        return e.replace(".", POINT_CHAR_JS_KEY);
    }

    public static Properties R_TO_JS = new Properties();

    static {
        R_TO_JS.put("R.version.string", "'R2js'");
        R_TO_JS.put(".GlobalEnv", ENVIRONMENT_DEFAULT);
        R_TO_JS.put("stop(", "throw new Error(");
        R_TO_JS.put("as.numeric(", "asNumeric(");
        R_TO_JS.put("as.integer(", "asInteger(");
        R_TO_JS.put("as.logical(", "asLogical(");
        R_TO_JS.put("as.character(", "asCharacter(");
        R_TO_JS.put("as.matrix(", "matrix(");
        R_TO_JS.put("rep_len(", "reLen(");
        R_TO_JS.put("as.array(", "array(");
        R_TO_JS.put("which.min(", "whichMin(");
        R_TO_JS.put("which.max(", "whichMax(");
        R_TO_JS.put("print(", "_print(");
        R_TO_JS.put("is.function(", "isFunction(");
        R_TO_JS.put("is.null(", "isNull(");
        R_TO_JS.put("is.nan(", "isNaN(");
        R_TO_JS.put("is.na(", "isNA(");
        R_TO_JS.put("isTRUE(", "isTRUE(");
        R_TO_JS.put("isFALSE(", "isFALSE(");
        R_TO_JS.put("Sys.sleep(", "SysSleep(");
        R_TO_JS.put("Sys.getenv(", "SysGetEnv(");
        R_TO_JS.put("capture.output(", "_print("); //should use the output stream capture instead...
        R_TO_JS.put("NA", "null");
        R_TO_JS.put("new.env()", "{}");
        R_TO_JS.put("dev.off()", "");
        R_TO_JS.put("return()", "return(NULL)");
        R_TO_JS.put("...", "varargs");
        R_TO_JS.put("Inf", "Infinity");
    }

    final static String AW = "((\\A)|(\\W)|(\\())(";
    final static String Az = ")((\\W)|(\\z)|(\\)))";

    public boolean debug_js = Boolean.parseBoolean(System.getProperty("debug.js", "false"));

    DecimalFormat formatter = new DecimalFormat("#.#############", DecimalFormatSymbols.getInstance(Locale.ENGLISH));


    private void checkBracketsCount(String expression, char openingBracket, char closingBracket) {
        long countOpeningBrackets = expression.chars().filter(ch -> ch == openingBracket).count();
        long countClosingBrackets =  expression.chars().filter(ch -> ch == closingBracket).count();
        if(countOpeningBrackets!=countClosingBrackets) {
            throw new IllegalArgumentException("Not the same number of opening bracket '"+ openingBracket +"' " +
                    "and closing brackets '"+ closingBracket + "' ( " +
                    countOpeningBrackets + "!=" + countClosingBrackets +")");
        }
    }



    /**
     * Check if expression syntax is valid (this test doesn't cover all cases)
     * @param expression - the expression to check
     */
    private void checkExpressionValidity(String expression) throws IllegalArgumentException {

        checkBracketsCount(expression, '(', ')');
        checkBracketsCount(expression, '[', ']');
        checkBracketsCount(expression, '{', '}');

        int commaIndex = expression.indexOf(",");
        if(commaIndex>=0) {
            String endingStoppingCharacters = ")]";
            String startingStoppingCharacters = "([";
            int nextParenthesis = getNextExpressionLastIndex(expression,commaIndex,endingStoppingCharacters);
            int prevParenthesis = getPreviousExpressionFirstIndex(expression, commaIndex, startingStoppingCharacters);
            if(prevParenthesis<=0 && !startingStoppingCharacters.contains(""+expression.charAt(0))) {
                throw new IllegalArgumentException("No opening bracket before ',' at index " + commaIndex);
            }
            if(nextParenthesis>=expression.length()-1 && !endingStoppingCharacters.contains(""+expression.charAt(expression.length()-1))) {
                throw new IllegalArgumentException("No closing bracket after ',' at index " + commaIndex);
            }
        }

        comaSyntaxCheck(expression);

    }

    private void comaSyntaxCheck(String e) throws IllegalArgumentException {
        // Throw exception for wrong syntaxes with coma ex: 2*(1,2) instead of 2*(1.2)
        Matcher m = Pattern.compile("(^|([=*\\+-<>()&%!^\\/\\s]\\b([^a-zA-Z_$,=*+\\-<()>&%!^\\/\\s])[a-zA-Z0-9_]*)|[=*+\\-<()>&%!^])[(](.[^,)(]*),(.[^,)(]*)[)]").matcher(e);
        if(m.find()) {
            int comaIdx = e.indexOf(",",m.start());
            throw new IllegalArgumentException("wrong syntax with ',' at index " + comaIdx + ". Use '.' instead of ','?");
        }
    }

    /**
     * Convert an R expression in a Js expression WARNING: many R syntaxes are
     * not supported yet
     *
     * @param e - the R expression
     * @return the js script expression
     */
    private String convertRtoJs(String e) throws RException {

        // If e contains already "__this__" ... (should not happen, but...)
        if (e.contains(THIS_ENVIRONMENT)) {
            return convertRtoJs(e.replace(THIS_ENVIRONMENT, "THEENVIRONMENT")).replace("THEENVIRONMENT", THIS_ENVIRONMENT);
        }

        String R = null;
        if (debug_js) {
            R = e;
        }

        e = removeCommentedLines(e);

        // remove ; at end of lines. We will re-add it later
        e = e.replaceAll(";+ *\\n", "\n");

        // non-regexp keys in R2js.propto replace
        if (R_TO_JS != null) {
            for (Object R_key : R_TO_JS.keySet()) {
                String var = Pattern.quote(R_key.toString());
                String regexp = AW + var + (R_key.toString().endsWith("(") ? ")" : Az);
                Matcher m = Pattern.compile(regexp).matcher(e);
                while (m.find()) {
                    String val = R_TO_JS.getProperty(R_key.toString());
                    e = e.replace(m.group(), m.group().replace(R_key.toString(), val));
                }
            }
        }

        //1E-8 -> 1*10^-8
        //e = e.replaceAll("(\\d|\\d\\.)[eE]+([+-])*(\\d)", "$1*10^$2$3");
        Matcher m = Pattern.compile("(\\d|\\d\\.)+[eE]+([+-])*(\\d*)").matcher(e);
        while (m.find()) {
            try {
                e = e.replace(m.group(), formatter.format(Double.parseDouble(m.group()))); // direct eval within java
            } catch (Exception ex) {//Do nothing, for instance if it is nt a number in facts (-> hostname bug 'travis-1ee1-...)
            }
        }

        // Get all expression in quote and replace them by variables to not
        // modify them in this function
        quotesList = replaceQuotesByVariables(e, 1);

        // Get the expression with replaced quotes (it's the first element of
        // the returned list)
        e = quotesList.get(0);

        checkExpressionValidity(e);

        //change variable names containing "." by "__", but avoid file names (ending with ')
        e = e.replaceAll("([a-zA-Z]*)\\.([a-zA-Z]+)", "$1__$2");

        // Remove all leading zeros before a number to prevent conversion from octal numeral system
        e = e.replaceAll("(?<!\\.)\\b0+([1-9\\.])", "$1");

        // ceil() is an alias for ceiling() in R
        e = e.replaceAll("(?<!\\.)(\\b)" + "ceiling" + "(?<!\\.)(\\b)", "ceil");

        // Replace Math functions
        for (String f : MATH_FUN_JS) {
            e = e.replaceAll("(?<!\\.)(\\b)" + f + "\\(", "$1 math." + f + "(");
        }

        // Replace Math constants
        for (String c : MATH_CONST_JS) {
            e = e.replaceAll("(?<!\\.)(\\b)" + c + "(?<!\\.)(\\b)", "$1 math." + c.toUpperCase() + "$2");
        }

        // Replace t(x) by math.transpose(x)
        e = e.replaceAll("(^|[^a-zA-Z\\d:])t\\(", "$1math.transpose(");

        // Replace determinant(x) by math.det(x)
        e = e.replaceAll("(^|[^a-zA-Z\\d:])determinant\\(", "$1math.det(");

        // Replace solve(A,B) by math.lusolve(A,B)
        e = e.replaceAll("(^|[^a-zA-Z\\d:])solve\\(", "$1math.lusolve(");

        // Replace dim(A) by r.dim(A)
        e = e.replaceAll("(^|[^a-zA-Z\\d\\.:])dim\\(", "$1__R.dim(");

        // replace '->' by '='
        e = e.replaceAll("<<-", "=");
        e = e.replaceAll("<-", "=");

        // replace "+-" by "-"
        e = e.replaceAll("\\+ *-", "-");
        // replace "-123" by "0-123" (at begining of expr)
        e = e.replaceAll("^ *-", "0-");

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
        //e = e.replaceAll("if( *[(][^)]*[)])(.[^}]*)else(.*)", "if$1{$2} else{$3}");
        e = e.replaceAll("(?<!\\.)(\\b)if \\(", "if(");
        e = addIfElseBrackets(e);

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
        e = e.replaceAll("(?<!\\.)(\\b)c[(]([.[^):]]*)[)]", "$1[$2]");
        e = e.replaceAll("(?<!\\.)(\\b)c[(]([.[^)]]*)[)]", "$1$2");

        // replace "for (x in array) {...}" by: "var arrayLength = array.length;
        // for(var i = 0; i < arrayLength; i++) { x = array[i]; ...}"
        // We can't used the "for (x of array)" expression in javascript because
        // it's not supported by Java8 and his javascript evaluator
        e = e.replaceAll("for *[(]([\\w\\-]+) +in +([\\w\\-]+)[)] *[{]",
                "var $2Length = __R.dim($2)[0]; for(var i = 0; i < $2Length; i++) {$1 = $2[i]; ");

        // Replace "TRUE" by "true" and "FALSE" by "false"
        e = e.replaceAll("(?<!\\.)(\\b)TRUE(\\b)", "true");
        e = e.replaceAll("(?<!\\.)(\\b)FALSE(\\b)", "false");

        e = e.replaceAll("(?<!\\.)(\\b)NULL(\\b)", "null");
        e = e.replaceAll("(?<!\\.)(\\b)NA(\\b)", "null");

        // Replace '++' operator by a=a+1
        e = e.replaceAll("([^ ]+)\\+\\+", "$1=$1+1");

        // Replace '+=' operator by a=a+x
        e = e.replaceAll("([^ ]+) *\\+\\=", "$1=$1+");

        // FIXME this doesn't support += ...
        // Replace operators (+, -, *, /, ...)
        e = e.replaceAll("\\=\\=", "ê");
        e = e.replaceAll("\\<\\=", "ŝ");
        e = e.replaceAll("\\>\\=", "ĝ");
        e = e.replaceAll("\\|\\|", "ô");
        e = e.replaceAll("\\&\\&", "â"); // to avoid replacing 'tolerance &' by two _and
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
        e = e.replaceAll("(?<!\\.)(\\b)list\\(\\)", "{}");
        e = createList(e);

        e = createSetEnv(e);

        // format paste function (with sep & collapse args)
        e = createPaste(e);
        e = createPaste0(e);

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
        e = e.replaceAll(" in ([^\\{]+)\\{", " in __R._in($1){");

        // force regular 'if' to throw error when arg is null
        e = e.replaceAll("if *\\(([^\\{\\n]+)\\)\\s*(\\{|(return))", "if (__R._if($1)) $2");

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

        e = e.replaceAll("(?<!\\.)(\\b)is__array\\(", "$1Array.isArray(");

        e = e.replaceAll("(?<!\\.)(\\b)ncol\\(", "$1__R.ncol(");
        e = e.replaceAll("(?<!\\.)(\\b)nrow\\(", "$1__R.nrow(");
        e = e.replaceAll("(?<!\\.)(\\b)names\\(((\\w|\\.)+)\\)\\s*=\\s*", "$1 $2.names = "); // names(X) = "abc"
        e = e.replaceAll("(?<!\\.)(\\b)names\\(", "$1__R.names(");
        e = e.replaceAll("(?<!\\.)(\\b)length\\(", "$1__R.length(");
        e = e.replaceAll("(?<!\\.)(\\b)dim\\(", "$1__R.dim(");
        e = e.replaceAll("(?<!\\.)(\\b)reLen\\(", "$1__R.repLen(");
        e = e.replaceAll("(?<!\\.)(\\b)rep\\(", "$1__R.rep(");
        e = e.replaceAll("(?<!\\.)(\\b)which\\(", "$1__R.which(");
        e = e.replaceAll("(?<!\\.)(\\b)whichMin\\(", "$1__R.whichMin(");
        e = e.replaceAll("(?<!\\.)(\\b)whichMax\\(", "$1__R.whichMax(");
        e = e.replaceAll("(?<!\\.)(\\b)_print\\(", "$1__R._print(");
        e = e.replaceAll("(?<!\\.)(\\b)getwd\\(", "$1__R.getwd(");
        e = e.replaceAll("(?<!\\.)(\\b)setwd\\(", "$1__R.setwd(");
        e = e.replaceAll("(?<!\\.)(\\b)SysSleep\\(", "$1__R.SysSleep(");
        e = e.replaceAll("(?<!\\.)(\\b)SysGetEnv\\(", "$1__R.SysGetEnv(");
        e = e.replaceAll("(?<!\\.)(\\b)isFunction\\(", "$1__R.isFunction(");
        e = e.replaceAll("(?<!\\.)(\\b)isNull\\(", "$1__R.isNull(");
        e = e.replaceAll("(?<!\\.)(\\b)isNA\\(", "$1__R.isNA(");
        e = e.replaceAll("(?<!\\.)(\\b)isTRUE\\(", "$1__R.isTRUE(");
        e = e.replaceAll("(?<!\\.)(\\b)isFALSE\\(", "$1__R.isFALSE(");
        e = e.replaceAll("(?<!\\.)(\\b)apply\\(", "$1__R.apply(");
        e = e.replaceAll("(?<!\\.)(\\b)rbind\\(", "$1__R.rbind(");
        e = e.replaceAll("(?<!\\.)(\\b)cbind\\(", "$1__R.cbind(");
        e = e.replaceAll("(?<!\\.)(\\b)Rpaste\\(", "$1__R.paste(");
        e = e.replaceAll("(?<!\\.)(\\b)all\\(", "$1__R.all(");
        e = e.replaceAll("(?<!\\.)(\\b)any\\(", "$1__R.any(");
        e = e.replaceAll("(?<!\\.)(\\b)strsplit\\(", "$1__R.strsplit(");
        e = e.replaceAll("(?<!\\.)(\\b)unlist\\(", "$1__R.unlist(");
        e = e.replaceAll("(?<!\\.)(\\b)Rpaste0\\(", "$1__R.paste0(");
        e = e.replaceAll("(?<!\\.)(\\b)asNumeric\\(", "$1__R.asNumeric(");
        e = e.replaceAll("(?<!\\.)(\\b)asInteger\\(", "$1__R.asInteger(");
        e = e.replaceAll("(?<!\\.)(\\b)asLogical\\(", "$1__R.asLogical(");
        e = e.replaceAll("(?<!\\.)(\\b)asCharacter\\(", "$1__R.asCharacter(");

        e = e.replaceAll("__R\\.__R\\.", "__R.");

        e = e.replaceAll("(?<!\\.)(\\b)runif\\(", "$1__rand.runif(");
        e = e.replaceAll("(?<!\\.)(\\b)rnorm\\(", "$1__rand.rnorm(");
        e = e.replaceAll("(?<!\\.)(\\b)rpois\\(", "$1__rand.rpois(");
        e = e.replaceAll("(?<!\\.)(\\b)rcauchy\\(", "$1__rand.rcauchy(");
        e = e.replaceAll("(?<!\\.)(\\b)rchisq\\(", "$1__rand.rchisq(");

        e = e.replaceAll("__rand\\.__rand\\.", "__rand.");

        // Replace function() {return if(condition){a} else {b}} by function() {if(condition{return a} else {return b}}
        e = replaceReturnIf(e);

        try {
            e = convertFunction(e);
        } catch (ScriptException ex) {
            Logger.getLogger(R2jsSession.class.getName()).log(java.util.logging.Level.SEVERE, null, ex);
        }

        // Store global variables in the object containing all global variables
        storeGlobalVariables(e);

        e = replaceArgsNames(e, "__");

        // Replace variables by variableStorageObject.variable
        e = replaceVariables(e, variablesSet, THIS_ENVIRONMENT + ".");

        // Finally replace "quotes variables" by their expressions associated
        e = replaceNameByQuotes(quotesList, e, false);

        // Replace '$' accessor of data.frame by a '.'
        e = e.replaceAll("\\$" + THIS_ENVIRONMENT + "\\.", "\\$"); // Remove the JS variable if there is a '$' before
        e = e.replaceAll("\\$([a-zA-Z._])", ".$1"); //FIXME

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
                        + " | " + rightPad((i + 1) + "", 3) + " | "
                        + (lines_JS.length > i ? lines_JS[i] : ""));
            }
            System.err.println("------------------------------------------------------------------------------------");
        }

        return e;
    }

    private String convertFunction(String expr) throws ScriptException {

        // Add a space after a parenthesis, otherwise the second regex bellow doesn't work for function in function
        expr = expr.replaceAll("[(](\\w)", "( $1");

        Pattern indexPattern = Pattern.compile("(^|[^\\.\\w])((?!function|if|for|while|switch|return)\\w+)[(]");
        Matcher indexMatcher = indexPattern.matcher(expr);

        if (indexMatcher.find()) {
            indexMatcher.reset();
            while (indexMatcher.find()) {
                String fctName = indexMatcher.group(2);
                // Ignore if the functions is already defined
                if (!this.variablesSet.contains(fctName)) {
                    // If the function is not defined yet in js environment
                    if (!this.asLogical(this.js.eval("typeof " + fctName + " !== 'undefined'"))) {
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
     * Remove commented lines
     *
     * @param expr
     * @return
     */
    private String removeCommentedLines(String expr) {
        StringBuilder sb = new StringBuilder();
        String lines[] = expr.split("\\r?\\n");
        for (String line : lines) {
            if (!line.trim().startsWith("#")) {
                sb.append(line);
                sb.append("\n");
            }
        }
        sb.setLength(sb.length() - 1);
        return sb.toString();
    }

    /**
     * Add prefix before arguments names of the function to not replace them
     * after by "global variables"
     *
     * Ex: In this example we add the prefix "__" before var1 to not mix up with
     * the __this__.var1 variable var1 <- 1 : __this__.var1 = 1 f1 <-
     * function(var1) { var1 + 1} : __this__.f1 = function(__var1) {return
     * math.add(__var1 , 1)}
     *
     * @param expr - the expression to replace
     * @param prefix - the prefix to add before arguments of the function
     * @return
     */
    private String replaceArgsNames(String expr, String prefix) {
        String result = expr;

        RFunctionArgumentsDTO rFunctionArgumentsDTO = getFunctionArguments(expr, "function");

        while (rFunctionArgumentsDTO != null) {

            int startIndex = rFunctionArgumentsDTO.getStartIndex();
            int endIndex = rFunctionArgumentsDTO.getStopIndex();
            Map<String, String> argumentsMap = rFunctionArgumentsDTO.getGroups();

            Collection<String> arguments = Arrays.stream(argumentsMap.get("default").split(",")).map(String::trim).collect(Collectors.toList());

            int ifStartBracketIndex = result.substring(endIndex).indexOf("{") + endIndex;
            int fctCloseBracketIndex = getNextExpressionLastIndex(result, ifStartBracketIndex + 1, "}") + 1;

            if(fctCloseBracketIndex + 1 >= result.length()) fctCloseBracketIndex-=1;
            String function = result.substring(startIndex, fctCloseBracketIndex + 1);

            // Prefix function's arguments by "__"
            String replacedFunction = replaceVariables(function, arguments, prefix);
            replacedFunction = replacedFunction.replaceAll("(\\b)function(\\b)", "$1_function$2");

            // expression
            StringBuilder sb = new StringBuilder();
            sb.append(result.substring(0, startIndex));
            sb.append(replacedFunction);
            sb.append(result.substring(fctCloseBracketIndex + 1));
            result = sb.toString();

            // Search the next "function" in the expression
            rFunctionArgumentsDTO = getFunctionArguments(result, "function");
        }
        result = result.replaceAll("(\\b)_function(\\b)", "$1function$2");

        // Now replace also in calls to functions: f(a=2) -> f(__a=2)
        for (String f : variablesSet) {
            Pattern fPattern = Pattern.compile("\\b(" + f + ")\\(([^\\)]*)\\)");
            Matcher fMatcher = fPattern.matcher(result);

            if (fMatcher.find()) {
                fMatcher.reset();
                StringBuffer result_buf = new StringBuffer();
                while (fMatcher.find()) {
                    String args = " " + fMatcher.group(2) + " ";
                    //System.err.println("args: "+args);
                    String[] argsArray = splitString(args, ",");
                    StringBuffer args_buf = new StringBuffer();
                    for (int i = 0; i < argsArray.length; i++) {
                        //System.err.println(" - "+argsArray[i]);
                        if (argsArray[i].contains("=")) {
                            String[] kv_arg = argsArray[i].split("=");
                            argsArray[i] = "__" + kv_arg[0].trim() + " = " + kv_arg[1];
                        }
                        args_buf.append(argsArray[i] + (i == (argsArray.length - 1) ? "" : ","));
                    }
                    fMatcher.appendReplacement(result_buf, (f + "(" + args_buf.toString() + ")").replace("$", "\\$"));
                }

                fMatcher.appendTail(result_buf);

                result = result_buf.toString();
            }
        }

        return result;
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
    private String replaceVariables(String expr, Iterable<String> variables, String prefix) {
        String result = expr;
        for (String variable : variables) {
            if (variable.length() > 0) {
                //result = result.replaceAll("(\\b)^((?!" + JS_VARIABLE_STORAGE_OBJECT + "\\.).)*(\\b)(" + variable + ")(\\b)", JS_VARIABLE_STORAGE_OBJECT + "." + variable);
                result = result.replaceAll("\\b(?<![\\$\\.[__]])" + variable + "\\b", prefix + variable);
                result = result.replaceAll(prefix + prefix, prefix);
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
     * @param expr - the expression with quotes
     * @param startIndex - the index i of the first "QUOTE_EXPRESSION_i_"
     * replacement
     * @return a list containing all quotes expression
     */
    static List<String> replaceQuotesByVariables(String expr, int startIndex) throws RException {

        List<String> quotesList = new ArrayList<>();
        String singleQuote = "'";
        String doubleQuote = "\"";

        int cmp = startIndex;
        quotesList.add(expr);
        StringBuffer currentExpr = new StringBuffer(expr);
        // While no more quotes founded
        while (true) {
            int doubleQuoteIdx = currentExpr.indexOf(doubleQuote);
            int singleQuoteIdx = currentExpr.indexOf(singleQuote);
            int startQuoteIdx = -1;
            String currentQuote = "";
            if (singleQuoteIdx >= 0 && (doubleQuoteIdx < 0 || singleQuoteIdx < doubleQuoteIdx)) {
                currentQuote = singleQuote;
                startQuoteIdx = singleQuoteIdx;
            } else {
                currentQuote = doubleQuote;
                startQuoteIdx = doubleQuoteIdx;
            }
            if (startQuoteIdx < 0) {
                break; // No more quotes
            }
            int endQuoteIdx = currentExpr.indexOf(currentQuote, startQuoteIdx + 1);
            if (endQuoteIdx <= startQuoteIdx) {
                throw new RException("No ending quotes for quote " + currentQuote + " at position: " + startQuoteIdx + " in: " + expr);
            }
            String quotedExpr = currentExpr.substring(startQuoteIdx, endQuoteIdx + 1);
            quotesList.add(quotedExpr);
            currentExpr.replace(startQuoteIdx, endQuoteIdx + 1, "QUOTE_EXPRESSION_" + cmp + "_");
            cmp++;
        }

        quotesList.set(0, currentExpr.toString());
        return quotesList;
    }

    /**
     * Replace variables: QUOTE_EXPRESSION_ID by their expression in quotesList
     *
     * @param quotesList - a list containing all quotes expression in the same
     * order than the ID of variables. (WARNING: The first index of the list is
     * not used)
     * @param expr - expression containing variables to replace by quotes
     * expressions
     * @return the expression with variables replaced by quotes expressions
     */
    static String replaceNameByQuotes(List<String> quotesList, String expr, boolean removeRoundingQuotes) {

        for (int i = 1; i < quotesList.size(); i++) {
            String quote = quotesList.get(i).trim();
            if (removeRoundingQuotes) {
                quote = quote.substring(1, quote.length() - 1);
            }
            quote = quote.replace("\\", "\\\\\\\\"); // Add more backslash to pass java and js

            expr = expr.replaceAll("QUOTE_EXPRESSION_" + i + "_", quote);
        }

        return expr;
    }

    /**
     * LS function
     *
     * @param expr - the expression containing the function to replace
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
                unifRandomSb.append("__R.removeMatching(Object.keys(");
                unifRandomSb.append(THIS_ENVIRONMENT);
                unifRandomSb.append("), new RegExp(");
                unifRandomSb.append(pattern);
                unifRandomSb.append("))");
            } else {
                unifRandomSb.append("Object.keys(");
                unifRandomSb.append(THIS_ENVIRONMENT);
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

        // replace by Obejct.keys() ?
        if (all) {
            return variablesandfunctions.toArray(new String[]{});
        } else {
            List<String> notall = new LinkedList<>();
            for (String v : variablesandfunctions) {
                if (!v.startsWith("_")) {
                    notall.add(v);
                }
            }
            return notall.toArray(new String[]{});
        }
    }

    /**
     * Split an array with the separator only if the separator string is not in
     * parenthesis or bracket
     *
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
            String argumentName = expr.substring(sepIndex + 1, nextIndex).trim();
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
    private static String index_pattern = "([\\w|\\$|\\.]+(\\([\\w|\\$|\\=|\\,|\\-|\\(|\\)|\\.]*\\))*[\\w|\\$|\\.]*)\\[+(.[^\\]]*)\\]+";
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
            List<String> found = new LinkedList<>();
            while (intricated.find()) {
                found.add(intricated.group(1) + intricated.group(2) + intricated.group(3));
            }
            throw new UnsupportedOperationException("Intricated indexes 'abc[def[i]]' not supported at:" + String.join("\n", found));
        }

        Pattern indexPattern = Pattern.compile(index_pattern);
        Matcher indexMatcher = indexPattern.matcher(expr);

        if (indexMatcher.find()) {
            indexMatcher.reset();
            StringBuffer sb = new StringBuffer("");
            while (indexMatcher.find()) {
                String arrayName = indexMatcher.group(1);
                String indexes = " " + indexMatcher.group(3) + " ";
                String[] indexesArray = splitString(indexes, ",");

                for (int i = 0; i < indexesArray.length; i++) {
                    if (indexesArray[i].trim().equals("")) { // If the index is empty, we create a range array to select all the line
                        int dim = i;
                        indexesArray[i] = "math.range(1, 1+math.subset(dim(" + arrayName + "), math.index(" + dim + ")))"; //range starting from 1, because R.r_index will apply -1
                    }
                }

                StringBuilder result = new StringBuilder();
                result.append("math.squeeze(math.subset(");
                result.append(arrayName);
                result.append(", __R._index(");
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
            List<String> found = new LinkedList<>();
            while (intricated.find()) {
                found.add(intricated.group(1) + intricated.group(2) + intricated.group(3));
            }
            throw new UnsupportedOperationException("Intricated indexes 'abc[def[i]]' not supported at:" + String.join("\n", found));
        }

        Pattern indexPattern = Pattern.compile(index_pattern + "\\s*[\\=]{1}(.*)");
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
                result.append(", __R._index(");
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
     * This function add brackets in if/else expression. In nashorn (Java8_161 -
     * ECMA5) the if without '{' work but not the if else without '{'
     *
     * Example: Before: "if(a>1) 1 else 2" After : "if(a>1) {1} else {2}"
     *
     * @param expr
     * @return the transformed expression
     */
    private static String addIfElseBrackets(String expr) {

        final String ifReplacementString = "__IF__";
        String result = expr;



        RFunctionArgumentsDTO rFunctionArgumentsDTO = getFunctionArguments(expr, "if", true);

        while (rFunctionArgumentsDTO != null) {

            int startIndex = rFunctionArgumentsDTO.getStartIndex();
            int endIndex = rFunctionArgumentsDTO.getStopIndex();
            Map<String, String> argumentsMap = rFunctionArgumentsDTO.getGroups();

            // Data to write
            String ifArg = argumentsMap.get("default");

            StringBuilder ifSb = new StringBuilder();
            ifSb.append(ifReplacementString);
            ifSb.append("(");
            ifSb.append(ifArg);
            ifSb.append(")");

            //if (!result.substring(endIndex + 1).trim().startsWith("{") && expr.indexOf("else", endIndex) >= 0) {


            int elseIndex = result.indexOf("else", endIndex);

            boolean dontCloseBrack = false;

            if (elseIndex >= 0) {
                String ifStatement = result.substring(endIndex + 1, elseIndex).trim();
                if(!ifStatement.startsWith("{")) ifSb.append("{");
                ifSb.append(ifStatement);
                if(!ifStatement.endsWith("}")) ifSb.append("}");
                ifSb.append(" else ");
                String elseStatement = result.substring(elseIndex + 4).trim();
                if(!elseStatement.startsWith("{") && !elseStatement.startsWith("if")) ifSb.append("{");
                ifSb.append(elseStatement);
                if(!elseStatement.startsWith("{") && !elseStatement.startsWith("if")) ifSb.append("}");
            } else {
                String ifStatement = result.substring(endIndex + 1, result.length()).trim();
                if(!ifStatement.startsWith("{")) {
                    ifSb.append("{");
                    int returnLineIdx = ifStatement.indexOf('\n');
                    if(returnLineIdx>=0) {
                        ifStatement = ifStatement.substring(0, returnLineIdx) + "}" + ifStatement.substring(returnLineIdx, ifStatement.length());
                        ifSb.append(ifStatement);
                        dontCloseBrack = true;
                    } else {
                        ifSb.append(ifStatement);
                        ifSb.append("}");
                    }
                } else {
                    ifSb.append(ifStatement);
                    dontCloseBrack = true;
                }

            }

            StringBuilder sb = new StringBuilder();
            sb.append(result.substring(0, startIndex));
            sb.append(ifSb.toString());
            if(!dontCloseBrack && !ifSb.toString().trim().endsWith("}")) sb.append("}");
            result = sb.toString();

            // Search the next "if"
            rFunctionArgumentsDTO = getFunctionArguments(result, "if", true);
        }

        result = result.replaceAll(ifReplacementString, "if");

        return result;
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
            unifRandomSb.append("__R.write(");
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
    private String createDataFrame(String expr) throws RException {

        String result = expr;

        RFunctionArgumentsDTO rFunctionArgumentsDTO = getFunctionArguments(expr, "data__frame");

        while (rFunctionArgumentsDTO != null) {

            int startIndex = rFunctionArgumentsDTO.getStartIndex();
            int endIndex = rFunctionArgumentsDTO.getStopIndex();

            StringBuilder dataFrameSb = new StringBuilder();
            dataFrameSb.append("__R.createMathObject({");

            Map<String, String> argumentsMap = rFunctionArgumentsDTO.getGroups();
            for (Map.Entry<String, String> entry : argumentsMap.entrySet()) {
                String key = entry.getKey();
                String value = entry.getValue();
                if (key.equals(value)) {
                    dataFrameSb.append("'");
                }
                dataFrameSb.append(key);
                if (key.equals(value)) {
                    dataFrameSb.append("'");
                }
                dataFrameSb.append(":");
                dataFrameSb.append(value);
                dataFrameSb.append(",");
            }

            dataFrameSb.replace(dataFrameSb.length() - 1, dataFrameSb.length(), "})");

            // Replace the R matrix expression by the current matrix js
            // expression
            StringBuilder sb = new StringBuilder();
            sb.append(result.substring(0, startIndex));
            sb.append(dataFrameSb.toString());
            sb.append(result.substring(endIndex + 1));
            result = sb.toString();

            List<String> newQuoteList = replaceQuotesByVariables(result, quotesList.size());
            result = newQuoteList.get(0);
            for (int i = 1; i < newQuoteList.size(); i++) {
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
    private String createList(String expr) throws RException {
        String result = expr;

        RFunctionArgumentsDTO rFunctionArgumentsDTO = getFunctionArguments(expr, "list");

        while (rFunctionArgumentsDTO != null) {

            int startIndex = rFunctionArgumentsDTO.getStartIndex();
            int endIndex = rFunctionArgumentsDTO.getStopIndex();

            StringBuilder dataFrameSb = new StringBuilder();
            dataFrameSb.append("__R.createMathObject({");

            Map<String, String> argumentsMap = rFunctionArgumentsDTO.getGroups();
            for (Map.Entry<String, String> entry : argumentsMap.entrySet()) {
                String key = entry.getKey();
                String value = entry.getValue();
                if (key.equals(value)) {
                    dataFrameSb.append("'");
                }
                dataFrameSb.append(key);
                if (key.equals(value)) {
                    dataFrameSb.append("'");
                }
                dataFrameSb.append(":");
                dataFrameSb.append(value);
                dataFrameSb.append(",");
            }

            dataFrameSb.replace(dataFrameSb.length() - 1, dataFrameSb.length(), "})");

            // Replace the R matrix expression by the current matrix js
            // expression
            StringBuilder sb = new StringBuilder();
            sb.append(result.substring(0, startIndex));
            sb.append(dataFrameSb.toString());
            sb.append(result.substring(endIndex + 1));
            result = sb.toString();

            List<String> newQuoteList = replaceQuotesByVariables(result, quotesList.size());
            result = newQuoteList.get(0);
            for (int i = 1; i < newQuoteList.size(); i++) {
                quotesList.add(newQuoteList.get(i));
            }

            // Search the next "array"
            rFunctionArgumentsDTO = getFunctionArguments(result, "list");
        }
        if (result.startsWith("{") && result.endsWith("}")) {
            result = "(" + result + ")"; //hack to avoid passing "{a:1,b:2}" to javascript, which is then misinterpredted as an expression, and not as a list declaration.
        }
        return result;
    }

    private String createSetEnv(String expr) throws RException {
        String result = expr;

        RFunctionArgumentsDTO rFunctionArgumentsDTO = getFunctionArguments(expr, "Sys__setenv");

        while (rFunctionArgumentsDTO != null) {

            int startIndex = rFunctionArgumentsDTO.getStartIndex();
            int endIndex = rFunctionArgumentsDTO.getStopIndex();

            StringBuilder dataFrameSb = new StringBuilder();
            dataFrameSb.append("__R.SysSetEnv({");

            Map<String, String> argumentsMap = rFunctionArgumentsDTO.getGroups();
            for (Map.Entry<String, String> entry : argumentsMap.entrySet()) {
                String key = entry.getKey();
                String value = entry.getValue();
                if (key.equals(value)) {
                    dataFrameSb.append("'");
                }
                dataFrameSb.append(key);
                if (key.equals(value)) {
                    dataFrameSb.append("'");
                }
                dataFrameSb.append(":");
                dataFrameSb.append(value);
                dataFrameSb.append(",");
            }

            dataFrameSb.replace(dataFrameSb.length() - 1, dataFrameSb.length(), "})");

            // Replace the R matrix expression by the current matrix js
            // expression
            StringBuilder sb = new StringBuilder();
            sb.append(result.substring(0, startIndex));
            sb.append(dataFrameSb.toString());
            sb.append(result.substring(endIndex + 1));
            result = sb.toString();

            List<String> newQuoteList = replaceQuotesByVariables(result, quotesList.size());
            result = newQuoteList.get(0);
            for (int i = 1; i < newQuoteList.size(); i++) {
                quotesList.add(newQuoteList.get(i));
            }

            // Search the next "array"
            rFunctionArgumentsDTO = getFunctionArguments(result, "Sys__setenv");
        }

        return result;
    }

    static Map<String, String> Env = new HashMap();

    public static void setEnv(String k, String v) {
        Env.put(k, v);
    }

    public static String getEnv(String k) {
        if (Env.containsKey(k))
            return Env.get(k);
        if (System.getenv().containsKey(k))
            return System.getenv().get(k);
        if (System.getProperties().containsKey(k))
            return System.getProperties().get(k).toString();
        return "";
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
                arraySb.append("math.transpose(math.reshape(__R.expendArray(");
                arraySb.append(data);
                arraySb.append(", math.prod(");
                arraySb.append(stringDimArray);
                arraySb.append(")), ");
                arraySb.append(stringDimArray);
                arraySb.append(".reverse()))");
            } else {
                arraySb.append("math.reshape(__R.expendArray(");
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
                arraySb.append("math.transpose(math.reshape(__R.expendArray(");
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

    private String createPaste(String expr) throws RException {
        String result = expr;

        RFunctionArgumentsDTO rFunctionArgumentsDTO = getFunctionArguments(expr, "paste");

        while (rFunctionArgumentsDTO != null) {

            int startIndex = rFunctionArgumentsDTO.getStartIndex();
            int endIndex = rFunctionArgumentsDTO.getStopIndex();

            StringBuilder dataFrameSb = new StringBuilder();
            Map<String, String> argumentsMap = rFunctionArgumentsDTO.getGroups();

            String sep = argumentsMap.getOrDefault("sep", "' '");
            String collapse = argumentsMap.getOrDefault("collapse", "';'");

            dataFrameSb.append("Rpaste(" + sep + "," + collapse + ",");
            //startIndex = startIndex+sep.length()+1+collapse.length()+1; //to move after sep & collapse args

            for (Map.Entry<String, String> entry : argumentsMap.entrySet()) {
                String key = entry.getKey();
                if (!key.equals("sep") && !key.equals("collapse")) {
                    String value = entry.getValue();
                    dataFrameSb.append(value);
                    dataFrameSb.append(",");
                }
            }

            dataFrameSb.replace(dataFrameSb.length() - 1, dataFrameSb.length(), ")");

            StringBuilder sb = new StringBuilder();
            sb.append(result.substring(0, startIndex));
            sb.append(dataFrameSb.toString());
            sb.append(result.substring(endIndex + 1));
            result = sb.toString();

            List<String> newQuoteList = replaceQuotesByVariables(result, quotesList.size());
            result = newQuoteList.get(0);
            for (int i = 1; i < newQuoteList.size(); i++) {
                quotesList.add(newQuoteList.get(i));
            }

            // Search the next "paste"
            rFunctionArgumentsDTO = getFunctionArguments(result, "paste");
        }

        return result;
    }

    private String createPaste0(String expr) throws RException {
        String result = expr;

        RFunctionArgumentsDTO rFunctionArgumentsDTO = getFunctionArguments(expr, "paste0");

        while (rFunctionArgumentsDTO != null) {

            int startIndex = rFunctionArgumentsDTO.getStartIndex();
            int endIndex = rFunctionArgumentsDTO.getStopIndex();

            StringBuilder dataFrameSb = new StringBuilder();
            Map<String, String> argumentsMap = rFunctionArgumentsDTO.getGroups();

            String collapse = argumentsMap.getOrDefault("collapse", "';'");

            dataFrameSb.append("Rpaste0(" + collapse + ",");
            //startIndex = startIndex+sep.length()+1+collapse.length()+1; //to move after sep & collapse args

            for (Map.Entry<String, String> entry : argumentsMap.entrySet()) {
                String key = entry.getKey();
                if (!key.equals("collapse")) {
                    String value = entry.getValue();
                    dataFrameSb.append(value);
                    dataFrameSb.append(",");
                }
            }

            dataFrameSb.replace(dataFrameSb.length() - 1, dataFrameSb.length(), ")");

            StringBuilder sb = new StringBuilder();
            sb.append(result.substring(0, startIndex));
            sb.append(dataFrameSb.toString());
            sb.append(result.substring(endIndex + 1));
            result = sb.toString();

            List<String> newQuoteList = replaceQuotesByVariables(result, quotesList.size());
            result = newQuoteList.get(0);
            for (int i = 1; i < newQuoteList.size(); i++) {
                quotesList.add(newQuoteList.get(i));
            }

            // Search the next "paste"
            rFunctionArgumentsDTO = getFunctionArguments(result, "paste0");
        }

        return result;
    }

    /**
     * This function replaces the R function save by JS equivalent It writes in
     * file the variable with its value sperated by ':' in the file (example:
     * "variable:value") WARNING the function works only if the variable to save
     * is between quotes
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

            saveSb.append("__R.write(");
            saveSb.append(fileString);
            saveSb.append(", ");
            saveSb.append("__R.createJsonString(");
            saveSb.append(listStringUnquotted);
            saveSb.append(", ");
            saveSb.append(THIS_ENVIRONMENT);
            saveSb.append("))");

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
                String readVariablesExpr = replaceNameByQuotes(quotesList, "__R.readJsonVariables(" + fileString + ")", false);
                String[] loadedVariables = (String[]) cast(js.eval(readVariablesExpr));
                addGlobalVariables(loadedVariables);
            } catch (ScriptException ex) {
                Logger.getLogger(R2jsSession.class.getName()).log(java.util.logging.Level.SEVERE, null, ex);
            }

            // Build the mathjs expression to create load data
            StringBuilder loadSb = new StringBuilder();
            loadSb.append("__R.loadJson(");
            loadSb.append(fileString);
            loadSb.append(", ");
            loadSb.append(THIS_ENVIRONMENT);
            loadSb.append(")");

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
     * Replace 'function() {return if(condition){a} else {b}}' by: 'function()
     * {if(condition{return a} else {return b}}'
     *
     * @param expr - the expression containing the function to replace
     * @return the expression with replaced function
     */
    private static String replaceReturnIf(String expr) {

        String result = expr;
        result = result.replaceAll("\\breturn +if *\\(", "returnif(");
        RFunctionArgumentsDTO rFunctionArgumentsDTO = getFunctionArguments(result, "returnif", true);

        while (rFunctionArgumentsDTO != null) {

            int startIndex = rFunctionArgumentsDTO.getStartIndex();
            int endIndex = rFunctionArgumentsDTO.getStopIndex();
            Map<String, String> argumentsMap = rFunctionArgumentsDTO.getGroups();

            String values = argumentsMap.get("default");

            // Build the mathjs expression
            StringBuilder ifSb = new StringBuilder();

            ifSb.append("if(");
            ifSb.append(values);
            ifSb.append(") {return ");

            int ifStartBracketIndex = result.substring(endIndex).indexOf("{") + endIndex;
            int ifCloseBracketIndex = getNextExpressionLastIndex(result, ifStartBracketIndex + 1, "}") + 1;
            String returnStatement = result.substring(ifStartBracketIndex + 1, ifCloseBracketIndex + 1).trim();
            if(returnStatement.startsWith("{")) returnStatement=returnStatement.substring(1);
            if(returnStatement.endsWith("}")) returnStatement=returnStatement.substring(0, returnStatement.length() - 1);

            ifSb.append(returnStatement);
            ifSb.append("}");
            if (result.substring(ifCloseBracketIndex + 1).trim().startsWith("else")) {
                int elseStartBracketIndex = result.substring(ifCloseBracketIndex + 1).indexOf("{") + ifCloseBracketIndex + 1;
                int elseCloseBracketIndex = getNextExpressionLastIndex(result, elseStartBracketIndex + 1, "}") + 1;
                ifSb.append(" else {return ");
                ifSb.append(result.substring(elseStartBracketIndex + 1, elseCloseBracketIndex + 1));
                endIndex = elseCloseBracketIndex;
            } else {
                endIndex = ifCloseBracketIndex;
            }

            // Replace the R cbind expression by the current js expression
            StringBuilder sb = new StringBuilder();
            sb.append(result.substring(0, startIndex));
            sb.append(ifSb);
            sb.append(result.substring(endIndex + 1));
            result = sb.toString();

            // Search the next "return if" in the expression
            result = result.replaceAll("\\breturn +if *\\(", "returnif(");
            rFunctionArgumentsDTO = getFunctionArguments(result, "returnif");
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

            fileExistSb.append("__R.fileExists(");
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
     * This function replaces the R function exists in JavaScript WARNING:
     * arguments('where', 'envir', 'frame', 'mode' and 'inherits') are not
     * supported yet and ignored
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

            fileExistSb.append(THIS_ENVIRONMENT);
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

            fileExistSb.append("__R.stopIfNot(");
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
     * This function replace f = function(x=1, y=2) by f = function(x = typeof x
     * != 'undefined' ? x : 1, y = typeof y != 'undefined' ? y : 2)
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

            for (Map.Entry<String, String> entry : argumentsMap.entrySet()) {
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

            functionSb = functionSb.deleteCharAt(functionSb.length() - 1);
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

    private static RFunctionArgumentsDTO getFunctionArguments(String expr, String fctName) {
        return getFunctionArguments(expr, fctName, false);
    }

    /**
     * Get the beginning, the ending and arguments of a function. This function
     * search for the first occurence of "fctName" in the "expr" and return a
     * DTO containing all these informations. If an argument field is not
     * informed, a default field will be affected.
     *
     * @param expr : the expression where we search the function
     * @param fctName : the name of the wanted function
     * @param ignoreOperators: if true, ignore '==' and return only arguments
     * separated by ','
     * @return a DTO containing: start index , end index and arguments of the
     * function found
     */
    private static RFunctionArgumentsDTO getFunctionArguments(String expr, String fctName, boolean ignoreOperators) {

        // Map containing possible arguments associated to each R functions
        Map<String, List<String>> argumentNamesByFunctions = new LinkedHashMap<>();
        argumentNamesByFunctions.put("array", Arrays.asList("data", "dim", "dimnames"));
        argumentNamesByFunctions.put("paste0", new ArrayList<String>());
        argumentNamesByFunctions.put("paste", new ArrayList<String>());
        argumentNamesByFunctions.put("unlist", new ArrayList<String>());
        argumentNamesByFunctions.put("strsplit", new ArrayList<String>());
        argumentNamesByFunctions.put("all", new ArrayList<String>());
        argumentNamesByFunctions.put("any", new ArrayList<String>());
        argumentNamesByFunctions.put("vector", Arrays.asList("mode", "length"));
        argumentNamesByFunctions.put("matrix", Arrays.asList("data", "nrow", "ncol", "byrow", "dimnames"));
        argumentNamesByFunctions.put("c", Arrays.asList("data", "dim", "dimnames"));
        argumentNamesByFunctions.put("ls", Arrays.asList("name", "pos", "envir", "all.names", "pattern", "sorted"));
        argumentNamesByFunctions.put("save", Arrays.asList("list", "file", "ascii", "all.names", "pattern", "sorted"));
        argumentNamesByFunctions.put("load", Arrays.asList("file", "envir", "verbose"));
        argumentNamesByFunctions.put("write__csv", Arrays.asList("data", "file", "row.names", "col.names", "sep", "na"));
        argumentNamesByFunctions.put("data__frame", new ArrayList<String>());
        argumentNamesByFunctions.put("list", new ArrayList<String>());
        argumentNamesByFunctions.put("Sys__setenv", new ArrayList<String>());
        argumentNamesByFunctions.put("length", Arrays.asList("default"));
        argumentNamesByFunctions.put("file__exists", Arrays.asList("default"));
        argumentNamesByFunctions.put("exists", Arrays.asList("default", "where", "envir", "mode", "frame", "inherits"));
        argumentNamesByFunctions.put("stopifnot", Arrays.asList("default"));
        argumentNamesByFunctions.put("function", Arrays.asList("default"));
        argumentNamesByFunctions.put("if", Arrays.asList("default"));
        argumentNamesByFunctions.put("returnif", Arrays.asList("default"));

        RFunctionArgumentsDTO rFunctionArgumentsDTO = null;

        List<String> argumentNamesList = new ArrayList<>(argumentNamesByFunctions.get(fctName));

        Map<String, String> argumentNamesAndValues = new LinkedHashMap<>(); //because we need to keep order of arguments, by default

        Pattern pattern = Pattern.compile("(?<!\\.)(\\b)" + fctName + "\\s*\\(");
        Matcher matcher = pattern.matcher(expr);

        // If an occurrence has been found
        if (matcher.find()) {

            int startIndex = matcher.start();
            int currentIndex = expr.indexOf("(", startIndex)+1;

            while (expr.charAt(currentIndex - 1) != ')') {

                String operators = ",=";
                if (ignoreOperators) {
                    operators = ",";
                }

                int argumentEndIndex = getNextExpressionLastIndex(expr, currentIndex - 1, operators);
                // Ignore if it is a comparison operator ( '!=', '<=', '>=' or '==')
                if (expr.charAt(argumentEndIndex + 1) == '=' && ("!=<>".contains("" + expr.charAt(argumentEndIndex)) || expr.charAt(argumentEndIndex + 2) == '=')) {
                    argumentEndIndex = getNextExpressionLastIndex(expr, argumentEndIndex + 1, operators);
                }
                String argumentName = null;
                String argument = null;
                if (expr.charAt(argumentEndIndex + 1) == '=') {
                    argumentName = expr.substring(currentIndex, argumentEndIndex + 1).trim();
                    currentIndex = argumentEndIndex + 2;
                    argumentEndIndex = getNextExpressionLastIndex(expr, currentIndex - 1, operators);
                }

                if (argumentName == null) {
                    if (argumentNamesList.size() > 0) {
                        // If argument has no "name", we take the first of the list
                        argumentName = argumentNamesList.get(0);
                        // And we remove it from the list unless the name is "default"
                        if (!argumentName.equals("default")) {
                            argumentNamesList.remove(0);
                        }
                    }
                } else {
                    // Remove the current argument name from the list
                    boolean removed = argumentNamesList.remove(argumentName);
//                    if (!removed) {
//                        Log.Err.println("Unknown argument:" + argumentName + " in function: " + fctName);
//                    }
                }

                argument = expr.substring(currentIndex, argumentEndIndex + 1);

                // If there is not argument name and the list argumentNamesList is empty, the argumentName is the name of argument
                if (argumentName == null) {
                    argumentName = argument;
                }

                // If the map already contains the argumentName, we add the new argument after and separated with comma
                if (argumentNamesAndValues.containsKey(argumentName)) {
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
     * Remove + operator if it is after a "return, if, else, (, [, {,),],},=,+,-
     * @param expr: the expression where we want to remove + operator
     * @return expr without + operators
     */
    private static String removePlusOperator(String expr) {
        expr = expr.replaceAll("(return|if|else|\\(|\\{|\\|[\\|]|\\}|=|,|<|>) *\\+", "$1");
        expr = expr.replaceAll("\\+\\s*\\+", "+");
        expr = expr.replaceAll("\\-\\s*\\+", "-");
        expr = expr.replaceAll("\\*\\s*\\+", "*");
        expr = expr.replaceAll("\\/\\s*\\+", "/");
        expr = expr.replaceAll("\\:\\s*\\+", ":");
        expr = expr.replaceAll("\\;\\s*\\+", ";");
        expr = expr.replaceAll("\\^\\s*\\+", "^");
        expr = expr.replaceAll("^\\s*\\+", "");
        return expr;
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

        expr = removePlusOperator(expr);

        // We consider differently the '-' operator in '2-3' to the '-' negative: '-3'.
        // So we replace -3 by î3 first, but 2-3 stays 2-3
        expr = expr.replaceAll("([\\[\\{\\(\\-\\\\=*\\/^;%+:,><&|ôâêŝĝ\\n]) *-", "$1 î");

        String stoppingCharacters = "-=*/^;%+:,><&|ôâêŝĝ\n"; // all operators but 'î'
        expr = expr.replaceAll("[)]/", ") /");
        expr = expr.replaceAll("(.)-", "$1 -");

        Map<String, String> operatorsMap = new HashMap<>();
        operatorsMap.put(">", "__R._gt");
        operatorsMap.put("<", "__R._lt");
        operatorsMap.put("ĝ", "__R._get");
        operatorsMap.put("ŝ", "__R._let");
        operatorsMap.put("ê", "__R._eq");
        operatorsMap.put("|", "__R._or");
        operatorsMap.put("ô", "__R._oror");
        operatorsMap.put("&", "__R._and");
        operatorsMap.put("â", "__R._andand");
        operatorsMap.put("+", "math.add");
        operatorsMap.put("-", "math.subtract");
        operatorsMap.put("*", "math.dotMultiply");
        operatorsMap.put("/", "math.dotDivide");
        operatorsMap.put("%*%", "math.multiply");
        operatorsMap.put("%/%", "math.floor(math.dotDivide");
        operatorsMap.put("%%", "math.mod");
        operatorsMap.put(":", "__R.range");
        operatorsMap.put("^", "math.dotPow");

        String[] operators = new String[]{"^", "/%:ê", "*", "+-&|ôâŝĝ><"}; // '/' has to be treated before '*'

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
                    int startingIndex = getPreviousExpressionFirstIndex(expr, i, stoppingCharacters);
                    String prevExp = expr.substring(startingIndex, i);

                    // If the left expression is not only whitespace (for
                    // example a = -4 or a = +4)
                    if (prevExp.trim().length() > 0) {

                        // Find the end of the right term
                        int endingIndex = getNextExpressionLastIndex(expr, i + nbChar - 1, stoppingCharacters);

                        String operatorName = operatorsMap.get(expr.substring(i, i + nbChar));
                        StringBuilder resultExpr = new StringBuilder();
                        resultExpr.append(operatorName);
                        resultExpr.append("(");
                        resultExpr.append(prevExp);
                        resultExpr.append(",");
                        resultExpr.append(expr.substring(i + nbChar, endingIndex + 1));
                        resultExpr.append(") ");

                        // Add a parenthesis if the operator is "%/%" because we
                        // add floor(..)
                        if (expr.charAt(i + 1) == '/') {
                            resultExpr.append(")");
                        }

                        expr = expr.substring(0, startingIndex) + resultExpr
                                + expr.substring(endingIndex + 1, expr.length());

                        // Decrement i to be sure to not miss an operator
                        //System.err.println("\n"+repeat(" ",startingIndex +2)+"[");
                        i = startingIndex - 1;
                        //System.err.println("\n"+repeat(" ",i +1)+"]: "+expr.charAt(i));
                    }
                } // if the next character is not and operator or "=" (not supported yet)
                else if (!("-+*/=".indexOf(expr.charAt(i + 1)) >= 0)) { // '-' is now a true operator ('î' replaces the sign)
                    //System.err.print("\n  "+expr);
                    //System.err.println("\n  "+repeat(" ",i)+"^"); 

                    // Find the beginning of the left term
                    int startingIndex = getPreviousExpressionFirstIndex(expr, i, stoppingCharacters);
                    String prevExp = expr.substring(startingIndex, i);

                    // let 'return' in previous expression
                    if (prevExp.trim().startsWith("return")) {
                        startingIndex = startingIndex + prevExp.indexOf("return") + "return".length();
                        prevExp = prevExp.replaceFirst("return", "");
                    }

                    //if(!prevExp.trim().equals("return")) {
                    // If the left expression is not only whitespace (for example a = -4 or a = +4)
                    if (prevExp.trim().length() > 0) {

                        // Find the end of the right term
                        int endingIndex = getNextExpressionLastIndex(expr, i, stoppingCharacters);

                        String operatorName = operatorsMap.get(currentChar + "");
                        StringBuilder resultExpr = new StringBuilder();
                        // Add a "+" operator before:
                        // Example: 1*2 -5*6 will be mult(1,2) + mult(-5,6) with a '+' between the two mult operators
                        resultExpr.append(" + ");
                        resultExpr.append(operatorName);
                        resultExpr.append("(");
                        resultExpr.append(prevExp);
                        resultExpr.append(",");
                        resultExpr.append(expr.substring(i + 1, endingIndex + 1));
                        resultExpr.append(") "); // add a space to not ignore ')-' later

                        expr = expr.substring(0, startingIndex) + resultExpr
                                + expr.substring(endingIndex + 1, expr.length());

                        //Remove + operator if it is after a "return, if, else, (, [, {,),],},=,+,-
                        expr = removePlusOperator(expr);

                        // Decrement i to be sure to not miss an operator                                   
                        //System.err.println("\n"+repeat(" ",startingIndex +2)+"[");
                        i = startingIndex - 1;
                        //System.err.println("\n"+repeat(" ",i +1)+"]");
                    }
                    //}

                } else {
                    i = i + 1;
                }

            }
            i = i + 1;

            if (i >= expr.length()) {
                if (priority < operators.length - 1) {
                    priority++;
//                    if(priority == 2)
//                        nextStoppingCharacters+="-"; // no longer needed, as î replaces '-' sign
                    i = 0;
                } else {
                    continueReplacing = false;
                }
            }
        }

        return expr.replace('î', '-'); // back to '-' sign
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
            if (equalIndex > 0 && expr.charAt(equalIndex) != '=') {
                // If there is no '=' (malformed expression)
                equalIndex += 1;
            } else {
                if ((equalIndex > 0 && expr.charAt(equalIndex - 1) == '=') || (equalIndex < exprLength - 1 && expr.charAt(equalIndex + 1) == '=')) {
                    // If it is a '==' we ignore it
                    equalIndex += 1;
                } else {
                    int startIndex = getPreviousExpressionFirstIndex(expr, equalIndex, "=*/^;%+,. ");
                    String variableName = expr.substring(startIndex, equalIndex).trim();
                    if (variableName.matches("\\w+")) {
                        variablesSet.add(variableName);
                    }
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
     * @param startIndex - index to start with to search a stopping characters
     * @param stoppingCharacters - a String containing stopping characters.
     * @return the starting index of the previous expression
     */
    private static int getPreviousExpressionFirstIndex(String expr, int startIndex, String stoppingCharacters) {
        //System.err.println("getPreviousExpressionFirstIndex:\n  "+expr+"\n"+repeat(" ",startIndex+2)+"^");
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
                //System.err.println("\n"+repeat(" ",i + 1 +2)+"|");
                return i + 1;
            }
        }

        // If there is no stopping character, the expression start at the
        // beginning of the sentence
        //System.err.println("\n" + repeat(" ", firstIndex + 2) + "|");
        return firstIndex;
    }

    /**
     * Get the index of the end of an expression
     * The function starts at the
     * given startIndex and return the index of the first character founded in
     * the stoppingCharacters string. This function ignore characters inside
     * brackets or paranthesis
     *
     * @param expr - the expression to check
     * @param startIndex - index to start with to search a stopping characters
     * @param stoppingCharacters - a String containing stopping characters.
     * @return the last index of the next expression
     */
    private static int getNextExpressionLastIndex(String expr, int startIndex, String stoppingCharacters) {
        //System.err.println("getNextExpressionLastIndex:\n  "+expr+"\n"+repeat(" ",startIndex+2)+"^");

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
                //System.err.println("\n" + repeat(" ", i - 1 + 2) + "|");
                return i - 1;
            }
        }

        // If there is no stopping character, the expression stop at the
        // end of the sentence
        //System.err.println("\n"+repeat(" ",lastIndex +2)+"|");
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
    public synchronized void end() {
        js = null;
        super.end();
    }

    @Override
    public boolean isAvailable() {
        return true;
    }

    public String gethomedir() {
        return System.getProperty("user.home");
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
    protected synchronized boolean silentlyVoidEval(String expression, boolean tryEval) {
        String jsExpr = "?";
        try {
            jsExpr = convertRtoJs(expression);
            this.js.eval(jsExpr);
        } catch (Exception e) {
            String ls = "?";
            try {
                ls = (this.js.eval("JSON.stringify(" + THIS_ENVIRONMENT + ")")).toString();
            } catch (Exception ee) {
                ls = ee.getMessage();
            }

            String msg = null;
            if (expression.contains("\n")) {
                msg = "Failed to evaluate code\n  ```{r}\n" + expression.replaceAll("^", "^  ") + "\n  ```\n as\n  ```{js}\n" + jsExpr.replaceAll("^", "^  ") + "\n  ```\n with variables: " + ls + "\n because: " + e.getMessage();
            } else {
                msg = "Failed to evaluate code\n  `{r} " + expression + " ` as `{js} " + jsExpr + " `\n with variables: " + ls + "\n because: " + e.getMessage();
            }
            log(msg, Level.ERROR);
            return false;
        }
        return true;
    }

    @Override
    protected synchronized Object silentlyRawEval(String expression, boolean tryEval) {
        Object result = null;
        String jsExpr = "?";
        try {
            jsExpr = convertRtoJs(expression);
            result = this.js.eval(jsExpr);
        } catch (Exception e) {
            String ls = "?";
            try {
                ls = (String) this.js.eval("JSON.stringify(" + THIS_ENVIRONMENT + ")").toString();
            } catch (Exception ee) {
                ls = ee.getMessage();
            }

            String msg = null;
            if (expression.contains("\n")) {
                msg = "Failed to evaluate code\n  ```{r}\n" + expression.replaceAll("^", "^  ") + "\n  ```\n as\n  ```{js}\n" + jsExpr.replaceAll("^", "^  ") + "\n  ```\n with variables: " + ls + "\n because: " + e.getMessage();
            } else {
                msg = "Failed to evaluate code\n  `{r} " + expression + " ` as `{js} " + jsExpr + " `\n with variables: " + ls + "\n because: " + e.getMessage();
            }
            log(msg, Level.ERROR);
            return new RException(msg);
        }
        return result;
    }

    @Override
    public synchronized boolean set(String varname, double[][] data, String... names) throws RException {

        note_code("`" + varname + "` <- " + (data == null ? "list()" : toRcode(data)));
        note_code("names(" + varname + ") <- " + toRcode(names));
        note_code("`" + varname + "` <- data.frame(" + varname + ")");

        // RList list = buildRList(data, names);
        // log(HEAD_SET + varname + " <- " + list, Level.INFO);
        varname = nameRtoJs(varname);
        String allnames = "";
        for (int i = 0; i < names.length; i++) {
            //names[i] = nameRtoJs(names[i]); No! this will override the names[i] value
            allnames = allnames + ",'" + names[i] + "'";
        }
        allnames = allnames.substring(1);
        try {
            //synchronized (js) {
            String dim = "[" + data.length + "," + data[0].length + "]";
            String stringMatrix = Arrays.deepToString(data);
            js.eval(varname + " = math.reshape(" + stringMatrix + ", " + dim + ")");

            js.eval(THIS_ENVIRONMENT + "." + varname + " = " + varname);
            js.eval(THIS_ENVIRONMENT + "." + varname + ".names = [" + allnames + "]");
            variablesSet.add(varname);
            //}
        } catch (Exception e) {
            log(HEAD_ERROR + " " + e.getMessage(), Level.ERROR);
            return false;
        }

        return true;
    }

    /**
     * Set R object in R env.
     *
     * @param varname R object name
     * @param var R object value
     * @return succeeded ?
     */
    @Override
    public synchronized boolean set(String varname, Object var) {
        note_code("`" + varname + "` <- " + toRcode(var));

        varname = nameRtoJs(varname);
        try {
            //synchronized (js) {
            // FIXME: find a better solution than this
            // For 2d double array, we need to instanciate the matrix with the function "math.reshape"
            // I don't know why but the function math.matrix doesn't create the same js object than math.reshape and
            // the output ScriptMirrorObject is uncastable in java double[][] array and operations on a math.matrix
            // object don't work.
            if (var instanceof double[][]) {
                double[][] var2DArray = (double[][]) var;
                String dim = "[" + var2DArray.length + "," + var2DArray[0].length + "]";
                String stringMatrix = Arrays.deepToString(var2DArray);
                js.eval(varname + " = math.reshape(" + stringMatrix + ", " + dim + ")");

                js.eval(THIS_ENVIRONMENT + "." + varname + " = " + varname);
                String allnames = "";
                for (int i = 0; i < var2DArray[0].length; i++) {
                    allnames = allnames + ",'X" + (i + 1) + "'";
                }
                allnames = allnames.substring(1);
                js.eval(THIS_ENVIRONMENT + "." + varname + ".names = [" + allnames + "]");
                variablesSet.add(varname);
            } else if (var instanceof double[]) {
                double[] var1DArray = (double[]) var;
                String dim = "[" + var1DArray.length + ",1]";
                String stringMatrix = Arrays.toString(var1DArray);
                js.eval(varname + " = " + Arrays.toString(var1DArray));

                js.eval(THIS_ENVIRONMENT + "." + varname + " = " + varname);
                variablesSet.add(varname);
            } else {
                js.put(varname, var);
                js.eval(THIS_ENVIRONMENT + "." + varname + " = " + varname);
                variablesSet.add(varname);
            }
            //}
        } catch (Exception e) {
            log(HEAD_ERROR + " " + e.getMessage(), Level.ERROR);
            return false;
        }

        return true;
    }

    public File putFileInWorkspace(File file) {
        if (file.isAbsolute()) {
            return file;
        }
        File rf = local2remotePath(file);
        if (!rf.getAbsolutePath().equals(file.getAbsolutePath())) {
            try {
                FileUtils.copyFile(file, rf);
            } catch (IOException ex) {
                log(IO_HEAD + ex.getMessage(), Level.ERROR);
            }
        }
        return rf;
    }

    public void getFileFromWorkspace(File file) {
        if (file.isAbsolute()) {
            return;
        }
        File rf = remote2localPath(file);
        if (file.getParentFile() != null) {
            if (!file.getParentFile().isDirectory()) {
                if (!file.getParentFile().mkdirs()) {
                    throw new IllegalArgumentException("Cannot create parent dir: " + file);
                }
            }
        }
        if (!rf.getAbsolutePath().equals(file.getAbsolutePath())) {
            try {
                FileUtils.copyFile(rf, new File(".", file.getPath()));
            } catch (IOException ex) {
                log(IO_HEAD + ex.getMessage(), Level.ERROR);
            }
        }
    }

    @Override
    public synchronized void source(File file) {
        file = putFileInWorkspace(file);

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
    public synchronized boolean rm(String... vars) throws RException {
        try {
            //synchronized (js) {
            for (String var : vars) {
                js.eval("delete " + THIS_ENVIRONMENT + "." + var + ";");
                variablesSet.remove(var);
                js.eval("delete " + var + ";");
            }
            //}
        } catch (Exception e) {
            log(HEAD_ERROR + " " + e.getMessage(), Level.ERROR);
            return false;
        }

        return true;
    }

    @Override
    public synchronized boolean rmAll() {
        try {
            //synchronized (js) {
            js.eval("delete " + envName + ";");
            variablesSet.clear();
            js.eval("var " + envName + " = math.clone({});");
            js.eval(THIS_ENVIRONMENT + " = " + envName);
            //}
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
        if (o instanceof Double) {
            return (Double) o; // because already cast in Nashorn/jdk11 (but not in Nashorn/jdk8 !!)
        }
        return (double) ScriptUtils.convert(o, double.class);
    }

    @Override
    public double[] asArray(Object o) throws ClassCastException {
        if (o instanceof double[]) {
            return (double[]) o; // because already cast in Nashorn/jdk11 (but not in Nashorn/jdk8 !!)
        } else if (o instanceof Double) {
            return new double[]{(double)o};
        }
        Object co = ScriptUtils.convert(o, double[].class);
        if (co instanceof Double) {
            return new double[]{(double)co};
        }            
        return (double[]) co; 
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
                    if (o instanceof ScriptObjectMirror) {
                        try {
                            v = (double[]) ((ScriptObjectMirror) o).to(double[].class);
                        } catch (Exception ex) {
                            //ex.printStackTrace();
                            throw new ClassCastException("[asMatrix] Cannot cast ScriptObjectMirror list element to double[] " + ((Map) o).get(k) + " for key " + k + " in " + o);
                        }
                    } else {
                        try {
                            v = (double[]) ((Map) o).get(k);
                        } catch (Exception ex) {
                            //ex.printStackTrace();
                            throw new ClassCastException("[asMatrix] Cannot cast list element to double[] " + ((Map) o).get(k) + " for key " + k + " in " + o);
                        }
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
                throw new ClassCastException("[asMatrix] Cannot cast Map to matrix: " + ex.getMessage());
            }
        }
    }

    @Override
    public String asString(Object o) throws ClassCastException {
        if (o instanceof String) {
            return (String) o; // because already cast in Nashorn/jdk11 (but not in Nashorn/jdk8 !!)
        }
        return (String) ScriptUtils.convert(o, String.class);
    }

    @Override
    public String[] asStrings(Object o) throws ClassCastException {
        if (o instanceof String[]) {
            return (String[]) o; // because already cast in Nashorn/jdk11 (but not in Nashorn/jdk8 !!)
        } else if (o instanceof String) {
            return new String[]{(String)o};
        }
        Object co = ScriptUtils.convert(o, String[].class);
        if (co instanceof String) {
            return new String[]{(String)co};
        }            
        return (String[]) co; 
    }

    @Override
    public int asInteger(Object o) throws ClassCastException {
        return (int) asDouble(o); // because int type does not exists in js
    }

    @Override
    public int[] asIntegers(Object o) throws ClassCastException {
        double[] d = asArray(o); // because int type does not exists in js
        int[] I = new int[d.length];
        for (int i =0; i<I.length; i++) {
            I[i] = (int) d[i];
        }
        return I;
    }

    @Override
    public boolean asLogical(Object o) throws ClassCastException {
        if (o instanceof Boolean) {
            return (Boolean) o; // because already cast in Nashorn/jdk11 (but not in Nashorn/jdk8 !!)
        }
        if (o instanceof RException) {
            throw new IllegalArgumentException("[asLogical] Exception: " + ((RException) o).getMessage());
        }
        return (boolean) ScriptUtils.convert(o, boolean.class);
    }

    @Override
    public boolean[] asLogicals(Object o) throws ClassCastException {
        if (o instanceof boolean[]) {
            return (boolean[]) o; // because already cast in Nashorn/jdk11 (but not in Nashorn/jdk8 !!)
        } else if (o instanceof Boolean) {
            return new boolean[]{(boolean)o};
        }
        Object co =  ScriptUtils.convert(o, boolean[].class);
        if (co instanceof Boolean) {
            return new boolean[]{(boolean)co};
        }            
        return (boolean[]) co; 
    }

    @Override
    public Map asList(Object o) throws ClassCastException {
        if (o instanceof Map) {
            return (Map) o; // because already cast in Nashorn/jdk11 (but not in Nashorn/jdk8 !!)
        }
        return (Map) ScriptUtils.convert(o, Map.class);
    }

    @Override
    public boolean isNull(Object o) {
        if (o == null) {
            return true;
        }
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
        if (o instanceof Integer) {
            return Double.valueOf((int) o);
        } else if (o instanceof ScriptObjectMirror) {
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

    Map<String, Set<String>> envVariables = new HashMap<>();

    @Override
    public void setGlobalEnv(String envName) {
        if (envName == null) {
            envName = ENVIRONMENT_DEFAULT;
        } else {
            envName = "__" + envName + "__";
        }

        try {
            if (asLogical(js.eval("typeof " + envName + " == 'undefined'"))) {// env still not exists            
                js.eval("var " + envName + " = math.clone({});");
            }
            js.eval(THIS_ENVIRONMENT + " = " + envName);
        } catch (ScriptException ex) {
            Log.Err.println(ex.getMessage());
        }

        String oldEnv = this.envName;
        envVariables.put(oldEnv, new TreeSet(variablesSet));

        variablesSet.clear();
        if (envVariables.containsKey(envName)) {
            variablesSet.addAll(envVariables.get(envName));
        }

        this.envName = envName;
    }

    @Override
    public void copyGlobalEnv(String envName) {
        if (envName == null) {
            envName = ENVIRONMENT_DEFAULT;
        } else {
            envName = "__" + envName + "__";
        }

        try {
            if (asLogical(js.eval("typeof " + envName + " == 'undefined'"))) // env still not exists            
            {
                js.eval("var " + envName + " = math.clone({});");
            }
        } catch (ScriptException ex) {
            Log.Err.println(ex.getMessage());
        }

        String[] ls = ls(true);
        for (String o : ls) {
            try {
                js.eval(envName + "." + o + " = " + this.envName + "." + o);
            } catch (ScriptException ex) {
                Log.Err.println(ex.getMessage());
            }
        }
        if (!envVariables.containsKey(envName)) {
            envVariables.put(envName, new TreeSet(variablesSet));
        } else {
            envVariables.get(envName).addAll(new TreeSet(variablesSet));
        }
    }

    private static String html_tmpl
            = "<html>\n"
            + "    <head>\n"
            + "        <script src=\"https://github.com/yannrichet/rsession/blob/master/src/main/resources/org/math/R/math.js\" type=\"text/javascript\"></script>\n"
            + "        <script src=\"https://github.com/yannrichet/rsession/blob/master/src/main/resources/org/math/R/rand.js\" type=\"text/javascript\"></script>\n"
            + "        <script src=\"https://github.com/yannrichet/rsession/blob/master/src/main/resources/org/math/R/R.js\" type=\"text/javascript\"></script>\n"
            + "    </head>"
            + "    <body>\n"
            + "        <code>\n"
            + "___R___"
            + "        </code>\n"
            + "        <script type = \"text/javascript\">\n"
            + "        ___JS___\n"
            + "        </script>\n"
            + "\n"
            + "        <form>\n"
            + "        ___INPUT___\n"
            + "        <br/>\n"
            + "        ___SUBMIT___\n"
            + "        </form>\n"
            + "    </body>\n"
            + "</html>";
    private static String input_tmpl = "        <input type=\"text\" name=\"inputform\" id=\"___ID___\" value=\"\">\n";
    private static String submit_tmpl = "        <input type=\"submit\" value=\"___F___\" onclick=\"___ONCLICK___\">\n";

    // maybe the worst idea I ever had...
    public static String HTMLfun(String Rcode, String fun, String... args) throws RException {

        R2jsSession R = new R2jsSession(System.out, null);
        String html = html_tmpl.replace("___JS___", R.convertRtoJs(Rcode).replace(R.THIS_ENVIRONMENT + ".", ""));
        html = html.replace("___R___", Rcode);
        html = html.replace("___f___", fun);
        String inputs = "";
        for (int i = 0; i < args.length; i++) {
            inputs = inputs + args[i] + ":" + input_tmpl.replace("___ID___", args[i]) + "<br/>";
            args[i] = "document.getElementById('" + args[i] + "').value";
        }
        html = html.replace("___INPUT___", inputs);

        html = html.replace("___SUBMIT___", submit_tmpl.replace("___ONCLICK___", "document.write(" + fun + "(" + cat(",", args) + "))"));

        return (html);
    }

    public static void main(String[] args) throws Exception {
        //args = new String[]{"install.packages('lhs',repos='\"http://cloud.r-project.org/\"',lib='.')", "1+1"};
        if (args == null || args.length == 0) {
            args = new String[10];
            for (int i = 0; i < args.length; i++) {
                args[i] = Math.random() + "+pi";
            }
        }
        R2jsSession R = new R2jsSession(System.out, null);

        for (int j = 0; j < args.length; j++) {
            System.out.print(args[j] + ": ");
            System.out.println(R.cast(R.rawEval(args[j])));
        }

        R.closeLog();

        System.out.println(R.notebook());
    }
}
