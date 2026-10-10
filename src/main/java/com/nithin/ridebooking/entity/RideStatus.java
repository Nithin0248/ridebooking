package com.nithin.ridebooking.entity;

public enum RideStatus {
    REQUESTED,    // rider booked, no driver yet
    ACCEPTED,     // a driver took it
    IN_PROGRESS,  // trip started
    COMPLETED,    // trip finished
    CANCELLED
}