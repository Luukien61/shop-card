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
    public static final byte INS_SET_PINS = (byte) 0x08;

    private static final byte INS_WRITE_USERNAME_ENC = (byte) 0x11;
    private static final byte INS_WRITE_ADDRESS_ENC = (byte) 0x12;
    private static final byte INS_WRITE_PHONE_ENC = (byte) 0x13;
    private static final byte INS_WRITE_CARD_ID_ENC = (byte) 0x14;
    private static final byte INS_READ_USERNAME_DEC = (byte) 0x21;
    private static final byte INS_READ_ADDRESS_DEC = (byte) 0x22;
    private static final byte INS_READ_PHONE_DEC = (byte) 0x23;
    private static final byte INS_READ_CARD_ID_DEC = (byte) 0x24;

    private static final byte PIN_TYPE_USER = (byte) 0x00;
    private static final byte PIN_TYPE_ADMIN = (byte) 0x01;

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
            byte[] setPINData = new byte[userPINData.length + adminPINData.length];
            System.arraycopy(userPINData, 0, setPINData, 0, userPINData.length);
            System.arraycopy(adminPINData, 0, setPINData, userPINData.length, adminPINData.length);

            BufferedImage original = ImageIO.read(avatar);
            BufferedImage resized = resize(original, 200, 200);
            byte[] avatarData = compressImage(resized, 0.6f);
            System.out.println("Avatar length: " + avatarData.length + " bytes");

            sendData(channel, INS_SET_PINS, setPINData);

//            sendData(channel, INS_WRITE_USERNAME, usernameData);
//            sendData(channel, INS_WRITE_ADDRESS, addressData);
//            sendData(channel, INS_WRITE_PHONE, phoneData);
//            sendData(channel, INS_WRITE_CARD_ID, cardIdData);
//            sendData(channel, INS_WRITE_USER_PIN, userPINData);
//            sendData(channel, INS_WRITE_ADMIN_PIN, adminPINData);
            sendEncryptedData(channel, INS_WRITE_USERNAME_ENC, userPINData, PIN_TYPE_USER, usernameData);
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

    public static void sendEncryptedData(CardChannel channel, byte ins, byte[] pin,
                                         byte pinType, byte[] data) throws CardException {
        if (pin.length != 6) {
            throw new IllegalArgumentException("PIN must be 6 bytes");
        }

        // Tạo payload: PIN (6 bytes) + data
        byte[] payload = new byte[6 + data.length];
        System.arraycopy(pin, 0, payload, 0, 6);
        System.arraycopy(data, 0, payload, 6, data.length);

        // Gửi APDU với P1 = pinType
        CommandAPDU apdu = new CommandAPDU(
                0x00,       // CLA
                ins,            // INS
                pinType,        // P1 (0x00=user, 0x01=admin)
                0x00,           // P2
                payload         // Data: [6-byte PIN][plaintext data]
        );

        ResponseAPDU resp = channel.transmit(apdu);
        if (resp.getSW() != 0x9000) {
            throw new CardException("Error writing encrypted data: 0x" +
                    Integer.toHexString(resp.getSW()));
        }

        System.out.println("Encrypted data written successfully");
    }

    /**
     * Đọc dữ liệu đã mã hóa từ thẻ và giải mã
     *
     * @param channel CardChannel để giao tiếp với thẻ
     * @param ins     Instruction code (0x21-0x24)
     * @param pin     6-byte PIN (user hoặc admin)
     * @param pinType 0x00 = user PIN, 0x01 = admin PIN
     * @return Dữ liệu đã giải mã (plaintext)
     * @throws CardException
     */
    public static byte[] readEncryptedData(CardChannel channel, byte ins, byte[] pin,
                                           byte pinType) throws CardException {
        if (pin.length != 6) {
            throw new IllegalArgumentException("PIN must be 6 bytes");
        }

        // Gửi APDU với PIN để yêu cầu giải mã
        CommandAPDU apdu = new CommandAPDU(
                0x00,       // CLA
                ins,            // INS
                pinType,        // P1 (0x00=user, 0x01=admin)
                0x00,           // P2
                pin,            // Data: 6-byte PIN
                256             // Le: Expect up to 256 bytes response
        );

        ResponseAPDU resp = channel.transmit(apdu);
        if (resp.getSW() != 0x9000) {
            throw new CardException("Error reading encrypted data: 0x" +
                    Integer.toHexString(resp.getSW()));
        }

        byte[] decryptedData = resp.getData();
        System.out.println("Read " + decryptedData.length + " bytes of decrypted data");

        return decryptedData;
    }

    /**
     * Helper: Ghi username đã mã hóa
     */
    public static void writeEncryptedUsername(CardChannel channel, byte[] pin,
                                              byte pinType, String username) throws CardException {
        sendEncryptedData(channel, INS_WRITE_USERNAME_ENC, pin, pinType,
                username.getBytes(StandardCharsets.UTF_8));
    }

    /**
     * Helper: Đọc username đã giải mã
     */
    public static String readEncryptedUsername(CardChannel channel, byte[] pin,
                                               byte pinType) throws Exception {
        byte[] data = readEncryptedData(channel, INS_READ_USERNAME_DEC, pin, pinType);
        return new String(data, "UTF-8");
    }

    /**
     * Helper: Ghi address đã mã hóa
     */
    public static void writeEncryptedAddress(CardChannel channel, byte[] pin,
                                             byte pinType, String address) throws Exception {
        sendEncryptedData(channel, INS_WRITE_ADDRESS_ENC, pin, pinType,
                address.getBytes("UTF-8"));
    }

    /**
     * Helper: Đọc address đã giải mã
     */
    public static String readEncryptedAddress(CardChannel channel, byte[] pin,
                                              byte pinType) throws Exception {
        byte[] data = readEncryptedData(channel, INS_READ_ADDRESS_DEC, pin, pinType);
        return new String(data, "UTF-8");
    }

    /**
     * Helper: Ghi phone đã mã hóa
     */
    public static void writeEncryptedPhone(CardChannel channel, byte[] pin,
                                           byte pinType, String phone) throws Exception {
        sendEncryptedData(channel, INS_WRITE_PHONE_ENC, pin, pinType,
                phone.getBytes("UTF-8"));
    }

    /**
     * Helper: Đọc phone đã giải mã
     */
    public static String readEncryptedPhone(CardChannel channel, byte[] pin,
                                            byte pinType) throws Exception {
        byte[] data = readEncryptedData(channel, INS_READ_PHONE_DEC, pin, pinType);
        return new String(data, "UTF-8");
    }

    /**
     * Helper: Ghi card ID đã mã hóa
     */
    public static void writeEncryptedCardId(CardChannel channel, byte[] pin,
                                            byte pinType, String cardId) throws Exception {
        sendEncryptedData(channel, INS_WRITE_CARD_ID_ENC, pin, pinType,
                cardId.getBytes("UTF-8"));
    }

    /**
     * Helper: Đọc card ID đã giải mã
     */
    public static String readEncryptedCardId(CardChannel channel, byte[] pin,
                                             byte pinType) throws Exception {
        byte[] data = readEncryptedData(channel, INS_READ_CARD_ID_DEC, pin, pinType);
        return new String(data, "UTF-8");
    }

    // ===================================================================
    // DEMO USAGE
    // ===================================================================
    public static void main(String[] args) {
        try {
            // Kết nối với thẻ
            TerminalFactory factory = TerminalFactory.getDefault();
            CardTerminal terminal = factory.terminals().list().get(0);
            Card card = terminal.connect("T=1");
            CardChannel channel = card.getBasicChannel();

            // Select applet (thay AID phù hợp)
            byte[] aid = {0x01, 0x02, 0x03, 0x04, 0x05, 0x06};
            CommandAPDU select = new CommandAPDU(0x00, 0xA4, 0x04, 0x00, aid);
            ResponseAPDU resp = channel.transmit(select);

            if (resp.getSW() != 0x9000) {
                System.err.println("Failed to select applet");
                return;
            }

            // User PIN (6 bytes)
            byte[] userPin = {0x31, 0x32, 0x33, 0x34, 0x35, 0x36}; // "123456"

            // Ghi dữ liệu mã hóa
            System.out.println("\n=== Writing encrypted data ===");
            writeEncryptedUsername(channel, userPin, PIN_TYPE_USER, "Nguyen Van A");
            writeEncryptedAddress(channel, userPin, PIN_TYPE_USER, "123 Hai Ba Trung, Hanoi");
            writeEncryptedPhone(channel, userPin, PIN_TYPE_USER, "+84912345678");
            writeEncryptedCardId(channel, userPin, PIN_TYPE_USER, "CARD-2024-001");

            // Đọc dữ liệu đã mã hóa
            System.out.println("\n=== Reading encrypted data ===");
            String username = readEncryptedUsername(channel, userPin, PIN_TYPE_USER);
            String address = readEncryptedAddress(channel, userPin, PIN_TYPE_USER);
            String phone = readEncryptedPhone(channel, userPin, PIN_TYPE_USER);
            String cardId = readEncryptedCardId(channel, userPin, PIN_TYPE_USER);

            System.out.println("Username: " + username);
            System.out.println("Address: " + address);
            System.out.println("Phone: " + phone);
            System.out.println("Card ID: " + cardId);

            // Test với sai PIN (sẽ throw exception)
            System.out.println("\n=== Testing wrong PIN ===");
            byte[] wrongPin = {0x00, 0x00, 0x00, 0x00, 0x00, 0x00};
            try {
                readEncryptedUsername(channel, wrongPin, PIN_TYPE_USER);
            } catch (CardException e) {
                System.out.println("Expected error with wrong PIN: " + e.getMessage());
            }

            card.disconnect(false);
            System.out.println("\nDone!");

        } catch (Exception e) {
            e.printStackTrace();
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
