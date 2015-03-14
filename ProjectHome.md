| **Professional support is now available at https://sites.google.com/site/mulabsltd/products/rsession**|
|:|

# Rsession: R sessions wrapping for Java #

Rsession provides an easy to use java class giving access to remote or local R session. The back-end engine is [Rserve](http://www.rforge.net/Rserve/index.html) 0.6, locally spawned automatically if necessary.

Rsession differs from Rserve as it is a higher level API, and it includes server side startup of Rserve.
Therefore, it is easier to use in some point of vue, as it provides a multi session R engine (including for Windows, thanks to an ugly turn-around).

Another alternative is JRI, but it does not provide multi-sessions feature. If you just need one R session in your java code, JRI is a good solution.


# Example Java code #

```
import static org.math.R.Rsession.*;
...
 
    public static void main(String args[]) {
        Rsession s = Rsession.newInstanceTry(System.out);
 
        double[] rand = (double[]) s.eval("rnorm(10)",null); //create java variable from R command
 
        ...
 
        s.set("c", Math.random()); //create R variable from java one
 
        s.save(new File("save.Rdata"), "c"); //save variables in .Rdata
        s.rm("c"); //delete variable in R environment
        s.load(new File("save.Rdata")); //load R variable from .Rdata
 
        ...
 
        s.set("df", new double[][]{{1, 2, 3}, {4, 5, 6}, {7, 8, 9}, {10, 11, 12}}, "x1", "x2", "x3"); //create data frame from given vectors
        double value= (Double) (cast(s.eval("df$x1[3]"))); //access one value in data frame
 
        ...
 
        s.toJPEG(new File("plot.jpg"), 400, 400, "plot(rnorm(10))"); //create jpeg file from R graphical command (like plot)
 
        String html = s.asHTML("summary(rnorm(100))"); //format in html using R2HTML
        System.out.println(html);
 
        String txt = s.asString("summary(rnorm(100))"); //format in text
        System.out.println(txt);
 
        ...
 
        System.out.println(s.installPackage("sensitivity", true)); //install and load R package
        System.out.println(s.installPackage("wavelets", true));
 
        s.end();
    }
```

# Use it #
  1. install [R](R.md) from http://cran.r-project.org (tested with 2.9 version)
  1. get Rsession libs:
    * **(hard)** build it yourself:
      1. checkout Rsession: <br> <code>svn checkout http://rsession.googlecode.com/svn/trunk/ rsession-read-only</code>
<ol><li>build Rsession: (command line inside Rsession dir) <br><code>ant dist</code>
</li><li>copy 'dist/lib' directory inside your java project (as 'lib' directory)<br>
</li></ol><ul><li><b>(easy)</b> or just unzip prebuilt archive: <a href='http://rsession.googlecode.com/files/libRsession.zip'>http://rsession.googlecode.com/files/libRsession.zip</a> in 'lib' directory<br>
</li></ul><ol><li>add <code>lib/Rsession.jar:lib/Rserve.jar:lib/REngine.jar</code> in your project classpath<br>
</li><li>to use in your code:<br>
<ol><li>create new <a href='http://rsession.googlecode.com/svn/trunk/Rsession/doc/org/math/R/Rsession.html'>Rsession</a>:<br>
<ul><li><b>(easy)</b> local spawning of Rserve (for Windows XP, Mac OS X, Linux 32 & 64):<br><code>Rsession s = Rsession.newInstanceTry(System.out,null);</code>
</li><li>connect to remote Rserve (previously started with <code>/usr/bin/R CMD Rserve --vanilla --RS-conf Rserve.conf</code>): <br><code>Rsession s = Rsession.newRemoteInstance(System.out,RserverConf.parse("R://192.168.1.1"));</code>
</li><li>connect to local Rserve (previously started with <code>/usr/bin/R CMD Rserve --vanilla --RS-conf Rserve.conf</code>): <br><code>Rsession s = Rsession.newLocalInstance(System.out,null);</code>
</li></ul></li><li>do your work in R and get Java objects<br>
<ul><li>create Java objects from R command using <br><code>Object o = s.eval("...",HashMap&lt;String,Object&gt; vars)</code> <br>(Object o is automatically cast to double, double<a href='.md'>.md</a>, double<a href='.md'>.md</a><a href='.md'>.md</a>,String, String<a href='.md'>.md</a>, ...)<br>
</li><li>OR<br>
<ol><li>create your R objects using <code>s.set("...",...)</code>
</li><li>call any R command using <code>s.evalR("...")</code>
</li><li>cast to Java objects using <code>Rsession.Rcast(...)</code>
</li></ol></li><li>if needed use remote R packages install & load: <code>s.installPackage("...", true);</code>
</li><li>you can access R command answers as string using: <code>s.asHTML("...")</code> <code>s.asString("...")</code> , <code>s.toJPEG(File f,"...")</code>
</li></ul></li><li>finally close your Rserve instance: <code>s.end();</code>