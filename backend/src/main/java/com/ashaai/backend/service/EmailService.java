package com.ashaai.backend.service;

import jakarta.mail.internet.MimeMessage;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.stereotype.Service;

@Service
public class EmailService {

    private static final Logger logger = LoggerFactory.getLogger(EmailService.class);

    private final JavaMailSender mailSender;

    @Value("${spring.mail.username:}")
    private String senderEmail;

    @Value("${spring.mail.password:}")
    private String senderPassword;

    @Autowired
    public EmailService(@Autowired(required = false) JavaMailSender mailSender) {
        this.mailSender = mailSender;
    }

    public boolean isConfigured() {
        return mailSender != null && senderEmail != null && !senderEmail.isBlank() && senderPassword != null && !senderPassword.isBlank();
    }

    public boolean sendAppointmentConfirmation(
            String toEmail,
            String ngoName,
            String scheduledDate,
            String scheduledTime,
            String purpose,
            String changeUrl
    ) {
        if (toEmail == null || toEmail.isBlank()) {
            logger.warn("event=email_dispatch_skipped reason=empty_recipient");
            return false;
        }

        if (!isConfigured()) {
            logger.error("event=email_dispatch_blocked reason=credentials_missing to={} senderEmail={}", toEmail, senderEmail);
            return false;
        }

        try {
            MimeMessage message = mailSender.createMimeMessage();
            MimeMessageHelper helper = new MimeMessageHelper(message, true, "UTF-8");
            helper.setFrom(senderEmail, "AshaAI Healthcare");
            helper.setTo(toEmail);
            helper.setSubject("Visit Scheduled — " + (ngoName != null ? ngoName : "AshaAI Partner"));

            String htmlBody = """
                <div style="font-family: Arial, sans-serif; max-width: 600px; margin: 0 auto; padding: 24px; border: 1px solid #CECBF6; border-radius: 12px; background: #ffffff;">
                    <div style="border-bottom: 2px solid #1D9E75; padding-bottom: 12px; margin-bottom: 16px;">
                        <h2 style="color: #1D9E75; margin: 0;">AshaAI Healthcare Visit Scheduled</h2>
                    </div>
                    <p style="color: #333; font-size: 15px;">Dear <strong>%s</strong>,</p>
                    <p style="color: #555; font-size: 14px; line-height: 1.5;">
                        A child health visit has been scheduled by the ASHA supervision team. Details are outlined below:
                    </p>
                    <div style="background-color: #F5FBF9; border-left: 4px solid #1D9E75; padding: 16px; margin: 20px 0; border-radius: 4px;">
                        <p style="margin: 4px 0; font-size: 14px; color: #222;"><strong>Scheduled Date:</strong> %s</p>
                        <p style="margin: 4px 0; font-size: 14px; color: #222;"><strong>Scheduled Time:</strong> %s</p>
                        <p style="margin: 4px 0; font-size: 14px; color: #222;"><strong>Purpose / Need:</strong> %s</p>
                    </div>
                    <p style="color: #555; font-size: 14px; line-height: 1.5;">
                        If this date or time does not work for your facility, please use the secure change link below to request alternate dates:
                    </p>
                    <div style="margin: 24px 0; text-align: center;">
                        <a href="%s" style="background-color: #2B5B84; color: #ffffff; padding: 12px 24px; text-decoration: none; border-radius: 8px; font-weight: bold; display: inline-block; font-size: 14px;">
                            Request Date Reschedule
                        </a>
                    </div>
                    <p style="font-size: 12px; color: #888; border-top: 1px solid #eeeeee; padding-top: 16px; margin-top: 24px;">
                        This is an automated notification from AshaAI Healthcare Platform. If you have immediate questions, contact your assigned ASHA worker.
                    </p>
                </div>
            """.formatted(
                    ngoName != null ? ngoName : "Partner NGO",
                    scheduledDate,
                    scheduledTime,
                    purpose != null ? purpose : "Child health follow-up",
                    changeUrl
            );

            helper.setText(htmlBody, true);
            mailSender.send(message);
            logger.info("event=email_dispatch_success to={} subject='Visit Scheduled'", toEmail);
            return true;
        } catch (Exception e) {
            logger.error("event=email_dispatch_failed to={} error={}", toEmail, e.getMessage(), e);
            return false;
        }
    }

    public boolean sendRescheduleConfirmation(
            String toEmail,
            String ngoName,
            String newDate,
            String newTime
    ) {
        if (toEmail == null || toEmail.isBlank()) {
            logger.warn("event=email_dispatch_skipped reason=empty_recipient");
            return false;
        }

        if (!isConfigured()) {
            logger.error("event=email_dispatch_blocked reason=credentials_missing to={} senderEmail={}", toEmail, senderEmail);
            return false;
        }

        try {
            MimeMessage message = mailSender.createMimeMessage();
            MimeMessageHelper helper = new MimeMessageHelper(message, true, "UTF-8");
            helper.setFrom(senderEmail, "AshaAI Healthcare");
            helper.setTo(toEmail);
            helper.setSubject("Appointment Rescheduled — " + (ngoName != null ? ngoName : "AshaAI Partner"));

            String htmlBody = """
                <div style="font-family: Arial, sans-serif; max-width: 600px; margin: 0 auto; padding: 24px; border: 1px solid #CECBF6; border-radius: 12px; background: #ffffff;">
                    <div style="border-bottom: 2px solid #2B5B84; padding-bottom: 12px; margin-bottom: 16px;">
                        <h2 style="color: #2B5B84; margin: 0;">AshaAI Visit Rescheduled</h2>
                    </div>
                    <p style="color: #333; font-size: 15px;">Dear <strong>%s</strong>,</p>
                    <p style="color: #555; font-size: 14px; line-height: 1.5;">
                        Your reschedule request has been reviewed and confirmed by the supervisor. Your new appointment details:
                    </p>
                    <div style="background-color: #EBF3FA; border-left: 4px solid #2B5B84; padding: 16px; margin: 20px 0; border-radius: 4px;">
                        <p style="margin: 4px 0; font-size: 14px; color: #222;"><strong>New Date:</strong> %s</p>
                        <p style="margin: 4px 0; font-size: 14px; color: #222;"><strong>New Time:</strong> %s</p>
                        <p style="margin: 4px 0; font-size: 14px; color: #222;"><strong>Status:</strong> Confirmed</p>
                    </div>
                    <p style="font-size: 12px; color: #888; border-top: 1px solid #eeeeee; padding-top: 16px; margin-top: 24px;">
                        This is an automated confirmation from AshaAI Healthcare Platform.
                    </p>
                </div>
            """.formatted(
                    ngoName != null ? ngoName : "Partner NGO",
                    newDate,
                    newTime
            );

            helper.setText(htmlBody, true);
            mailSender.send(message);
            logger.info("event=email_dispatch_success to={} subject='Appointment Rescheduled'", toEmail);
            return true;
        } catch (Exception e) {
            logger.error("event=email_dispatch_failed to={} error={}", toEmail, e.getMessage(), e);
            return false;
        }
    }
}
