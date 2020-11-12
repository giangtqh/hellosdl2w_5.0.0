package com.example.hellosdl2w;

public class SMSMessage {
    public String address;
    public int read;
    public String body;
    public String date_sent;

    public SMSMessage(String address, int read, String body, String date_sent) {
        this.address = address;
        this.read = read;
        this.body = body;
        this.date_sent = date_sent;
    }
}
