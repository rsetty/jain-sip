/**
 *
 */
package test.unit.gov.nist.javax.sip.stack.forkedinvite482;

import java.util.Hashtable;

import javax.sip.SipListener;
import javax.sip.SipProvider;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import test.tck.msgflow.callflows.AssertUntil;
import test.tck.msgflow.callflows.NetworkPortAssigner;
import test.tck.msgflow.callflows.ScenarioHarness;

/**
 * @author M. Ranganathan
 *
 */
public class AbstractForkedInviteTestCase extends ScenarioHarness implements
        SipListener {


    protected Shootist shootist;

    private static Logger logger = LogManager.getLogger("test.tck");


    protected Shootme shootme;

    private Proxy proxy;

    private static final int TIMEOUT = 4000;
    
    // private Appender appender;

    public AbstractForkedInviteTestCase() {

        super("forkedInviteTest", true);

        try {
            providerTable = new Hashtable();

        } catch (Exception ex) {
            logger.error("unexpected exception", ex);
            fail("unexpected exception ");
        }
    }

    public void setUp() {

        try {
            super.setUp(false);
            
            int shootitsPort = NetworkPortAssigner.retrieveNextPort();
            int proxyPort = NetworkPortAssigner.retrieveNextPort();
            int shootmePort = NetworkPortAssigner.retrieveNextPort();
          
            
            shootist = new Shootist(shootitsPort, proxyPort, getTiProtocolObjects());
            SipProvider shootistProvider = shootist.createSipProvider();
            providerTable.put(shootistProvider, shootist);

            this.shootme = new Shootme(shootmePort, getTiProtocolObjects());
            SipProvider shootmeProvider = shootme.createProvider();
            providerTable.put(shootmeProvider, shootme);
            shootistProvider.addSipListener(this);
            shootmeProvider.addSipListener(this);


            this.proxy = new Proxy(proxyPort, getRiProtocolObjects());
            proxy.setTargetPort(shootmePort);
            SipProvider provider = proxy.createSipProvider();
            provider.setAutomaticDialogSupportEnabled(false);
            providerTable.put(provider, proxy);
            provider.addSipListener(this);

            getTiProtocolObjects().start();
            if (getTiProtocolObjects() != getRiProtocolObjects())
                getRiProtocolObjects().start();
        } catch (Exception ex) {
            fail("unexpected exception ");
        }
    }


    public void tearDown() {
        try {
            assertTrue(
                    "Should see at most one dialog",
                    AssertUntil.assertUntil(shootist.getAssertion(), TIMEOUT));
            assertTrue(
                    "Should see invite",
                    AssertUntil.assertUntil(shootme.getAssertion(), TIMEOUT));
            assertTrue(
                    "Should see LOOP DETECTED",
                    AssertUntil.assertUntil(proxy.getAssertion(), TIMEOUT));
            getTiProtocolObjects().destroy();
            if (getRiProtocolObjects() != getTiProtocolObjects())
                getRiProtocolObjects().destroy();
            Thread.sleep(2000);
            this.providerTable.clear();

            super.logTestCompleted();
        } catch (Exception ex) {
            logger.error("unexpected exception", ex);
            fail("unexpected exception ");
        }
    }



}
