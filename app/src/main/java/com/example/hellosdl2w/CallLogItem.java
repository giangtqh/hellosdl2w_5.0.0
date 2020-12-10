package com.example.hellosdl2w;

public class CallLogItem {
    public String name;
    public String number;
    public String date;
    public String duration;
    public int type;
    // There are 3 types of it.
    // 1. Incomming
    // 2. Outgoing
    // 3. Missed
    public CallLogItem( String name, String number, String date, String duration, int type) {
        this.name = name;
        this.number = number;
        this.date = date;
        this.duration = duration;
        this.type = type;
    }
}
