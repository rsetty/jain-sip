/*
* Conditions Of Use
*
* This software was developed by employees of the National Institute of
* Standards and Technology (NIST), and others.
* This software is has been contributed to the public domain.
* As a result, a formal license is not needed to use the software.
*
* This software is provided "AS IS."
* NIST MAKES NO WARRANTY OF ANY KIND, EXPRESS, IMPLIED
* OR STATUTORY, INCLUDING, WITHOUT LIMITATION, THE IMPLIED WARRANTY OF
* MERCHANTABILITY, FITNESS FOR A PARTICULAR PURPOSE, NON-INFRINGEMENT
* AND DATA ACCURACY.  NIST does not warrant or make any representations
* regarding the use of the software or the results thereof, including but
* not limited to the correctness, accuracy, reliability or usefulness of
* the software.
*
*
*/
/**
 *
 */
package test.unit.gov.nist.javax.sip.stack.no491;

import java.util.EventObject;

import javax.sip.DialogTerminatedEvent;
import javax.sip.IOExceptionEvent;
import javax.sip.RequestEvent;
import javax.sip.ResponseEvent;
import javax.sip.SipListener;
import javax.sip.SipProvider;
import javax.sip.TimeoutEvent;
import javax.sip.TransactionTerminatedEvent;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import gov.nist.javax.sip.SipStackImpl;
import test.tck.msgflow.callflows.AssertUntil;
import test.tck.msgflow.callflows.ScenarioHarness;

/**
 * @author M. Ranganathan
 *
 */
public class ReInviteTest extends ScenarioHarness implements SipListener {


    protected Shootist shootist;

    private Shootme shootme;

    private static Logger logger = LogManager.getLogger("test.tck");
    
    private static final int TIMEOUT = 60000;     

   
    private SipListener getSipListener(EventObject sipEvent) {
        SipProvider source = (SipProvider) sipEvent.getSource();
        SipListener listener = (SipListener) providerTable.get(source);
        assertTrue(listener != null);
        return listener;
    }

    @Override
    public String getName() {
    	return ReInviteTest.class.getName();
    }
    public ReInviteTest() {
        super("reinvitetest", true);
    }

    public void setUp() {

        try {
            this.transport = "udp";

            super.setUp();
            
            shootme = new Shootme(getTiProtocolObjects());
            SipProvider shootmeProvider = shootme.createSipProvider();
            providerTable.put(shootmeProvider, shootme);
            
            shootist = new Shootist(getRiProtocolObjects(), shootme);
            SipProvider shootistProvider = shootist.createSipProvider();
            providerTable.put(shootistProvider, shootist);
            
            
            shootistProvider.addSipListener(this);
            shootmeProvider.addSipListener(this);
            
            ((SipStackImpl)getTiProtocolObjects().sipStack).setIsBackToBackUserAgent(true);
            ((SipStackImpl)getRiProtocolObjects().sipStack).setIsBackToBackUserAgent(true);

            getRiProtocolObjects().start();
            if (getTiProtocolObjects() != getRiProtocolObjects())
                getTiProtocolObjects().start();
        } catch (Exception ex) {
            ex.printStackTrace();
            fail("unexpected exception ");
        }
    }

    public void testSendInvite() throws InterruptedException {
        this.shootist.sendInvite();
        AssertUntil.assertUntil(shootist.getAssertion(), TIMEOUT);
        AssertUntil.assertUntil(shootme.getAssertion(), TIMEOUT);            
    }
    
    public void tearDown() {
        try {            
            this.shootist.checkState();
            this.shootme.checkState();
            super.tearDown();
            Thread.sleep(1000);
            this.providerTable.clear();
            logTestCompleted();
        } catch (Exception ex) {
            ex.printStackTrace();
        }

    }

    public void processRequest(RequestEvent requestEvent) {
        getSipListener(requestEvent).processRequest(requestEvent);

    }

    public void processResponse(ResponseEvent responseEvent) {
        getSipListener(responseEvent).processResponse(responseEvent);

    }

    public void processTimeout(TimeoutEvent timeoutEvent) {
        getSipListener(timeoutEvent).processTimeout(timeoutEvent);
    }

    public void processIOException(IOExceptionEvent exceptionEvent) {
        fail("unexpected exception");

    }

    public void processTransactionTerminated(
            TransactionTerminatedEvent transactionTerminatedEvent) {
        getSipListener(transactionTerminatedEvent)
                .processTransactionTerminated(transactionTerminatedEvent);

    }

    public void processDialogTerminated(
            DialogTerminatedEvent dialogTerminatedEvent) {
        getSipListener(dialogTerminatedEvent).processDialogTerminated(
                dialogTerminatedEvent);

    }

}
