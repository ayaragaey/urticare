package com.example.ayasantihistaminestracker;

import java.util.UUID;

public class AppointmentReminder {
    public String id;
    public String title; 
    public String type; // "Doctor", "FollowUp", "Custom"
    public long dateTime;
    public String repeatPattern; // "None", "Weekly", "Biweekly", "Monthly"
    public boolean isActive;

    public AppointmentReminder(String type) {
        this.id = UUID.randomUUID().toString();
        this.title = "";
        this.type = type;
        this.dateTime = 0;
        this.repeatPattern = "None";
        this.isActive = false;
    }
}