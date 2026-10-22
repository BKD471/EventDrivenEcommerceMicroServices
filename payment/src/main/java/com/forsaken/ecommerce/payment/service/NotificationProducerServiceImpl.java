package com.forsaken.ecommerce.payment.service;


import com.forsaken.ecommerce.avro.PaymentConfirmation;
import com.forsaken.ecommerce.payment.configs.kafka.KafkaProperties;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.messaging.Message;
import org.springframework.messaging.support.MessageBuilder;
import org.springframework.stereotype.Service;

import static org.springframework.kafka.support.KafkaHeaders.TOPIC;

@Service
@RequiredArgsConstructor
@Slf4j
public class NotificationProducerServiceImpl implements INotificationProducerService {

    private final KafkaProperties kafkaProperties;
    private final KafkaTemplate<String, PaymentConfirmation> kafkaTemplate;

    @Override
    public void sendNotification(final PaymentConfirmation request) {
        log.info("Sending notification with body = < {} >", request);
        final Message<PaymentConfirmation> message = MessageBuilder
                .withPayload(request)
                .setHeader(TOPIC, kafkaProperties.topicName())
                .build();
        kafkaTemplate.send(message).join();
        log.info("Payment notification acknowledged by Kafka");
    }
}
