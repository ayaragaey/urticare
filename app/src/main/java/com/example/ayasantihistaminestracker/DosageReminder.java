package com.example.ayasantihistaminestracker;

import java.util.UUID;

public class DosageReminder {
    public String id;
    public String medName;
    public String condition;
    public int intervalHours;
    public boolean isActive;
    public String section; 
    public long nextTriggerTime;

    public DosageReminder(String section) {
        this.id = UUID.randomUUID().toString();
        this.medName = "";
        this.condition = "";
        this.intervalHours = 1;
        this.isActive = false;
        this.section = section;
        this.nextTriggerTime = 0;
    }
}