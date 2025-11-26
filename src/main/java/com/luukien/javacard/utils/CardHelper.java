package com.luukien.javacard.utils;

import javax.imageio.IIOImage;
import javax.imageio.ImageIO;
import javax.imageio.ImageWriteParam;
import javax.imageio.ImageWriter;
import javax.imageio.stream.MemoryCacheImageOutputStream;
import javax.smartcardio.*;
import java.awt.*;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.security.SecureRandom;
import java.util.Arrays;
import java.util.HexFormat;
import java.util.List;

public class CardHelper {
    public static final byte[] AID = {(byte) 0x00, 0x00, 0x00, 0x00, 0x00, 0x01};
    public static final byte INS_INITIATE_KEY = (byte) 0x00;
    public static final byte INS_WRITE_USERNAME = (byte) 0x01;
    public static final byte INS_WRITE_ADDRESS = (byte) 0x02;
    public static final byte INS_WRITE_PHONE = (byte) 0x03;
    public static final byte INS_WRITE_CARD_ID = (byte) 0x04;
    public static final byte INS_WRITE_USER_PIN = (byte) 0x05;
    public static final byte INS_WRITE_ADMIN_PIN = (byte) 0x06;
    public static final byte INS_WRITE_AVATAR = (byte) 0x07;
    public static final byte INS_CLEAR_DATA = (byte) 0x10;
    public static final byte INS_UPDATE_USER_PIN = (byte) 0x20;

    public static final String SUCCESS_RESPONSE = "9000";

    private static final SecureRandom random = new SecureRandom();
    public static final int SUCCESS_SW = 0x9000;


    public static CardChannel connect() throws CardException {
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

        if (channel == null) {
            throw new RuntimeException();
        }

        return channel;
    }

    public static CommandAPDU selectAID(byte[] aid) {
        return new CommandAPDU(0x00, 0xA4, 0x04, 0x00, aid);
    }


    public static Boolean initiateCard(String username, String address, String phone, String userPIN, String adminPIN, File avatar, String cardId) {
        try {
            CardChannel channel = connect();
            CommandAPDU select = selectAID(AID);
            ResponseAPDU resp = channel.transmit(select);
            if (!Integer.toHexString(resp.getSW()).equals(SUCCESS_RESPONSE)) {
                throw new RuntimeException("unable to select the applet");
            }

            byte[] usernameData = username.getBytes(StandardCharsets.UTF_8);
            byte[] addressData = address.getBytes(StandardCharsets.UTF_8);
            byte[] phoneData = phone.getBytes(StandardCharsets.UTF_8);
            byte[] cardIdData = cardId.getBytes(StandardCharsets.UTF_8);
            byte[] userPINData = userPIN.getBytes(StandardCharsets.UTF_8);
            byte[] adminPINData = adminPIN.getBytes(StandardCharsets.UTF_8);

            BufferedImage original = ImageIO.read(avatar);
            BufferedImage resized = resize(original, 200, 200);
            byte[] avatarData = compressImage(resized, 0.6f);
            System.out.println("Avatar length: " + avatarData.length + " bytes");

            sendData(channel, INS_WRITE_USERNAME, usernameData);
            sendData(channel, INS_WRITE_ADDRESS, addressData);
            sendData(channel, INS_WRITE_PHONE, phoneData);
            sendData(channel, INS_WRITE_CARD_ID, cardIdData);
            sendData(channel, INS_WRITE_USER_PIN, userPINData);
            sendData(channel, INS_WRITE_ADMIN_PIN, adminPINData);
            sendData(channel, INS_WRITE_AVATAR, avatarData);
            return true;
        } catch (Exception e) {
            System.out.println(e.getMessage());
            return false;
        }
    }

    public static String[] initiateKeyAndCardId() {
        try {
            String cardId = generate16Digits();
            CardChannel channel = connect();
            CommandAPDU select = selectAID(AID);
            ResponseAPDU resp = channel.transmit(select);
            if (!Integer.toHexString(resp.getSW()).equals(SUCCESS_RESPONSE)) {
                throw new RuntimeException("unable to select the applet");
            }
            String publicKey = initiateKey(channel);
            return new String[]{publicKey, cardId};
        } catch (Exception e) {
            System.out.println(e.getMessage());
            return null;
        }
    }

    public static boolean clearCardData() {
        try {
            CardChannel channel = connect();
            CommandAPDU select = selectAID(AID);
            ResponseAPDU resp = channel.transmit(select);
            if (!Integer.toHexString(resp.getSW()).equals(SUCCESS_RESPONSE)) {
                throw new RuntimeException("unable to select the applet");
            }

            CommandAPDU clearData = new CommandAPDU(0x00, INS_CLEAR_DATA, 0x00, 0x00, 0);
            ResponseAPDU r = channel.transmit(clearData);

            return r.getSW() == 0x9000;
        } catch (Exception e) {
            return false;
        }
    }

    public static boolean changeUserPin(String currentPin, String newPin) {
        if (currentPin.length() != 6 || newPin.length() != 6 || !currentPin.matches("\\d{6}") || !newPin.matches("\\d{6}")) {
            return false;
        }
        try {
            CardChannel channel = connect();
            CommandAPDU select = selectAID(AID);
            ResponseAPDU resp = channel.transmit(select);
            if (resp.getSW() != SUCCESS_SW) {
                throw new RuntimeException("unable to select the applet");
            }
            CommandAPDU cmd = new CommandAPDU(
                    0x00,
                    INS_UPDATE_USER_PIN,
                    0x00, 0x00,
                    (currentPin + newPin).getBytes()
            );

            ResponseAPDU result = channel.transmit(cmd);
            return result.getSW() == SUCCESS_SW;

        } catch (Exception e) {
            e.printStackTrace();
            return false;
        }
    }

    private static String initiateKey(CardChannel channel) throws CardException {
        CommandAPDU generateKey = new CommandAPDU(0x00, INS_INITIATE_KEY, 0x00, 0x00, 0);
        ResponseAPDU r = channel.transmit(generateKey);

        if (r.getSW() == 0x9000) {
            byte[] publicKeyData = r.getData();
            String publicKeyText = HexFormat.of().formatHex(publicKeyData);
            System.out.println("Public Key: " + publicKeyText);
            return publicKeyText;
        }
        return null;
    }

    private static String generate16Digits() {
        StringBuilder sb = new StringBuilder(16);

        sb.append(random.nextInt(9) + 1);

        for (int i = 1; i < 16; i++) {
            sb.append(random.nextInt(10));
        }

        return sb.toString();
    }

    private static void sendData(CardChannel channel, byte ins, byte[] data) throws CardException {
        int chunkSize = 250;
        for (int offset = 0; offset < data.length; offset += chunkSize) {
            int len = Math.min(chunkSize, data.length - offset);
            byte[] chunk = Arrays.copyOfRange(data, offset, offset + len);

            CommandAPDU apdu = new CommandAPDU(
                    0x00,
                    ins,
                    (byte) ((offset >> 8) & 0xFF), // P1 = high byte offset
                    (byte) (offset & 0xFF), // P2 = low byte offset
                    chunk
            );

            ResponseAPDU resp = channel.transmit(apdu);
            if (resp.getSW() != 0x9000) {
                throw new CardException("Error writing data to card: " + Integer.toHexString(resp.getSW()));
            }
        }
    }

    private static byte[] compressImage(BufferedImage image, float quality) throws IOException {
        ByteArrayOutputStream baos = new ByteArrayOutputStream();

        ImageWriter writer = ImageIO.getImageWritersByFormatName("jpg").next();
        ImageWriteParam param = writer.getDefaultWriteParam();

        if (param.canWriteCompressed()) {
            param.setCompressionMode(ImageWriteParam.MODE_EXPLICIT);
            param.setCompressionQuality(quality); // 0.0f → thấp nhất, 1.0f → tốt nhất
        }

        writer.setOutput(new MemoryCacheImageOutputStream(baos));
        writer.write(null, new IIOImage(image, null, null), param);
        writer.dispose();

        return baos.toByteArray();
    }

    private static BufferedImage resize(BufferedImage originalImage, int targetWidth, int targetHeight) {
        BufferedImage resizedImage = new BufferedImage(targetWidth, targetHeight, BufferedImage.TYPE_INT_RGB);
        Graphics2D g = resizedImage.createGraphics();
        g.drawImage(originalImage, 0, 0, targetWidth, targetHeight, null);
        g.dispose();
        return resizedImage;
    }
}
