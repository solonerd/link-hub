package com.linkhub.config;

import org.springframework.amqp.core.Binding;
import org.springframework.amqp.core.BindingBuilder;
import org.springframework.amqp.core.DirectExchange;
import org.springframework.amqp.core.QueueBuilder;
import org.springframework.amqp.core.Queue;
import org.springframework.amqp.support.converter.Jackson2JsonMessageConverter;
import org.springframework.amqp.support.converter.MessageConverter;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
/**
 * 邮件发送队列拓扑：直连交换机 + 死信交换机，消费失败可转入 DLQ。
 */
@Configuration
public class MQConfig {

    public static final String MAIL_EXCHANGE = "mail.direct.exchange";
    public static final String MAIL_QUEUE = "mail.direct.queue";
    public static final String MAIL_ROUTING_KEY = "mail.send.routing";

    public static final String DLX = "mail.dead.letter.exchange";
    public static final String DLQ = "mail.dead.letter.queue";
    public static final String DLK = "mail.dead.letter.routing";

    @Bean
    public MessageConverter jsonMessageConverter() {
        return new Jackson2JsonMessageConverter();
    }

    @Bean
    public DirectExchange mailExchange() {
        return new DirectExchange(MAIL_EXCHANGE);
    }

    @Bean
    public Queue mailQueue() {
        return QueueBuilder.durable(MAIL_QUEUE)
                .deadLetterExchange(DLX)
                .deadLetterRoutingKey(DLK)
                .build();
    }

    @Bean
    public Binding mailBinding() {
        return BindingBuilder.bind(mailQueue()).to(mailExchange()).with(MAIL_ROUTING_KEY);
    }

    @Bean
    public DirectExchange deadLetterExchange() {
        return new DirectExchange(DLX);
    }

    @Bean
    public Queue deadLetterQueue() {
        return QueueBuilder.durable(DLQ).build();
    }

    @Bean
    public Binding deadLetterBinding() {
        return BindingBuilder.bind(deadLetterQueue()).to(deadLetterExchange()).with(DLK);
    }

}