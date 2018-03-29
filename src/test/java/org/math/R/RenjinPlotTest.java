package org.math.R;

import java.awt.Graphics2D;
import java.awt.color.ColorSpace;
import java.awt.image.BufferedImage;
import java.io.FileOutputStream;
import java.io.IOException;
import javax.imageio.ImageIO;
import org.junit.Before;
import org.junit.Test;
import org.renjin.eval.Context;
import org.renjin.graphics.Color;
import org.renjin.graphics.GraphicParameters;
import org.renjin.graphics.GraphicsDevice;
import org.renjin.graphics.GraphicsDeviceDriver;
import org.renjin.graphics.GraphicsDevices;
import org.renjin.graphics.device.AwtGraphicsDevice;
import org.renjin.graphics.geom.Dimension;
import org.renjin.graphics.geom.Rectangle;
import org.renjin.parser.RParser;
import org.renjin.primitives.Warning;
import org.renjin.sexp.DoubleArrayVector;
import org.renjin.sexp.Environment;
import org.renjin.sexp.FunctionCall;
import org.renjin.sexp.Logical;
import org.renjin.sexp.LogicalArrayVector;
import org.renjin.sexp.Null;
import org.renjin.sexp.SEXP;
import org.renjin.sexp.StringArrayVector;
import org.renjin.sexp.Symbol;

/**
 *
 * @author richet
 */
public class RenjinPlotTest {

    protected Environment global;
    protected Environment base;
    protected Context topLevelContext;
    public static final SEXP NULL = Null.INSTANCE;
    public static final SEXP CHARACTER_0 = new StringArrayVector();
    public static final SEXP DOUBLE_0 = new DoubleArrayVector();

    public SEXP GlobalEnv;

    public static void main(String args[]) {
        org.junit.runner.JUnitCore.main(RenjinPlotTest.class.getName());
    }

    @Before
    public void setupPackages() {
        assumingBasePackagesLoad();
    }

    @Before
    public final void setUp() {
        topLevelContext = Context.newTopLevelContext();
        global = topLevelContext.getEnvironment();
        base = topLevelContext.getBaseEnvironment();
        GlobalEnv = global;
    }

    protected SEXP eval(String source) {
        SEXP result = evaluate(source);
        printWarnings();
        return result;
    }

    private void printWarnings() {
        SEXP warnings = topLevelContext.getBaseEnvironment().getVariable(Warning.LAST_WARNING);
        if (warnings != Symbol.UNBOUND_VALUE) {
            topLevelContext.evaluate(FunctionCall.newCall(Symbol.get("print.warnings"), warnings),
                    topLevelContext.getBaseEnvironment());

            System.out.println();
        }
    }

    /**
     * Fully initializes the context, loading the R-language base packages and
     * recommended packages. If this initializes fails, an
     * AssumptionViolatedError exception will be thrown rather than an error.
     */
    protected void assumingBasePackagesLoad() {
        try {
            topLevelContext.init();
        } catch (Exception e) {
            System.err.println(new Exception("Exception thrown while loading R-language packages"));
        }
    }

    protected SEXP evaluate(String source) {
        if (!source.endsWith(";") && !source.endsWith("\n")) {
            source = source + "\n";
        }
        SEXP exp = RParser.parseSource(source);

        return topLevelContext.evaluate(exp);
    }

    protected SEXP c(boolean... values) {
        return new LogicalArrayVector(values);
    }

    protected SEXP c(Logical... values) {
        return new LogicalArrayVector(values);
    }

    protected SEXP c(String... values) {
        return new StringArrayVector(values);
    }

    protected SEXP c(double... values) {
        return new DoubleArrayVector(values);
    }

    @Test
    public void coordinateSystems() throws IOException {

        // compared to output from R2.12
        // with png(filename='test.png', width=420, height=340)
        GraphicsDeviceDriverStub driver = new GraphicsDeviceDriverStub(420, 340);
        GraphicsDevice device = new GraphicsDevice(driver);
        topLevelContext.getSingleton(GraphicsDevices.class).setActive(device);

        /*assertThat("din", eval("par('din')"), equalTo(c(driver.getSize().getWidth(), driver.getSize().getHeight())));
         assertThat("fig", eval("par('fig')"), equalTo(c(0, 1, 0, 1)));
         assertThat("mar", eval("par('mar')"), equalTo(c(5.1,4.1,4.1,2.1)));
         assertThat("cra", eval("par('cra')"), equalTo(c(10.8, 14.4)));
         assertThat("pin", eval("par('pin')"), closeTo(c( 4.593333, 2.882222), 0.0001));
         assertThat("plt", eval("par('plt')"), closeTo(c( 0.1405714, 0.9280000, 0.2160000, 0.8263529), 0.0001));*/
        eval("graphics::plot.new()");

        /*assertThat("usr", eval("par('usr')"), closeTo(c(-0.04,1.04,-0.04,1.04), 0.001));
         assertThat(eval("grconvertX(0, from='user', to='device')"), closeTo(c(71.28889), 0.0001));
         assertThat(eval("grconvertY(0, from='user', to='device')"), closeTo(c(258.8741), 0.0001));
         assertThat(eval("grconvertY(1, from='user', to='device')"), closeTo(c(66.72593), 0.0001));*/
    }

    @Test
    public void plotWindow() throws IOException {

        // compared to output from R2.12
        // with png(filename='test.png', width=420, height=340)
        GraphicsDeviceDriverStub driver = new GraphicsDeviceDriverStub(420, 340);
        GraphicsDevice device = new GraphicsDevice(driver);
        topLevelContext.getSingleton(GraphicsDevices.class).setActive(device);

        eval("graphics::barplot(c(1,2,3), main='Distribution', xlab='Number')");

        /*assertThat("usr", eval("par('usr')"), closeTo(c(0.064, 3.736, -0.030, 3.00), 0.001));
         assertThat("pin", eval("par('pin')"), closeTo(c(4.59333, 2.88222), 0.001));
         assertThat(eval("grconvertX(0, from='user', to='device')"), closeTo(c(53.27582), 0.0001));
         assertThat(eval("grconvertY(0, from='user', to='device')"), closeTo(c(264.5053), 0.0001));
         assertThat(eval("grconvertY(1, from='user', to='device')"), closeTo(c(196.0169), 0.0001));*/
    }

    @Test
    public void awtIntegrationTest() throws IOException {
        BufferedImage image = new BufferedImage(420, 340, ColorSpace.TYPE_RGB);

        Graphics2D g2d = (Graphics2D) image.getGraphics();
        g2d.setColor(java.awt.Color.WHITE);
        g2d.setBackground(java.awt.Color.WHITE);
        g2d.fill(g2d.getDeviceConfiguration().getBounds());

        AwtGraphicsDevice driver = new AwtGraphicsDevice(g2d);
        topLevelContext.getSingleton(GraphicsDevices.class).setActive(new GraphicsDevice(driver));

        try {
            eval("graphics::plot(c(1,2,3), main='Distribution', xlab='Number')");
        } finally {
            FileOutputStream fos = new FileOutputStream("simplestPossible.png");
            ImageIO.write(image, "PNG", fos);
            fos.close();

        }
    }

    static class GraphicsDeviceDriverStub implements GraphicsDeviceDriver {

        private Rectangle deviceRegion;
        private Dimension size;

        public GraphicsDeviceDriverStub(Rectangle deviceRegion, Dimension size) {
            super();
            this.deviceRegion = deviceRegion;
            this.size = size;
        }

        public GraphicsDeviceDriverStub(int widthPixels, int heightPixels) {
            deviceRegion = new Rectangle(0, widthPixels, 0, heightPixels);
            size = new Dimension(widthPixels / 72d, heightPixels / 72d);
        }

        @Override
        public Dimension getInchesPerPixel() {
            return new Dimension(size.getWidth() / deviceRegion.getWidth(),
                    size.getHeight() / deviceRegion.getHeight());
        }

        @Override
        public Dimension getCharacterSize() {
            return new Dimension(10.8, 14.4);
        }

        @Override
        public void drawRectangle(Rectangle bounds, Color fillColor,
                Color borderColor, GraphicParameters parameters) {

        }

        @Override
        public Rectangle getDeviceRegion() {
            return deviceRegion;
        }

        public Dimension getSize() {
            return size;
        }

    }
}
