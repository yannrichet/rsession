package org.math.R.R2js;

import org.math.R.RLog;
import org.math.R.RLogPrintStream;

import java.io.PrintStream;
import java.util.Properties;

public class R2jsBuilder {

//    public static R2jsSession newInstance(RLog console, Properties properties) {
//        super(console, properties);
//    }
//
//
//    public static R2jsSession newInstance(PrintStream p, Properties properties) {
//        return newInstance(p, properties, null);
//    }

    public static R2jsSession newInstance(RLog console, Properties properties) {
        return newInstance(console, properties, null);
    }

    public static R2jsSession newInstance(final PrintStream p, Properties properties) {
        return newInstance(p, properties, null);
    }

    public static R2jsSession newInstance(final PrintStream p, Properties properties, String environmentName) {
        return newInstance(new RLogPrintStream(p), properties, environmentName);
    }

    public static R2jsSession newInstance(RLog console, Properties properties, String environmentName) {
        String javaVersion = System.getProperty("java.version");

        if (javaVersion.startsWith("21")) {
            //return new SubClassJava11();
            return null; // TODO graaljs builder
        } else {
            return new R2jsNashornSession(console, properties, environmentName);
        }
    }


}
