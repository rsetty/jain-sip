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
package test.unit.gov.nist.javax.sip.stack.reinvitechallenge;

import javax.sip.ClientTransaction;
import javax.sip.Dialog;
import javax.sip.DialogState;
import javax.sip.DialogTerminatedEvent;
import javax.sip.IOExceptionEvent;
import javax.sip.ListeningPoint;
import javax.sip.RequestEvent;
import javax.sip.ResponseEvent;
import javax.sip.ServerTransaction;
import javax.sip.SipListener;
import javax.sip.SipProvider;
import javax.sip.Transaction;
import javax.sip.TransactionTerminatedEvent;
import javax.sip.address.Address;
import javax.sip.address.SipURI;
import javax.sip.header.CSeqHeader;
import javax.sip.header.ContactHeader;
import javax.sip.header.ContentTypeHeader;
import javax.sip.header.MaxForwardsHeader;
import javax.sip.header.ToHeader;
import javax.sip.header.ViaHeader;
import javax.sip.message.Request;
import javax.sip.message.Response;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import gov.nist.javax.sip.SipStackExt;
import gov.nist.javax.sip.clientauthutils.AuthenticationHelper;
import test.tck.msgflow.callflows.NetworkPortAssigner;
import test.tck.msgflow.callflows.ProtocolObjects;

/**
 * This class is a UAC template.
 *
 * @author M. Ranganathan
 */

public class Shootme  implements SipListener {


    private ProtocolObjects  protocolObjects;


    // To run on two machines change these to suit.
    public static final String myAddress = "127.0.0.1";

    public final int myPort = NetworkPortAssigner.retrieveNextPort();

    private ServerTransaction inviteTid;

    private static Logger logger = LogManager.getLogger(Shootme.class);

    private Dialog dialog;

    private boolean okRecieved;

    class ApplicationData {
        protected int ackCount;
    }

    public Shootme(ProtocolObjects protocolObjects) {
        this.protocolObjects = protocolObjects;
    }


    public void processRequest(RequestEvent requestEvent) {
        Request request = requestEvent.getRequest();
        ServerTransaction serverTransactionId = requestEvent
                .getServerTransaction();

        logger.info("\n\nRequest " + request.getMethod()
                + " received at " + protocolObjects.sipStack.getStackName()
                + " with server transaction id " + serverTransactionId);

        if (request.getMethod().equals(Request.INVITE)) {
            processInvite(requestEvent, serverTransactionId);
        } else if (request.getMethod().equals(Request.ACK)) {
            processAck(requestEvent, serverTransactionId);
        } else if (request.getMethod().equals(Request.BYE)) {
            processBye(requestEvent, serverTransactionId);
        }

    }

    /**
     * Process the ACK request. Send the bye and complete the call flow.
     */
    public void processAck(RequestEvent requestEvent,
            ServerTransaction serverTransaction) {
        SipProvider sipProvider = (SipProvider) requestEvent.getSource();
        try {
            logger.info("shootme: got an ACK "
                    + requestEvent.getRequest());

            int ackCount = ((ApplicationData) dialog.getApplicationData()).ackCount;
            if (ackCount == 1) {
                dialog = inviteTid.getDialog();
                Thread.sleep(100);
                this.sendReInvite(sipProvider);

            } 
            ((ApplicationData) dialog.getApplicationData()).ackCount++;
               
          
                
        } catch (Exception ex) {
            String s = "Unexpected error";
            logger.error(s,ex);
            ReInviteTest.fail(s);
        }
    }

    /**
     * Process the invite request.
     */
    public void processInvite(RequestEvent requestEvent,
            ServerTransaction serverTransaction) {
        SipProvider sipProvider = (SipProvider) requestEvent.getSource();
        Request request = requestEvent.getRequest();
        logger.info("Got an INVITE  " + request);
        try {
            logger.info("shootme: got an Invite sending OK");
            // logger.info("shootme: " + request);
            Response response = protocolObjects.messageFactory.createResponse(180, request);
            ToHeader toHeader = (ToHeader) response.getHeader(ToHeader.NAME);
            toHeader.setTag("4321");
            Address address = protocolObjects.addressFactory.createAddress("Shootme <sip:"
                    + myAddress + ":" + myPort + ">");
            ContactHeader contactHeader = protocolObjects.headerFactory
                    .createContactHeader(address);
            response.addHeader(contactHeader);
            ServerTransaction st = requestEvent.getServerTransaction();

            if (st == null) {
                st = sipProvider.getNewServerTransaction(request);
                logger.info("Server transaction created!" + request);

                logger.info("Dialog = " + st.getDialog());
                if (st.getDialog().getApplicationData() == null) {
                    st.getDialog().setApplicationData(new ApplicationData());
                }
            } else {
                // If Server transaction is not null, then
                // this is a re-invite.
                logger.info("This is a RE INVITE ");
                ReInviteTest.assertSame("Dialog mismatch ", st.getDialog(),this.dialog);
            }

            // Thread.sleep(5000);
            logger.info("got a server tranasaction " + st);
            byte[] content = request.getRawContent();
            if (content != null) {
                logger.info(" content = " + new String(content));
                ContentTypeHeader contentTypeHeader = protocolObjects.headerFactory
                        .createContentTypeHeader("application", "sdp");
                logger.info("response = " + response);
                response.setContent(content, contentTypeHeader);
            }
            dialog = st.getDialog();
            
            if (dialog != null) {
                logger.info("Dialog " + dialog);
                logger.info("Dialog state " + dialog.getState());
            }
            st.sendResponse(response);
            response = protocolObjects.messageFactory.createResponse(200, request);
            toHeader = (ToHeader) response.getHeader(ToHeader.NAME);
            toHeader.setTag("4321");
            // Application is supposed to set.
            response.addHeader(contactHeader);
            st.sendResponse(response);
            logger.info("TxState after sendResponse = " + st.getState());
            this.inviteTid = st;
        } catch (Exception ex) {
            String s = "unexpected exception";

            logger.error(s,ex);
            ReInviteTest.fail(s);
        }
    }

    public void sendReInvite(SipProvider sipProvider) throws Exception {
        Request inviteRequest = dialog.createRequest(Request.INVITE);
        ((SipURI) inviteRequest.getRequestURI()).removeParameter("transport");
        MaxForwardsHeader mf = protocolObjects.headerFactory.createMaxForwardsHeader(10);
        inviteRequest.addHeader(mf);
        ((ViaHeader) inviteRequest.getHeader(ViaHeader.NAME))
                .setTransport(protocolObjects.transport);
        Address address = protocolObjects.addressFactory.createAddress("Shootme <sip:"
                + myAddress + ":" + myPort + ">");
        ContactHeader contactHeader = protocolObjects.headerFactory
                .createContactHeader(address);
        inviteRequest.addHeader(contactHeader);
        ClientTransaction ct = sipProvider
                .getNewClientTransaction(inviteRequest);
        dialog.sendRequest(ct);
    }

    /**
     * Process the bye request.
     */
    public void processBye(RequestEvent requestEvent,
            ServerTransaction serverTransactionId) {

        SipProvider sipProvider = (SipProvider) requestEvent.getSource();
        Request request = requestEvent.getRequest();
        try {
            logger.info("shootme:  got a bye sending OK.");
            Response response = protocolObjects.messageFactory.createResponse(200, request);
            if (serverTransactionId != null) {
                serverTransactionId.sendResponse(response);
                logger.info("Dialog State is "
                        + serverTransactionId.getDialog().getState());
            } else {
                logger.info("null server tx.");
                // sipProvider.sendResponse(response);
            }

        } catch (Exception ex) {
            String s = "Unexpected exception";
            logger.error(s,ex);
            ReInviteTest.fail(s);

        }
    }

    public void processResponse(ResponseEvent responseReceivedEvent) {
        logger.info("Got a response");
        Response response = (Response) responseReceivedEvent.getResponse();
        Transaction tid = responseReceivedEvent.getClientTransaction();
        
        SipProvider provider = (SipProvider) responseReceivedEvent.getSource();

        logger.info("Response received with client transaction id "
                + tid + ":\n" + response);
        try {
            if (response.getStatusCode() == Response.OK
                    && ((CSeqHeader) response.getHeader(CSeqHeader.NAME))
                            .getMethod().equals(Request.INVITE)) {
                this.okRecieved  = true;
                ReInviteTest.assertNotNull( "INVITE 200 response should match a transaction", tid );
                Dialog dialog = tid.getDialog();
                CSeqHeader cseq = (CSeqHeader) response.getHeader(CSeqHeader.NAME);
                Request request = dialog.createAck(cseq.getSeqNumber());
                dialog.sendAck(request);
            } else if ( response.getStatusCode() == Response.PROXY_AUTHENTICATION_REQUIRED)  {
            	 AuthenticationHelper authenticationHelper =
                     ((SipStackExt) protocolObjects.sipStack).getAuthenticationHelper(new AccountManagerImpl(),
                    		 protocolObjects.headerFactory);

                 tid = authenticationHelper.handleChallenge(response, (ClientTransaction) tid, provider, 5);
                 Thread.sleep(100);
                 if ( dialog.getState() == DialogState.CONFIRMED) {
                	 dialog.sendRequest((ClientTransaction) tid);
                 } else {
                	 ((ClientTransaction)tid).sendRequest();
                 }
            } else {
            	ReInviteTest.fail("Unexpected response "+ response.getStatusCode());
            }
            if ( tid != null ) {
                Dialog dialog = tid.getDialog();
                logger.info("Dalog State = " + dialog.getState());          }
        } catch (Exception ex) {

            String s = "Unexpected exception";

            logger.error(s,ex);
            ReInviteTest.fail(s);
        }

    }

    public void processTimeout(javax.sip.TimeoutEvent timeoutEvent) {
        Transaction transaction;
        if (timeoutEvent.isServerTransaction()) {
            transaction = timeoutEvent.getServerTransaction();
        } else {
            transaction = timeoutEvent.getClientTransaction();
        }
        logger.info("state = " + transaction.getState());
        logger.info("dialog = " + transaction.getDialog());
        logger.info("dialogState = "
                + transaction.getDialog().getState());
        logger.info("Transaction Time out");
    }




    public SipProvider createSipProvider() throws Exception {
        ListeningPoint lp = protocolObjects.sipStack.createListeningPoint(myAddress,
                myPort, protocolObjects.transport);


        SipProvider sipProvider = protocolObjects.sipStack.createSipProvider(lp);
        return sipProvider;
    }

    public static void main(String args[]) throws Exception {
        ProtocolObjects protocolObjects = new ProtocolObjects("shootme", "gov.nist","udp",true,false, false);

        Shootme shootme = new Shootme(protocolObjects);
        shootme.createSipProvider().addSipListener(shootme);

    }

    public void checkState() {
        ApplicationData data = (ApplicationData) dialog.getApplicationData();
        ReInviteTest.assertTrue("Must see ack", 2 <= data.ackCount);
        ReInviteTest.assertTrue(okRecieved);
    }
    /*
     * (non-Javadoc)
     *
     * @see javax.sip.SipListener#processIOException(javax.sip.IOExceptionEvent)
     */
    public void processIOException(IOExceptionEvent exceptionEvent) {
        logger.error("An IO Exception was detected : "
                + exceptionEvent.getHost());

    }

    /*
     * (non-Javadoc)
     *
     * @see javax.sip.SipListener#processTransactionTerminated(javax.sip.TransactionTerminatedEvent)
     */
    public void processTransactionTerminated(
            TransactionTerminatedEvent transactionTerminatedEvent) {
        logger.info("Tx terminated event ");

    }

    /*
     * (non-Javadoc)
     *
     * @see javax.sip.SipListener#processDialogTerminated(javax.sip.DialogTerminatedEvent)
     */
    public void processDialogTerminated(
            DialogTerminatedEvent dialogTerminatedEvent) {
        logger.info("Dialog terminated event detected ");

    }

}
