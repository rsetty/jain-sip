package test.tck.msgflow.callflows.redirect;

import javax.sip.SipListener;
import javax.sip.SipProvider;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import test.tck.msgflow.callflows.AssertUntil;
import test.tck.msgflow.callflows.ScenarioHarness;

/**
 * @author M. Ranganathan
 *
 */
public abstract class AbstractRedirectTestCase extends ScenarioHarness implements
        SipListener {

    protected Shootist shootist;

    protected Shootme shootme;
    
    private static final int TIMEOUT = 4000;

    private static Logger logger = LogManager.getLogger("test.tck");

    // private Appender appender;

    public AbstractRedirectTestCase() {

        super("redirect", true);

    }

    public void setUp() {
        try {
            super.setUp();

            logger.info("RedirectTest: setup()");
            shootist = new Shootist(getTiProtocolObjects());
            SipProvider shootistProvider = shootist.createProvider();
            providerTable.put(shootistProvider, shootist);

            shootme = new Shootme(getRiProtocolObjects());
            SipProvider shootmeProvider = shootme.createProvider();
            providerTable.put(shootmeProvider, shootme);
            shootistProvider.addSipListener(this);
            shootmeProvider.addSipListener(this);
            if (getTiProtocolObjects() != getRiProtocolObjects())
                getTiProtocolObjects().start();
            getRiProtocolObjects().start();
        } catch (Exception ex) {
            logger.error("unexpected excecption ", ex);
            fail("unexpected exception");
        }

    }

    public void tearDown() {
        try {
            
            assertTrue(AssertUntil.assertUntil(shootist.getAssertion(), TIMEOUT));
            assertTrue(AssertUntil.assertUntil(shootme.getAssertion(), TIMEOUT));
            super.tearDown();
            Thread.sleep(1000);
            this.providerTable.clear();

            logTestCompleted();
        } catch (Exception ex) {
            logger.error("unexpected exception", ex);
            fail("unexpected exception ");
        }

    }


}
