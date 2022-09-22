package health.medunited.artemis;

import java.util.Objects;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.logging.Logger;

import javax.enterprise.context.ApplicationScoped;
import javax.enterprise.event.ObservesAsync;
import javax.inject.Inject;
import javax.jms.BytesMessage;
import javax.jms.ConnectionFactory;
import javax.jms.JMSConsumer;
import javax.jms.JMSContext;
import javax.jms.JMSException;
import javax.jms.Message;
import javax.jms.Queue;

import health.medunited.event.SshConnectionClosed;
import health.medunited.model.*;
import health.medunited.service.BundleParser;
import org.hl7.fhir.r4.model.Bundle;
import health.medunited.isynet.IsynetMSQLConnector;
import health.medunited.t2med.T2MedConnector;

@ApplicationScoped
public class PrescriptionConsumer {

    private static final Logger log = Logger.getLogger(PrescriptionConsumer.class.getName());

    private static final String PVS_HEADER = "practiceManagementTranslation";

    private static final String FINGERPRINT_HEADER = "receiverPublicKeyFingerprint";

    private static final String PRACTITIONER = "practitioner";
    private static final String PATIENT = "patient";
    private static final String MEDICATIONSTATEMENT = "medicationStatement";
    private static final String PHARMACY = "organization";

    @Inject
    ConnectionFactory connectionFactory;

    private final ExecutorService scheduler = Executors.newSingleThreadExecutor();

    void onStop(@ObservesAsync SshConnectionClosed ev) {
        scheduler.shutdown();
    }

    @Inject
    IsynetMSQLConnector isynetMSQLConnector;

    @Inject
    T2MedConnector t2MedConnector;

    public void run(String publicKey) {
        try (JMSContext context = connectionFactory.createContext(JMSContext.AUTO_ACKNOWLEDGE)) {
            Queue queue = context.createQueue("Prescriptions");
            while (!scheduler.isShutdown()) {
                try (JMSConsumer consumer = context.createConsumer(queue, "receiverPublicKeyFingerprint = '" + publicKey + "'")) {
                    Message message = consumer.receive();
                    if (message == null) return;
                    if (message.propertyExists(FINGERPRINT_HEADER) && message.propertyExists(PVS_HEADER)) {
                        String practiceManagement = message.getObjectProperty(PVS_HEADER).toString();
                        String fhirBundle = getFhirBundleFromBytesMessage((BytesMessage) message);
                        PrescriptionRequest prescription = new PrescriptionRequest(practiceManagement, publicKey, fhirBundle);

                        Bundle parsedBundle = BundleParser.parseBundle(prescription.getFhirBundle());

                        log.info("^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^");
                        log.info("[ PRACTITIONER ]" + " first name: " + BundleParser.getFirstName(PRACTITIONER, parsedBundle) +
                                " // last name: " + BundleParser.getLastName(PRACTITIONER, parsedBundle) +
                                " // LANR: " + BundleParser.getLanr(PRACTITIONER, parsedBundle) +
                                " // street: " + BundleParser.getStreet(PRACTITIONER, parsedBundle) +
                                " // house number: " + BundleParser.getHouseNumber(PRACTITIONER, parsedBundle) +
                                " // city: " + BundleParser.getCity(PRACTITIONER, parsedBundle) +
                                " // postal code: " + BundleParser.getPostalCode(PRACTITIONER, parsedBundle) +
                                " // e-mail: " + BundleParser.getEmail(PRACTITIONER, parsedBundle) +
                                " // phone: " + BundleParser.getPhone(PRACTITIONER, parsedBundle) +
                                " // fax: " + BundleParser.getFax(PRACTITIONER, parsedBundle) +
                                " // modality: " + BundleParser.getModality(PRACTITIONER, parsedBundle));

                        log.info("[ PATIENT ]" + " first name: " + BundleParser.getFirstName(PATIENT, parsedBundle) +
                                " // last name: " + BundleParser.getLastName(PATIENT, parsedBundle) +
                                " // street: " + BundleParser.getStreet(PATIENT, parsedBundle) +
                                " // house number: " + BundleParser.getHouseNumber(PATIENT, parsedBundle) +
                                " // city: " + BundleParser.getCity(PATIENT, parsedBundle) +
                                " // postal code: " + BundleParser.getPostalCode(PATIENT, parsedBundle) +
                                " // gender: " + BundleParser.getGender(PATIENT, parsedBundle) +
                                " // birthDate: " + BundleParser.getBirthDate(PATIENT, parsedBundle));

                        log.info("[ MEDICATION STATEMENT ]" + " medication name: " + BundleParser.getMedicationName(MEDICATIONSTATEMENT, parsedBundle) +
                                " // PZN: " + BundleParser.getPzn(MEDICATIONSTATEMENT, parsedBundle) +
                                " // dosage: " + BundleParser.getDosage(MEDICATIONSTATEMENT, parsedBundle));

                        log.info("[ PHARMACY ]" + " name: " + BundleParser.getName(PHARMACY, parsedBundle) +
                                " // street: " + BundleParser.getStreet(PHARMACY, parsedBundle) +
                                " // house number: " + BundleParser.getHouseNumber(PHARMACY, parsedBundle) +
                                " // city: " + BundleParser.getCity(PHARMACY, parsedBundle) +
                                " // postal code: " + BundleParser.getPostalCode(PHARMACY, parsedBundle) +
                                " // phone: " + BundleParser.getPhone(PHARMACY, parsedBundle) +
                                " // email: " + BundleParser.getEmail(PHARMACY, parsedBundle) + "\n");

                        if (Objects.equals(message.getStringProperty(PVS_HEADER), "isynet")) {

                            isynetMSQLConnector.insertToIsynet(parsedBundle);

                        } else if (Objects.equals(message.getStringProperty(PVS_HEADER), "t2med")) {

                            t2MedConnector.createPrescriptionFromBundle(parsedBundle);
                        }

                    } else {
                        log.info("Invalid content");
                    }
                } catch (Exception e) {
                    log.info(e.getMessage());
                }
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    private String getFhirBundleFromBytesMessage(BytesMessage message) throws JMSException {
        byte[] byteData = new byte[(int) message.getBodyLength()];
        message.readBytes(byteData);
        message.reset();
        return new String(byteData);
    }

}
