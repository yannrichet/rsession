package org.math.R;

import java.io.PrintStream;

public class RLogPrintStream implements RLog {

    PrintStream p;

    public RLogPrintStream(PrintStream p) {
        this.p = p;
    }

    @Override
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

    @Override
    public void closeLog() {
        if (p != null) {
            p.close();
        }
    }

}
