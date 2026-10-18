package com.nithin.ridebooking.event;

import com.nithin.ridebooking.dto.RideStatusMessage;
import lombok.RequiredArgsConstructor;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionalEventListener;

@Component
@RequiredArgsConstructor
public class RideEventListener {

    private final SimpMessagingTemplate messagingTemplate;

    // Runs only AFTER the database transaction has committed successfully
    @TransactionalEventListener
    public void onRideStatusChanged(RideStatusMessage message) {
        messagingTemplate.convertAndSend(
                "/topic/rides/" + message.rideId() + "/status", message);
    }
}