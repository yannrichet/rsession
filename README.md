Linux & MacOS: [![Build Status](https://travis-ci.org/yannrichet/rsession.png)](https://travis-ci.org/yannrichet/rsession)
Windows: [![AppVeyor Build Status](https://ci.appveyor.com/api/projects/status/github/yannrichet/rsession?branch=master&svg=true)](https://ci.appveyor.com/project/yannrichet/rsession)

[![codecov](https://codecov.io/gh/yannrichet/rsession/branch/master/graph/badge.svg)](https://codecov.io/gh/yannrichet/rsession)
[![Maven Central](https://maven-badges.herokuapp.com/maven-central/com.github.yannrichet/Rsession/badge.svg)](https://maven-badges.herokuapp.com/maven-central/com.github.yannrichet/Rsession)

# Rsession: R sessions wrapping for Java #

Rsession provides an easy to use java class giving access to remote or local R session. The back-end engine is Renjin (NEW) or Rserve, locally spawned automatically if necessary.
Rsession differs from Rserve or Renjin as it is a higher level API, and it includes server side startup of Rserve. Therefore, it is easier to use in some point of view, as it provides a multi session R engine (including for Windows, thanks to an ugly turn-around).

Other alternatives:
  * JRI, but it does not provide multi-sessions feature. If you just need one R session in your java code (or work with R environments), JRI is a good solution.

## Example Java code ##
```java
import static org.math.R.*;
...
 
    public static void main(String args[]) {
        Rsession s = RserveSession.newInstanceTry(System.out, null);

        double[] rand = (double[]) s.eval("rnorm(10)"); //create java variable from R command

        //...
        s.set("c", Math.random()); //create R variable from java one

        s.save(new File("save.Rdata"), "c"); //save variables in .Rdata
        s.rm("c"); //delete variable in R environment
        s.load(new File("save.Rdata")); //load R variable from .Rdata

        //...
        s.set("df", new double[][]{{1, 2, 3}, {4, 5, 6}, {7, 8, 9}, {10, 11, 12}}, "x1", "x2", "x3"); //create data frame from given vectors
        double value = (double) (s.eval("df$x1[3]")); //access one value in data frame

        //...
        s.toJPEG(new File("plot.jpg"), 400, 400, "plot(rnorm(10))"); //create jpeg file from R graphical command (like plot)

        String html = s.asHTML("summary(rnorm(100))"); //format in html using R2HTML
        System.out.println(html);

        String txt = s.asString("summary(rnorm(100))"); //format in text
        System.out.println(txt);

        //...
        System.out.println(s.installPackage("sensitivity", true)); //install and load R package
        System.out.println(s.installPackage("wavelets", true));

        s.end();
    }
```
## Use it ##

### Using Renjin: ###

Add `lib/rsession.jar:lib/renjin*.jar` in your project classpath: 
  * copy https://github.com/yannrichet/rsession/blob/master/Rsession/dist/rsession.jar (latest commit version)
  * copy Renjin https://nexus.bedatadriven.com/service/local/artifact/maven/redirect?r=renjin-release&g=org.renjin&a=renjin-script-engine&c=jar-with-dependencies&v=RELEASE&e=jar

### Using Rserve: ###

Install R from http://cran.r-project.org.

Add `lib/rsession.jar:lib/Rserve*.jar:lib/REngine*.jar` in your project classpath: 
  * copy https://github.com/yannrichet/rsession/blob/master/Rsession/dist/rsession.jar (latest commit version)
  * copy Rserve https://search.maven.org/remotecontent?filepath=org/rosuda/REngine/Rserve/1.8.1/Rserve-1.8.1.jar
  * copy REngine https://search.maven.org/remotecontent?filepath=org/rosuda/REngine/REngine/2.1.0/REngine-2.1.0.jar

### Using a maven dependency ###
```xml
<dependencies>
...
    <dependency>
      <groupId>com.github.yannrichet</groupId>
      <artifactId>Rsession</artifactId>
      <version>2.0.3</version>
    </dependency>
...
</dependencies>
```


Then, use it in your code:
  * create new Rsession:
    * Renjin (pure Java, no R install necessary):
      ```java
      Rsession s = new RenjinSession(System.out,null);
      ```
    * OR local spawning of Rserve (for Windows XP, Mac OS X, Linux 32 & 64):
      ```java
      Rsession s = RserveSession.newInstanceTry(System.out,null);
      ```
    * OR connect to remote Rserve (previously started with /usr/bin/R CMD Rserve --vanilla --RS-conf Rserve.conf):
      ```java
      Rsession s = RserveSession.newRemoteInstance(System.out,RserverConf.parse("R://192.168.1.1"));
      //connect to local Rserve (previously started with /usr/bin/R CMD Rserve --vanilla --RS-conf Rserve.conf):
      session s = RserveSession.newLocalInstance(System.out,null); 
      ```
  * do your work in R and get Java objects
    * create Java objects from R command using
    ```java
    Object o = s.eval("...",HashMap<String,Object> vars)
    ```
    (Object o is automatically cast to double, double[], double[][],String, String[], ...)
    * OR
      * create your R objects using `s.set("...",...)`
      * call any R command using `s.eval("...")`
      * cast to Java objects using `Rsession.cast(...)`
      * if needed use remote R packages install & load: `s.installPackage("...", true);`
      * you can access R command answers as string using: `s.asHTML("...")` `s.asString("...")` , `s.toJPEG(File f,"...")` 
  * finally close your Rsession instance: `s.end(); `

![Analytics](https://ga-beacon.appspot.com/UA-109580-20/rsession)
