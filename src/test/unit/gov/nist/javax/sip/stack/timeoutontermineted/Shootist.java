package test.unit.gov.nist.javax.sip.stack.timeoutontermineted;

import java.util.ArrayList;

import javax.sip.ClientTransaction;
import javax.sip.Dialog;
import javax.sip.DialogState;
import javax.sip.DialogTerminatedEvent;
import javax.sip.IOExceptionEvent;
import javax.sip.ListeningPoint;
import javax.sip.RequestEvent;
import javax.sip.ResponseEvent;
import javax.sip.SipListener;
import javax.sip.SipProvider;
import javax.sip.SipStack;
import javax.sip.TransactionTerminatedEvent;
import javax.sip.address.Address;
import javax.sip.address.AddressFactory;
import javax.sip.address.SipURI;
import javax.sip.header.CSeqHeader;
import javax.sip.header.CallIdHeader;
import javax.sip.header.ContactHeader;
import javax.sip.header.ContentTypeHeader;
import javax.sip.header.FromHeader;
import javax.sip.header.Header;
import javax.sip.header.HeaderFactory;
import javax.sip.header.MaxForwardsHeader;
import javax.sip.header.ToHeader;
import javax.sip.header.ViaHeader;
import javax.sip.message.MessageFactory;
import javax.sip.message.Request;
import javax.sip.message.Response;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import junit.framework.TestCase;
import test.tck.msgflow.callflows.TestAssertion;

/**
 * This class is a UAC template. Shootist is the guy that shoots and shootme is
 * the guy that gets shot.
 *
 * @author <a href="mailto:baranowb@gmail.com"> Bartosz Baranowski </a>
 */

public class Shootist implements SipListener {

    private ContactHeader contactHeader;

    private ClientTransaction inviteTid;

    private Dialog inviteDialog;
    private SipProvider sipProvider;

    private final String host = "127.0.0.1";

    private final int port;

    private final String peerHost = "127.0.0.1";

    private final int peerPort;

    private ListeningPoint listeningPoint;

    private static String unexpectedException = "Unexpected exception ";

    private static Logger logger = LogManager.getLogger(Shootist.class);

    private final SipStack sipStack;

    private static HeaderFactory headerFactory;

    private static MessageFactory messageFactory;

    private static AddressFactory addressFactory;

    private static final String transport = "udp";

    private boolean seen_txTerm, seen_txTimeout, seen_dte;

    public Shootist(int myPort, int proxyPort) {

        this.port = myPort;

        SipObjects sipObjects = new SipObjects(myPort, "shootist", "on");
        addressFactory = sipObjects.addressFactory;
        messageFactory = sipObjects.messageFactory;
        headerFactory = sipObjects.headerFactory;
        this.sipStack = sipObjects.sipStack;

        this.peerPort = proxyPort;

    }

    public void processRequest(RequestEvent requestReceivedEvent) {

    }

    public synchronized void processResponse(ResponseEvent responseReceivedEvent) {
        logger.info("Got a response");
        Response response = responseReceivedEvent.getResponse();
        ClientTransaction tid = responseReceivedEvent.getClientTransaction();
        CSeqHeader cseq = (CSeqHeader) response.getHeader(CSeqHeader.NAME);

        logger.info("Response received : Status Code = " + response.getStatusCode() + " " + cseq);
        logger.info("Response = " + response + " class=" + response.getClass());

        Dialog dialog = responseReceivedEvent.getDialog();
        TestCase.assertNotNull(dialog);

        if (tid != null)
            logger.info("transaction state is " + tid.getState());
        else
            logger.info("transaction = " + tid);

        logger.info("Dialog = " + dialog);

        logger.info("Dialog state is " + dialog.getState());

        try {
            if (response.getStatusCode() == Response.OK) {
                if (cseq.getMethod().equals(Request.INVITE)) {
                    TestCase.assertEquals(DialogState.CONFIRMED, dialog.getState());

                } else {
                    logger.info("Response method = " + cseq.getMethod());
                }
            }
        } catch (Throwable ex) {
            ex.printStackTrace();
            // junit.framework.TestCase.fail("Exit JVM");
        }

    }

    public SipProvider createSipProvider() {
        try {
            listeningPoint = sipStack.createListeningPoint(host, port, "udp");

            logger.info("listening point = " + host + " port = " + port);
            logger.info("listening point = " + listeningPoint);
            sipProvider = sipStack.createSipProvider(listeningPoint);
            return sipProvider;
        } catch (Exception ex) {
            logger.error(unexpectedException, ex);
            ex.printStackTrace();
            TestCase.fail(unexpectedException);
            return null;
        }

    }
    
    public TestAssertion getAssertion() {
        return new TestAssertion() {
            @Override
            public boolean assertCondition() {
                return seen_txTerm && seen_txTimeout && seen_dte;
            };
        }; 
    }

    public void checkState() {
        TestCase.assertTrue("INVITE transaction should temrinate.", seen_txTerm);
        TestCase.assertFalse("INVITE transaction should not timeout.", seen_txTimeout);
        TestCase.assertTrue("INVITE Dialog should die.", seen_dte);

    }



    public void sendInvite() {
        try {

            String fromName = "BigGuy";
            String fromSipAddress = "here.com";
            String fromDisplayName = "The Master Blaster";

            String toSipAddress = "there.com";
            String toUser = "LittleGuy";
            String toDisplayName = "The Little Blister";

            // create >From Header
            SipURI fromAddress = addressFactory.createSipURI(fromName, fromSipAddress);

            Address fromNameAddress = addressFactory.createAddress(fromAddress);
            fromNameAddress.setDisplayName(fromDisplayName);
            FromHeader fromHeader = headerFactory.createFromHeader(fromNameAddress, "12345");

            // create To Header
            SipURI toAddress = addressFactory.createSipURI(toUser, toSipAddress);
            Address toNameAddress = addressFactory.createAddress(toAddress);
            toNameAddress.setDisplayName(toDisplayName);
            ToHeader toHeader = headerFactory.createToHeader(toNameAddress, null);

            // create Request URI
            String peerHostPort = peerHost + ":" + peerPort;
            SipURI requestURI = addressFactory.createSipURI(toUser, peerHostPort);

            // Create ViaHeaders

            ArrayList viaHeaders = new ArrayList();
            ViaHeader viaHeader = headerFactory.createViaHeader(host, sipProvider.getListeningPoint(transport).getPort(), transport, null);

            // add via headers
            viaHeaders.add(viaHeader);

            //SipURI sipuri = addressFactory.createSipURI(null, host);
            //sipuri.setPort(peerPort);
            //sipuri.setLrParam();
            //
            //RouteHeader routeHeader = headerFactory.createRouteHeader(addressFactory.createAddress(sipuri));

            // Create ContentTypeHeader
            ContentTypeHeader contentTypeHeader = headerFactory.createContentTypeHeader("application", "sdp");

            // Create a new CallId header
            CallIdHeader callIdHeader = sipProvider.getNewCallId();
            // JvB: Make sure that the implementation matches the messagefactory
            callIdHeader = headerFactory.createCallIdHeader(callIdHeader.getCallId());

            // Create a new Cseq header
            CSeqHeader cSeqHeader = headerFactory.createCSeqHeader(1L, Request.INVITE);

            // Create a new MaxForwardsHeader
            MaxForwardsHeader maxForwards = headerFactory.createMaxForwardsHeader(70);

            // Create the request.
            Request request = messageFactory.createRequest(requestURI, Request.INVITE, callIdHeader, cSeqHeader, fromHeader, toHeader, viaHeaders, maxForwards);
            // Create contact headers

            SipURI contactUrl = addressFactory.createSipURI(fromName, host);
            contactUrl.setPort(listeningPoint.getPort());

            // Create the contact name address.
            SipURI contactURI = addressFactory.createSipURI(fromName, host);
            contactURI.setPort(sipProvider.getListeningPoint(transport).getPort());
            contactURI.setTransportParam(transport);

            Address contactAddress = addressFactory.createAddress(contactURI);

            // Add the contact address.
            contactAddress.setDisplayName(fromName);

            contactHeader = headerFactory.createContactHeader(contactAddress);
            request.addHeader(contactHeader);

            // Dont use the Outbound Proxy. Use Lr instead.
            //request.setHeader(routeHeader);

            // Add the extension header.
            Header extensionHeader = headerFactory.createHeader("My-Header", "my header value");
            request.addHeader(extensionHeader);

            String sdpData = "v=0\r\n" + "o=4855 13760799956958020 13760799956958020" + " IN IP4  129.6.55.78\r\n" + "s=mysession session\r\n" + "p=+46 8 52018010\r\n" + "c=IN IP4  129.6.55.78\r\n"
                    + "t=0 0\r\n" + "m=audio 6022 RTP/AVP 0 4 18\r\n" + "a=rtpmap:0 PCMU/8000\r\n" + "a=rtpmap:4 G723/8000\r\n" + "a=rtpmap:18 G729A/8000\r\n" + "a=ptime:20\r\n";
            byte[] contents = sdpData.getBytes();

            request.setContent(contents, contentTypeHeader);

            extensionHeader = headerFactory.createHeader("My-Other-Header", "my new header value ");
            request.addHeader(extensionHeader);

            Header callInfoHeader = headerFactory.createHeader("Call-Info", "<http://www.antd.nist.gov>");
            request.addHeader(callInfoHeader);

            // Create the client transaction.
            inviteTid = sipProvider.getNewClientTransaction(request);
            inviteDialog = inviteTid.getDialog();

            TestCase.assertTrue("Initial dialog state should be null", inviteDialog.getState() == null);

            // send the request out.
            inviteTid.sendRequest();

        } catch (Exception ex) {
            logger.error(unexpectedException, ex);
            TestCase.fail(unexpectedException);

        }
    }

    public void processIOException(IOExceptionEvent exceptionEvent) {
        logger.error("IOException happened for " + exceptionEvent.getHost() + " port = " + exceptionEvent.getPort());
        TestCase.fail("Unexpected exception");

    }

    public void processTransactionTerminated(TransactionTerminatedEvent transactionTerminatedEvent) {
        logger.info("[c]Transaction terminated event recieved: "+transactionTerminatedEvent.getClientTransaction());
        System.err.println("[c]Transaction terminated event recieved: "+transactionTerminatedEvent.getClientTransaction());
        //if (transactionTerminatedEvent.getClientTransaction() == inviteTid)
            seen_txTerm = true;
    }
    public void processTimeout(javax.sip.TimeoutEvent timeoutEvent) {
        logger.info("[c]Transaction timedout event recieved: "+timeoutEvent.getClientTransaction());
        System.err.println("[c]Transaction timedout event recieved: "+timeoutEvent.getClientTransaction());
        //if (timeoutEvent.getClientTransaction() == inviteTid)
            seen_txTimeout = true;
    }
    public void processDialogTerminated(DialogTerminatedEvent dialogTerminatedEvent) {
        logger.info("[c]Dialog terminated event recieved: "+dialogTerminatedEvent.getDialog());
        System.err.println("[c]Dialog terminated event recieved: "+dialogTerminatedEvent.getDialog());
        //if (dialogTerminatedEvent.getDialog() == inviteDialog)
            seen_dte = true;

    }

    public void stop() {
        this.sipStack.stop();
    }
}
