package org.wso2.carbon.inbound.smpp;

import org.apache.commons.lang.StringUtils;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.apache.synapse.SynapseException;
import org.apache.synapse.core.SynapseEnvironment;
import org.jsmpp.bean.AlertNotification;
import org.jsmpp.bean.BindType;
import org.jsmpp.bean.DataSm;
import org.jsmpp.bean.DeliverSm;
import org.jsmpp.bean.NumberingPlanIndicator;
import org.jsmpp.bean.TypeOfNumber;
import org.jsmpp.extra.ProcessRequestException;
import org.jsmpp.session.BindParameter;
import org.jsmpp.session.DataSmResult;
import org.jsmpp.session.MessageReceiverListener;
import org.jsmpp.session.SMPPSession;
import org.jsmpp.session.Session;
import org.wso2.carbon.inbound.endpoint.protocol.generic.GenericEventBasedConsumer;

import java.io.IOException;
import java.util.Properties;

/**
 * SMPP inbound endpoint is used to listen and consume messages from SMSC via WSO2 ESB.
 *
 * @since 1.0.0.
 */
public class SMPPListeningConsumer extends GenericEventBasedConsumer {

    private static final Log logger = LogFactory.getLog(SMPPListeningConsumer.class.getName());
    //IP address of the SMSC
    private String host;
    //Port to access the SMSC
    private int port;
    //Identifies the type of ESME system requesting to bind as a receiver with the SMSC
    private String systemType;
    //Indicates Type of Number of the ESME address
    private String addressTon;
    //Numbering Plan Indicator for ESME address
    private String addressNpi;
    private SMPPSession session;
    //To check the connection to the SMSC.
    private boolean isConnected;
    private BindParameter bind_param;

    public SMPPListeningConsumer(Properties smppProperties, String name,
                                 SynapseEnvironment synapseEnvironment, String injectingSeq,
                                 String onErrorSeq, boolean coordination, boolean sequential) {
        super(smppProperties, name, synapseEnvironment, injectingSeq, onErrorSeq, coordination,
                sequential);
        this.injectingSeq = injectingSeq;
        logger.info("Starting to load the SMPP Inbound Endpoint " + name);
        if (logger.isDebugEnabled()) {
            logger.debug("Starting to load the SMPP Properties for " + name);
        }
        //Identifies the ESME system requesting to bind as a receiver with the SMSC.
        String systemId;
        //The password may be used by the SMSC to authenticate the ESME requesting to bind.
        String password;
        this.host = properties.getProperty(SMPPConstant.HOST);
        if (StringUtils.isEmpty(host)) {
            throw new SynapseException("Host is not set");
        }
        if (StringUtils.isEmpty(properties.getProperty(SMPPConstant.PORT))) {
            throw new SynapseException("Port is not set");
        } else {
            this.port = Integer.parseInt(properties.getProperty(SMPPConstant.PORT));
        }
        systemId = properties.getProperty(SMPPConstant.SYSTEMID);
        if (StringUtils.isEmpty(systemId)) {
            throw new SynapseException("SystemId is not set");
        }
        password = properties.getProperty(SMPPConstant.PASSWORD);
        if (StringUtils.isEmpty(password)) {
            throw new SynapseException("Password is not set");
        }
        this.systemType = properties.getProperty(SMPPConstant.SYSTEMTYPE);
        this.addressTon = properties.getProperty(SMPPConstant.ADDRESSTON);
        if (StringUtils.isEmpty(addressTon)) {
            this.addressTon = SMPPConstant.UNKNOWN;
        }
        this.addressNpi = properties.getProperty(SMPPConstant.ADDRESSNPI);
        if (StringUtils.isEmpty(addressNpi)) {
            this.addressNpi = SMPPConstant.UNKNOWN;
        }
        if (logger.isDebugEnabled()) {
            logger.debug("Loaded the SMPP Parameters with Host : " + host
                    + " , Port : " + port + " , SystemId : " + systemId
                    + " , Password : " + password + " , SystemType : "
                    + systemType + " , AddressTon : " + addressTon
                    + " , AddressNpi : " + addressNpi + " for " + name);
        }
        bind_param = new BindParameter(BindType.BIND_TRX, systemId, password, systemType,
                TypeOfNumber.valueOf(addressTon), NumberingPlanIndicator.valueOf(addressNpi), null);
        logger.info("Initialized the SMPP inbound consumer " + name);
    }

    /**
     * Create connection with SMSC and listen to retrieve the messages. Then inject
     * according to the registered handler.
     */
    public void listen() {
        if (logger.isDebugEnabled()) {
            logger.debug("Started to Listen SMPP messages for " + name);
        }
        if (session == null) {
            session = new SMPPSession();
        }
        if (!isConnected) {
            try {
                session.connectAndBind(host, port, bind_param);
                isConnected = true;
            } catch (IOException e) {
                isConnected = false;
                throw new SynapseException("Failed connect and bind to host for" + name, e);
            }
        }

        // Set listener to receive SMPP messages
        if (logger.isDebugEnabled()) {
            logger.debug("Listening SMPP messages for " + name);
        }
        session.setMessageReceiverListener(new MessageReceiverListener() {
            public void onAcceptDeliverSm(DeliverSm deliverSm) throws ProcessRequestException {
                // inject short message into the sequence
                injectMessage(new String(deliverSm.getShortMessage()), SMPPConstant.CONTENT_TYPE);
            }

            public void onAcceptAlertNotification(AlertNotification alertNotification) {
                logger.info("onAcceptAlertNotification");
            }

            public DataSmResult onAcceptDataSm(DataSm dataSm, Session source) throws ProcessRequestException {
                logger.info("onAcceptDataSm");
                return null;
            }
        });

    }

    /**
     * Close the connection to the SMSC.
     */
    public void destroy() {
        try {
            if (session != null) {
                session.unbindAndClose();
                if (logger.isDebugEnabled()) {
                    logger.debug("The SMPP connection has been shutdown ! for " + name);
                }
            }
        } catch (Exception e) {
            logger.error("Error while shutdown the connection with SMSC " + name + " " + e.getMessage(), e);
        }
    }
}