package manyfaces.mailer.smtp;

import jakarta.mail.AuthenticationFailedException;
import jakarta.mail.MessagingException;
import jakarta.mail.Session;
import jakarta.mail.Transport;
import java.util.Properties;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/** Opens an SMTP connection without sending MIME (admin test-smtp probe). */
public final class SmtpConnectionProbe {

    private static final Logger LOG = LoggerFactory.getLogger(SmtpConnectionProbe.class);

    private SmtpConnectionProbe() {}

    public static void probe(SmtpTransportSettings transport) throws MessagingException {
        transport.validateForSend();
        Properties props = sessionProperties(transport);
        Session session = Session.getInstance(props);
        session.setDebug(false);
        Transport smtpTransport = session.getTransport("smtp");
        try {
            boolean auth = !transport.user().isEmpty();
            if (auth) {
                smtpTransport.connect(transport.host(), transport.port(), transport.user(), transport.password());
            } else {
                smtpTransport.connect(transport.host(), transport.port(), null, null);
            }
            LOG.debug("SMTP probe ok host={} port={}", transport.host(), transport.port());
        } catch (AuthenticationFailedException afe) {
            throw afe;
        } finally {
            smtpTransport.close();
        }
    }

    static Properties sessionProperties(SmtpTransportSettings transport) {
        Properties props = new Properties();
        props.put("mail.smtp.host", transport.host());
        props.put("mail.smtp.port", String.valueOf(transport.port()));
        boolean auth = !transport.user().isEmpty();
        props.put("mail.smtp.auth", Boolean.toString(auth));
        props.put("mail.smtp.starttls.enable", Boolean.toString(transport.startTls()));
        props.put("mail.smtp.connectiontimeout", "10000");
        props.put("mail.smtp.timeout", "30000");
        props.put("mail.smtp.writetimeout", "30000");
        return props;
    }
}
