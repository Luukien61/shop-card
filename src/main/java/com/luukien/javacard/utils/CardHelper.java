package com.luukien.javacard.utils;

import com.luukien.javacard.model.UserCardInfo;

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
import java.security.KeyFactory;
import java.security.PublicKey;
import java.security.SecureRandom;
import java.security.Signature;
import java.security.spec.RSAPublicKeySpec;
import java.util.Arrays;
import java.util.Base64;
import java.util.List;

public class CardHelper {
    public static final byte[] AID = {(byte) 0x00, 0x00, 0x00, 0x00, 0x00, 0x01};
    public static final byte INS_INITIATE_KEY = (byte) 0x00;
    public static final byte INS_WRITE_USERNAME = (byte) 0x01;
    public static final byte INS_WRITE_ADDRESS = (byte) 0x02;
    public static final byte INS_WRITE_PHONE = (byte) 0x03;
    public static final byte INS_WRITE_CARD_ID = (byte) 0x04;
    public static final byte INS_SET_PINS = (byte) 0x05;
    public static final byte INS_WRITE_AVATAR = (byte) 0x07;
    public static final byte INS_CLEAR_DATA = (byte) 0x10;
    public static final byte INS_UPDATE_USER_PIN = (byte) 0x20;
    public static final byte INS_VERIFY_PIN = (byte) 0x40;
    public static final byte INS_READ_ALL_DATA = (byte) 0x55;
    public static final byte INS_READ_AVATAR = (byte) 0x54;
    public static final byte INS_READ_CARD_ID = (byte) 0x53;
    public static final byte INS_VERIFY_CARD = (byte) 0x11;


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
            throw new RuntimeException("Vui lòng cắm thẻ...");
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

            sendData(channel, INS_WRITE_USERNAME,
                    withUserPin(userPINData, usernameData));

            sendData(channel, INS_WRITE_ADDRESS,
                    withUserPin(userPINData, addressData));

            sendData(channel, INS_WRITE_PHONE,
                    withUserPin(userPINData, phoneData));

            sendData(channel, INS_WRITE_CARD_ID, cardIdData);


            //sendEncryptedData(channel, INS_WRITE_USERNAME_ENC, userPINData, PIN_TYPE_USER, usernameData);
            sendAvatarData(channel, INS_WRITE_AVATAR, userPINData, avatarData);
            return true;
        } catch (Exception e) {
            System.out.println(e.getMessage());
            return false;
        }
    }

    private static byte[] withUserPin(byte[] userPin, byte[] data) {
        byte[] out = new byte[userPin.length + data.length];
        System.arraycopy(userPin, 0, out, 0, userPin.length);
        System.arraycopy(data, 0, out, userPin.length, data.length);
        return out;
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
            var data = setPinDataWithTimestamp(currentPin, newPin);
            CommandAPDU cmd = new CommandAPDU(
                    0x00,
                    INS_UPDATE_USER_PIN,
                    0x00, 0x00,
                    data
            );

            ResponseAPDU result = channel.transmit(cmd);
            return result.getSW() == SUCCESS_SW;

        } catch (Exception e) {
            e.printStackTrace();
            return false;
        }
    }

    private static String initiateKey(CardChannel channel) throws CardException {
        CommandAPDU generateKey = new CommandAPDU(0x00, INS_INITIATE_KEY, 0x00, 0x00);
        ResponseAPDU r = channel.transmit(generateKey);

        if (r.getSW() != SUCCESS_SW) {
            throw new CardException(
                    String.format("Failed to generate key: 0x%04X", r.getSW())
            );
        }

        byte[] publicKeyData = r.getData();

        if (publicKeyData.length == 0) {
            throw new CardException("Empty public key data");
        }

        validatePublicKeyFormat(publicKeyData);

        // Convert to Base64 để lưu vào database

        return Base64.getEncoder().encodeToString(publicKeyData);
    }

    private static void validatePublicKeyFormat(byte[] data) throws CardException {
        if (data.length < 4) {
            throw new CardException("Invalid public key data: too short");
        }

        int offset = 0;

        // Check modulus length
        int modulusLen = ((data[offset] & 0xFF) << 8) | (data[offset + 1] & 0xFF);
        offset += 2;

        if (offset + modulusLen > data.length) {
            throw new CardException("Invalid modulus length");
        }
        offset += modulusLen;

        // Check exponent length
        if (offset + 2 > data.length) {
            throw new CardException("Missing exponent length");
        }

        int expLen = ((data[offset] & 0xFF) << 8) | (data[offset + 1] & 0xFF);
        offset += 2;

        if (offset + expLen != data.length) {
            throw new CardException("Invalid exponent length");
        }

        System.out.println("✓ Public key format valid: modulus=" + modulusLen + " bytes, exponent=" + expLen + " bytes");
    }

    private static String generate16Digits() {
        StringBuilder sb = new StringBuilder(16);

        sb.append(random.nextInt(9) + 1);

        for (int i = 1; i < 16; i++) {
            sb.append(random.nextInt(10));
        }

        return sb.toString();
    }

    private static byte[] setPinDataWithTimestamp(String currentPin, String newPin) {

        byte[] data = new byte[16];
        System.arraycopy(currentPin.getBytes(), 0, data, 0, 6);
        System.arraycopy(newPin.getBytes(), 0, data, 6, 6);

        return setWithTimestamp(data);
    }

    private static byte[] setWithTimestamp(byte[] data) {
        long currentTimestamp = System.currentTimeMillis() / 1000;
        byte[] timestampBytes = new byte[4];
        timestampBytes[0] = (byte) ((currentTimestamp >> 24) & 0xFF);
        timestampBytes[1] = (byte) ((currentTimestamp >> 16) & 0xFF);
        timestampBytes[2] = (byte) ((currentTimestamp >> 8) & 0xFF);
        timestampBytes[3] = (byte) (currentTimestamp & 0xFF);
        int length = data.length;
        byte[] newData = new byte[length + 4];
        System.arraycopy(data, 0, newData, 0, length);
        System.arraycopy(timestampBytes, 0, newData, length, 4);
        return newData;
    }

    public static Boolean verifyUserPin(String pin) throws CardException {
        CardChannel channel = connect();
        CommandAPDU select = selectAID(AID);
        ResponseAPDU resp = channel.transmit(select);
        if (!Integer.toHexString(resp.getSW()).equals(SUCCESS_RESPONSE)) {
            throw new RuntimeException("unable to select the applet");
        }
        byte[] data = setWithTimestamp(pin.getBytes(StandardCharsets.UTF_8));

        CommandAPDU apdu = new CommandAPDU(
                0x00,
                INS_VERIFY_PIN,
                0x00,
                0x00,
                data
        );

        resp = channel.transmit(apdu);
        return  resp.getSW() == SUCCESS_SW;
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
            if (resp.getSW() != SUCCESS_SW) {
                throw new CardException("Error writing data to card: " + Integer.toHexString(resp.getSW()));
            }
        }
    }

    private static void sendAvatarData(
            CardChannel channel,
            byte ins,
            byte[] userPINData,
            byte[] avatarData
    ) throws CardException {

        final int MAX_APDU_DATA = 246;
        final int PIN_LEN = userPINData.length;
        final int AVATAR_CHUNK_SIZE = MAX_APDU_DATA - PIN_LEN;

        for (int offset = 0; offset < avatarData.length; offset += AVATAR_CHUNK_SIZE) {

            int len = Math.min(AVATAR_CHUNK_SIZE, avatarData.length - offset);

            byte[] payload = new byte[PIN_LEN + len];
            System.arraycopy(userPINData, 0, payload, 0, PIN_LEN);
            System.arraycopy(avatarData, offset, payload, PIN_LEN, len);

            CommandAPDU apdu = new CommandAPDU(
                    0x00,
                    ins,
                    (byte) ((offset >> 8) & 0xFF),
                    (byte) (offset & 0xFF),
                    payload
            );

            ResponseAPDU resp = channel.transmit(apdu);
            if (resp.getSW() != SUCCESS_SW) {
                throw new CardException(
                        "Error writing avatar data: " + Integer.toHexString(resp.getSW())
                );
            }
        }
    }

    public static Boolean isCardVerified() throws Exception {
        CardChannel channel = connect();
        CommandAPDU select = selectAID(AID);
        ResponseAPDU resp = channel.transmit(select);
        if (!Integer.toHexString(resp.getSW()).equals(SUCCESS_RESPONSE)) {
            throw new RuntimeException("unable to select the applet");
        }
        String cardId = readCardId();
        String publicKey = DatabaseHelper.getUserPublicKey(cardId);
        return verifyCard(channel, publicKey);
    }


    public static UserCardInfo getUserCardInfo(String pin) throws Exception {
        CardChannel channel = connect();
        CommandAPDU select = selectAID(AID);
        ResponseAPDU resp = channel.transmit(select);
        if (!Integer.toHexString(resp.getSW()).equals(SUCCESS_RESPONSE)) {
            throw new RuntimeException("unable to select the applet");
        }
        byte[] pinData = pin.getBytes(StandardCharsets.UTF_8);
        return readData(channel, INS_READ_ALL_DATA, pinData);
    }

    private static UserCardInfo readData(CardChannel channel, byte ins, byte[] pin) throws Exception {

        ByteArrayOutputStream dataStream = new ByteArrayOutputStream();

        short offset = 0;
        byte[] command = new byte[11]; // CLA INS P1 P2 LC + 6 PIN
        command[0] = (byte) 0x00; // CLA
        command[1] = (byte) 0x55; // INS_READ_ALL_DATA
        command[2] = (byte) (0); // P1
        command[3] = (byte) (0); // P2
        command[4] = (byte) 0x06; // LC = 6
        System.arraycopy(pin, 0, command, 5, 6);

        ResponseAPDU response = channel.transmit(new CommandAPDU(command));

        if (response.getSW() != SUCCESS_SW) {
            throw new CardException("Read failed: " + Integer.toHexString(response.getSW()));
        }

        byte[] data = response.getData();

        // Parse header (10 bytes lengths)
        int readOffset = 0;
        short nameLen = (short) (((data[readOffset++] & 0xFF) << 8) | (data[readOffset++] & 0xFF));
        short addressLen = (short) (((data[readOffset++] & 0xFF) << 8) | (data[readOffset++] & 0xFF));
        short phoneLen = (short) (((data[readOffset++] & 0xFF) << 8) | (data[readOffset++] & 0xFF));
        short cardIdLen = (short) (((data[readOffset++] & 0xFF) << 8) | (data[readOffset++] & 0xFF));


        short totalDataLen = (short) (nameLen + addressLen + phoneLen + cardIdLen);

        // Copy data sau header
        dataStream.write(data, readOffset, data.length - readOffset);
        offset = (short) (data.length - readOffset);

        // Đọc tiếp nếu chưa đủ
        while (offset < totalDataLen) {
            command[2] = (byte) ((offset >> 8) & 0xFF); // Update P1
            command[3] = (byte) (offset & 0xFF); // Update P2

            response = channel.transmit(new CommandAPDU(command));

            if (response.getSW() != SUCCESS_SW) {
                throw new CardException("Read continuation failed: " + Integer.toHexString(response.getSW()));
            }

            data = response.getData();
            dataStream.write(data, 0, data.length);
            offset += (short) data.length;
        }

        // Parse data thành các fields
        byte[] allData = dataStream.toByteArray();
        int pos = 0;

        byte[] name = new byte[nameLen];
        System.arraycopy(allData, pos, name, 0, nameLen);
        pos += nameLen;

        byte[] address = new byte[addressLen];
        System.arraycopy(allData, pos, address, 0, addressLen);
        pos += addressLen;

        byte[] phone = new byte[phoneLen];
        System.arraycopy(allData, pos, phone, 0, phoneLen);
        pos += phoneLen;

        byte[] cardId = new byte[cardIdLen];
        System.arraycopy(allData, pos, cardId, 0, cardIdLen);

        String userName = new String(name, StandardCharsets.UTF_8);
        System.out.println("Name: " + userName);
        String userAddress = new String(address, StandardCharsets.UTF_8);
        System.out.println("Address: " + userAddress);
        String userPhone = new String(phone, StandardCharsets.UTF_8);
        System.out.println("Phone: " + userPhone);
        String userCardId = new String(cardId, StandardCharsets.UTF_8);
        System.out.println("CardId: " + userCardId);
        byte[] avatar = readAvatar(channel, pin, INS_READ_AVATAR);
        String avatarBase64 = Base64.getEncoder().encodeToString(avatar);
        return UserCardInfo.builder()
                .userName(userName)
                .address(userAddress)
                .phone(userPhone)
                .image(avatarBase64)
                .cardId(userCardId)
                .build();
    }

    private static byte[] readAvatar(CardChannel channel, byte[] pin, byte ins)
            throws CardException {

        ByteArrayOutputStream dataStream = new ByteArrayOutputStream();
        int offset = 0;
        boolean isLastChunk = false;

        while (!isLastChunk) {
            byte[] command = new byte[11];
            command[0] = (byte) 0x00;
            command[1] = ins;
            command[2] = (byte) ((offset >> 8) & 0xFF);
            command[3] = (byte) (offset & 0xFF);
            command[4] = (byte) 0x06;
            System.arraycopy(pin, 0, command, 5, 6);

            ResponseAPDU response = channel.transmit(new CommandAPDU(command));

            if (response.getSW() != SUCCESS_SW) {
                throw new CardException(
                        String.format("Read failed at offset %d: 0x%04X", offset, response.getSW())
                );
            }

            byte[] responseData = response.getData();

            if (responseData.length == 0) {
                throw new CardException("Received empty response from card");
            }

            // Byte đầu tiên là flag
            byte flag = responseData[0];
            isLastChunk = (flag == 0x01);

            // Data thực sự bắt đầu từ byte thứ 2
            int dataLen = responseData.length - 1;
            if (dataLen > 0) {
                dataStream.write(responseData, 1, dataLen);
                offset += dataLen;
            }
        }

        System.out.println("Data length: " + dataStream.size());
        return dataStream.toByteArray();
    }

    public static String readCardId() throws Exception {
        CardChannel channel = connect();
        CommandAPDU select = selectAID(AID);
        ResponseAPDU resp = channel.transmit(select);
        if (!Integer.toHexString(resp.getSW()).equals(SUCCESS_RESPONSE)) {
            throw new RuntimeException("unable to select the applet");
        }
        CommandAPDU apdu = new CommandAPDU(
                0x00,
                INS_READ_CARD_ID,
                0x00,
                0x00
        );

        resp = channel.transmit(apdu);
        if (resp.getSW() != SUCCESS_SW) {
            throw new RuntimeException("unable to get card id");
        }

        byte[] cardIdData = resp.getData();
        String cardId = new String(cardIdData, StandardCharsets.UTF_8);
        System.out.println("CardId: " + cardId);
        return cardId;

    }

    private static PublicKey parsePublicKey(byte[] pubKeyData) throws Exception {
        int offset = 0;

        // Read modulus length
        int modulusLen = ((pubKeyData[offset] & 0xFF) << 8) | (pubKeyData[offset + 1] & 0xFF);
        offset += 2;

        // Read modulus
        byte[] modulus = Arrays.copyOfRange(pubKeyData, offset, offset + modulusLen);
        offset += modulusLen;

        // Read exponent length
        int expLen = ((pubKeyData[offset] & 0xFF) << 8) | (pubKeyData[offset + 1] & 0xFF);
        offset += 2;

        // Read exponent
        byte[] exponent = Arrays.copyOfRange(pubKeyData, offset, offset + expLen);

        // Create RSA public key
        RSAPublicKeySpec keySpec = new RSAPublicKeySpec(
                new java.math.BigInteger(1, modulus),
                new java.math.BigInteger(1, exponent)
        );

        KeyFactory keyFactory = KeyFactory.getInstance("RSA");
        PublicKey publicKey = keyFactory.generatePublic(keySpec);

        System.out.println("Public key reconstructed successfully");
        return publicKey;
    }

    public static boolean verifyCard(CardChannel channel, String publicKeyBase64) throws Exception {

        byte[] pubKeyData = Base64.getDecoder().decode(publicKeyBase64);
        PublicKey publicKey = parsePublicKey(pubKeyData);


        SecureRandom random = new SecureRandom();
        byte[] challenge = new byte[16];
        random.nextBytes(challenge);


        System.out.println("Challenge: " + bytesToHex(challenge));

        CommandAPDU command = new CommandAPDU(
                0x00,                    // CLA
                INS_VERIFY_CARD,        // INS
                0x00,                    // P1
                0x00,                    // P2
                challenge
        );

        ResponseAPDU response = channel.transmit(command);

        if (response.getSW() != SUCCESS_SW) {
            throw new CardException(
                    String.format("Card verification failed: 0x%04X", response.getSW())
            );
        }

        byte[] signature = response.getData();
        System.out.println("Signature length: " + signature.length);
        System.out.println("Signature: " + bytesToHex(signature));

        // Verify signature with public key
        Signature verifier = Signature.getInstance("SHA1withRSA");
        verifier.initVerify(publicKey);
        verifier.update(challenge);

        boolean isValid = verifier.verify(signature);

        System.out.println("Signature valid: " + isValid);
        return isValid;
    }

    /**
     * Helper method to convert bytes to hex string
     */
    private static String bytesToHex(byte[] bytes) {
        StringBuilder sb = new StringBuilder();
        for (byte b : bytes) {
            sb.append(String.format("%02X", b));
        }
        return sb.toString();
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
