package com.example.hellosdl2w;

public class CallLogMessage {
    public String name;
    public String number;
    public String date;
    public String duration;
    public int type ;
    
    /* There are 7 types of call log:
    1. incomming
    2. outgoing
    3. missed
    4. voicemail
    5. rejected
    6. blocked
    7. answer externally ( in different device)
     */

    public CallLogMessage (String name, String number, String duration, String date, int type) {
        this.name = name;
        this.number = number;
        this.duration = duration;
        this.date = date;
        this.type = type;
    }
}
