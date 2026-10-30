package com.linkhub.mq;

import com.linkhub.config.MQConfig;
import com.linkhub.dto.MailMessageDTO;
import lombok.RequiredArgsConstructor;

import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RequiredArgsConstructor
public class MailListener {

    private final ObjectProvider<JavaMailSender> mailSenderProvider;   // ← 建议改个名

    @Value("${spring.mail.username:}")
    private String fromEmail;

    @RabbitListener(queues = MQConfig.MAIL_QUEUE)
    public void listenMailQueue(MailMessageDTO messageDTO) {

        log.info("MQ 接收到发邮件任务，目标邮箱: {}", messageDTO.getEmail());

        JavaMailSender mailSender = mailSenderProvider.getIfAvailable();   // ← 取出来
        if (mailSender == null) {
            log.error("JavaMailSender 未配置，丢弃邮件任务，邮箱: {}", messageDTO.getEmail());
            return;
        }

        SimpleMailMessage mail = new SimpleMailMessage();
        mail.setFrom(fromEmail);
        mail.setTo(messageDTO.getEmail());
        mail.setSubject("【Link-Hub】账号注册验证码");
        mail.setText("欢迎注册 Link-Hub！\n\n您的验证码是：" + messageDTO.getCode() +
                "\n\n请在 5 分钟内完成验证。");

        mailSender.send(mail);   // ← 这时才是 JavaMailSender，send 能用
        log.info("邮件发送成功！已发送至: {}", messageDTO.getEmail());
    }
}