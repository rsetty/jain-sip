/*
 * This source code has been contributed to the public domain by Mobicents
 *
 * This software is provided by NIST as a service and is expressly
 * provided "AS IS."  NIST MAKES NO WARRANTY OF ANY KIND, EXPRESS, IMPLIED
 * OR STATUTORY, INCLUDING, WITHOUT LIMITATION, THE IMPLIED WARRANTY OF
 * MERCHANTABILITY, FITNESS FOR A PARTICULAR PURPOSE, NON-INFRINGEMENT
 * AND DATA ACCURACY.  NIST does not warrant or make any representations
 * regarding the use of the software or the results thereof, including but
 * not limited to the correctness, accuracy, reliability or usefulness of
 * the software.
 *
 * Permission to use this software is contingent upon your acceptance
 * of the terms of this agreement.
 */
 package test.unit.gov.nist.javax.sip.stack.tx.timeout;

import javax.sip.SipProvider;
import javax.sip.message.Request;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import gov.nist.javax.sip.SipStackImpl;
import test.tck.msgflow.callflows.AssertUntil;
import test.tck.msgflow.callflows.ProtocolObjects;
import test.tck.msgflow.callflows.ScenarioHarness;
import test.tck.msgflow.callflows.TestAssertion;

/**
 * Non regression test for http://java.net/jira/browse/JSIP-420
 * 
 * @author jean deruelle
 *
 */
public class TxTimeoutTest extends ScenarioHarness {

	private ProtocolObjects shootistProtocolObjs;

	private ProtocolObjects shootmeProtocolObjs;
	    
    protected Shootist shootist;

    private Shootme shootme;
    
    private static final Logger logger = LogManager.getLogger("test.tck");
    private static final int TIMEOUT = 60000;


    public TxTimeoutTest() {
        super("DialogTerminationOn500Test", true);

    }

    public void setUp() {
        doSetUp();

    }

    private void doSetUp() {
        try {            
            super.setUp();            
        } catch (Exception ex) {
            ex.printStackTrace();
            fail("unexpected exception ");
        }
    }
    
 // check that the apps gets called for early dialog timeout event when no ack is received nor sent
    public void testInviteTransactionTimeout() throws InterruptedException {
        
            try {


                this.shootmeProtocolObjs = new ProtocolObjects("shootme", "gov.nist", "udp", false,false, true);
                ((SipStackImpl)shootmeProtocolObjs.sipStack).setAggressiveCleanup(true);                
                shootme = new Shootme(shootmeProtocolObjs);
                SipProvider shootmeProvider = shootme.createSipProvider();                
                
            	this.shootistProtocolObjs = new ProtocolObjects("shootist", "gov.nist", "udp", false,false, true);
                shootist = new Shootist(shootistProtocolObjs, shootme);
                SipProvider shootistProvider = shootist.createSipProvider();                
               
                shootist.init();
                providerTable.put(shootistProvider, shootist);

                shootme.init(false);                
                providerTable.put(shootmeProvider, shootme);
                shootistProvider.addSipListener(shootist);
                shootmeProvider.addSipListener(shootme);

                getRiProtocolObjects().start();
                if (getTiProtocolObjects() != getRiProtocolObjects())
                    getTiProtocolObjects().start();
                
                ((SipStackImpl)shootmeProtocolObjs.sipStack).setMaxTxLifetimeInvite(30);
                ((SipStackImpl)shootistProtocolObjs.sipStack).setMaxTxLifetimeInvite(30);
                
                this.shootist.sendRequest(Request.INVITE);
                Thread.currentThread().sleep(10000);
            } catch (Exception e) {
                // TODO Auto-generated catch block
                e.printStackTrace();
                fail("unexpected exception ");
            }
            
            assertTrue("Test Failed - Didnt receive Dialog Timeout Event", 
                AssertUntil.assertUntil(new TestAssertion() {
                    @Override
                    public boolean assertCondition() {
                        return shootme.checkState() && shootist.checkState();
                    };
                }, TIMEOUT)
            );            


//            doTearDown(false);
//            doSetUp();
    }
    
 // check that the apps gets called for early dialog timeout event when no ack is received nor sent
    public void testNonInviteTransactionTimeout() throws InterruptedException {
        
            try {

                this.shootmeProtocolObjs = new ProtocolObjects("shootme", "gov.nist", "udp", true,false, true);
                ((SipStackImpl)shootmeProtocolObjs.sipStack).setAggressiveCleanup(true);                
                shootme = new Shootme(shootmeProtocolObjs);
                SipProvider shootmeProvider = shootme.createSipProvider();                
                
            	this.shootistProtocolObjs = new ProtocolObjects("shootist", "gov.nist", "udp", true,false, true);
                shootist = new Shootist(shootistProtocolObjs,shootme);
                SipProvider shootistProvider = shootist.createSipProvider();                
               
                shootist.init();
                providerTable.put(shootistProvider, shootist);

                shootme.init(false);                
                providerTable.put(shootmeProvider, shootme);
                shootistProvider.addSipListener(shootist);
                shootmeProvider.addSipListener(shootme);

                getRiProtocolObjects().start();
                if (getTiProtocolObjects() != getRiProtocolObjects())
                    getTiProtocolObjects().start();
                
                ((SipStackImpl)shootmeProtocolObjs.sipStack).setMaxTxLifetimeNonInvite(30);
                ((SipStackImpl)shootistProtocolObjs.sipStack).setMaxTxLifetimeNonInvite(30);
                
                this.shootist.sendRequest(Request.MESSAGE);
                Thread.currentThread().sleep(10000);
            } catch (Exception e) {
                // TODO Auto-generated catch block
                e.printStackTrace();
                fail("unexpected exception ");
            }

            
            assertTrue("Test Failed - Didnt receive Dialog Timeout Event", 
                AssertUntil.assertUntil(new TestAssertion() {
                    @Override
                    public boolean assertCondition() {
                        return shootme.checkState() && shootist.checkState();
                    };
                }, TIMEOUT)
            );               

//            doTearDown(false);
//            doSetUp();
    }
    
    public void tearDown() {
        doTearDown(true);

    }

    private void doTearDown(boolean definetly) {
        try {
            Thread.sleep(3000);
            // this.shootist.checkState();
            // this.shootme.checkState();
            shootmeProtocolObjs.destroy();
            shootistProtocolObjs.destroy();
            Thread.sleep(1000);
            this.providerTable.clear();
            if (definetly)
                logTestCompleted();
        } catch (Exception ex) {
            ex.printStackTrace();
        }
    }
}
