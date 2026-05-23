package com.firstclub.membership.application.port.outbound;

import com.firstclub.membership.domain.event.DomainEvent;

public interface EventPublisher {

    void publish(DomainEvent event);
}
