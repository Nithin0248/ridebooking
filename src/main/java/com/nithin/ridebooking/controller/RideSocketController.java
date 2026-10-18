package com.nithin.ridebooking.controller;

import com.nithin.ridebooking.dto.LocationRequest;
import com.nithin.ridebooking.dto.LocationUpdate;
import com.nithin.ridebooking.service.TrackingService;
import lombok.RequiredArgsConstructor;
import org.springframework.messaging.handler.annotation.DestinationVariable;
import org.springframework.messaging.handler.annotation.MessageExceptionHandler;
import org.springframework.messaging.handler.annotation.MessageMapping;
import org.springframework.messaging.handler.annotation.Payload;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.messaging.simp.annotation.SendToUser;
import org.springframework.stereotype.Controller;

import java.security.Principal;

@Controller
@RequiredArgsConstructor
public class RideSocketController {

    private final TrackingService trackingService;
    private final SimpMessagingTemplate messagingTemplate;

    // Client sends to: /app/rides/{rideId}/location
    @MessageMapping("/rides/{rideId}/location")
    public void driverLocation(@DestinationVariable Long rideId,
                               @Payload LocationRequest payload,
                               Principal principal) {

        LocationUpdate update = trackingService.processDriverLocation(
                principal.getName(), rideId, payload.latitude(), payload.longitude());

        messagingTemplate.convertAndSend("/topic/rides/" + rideId + "/location", update);
    }

    // Errors go only to the user who caused them: /user/queue/errors
    @MessageExceptionHandler
    @SendToUser("/queue/errors")
    public String handleException(Exception ex) {
        return ex.getMessage();
    }
}