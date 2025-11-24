package com.luukien.javacard.utils;

import javax.smartcardio.*;
import java.util.List;

public class CardHelper {
    public static void connect()  {
        try{
            TerminalFactory factory = TerminalFactory.getDefault();
            List<CardTerminal> terminals = factory.terminals().list();

            System.out.println("Card Readers:");
            for (CardTerminal terminal : terminals) {
                System.out.println(" - " + terminal.getName());
            }

            CardTerminal terminal = terminals.getFirst();
            terminal.waitForCardPresent(0);

            Card card = terminal.connect("T=0");
            CardChannel channel = card.getBasicChannel();

            if(channel==null){
                throw new RuntimeException();
            }

            System.out.println("Connected: " + card);

            byte[] aid = {(byte) 0x00, 0x00, 0x00, 0x00, 0x00, 0x06};

            CommandAPDU select = new CommandAPDU(0x00, 0xA4, 0x04, 0x00, aid);
            ResponseAPDU resp = channel.transmit(select);

            if(!Integer.toHexString(resp.getSW()).equals("9000")){
                throw new RuntimeException("unable to select the applet");
            }
            System.out.println("SELECT SW: " + Integer.toHexString(resp.getSW()));


            byte[] apdu = new byte[] { 0x00, 0x20, 0x00, 0x00, 0x00 };
            ResponseAPDU response = channel.transmit(new CommandAPDU(apdu));

            System.out.println("Response: " + new String(response.getData()));
            System.out.println("SW: " + Integer.toHexString(response.getSW()));


        }catch (Exception e){

        }

    }
}
