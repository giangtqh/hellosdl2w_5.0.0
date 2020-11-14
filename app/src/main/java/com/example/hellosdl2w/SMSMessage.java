package com.example.hellosdl2w;

public class SMSMessage {
    public String address; // sender or receiver depend on the type
    public String body;
    public String date;
    public int read;    // 0: unread, 1: read
    public int type;    // 1: address is sender (inbox), 2: address is receiver (sent)

    public SMSMessage(String address, String body, String date, int read, int type) {
        this.address = address;
        this.body = body;
        this.date = date;
        this.read = read;
        this.type = type;
    }
}
