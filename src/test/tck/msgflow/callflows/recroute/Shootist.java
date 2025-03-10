package test.tck.msgflow.callflows.recroute;

import java.util.ArrayList;
import java.util.Timer;
import java.util.TimerTask;

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
import javax.sip.TransactionTerminatedEvent;
import javax.sip.address.Address;
import javax.sip.address.SipURI;
import javax.sip.header.CSeqHeader;
import javax.sip.header.CallIdHeader;
import javax.sip.header.ContactHeader;
import javax.sip.header.ContentTypeHeader;
import javax.sip.header.FromHeader;
import javax.sip.header.Header;
import javax.sip.header.MaxForwardsHeader;
import javax.sip.header.RouteHeader;
import javax.sip.header.ToHeader;
import javax.sip.header.ViaHeader;
import javax.sip.message.Request;
import javax.sip.message.Response;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import test.tck.TestHarness;
import test.tck.msgflow.callflows.ProtocolObjects;
import test.tck.msgflow.callflows.TestAssertion;

/**
 * This class is a UAC template. Shootist is the guy that shoots and shootme is
 * the guy that gets shot.
 *
 * @author M. Ranganathan
 * @author Jeroen van Bemmel
 */

public class Shootist implements SipListener {

    private ContactHeader contactHeader;

    private ClientTransaction inviteTid;

    private SipProvider sipProvider;

    private String host = "127.0.0.1";

    private int port;

    private String peerHost = "127.0.0.1";

    private int peerPort;

    private ListeningPoint listeningPoint;

    private static String unexpectedException = "Unexpected exception ";

    private static Logger logger = LogManager.getLogger("test.tck");

    private ProtocolObjects protocolObjects;

    private boolean byeReceived;

    private boolean infoReceived;

    public Shootist(int myPort, int proxyPort, ProtocolObjects protocolObjects) {
        this.protocolObjects = protocolObjects;
        this.port = myPort;
        this.peerPort = proxyPort;

        protocolObjects.logLevel = 32; // JvB
    }

    public void processRequest(RequestEvent requestReceivedEvent) {
        Request request = requestReceivedEvent.getRequest();
        ServerTransaction serverTransactionId = requestReceivedEvent
                .getServerTransaction();

        logger.info("\n\nRequest " + request.getMethod() + " received at "
                + protocolObjects.sipStack.getStackName()
                + " with server transaction id " + serverTransactionId);

        // We are the UAC so the only request we get is the BYE.
        if (request.getMethod().equals(Request.INFO))
            processInfo(requestReceivedEvent);
        else
            TestHarness.fail("Unexpected request ! : " + request);

    }

    public void processInfo(RequestEvent requestEvent) {
        try {
            this.infoReceived = true;
            logger.info("This is the info request " + requestEvent.getRequest());
            logger.info("This is the dialog " + requestEvent.getDialog());
            SipProvider sipProvider = (SipProvider) requestEvent.getSource();
            Dialog dialog = requestEvent.getDialog();

            ServerTransaction serverTransaction = requestEvent
                    .getServerTransaction();
            Response ok = protocolObjects.messageFactory.createResponse(
                    Response.OK, requestEvent.getRequest());
            serverTransaction.sendResponse(ok);

            new Timer().schedule(new TimerTask() {
                Dialog dialog;
                SipProvider sipProvider;

                public TimerTask setInfo(SipProvider provider, Dialog dialog) {
                    this.dialog = dialog;
                    this.sipProvider = provider;
                    return this;
                }

                @Override
                public void run() {
                    try {
                        Request byeRequest = dialog.createRequest(Request.BYE);
                        ClientTransaction ct = sipProvider
                                .getNewClientTransaction(byeRequest);
                        dialog.sendRequest(ct);
                    } catch (Exception ex) {
                        TestHarness
                                .fail("Unexpected exception sending BYE", ex);
                    }

                }

            }.setInfo(sipProvider, dialog), 1000);

        } catch (Exception ex) {
            ex.printStackTrace();
            junit.framework.TestCase.fail("Exit JVM");

        }
    }

    public synchronized void processResponse(ResponseEvent responseReceivedEvent) {
        logger.info("Got a response");
        Response response = (Response) responseReceivedEvent.getResponse();
        ClientTransaction tid = responseReceivedEvent.getClientTransaction();
        CSeqHeader cseq = (CSeqHeader) response.getHeader(CSeqHeader.NAME);

        logger.info("Response received : Status Code = "
                + response.getStatusCode() + " " + cseq);
        logger.info("Response = " + response + " class=" + response.getClass());

        Dialog dialog = responseReceivedEvent.getDialog();
        TestHarness.assertNotNull(dialog);

        if (tid != null)
            logger.info("transaction state is " + tid.getState());
        else
            logger.info("transaction = " + tid);

        logger.info("Dialog = " + dialog);

        logger.info("Dialog state is " + dialog.getState());

        try {
            if (response.getStatusCode() == Response.OK) {
                if (cseq.getMethod().equals(Request.INVITE)) {
                    TestHarness.assertEquals(DialogState.CONFIRMED, dialog
                            .getState());
                    Request ackRequest = dialog.createAck(cseq.getSeqNumber());

                    dialog.sendAck(ackRequest);

                    TestHarness.assertNotNull(ackRequest
                            .getHeader(MaxForwardsHeader.NAME));

                    // Proxy will fork. I will accept the second dialog
                    // but not the first.

                    SipProvider sipProvider = (SipProvider) responseReceivedEvent
                            .getSource();

                    Request infoRequest = dialog.createRequest(Request.INFO);
                    ClientTransaction ct = sipProvider
                            .getNewClientTransaction(infoRequest);
                    dialog.sendRequest(ct);

                } else {
                    logger.info("Response method = " + cseq.getMethod());
                }
            } else if (response.getStatusCode() == Response.RINGING) {
                // TestHarness.assertEquals( DialogState.EARLY,
                // dialog.getState() );
            }
        } catch (Throwable ex) {
            ex.printStackTrace();
            // junit.framework.TestCase.fail("Exit JVM");
        }

    }

    public SipProvider createSipProvider() {
        try {
            listeningPoint = protocolObjects.sipStack.createListeningPoint(
                    host, port, protocolObjects.transport);

            logger.info("listening point = " + host + " port = " + port);
            logger.info("listening point = " + listeningPoint);
            sipProvider = protocolObjects.sipStack
                    .createSipProvider(listeningPoint);
            return sipProvider;
        } catch (Exception ex) {
            logger.error(unexpectedException, ex);
            TestHarness.fail(unexpectedException);
            return null;
        }

    }
    
    public TestAssertion getAssertion() {
        return new TestAssertion() {
            
            @Override
            public boolean assertCondition() {
                return infoReceived;
            }
        };
    }

    public void checkState() {
        TestHarness.assertTrue("Should see an INFO", infoReceived);

    }

    public void processTimeout(javax.sip.TimeoutEvent timeoutEvent) {

        logger.info("Transaction Time out");
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
            SipURI fromAddress = protocolObjects.addressFactory.createSipURI(
                    fromName, fromSipAddress);

            Address fromNameAddress = protocolObjects.addressFactory
                    .createAddress(fromAddress);
            fromNameAddress.setDisplayName(fromDisplayName);
            FromHeader fromHeader = protocolObjects.headerFactory
                    .createFromHeader(fromNameAddress, "12345");

            // create To Header
            SipURI toAddress = protocolObjects.addressFactory.createSipURI(
                    toUser, toSipAddress);
            Address toNameAddress = protocolObjects.addressFactory
                    .createAddress(toAddress);
            toNameAddress.setDisplayName(toDisplayName);
            ToHeader toHeader = protocolObjects.headerFactory.createToHeader(
                    toNameAddress, null);

            // create Request URI
            String peerHostPort = peerHost + ":" + peerPort;
            SipURI requestURI = protocolObjects.addressFactory.createSipURI(
                    toUser, peerHostPort);

            // Create ViaHeaders

            ArrayList viaHeaders = new ArrayList();
            ViaHeader viaHeader = protocolObjects.headerFactory
                    .createViaHeader(host, sipProvider.getListeningPoint(
                            protocolObjects.transport).getPort(),
                            protocolObjects.transport, null);

            // add via headers
            viaHeaders.add(viaHeader);

            SipURI sipuri = protocolObjects.addressFactory.createSipURI(null,
                    host);
            sipuri.setPort(peerPort);
            sipuri.setLrParam();

            RouteHeader routeHeader = protocolObjects.headerFactory
                    .createRouteHeader(protocolObjects.addressFactory
                            .createAddress(sipuri));

            // Create ContentTypeHeader
            ContentTypeHeader contentTypeHeader = protocolObjects.headerFactory
                    .createContentTypeHeader("application", "sdp");

            // Create a new CallId header
            CallIdHeader callIdHeader = sipProvider.getNewCallId();
            // JvB: Make sure that the implementation matches the messagefactory
            callIdHeader = protocolObjects.headerFactory
                    .createCallIdHeader(callIdHeader.getCallId());

            // Create a new Cseq header
            CSeqHeader cSeqHeader = protocolObjects.headerFactory
                    .createCSeqHeader(1L, Request.INVITE);

            // Create a new MaxForwardsHeader
            MaxForwardsHeader maxForwards = protocolObjects.headerFactory
                    .createMaxForwardsHeader(70);

            // Create the request.
            Request request = protocolObjects.messageFactory.createRequest(
                    requestURI, Request.INVITE, callIdHeader, cSeqHeader,
                    fromHeader, toHeader, viaHeaders, maxForwards);
            // Create contact headers

            SipURI contactUrl = protocolObjects.addressFactory.createSipURI(
                    fromName, host);
            contactUrl.setPort(listeningPoint.getPort());

            // Create the contact name address.
            SipURI contactURI = protocolObjects.addressFactory.createSipURI(
                    fromName, host);
            contactURI.setPort(sipProvider.getListeningPoint(
                    protocolObjects.transport).getPort());
            contactURI.setTransportParam(protocolObjects.transport);

            Address contactAddress = protocolObjects.addressFactory
                    .createAddress(contactURI);

            // Add the contact address.
            contactAddress.setDisplayName(fromName);

            contactHeader = protocolObjects.headerFactory
                    .createContactHeader(contactAddress);
            request.addHeader(contactHeader);

            // Dont use the Outbound Proxy. Use Lr instead.
            request.setHeader(routeHeader);

            // Add the extension header.
            Header extensionHeader = protocolObjects.headerFactory
                    .createHeader("My-Header", "my header value");
            request.addHeader(extensionHeader);

            String sdpData = "v=0\r\n"
                    + "o=4855 13760799956958020 13760799956958020"
                    + " IN IP4  129.6.55.78\r\n" + "s=mysession session\r\n"
                    + "p=+46 8 52018010\r\n" + "c=IN IP4  129.6.55.78\r\n"
                    + "t=0 0\r\n" + "m=audio 6022 RTP/AVP 0 4 18\r\n"
                    + "a=rtpmap:0 PCMU/8000\r\n" + "a=rtpmap:4 G723/8000\r\n"
                    + "a=rtpmap:18 G729A/8000\r\n" + "a=ptime:20\r\n";
            byte[] contents = sdpData.getBytes();

            request.setContent(contents, contentTypeHeader);

            extensionHeader = protocolObjects.headerFactory.createHeader(
                    "My-Other-Header", "my new header value ");
            request.addHeader(extensionHeader);

            Header callInfoHeader = protocolObjects.headerFactory.createHeader(
                    "Call-Info", "<http://www.antd.nist.gov>");
            request.addHeader(callInfoHeader);

            // Create the client transaction.
            inviteTid = sipProvider.getNewClientTransaction(request);
            Dialog dialog = inviteTid.getDialog();

            TestHarness.assertTrue("Initial dialog state should be null",
                    dialog.getState() == null);

            // send the request out.
            inviteTid.sendRequest();

        } catch (Exception ex) {
            logger.error(unexpectedException, ex);
            TestHarness.fail(unexpectedException);

        }
    }

    public void processIOException(IOExceptionEvent exceptionEvent) {
        logger.info("IOException happened for " + exceptionEvent.getHost()
                + " port = " + exceptionEvent.getPort());

    }

    public void processTransactionTerminated(
            TransactionTerminatedEvent transactionTerminatedEvent) {
        logger.info("Transaction terminated event recieved");
    }

    public void processDialogTerminated(
            DialogTerminatedEvent dialogTerminatedEvent) {
        logger.info("dialogTerminatedEvent");

    }
}
