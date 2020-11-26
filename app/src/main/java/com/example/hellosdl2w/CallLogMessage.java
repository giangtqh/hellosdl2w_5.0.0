package com.example.hellosdl2w;

public class CallLogMessage {
    public String name;
    public String number;
    public int type ;
    
    /* There are 7 types of call log:
    1. incomming
    2. outgoing
    3. missed
     */

    public CallLogMessage (String name, String number, int type) {
        this.name = name;
        this.number = number;
        this.type = type;
    }
}
