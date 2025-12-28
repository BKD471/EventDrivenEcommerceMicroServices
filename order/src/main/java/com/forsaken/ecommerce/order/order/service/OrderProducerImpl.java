package com.forsaken.ecommerce.order.order.service;

import com.forsaken.ecommerce.avro.OrderConfirmation;
import com.forsaken.ecommerce.order.configs.kafka.KafkaProperties;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.messaging.support.MessageBuilder;
import org.springframework.messaging.Message;
import org.springframework.stereotype.Component;

import static org.springframework.kafka.support.KafkaHeaders.TOPIC;

@Component
@RequiredArgsConstructor
@Slf4j
public class OrderProducerImpl implements IOrderProducer {

    private final KafkaProperties kafkaProperties;
    private final KafkaTemplate<String, OrderConfirmation> kafkaTemplate;

    @Override
    public void sendOrderConfirmation(final OrderConfirmation orderConfirmation) {
        log.info("Sending order confirmation");
        final Message<OrderConfirmation> message = MessageBuilder
                .withPayload(orderConfirmation)
                .setHeader(TOPIC, kafkaProperties.topicName())
                .build();

        kafkaTemplate.send(message);
    }
}
