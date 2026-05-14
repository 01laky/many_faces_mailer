package manyfaces.mailer.smtp;

import jakarta.mail.Message;
import jakarta.mail.MessagingException;
import jakarta.mail.Session;
import jakarta.mail.Transport;
import jakarta.mail.internet.AddressException;
import jakarta.mail.internet.InternetAddress;
import jakarta.mail.internet.MimeBodyPart;
import jakarta.mail.internet.MimeMessage;
import jakarta.mail.internet.MimeMultipart;
import jakarta.mail.internet.MimeUtility;
import java.io.UnsupportedEncodingException;
import java.util.List;
import java.util.Properties;
import manyfaces.mailer.config.MailerConfig;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Thin adapter around Jakarta Mail (Angus implementation on the classpath) for one transactional MIME message.
 *
 * <p>Blocking I/O runs on the gRPC executor thread; keep SMTP timeouts tight so a dead Mailpit/relay cannot wedge the
 * process indefinitely. Virtual-thread migration is a future optimization documented in README.
 */
public final class SmtpMailSender {

    private static final Logger LOG = LoggerFactory.getLogger(SmtpMailSender.class);

    private final MailerConfig cfg;

    public SmtpMailSender(MailerConfig cfg) {
        this.cfg = cfg;
    }

    /**
     * Sends multipart/alternative (plain + HTML). Returns SMTP {@code Message-ID} header when the server assigns one.
     */
    public String send(
            List<String> to,
            List<String> cc,
            List<String> bcc,
            String subject,
            String textPart,
            String htmlPart,
            String replyTo)
            throws MessagingException, UnsupportedEncodingException, AddressException {
        Properties props = new Properties();
        props.put("mail.smtp.host", cfg.smtpHost());
        props.put("mail.smtp.port", String.valueOf(cfg.smtpPort()));
        boolean auth = !cfg.smtpUser().isEmpty();
        props.put("mail.smtp.auth", Boolean.toString(auth));
        props.put("mail.smtp.starttls.enable", Boolean.toString(cfg.smtpStartTls()));
        props.put("mail.smtp.connectiontimeout", "10000");
        props.put("mail.smtp.timeout", "30000");
        props.put("mail.smtp.writetimeout", "30000");

        Session session = Session.getInstance(props);
        session.setDebug(false);

        MimeMessage message = new MimeMessage(session);
        message.setFrom(buildFromAddress());
        for (String addr : to) {
            message.addRecipient(Message.RecipientType.TO, new InternetAddress(addr.trim()));
        }
        for (String addr : cc) {
            message.addRecipient(Message.RecipientType.CC, new InternetAddress(addr.trim()));
        }
        for (String addr : bcc) {
            message.addRecipient(Message.RecipientType.BCC, new InternetAddress(addr.trim()));
        }
        if (replyTo != null && !replyTo.isBlank()) {
            message.setReplyTo(new InternetAddress[] {new InternetAddress(replyTo.trim())});
        }

        String encodedSubject = MimeUtility.encodeText(subject, "UTF-8", "B");
        message.setSubject(encodedSubject, "UTF-8");

        MimeMultipart alternatives = new MimeMultipart("alternative");

        MimeBodyPart textBody = new MimeBodyPart();
        textBody.setText(textPart, "UTF-8", "plain");
        alternatives.addBodyPart(textBody);

        MimeBodyPart htmlBody = new MimeBodyPart();
        htmlBody.setContent(htmlPart, "text/html; charset=UTF-8");
        alternatives.addBodyPart(htmlBody);

        message.setContent(alternatives);
        message.saveChanges();

        Transport transport = session.getTransport("smtp");
        try {
            if (auth) {
                transport.connect(cfg.smtpHost(), cfg.smtpPort(), cfg.smtpUser(), cfg.smtpPassword());
            } else {
                transport.connect();
            }
            transport.sendMessage(message, message.getAllRecipients());
        } finally {
            transport.close();
        }

        String[] ids = message.getHeader("Message-ID");
        String smtpId = ids != null && ids.length > 0 ? ids[0] : null;
        LOG.debug("SMTP accepted message; Message-ID={}", smtpId);
        return smtpId;
    }

    private InternetAddress buildFromAddress() throws UnsupportedEncodingException, AddressException {
        String email = cfg.fromEmail().trim();
        String name = cfg.fromDisplayName().trim();
        if (name.isEmpty()) {
            return new InternetAddress(email);
        }
        return new InternetAddress(email, name, "UTF-8");
    }
}
