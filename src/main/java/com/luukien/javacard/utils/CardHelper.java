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
import java.security.*;
import java.security.spec.RSAPublicKeySpec;
import java.math.BigInteger;
import java.util.Arrays;
import java.util.HexFormat;
import java.util.List;

public class CardHelper {

    public static final byte[] AID = {(byte) 0x11, 0x22, 0x33, 0x44, 0x55, 0x00};

    public static final byte INS_INITIATE_KEY = (byte) 0x00;
    public static final byte INS_WRITE_AVATAR = (byte) 0x07;
    public static final byte INS_SET_PINS     = (byte) 0x08;

    public static final byte INS_CLEAR_DATA      = (byte) 0x10;
    public static final byte INS_UPDATE_USER_PIN = (byte) 0x20;

    private static final byte INS_WRITE_USERNAME_ENC = (byte) 0x11;
    private static final byte INS_WRITE_ADDRESS_ENC  = (byte) 0x12;
    private static final byte INS_WRITE_PHONE_ENC    = (byte) 0x13;
    private static final byte INS_WRITE_CARD_ID_ENC  = (byte) 0x14;

    private static final byte INS_READ_USERNAME_DEC = (byte) 0x21;
    private static final byte INS_READ_ADDRESS_DEC  = (byte) 0x22;
    private static final byte INS_READ_PHONE_DEC    = (byte) 0x23;
    private static final byte INS_READ_CARD_ID_DEC  = (byte) 0x24;

    // Card authentication
    private static final byte INS_SIGN_CHALLENGE = (byte) 0x30;

    private static final byte PIN_TYPE_USER  = (byte) 0x00;
    private static final byte PIN_TYPE_ADMIN = (byte) 0x01;

    public static final int SUCCESS_SW = 0x9000;
    private static final SecureRandom random = new SecureRandom();

    // IMPORTANT: cache public key of the last generated keypair (so initiateCard can authenticate later)
    private static String LAST_PUBLIC_KEY_HEX = null;

    // ============================================================
    // CONNECT / SELECT
    // ============================================================

    public static CardChannel connect() throws CardException {
        TerminalFactory factory = TerminalFactory.getDefault();
        List<CardTerminal> terminals = factory.terminals().list();

        System.out.println("Card Readers:");
        for (CardTerminal terminal : terminals) {
            System.out.println(" - " + terminal.getName());
        }
        if (terminals.isEmpty()) throw new CardException("No card terminals found");

        CardTerminal terminal = terminals.get(0);
        terminal.waitForCardPresent(0);

        Card card = terminal.connect("*");
        CardChannel channel = card.getBasicChannel();
        if (channel == null) throw new CardException("Basic channel is null");
        return channel;
    }

    public static CommandAPDU selectAID(byte[] aid) {
        return new CommandAPDU(0x00, 0xA4, 0x04, 0x00, aid);
    }

    private static void selectOrThrow(CardChannel channel) throws CardException {
        ResponseAPDU resp = channel.transmit(selectAID(AID));
        if (resp.getSW() != SUCCESS_SW) {
            throw new CardException("Unable to select applet. SW=0x" + Integer.toHexString(resp.getSW()));
        }
    }

    // ============================================================
    // STEP 1: INITIATE KEY + CARD ID (NO AUTH HERE!)
    // ============================================================

    /**
     * Chỉ làm: SELECT -> INITIATE_KEY -> cache public key -> return [publicKeyHex, cardId]
     * KHÔNG gọi SIGN_CHALLENGE ở đây (vì sau CLEAR thì PIN chưa set => 6982).
     */
    public static String[] initiateKeyAndCardId() {
        try {
            String cardId = generate16Digits();

            CardChannel channel = connect();
            selectOrThrow(channel);

            String publicKeyHex = initiateKey(channel);
            LAST_PUBLIC_KEY_HEX = publicKeyHex; // cache for later auth in initiateCard()

            return new String[]{publicKeyHex, cardId};
        } catch (Exception e) {
            System.out.println("initiateKeyAndCardId error: " + e.getMessage());
            return null;
        }
    }

    private static String initiateKey(CardChannel channel) throws CardException {
        // Le 256 to avoid 6C83 round-trip
        CommandAPDU generateKey = new CommandAPDU(0x00, INS_INITIATE_KEY, 0x00, 0x00, 256);
        ResponseAPDU r = channel.transmit(generateKey);

        if (r.getSW() != SUCCESS_SW) {
            throw new CardException("INITIATE_KEY failed. SW=0x" + Integer.toHexString(r.getSW()));
        }

        byte[] publicKeyData = r.getData();
        String publicKeyHex = HexFormat.of().formatHex(publicKeyData);
        System.out.println("Public Key(hex): " + publicKeyHex);
        return publicKeyHex;
    }

    // ============================================================
    // STEP 2: WRITE CARD DATA + AUTH AFTER SET_PINS
    // ============================================================

    public static Boolean initiateCard(String username, String address, String phone,
                                       String userPIN, String adminPIN, File avatar, String cardId) {
        try {
            CardChannel channel = connect();
            selectOrThrow(channel);

            byte[] userPin6  = pinTo6Bytes(userPIN);
            byte[] adminPin6 = pinTo6Bytes(adminPIN);

            // 1) SET_PINS first (so SIGN_CHALLENGE can verify admin PIN)
            byte[] setPINData = new byte[12];
            System.arraycopy(userPin6, 0, setPINData, 0, 6);
            System.arraycopy(adminPin6, 0, setPINData, 6, 6);
            sendOnce(channel, INS_SET_PINS, (byte)0x00, (byte)0x00, setPINData);

            // 2) AUTHENTICATE CARD (Cách 1) AFTER SET_PINS
            if (LAST_PUBLIC_KEY_HEX == null) {
                throw new CardException("Missing cached public key. Call initiateKeyAndCardId() first.");
            }
            boolean authOk = authenticateCard(channel, LAST_PUBLIC_KEY_HEX, adminPin6);
            if (!authOk) {
                throw new CardException("Card authentication verify = false");
            }

            // 3) WRITE encrypted fields
            sendEncryptedData(channel, INS_WRITE_USERNAME_ENC, userPin6, PIN_TYPE_USER, username.getBytes(StandardCharsets.UTF_8));
            sendEncryptedData(channel, INS_WRITE_ADDRESS_ENC,  userPin6, PIN_TYPE_USER, address.getBytes(StandardCharsets.UTF_8));
            sendEncryptedData(channel, INS_WRITE_PHONE_ENC,    userPin6, PIN_TYPE_USER, phone.getBytes(StandardCharsets.UTF_8));
            sendEncryptedData(channel, INS_WRITE_CARD_ID_ENC,  userPin6, PIN_TYPE_USER, cardId.getBytes(StandardCharsets.UTF_8));

            // 4) Avatar
            if (avatar != null) {
                BufferedImage original = ImageIO.read(avatar);
                if (original == null) throw new IOException("Cannot read image: " + avatar.getAbsolutePath());

                BufferedImage resized = resize(original, 200, 200);
                byte[] avatarData = compressImage(resized, 0.6f);

                System.out.println("Avatar length: " + avatarData.length + " bytes");
                sendAvatar(channel, avatarData);
            }

            return true;
        } catch (Exception e) {
            System.out.println("initiateCard error: " + e.getMessage());
            return false;
        }
    }

    // ============================================================
    // AUTH helpers
    // ============================================================

    private static boolean authenticateCard(CardChannel channel, String publicKeyHex, byte[] adminPin6) throws Exception {
        PublicKey rsaPub = parseRsa1024PubFromHex(publicKeyHex);

        byte[] challenge = new byte[32];
        random.nextBytes(challenge);

        byte[] payload = new byte[6 + challenge.length];
        System.arraycopy(adminPin6, 0, payload, 0, 6);
        System.arraycopy(challenge, 0, payload, 6, challenge.length);

        CommandAPDU apdu = new CommandAPDU(0x00, INS_SIGN_CHALLENGE, 0x00, 0x00, payload, 256);
        ResponseAPDU resp = channel.transmit(apdu);
        if (resp.getSW() != SUCCESS_SW) {
            throw new CardException("SIGN_CHALLENGE failed. SW=0x" + Integer.toHexString(resp.getSW()));
        }

        byte[] sig = resp.getData();

        Signature verifier = Signature.getInstance("SHA1withRSA");
        verifier.initVerify(rsaPub);
        verifier.update(challenge);

        boolean ok = verifier.verify(sig);
        System.out.println("Authenticate card verified = " + ok);
        return ok;
    }

    private static PublicKey parseRsa1024PubFromHex(String publicKeyHex) throws Exception {
        byte[] pub = HexFormat.of().parseHex(publicKeyHex);
        if (pub.length < 129) throw new IllegalArgumentException("Public key data too short: " + pub.length);

        byte[] mod = Arrays.copyOfRange(pub, 0, 128);
        byte[] exp = Arrays.copyOfRange(pub, 128, pub.length);

        BigInteger n = new BigInteger(1, mod);
        BigInteger e = new BigInteger(1, exp);

        return KeyFactory.getInstance("RSA").generatePublic(new RSAPublicKeySpec(n, e));
    }

    // ============================================================
    // CLEAR / CHANGE PIN
    // ============================================================

    public static boolean clearCardData() {
        try {
            CardChannel channel = connect();
            selectOrThrow(channel);

            CommandAPDU clearData = new CommandAPDU(0x00, INS_CLEAR_DATA, 0x00, 0x00);
            ResponseAPDU r = channel.transmit(clearData);
            System.out.println("CLEAR SW = 0x" + Integer.toHexString(r.getSW()));
            return r.getSW() == SUCCESS_SW;
        } catch (CardException e) {
            e.printStackTrace();
            return false;
        }
    }

    public static boolean changeUserPin(String currentPin, String newPin) {
        try {
            byte[] cur = pinTo6Bytes(currentPin);
            byte[] nxt = pinTo6Bytes(newPin);

            CardChannel channel = connect();
            selectOrThrow(channel);

            byte[] payload = new byte[12];
            System.arraycopy(cur, 0, payload, 0, 6);
            System.arraycopy(nxt, 0, payload, 6, 6);

            ResponseAPDU r = sendOnce(channel, INS_UPDATE_USER_PIN, (byte)0x00, (byte)0x00, payload);
            return r.getSW() == SUCCESS_SW;
        } catch (Exception e) {
            e.printStackTrace();
            return false;
        }
    }

    // ============================================================
    // ENCRYPTED WRITE / READ
    // ============================================================

    private static void sendEncryptedData(CardChannel channel, byte ins, byte[] pin6,
                                          byte pinType, byte[] data) throws CardException {
        if (pin6.length != 6) throw new IllegalArgumentException("PIN must be 6 bytes");

        byte[] payload = new byte[6 + data.length];
        System.arraycopy(pin6, 0, payload, 0, 6);
        System.arraycopy(data, 0, payload, 6, data.length);

        CommandAPDU apdu = new CommandAPDU(0x00, ins, pinType, 0x00, payload);
        ResponseAPDU resp = channel.transmit(apdu);

        if (resp.getSW() != SUCCESS_SW) {
            throw new CardException("Error writing encrypted data. INS=0x" + Integer.toHexString(ins & 0xFF)
                    + " SW=0x" + Integer.toHexString(resp.getSW()));
        }
    }

    public static byte[] readEncryptedData(CardChannel channel, byte ins, byte[] pin6, byte pinType) throws CardException {
        if (pin6.length != 6) throw new IllegalArgumentException("PIN must be 6 bytes");

        CommandAPDU apdu = new CommandAPDU(0x00, ins, pinType, 0x00, pin6, 256);
        ResponseAPDU resp = channel.transmit(apdu);

        if (resp.getSW() != SUCCESS_SW) {
            throw new CardException("Error reading decrypted data. INS=0x" + Integer.toHexString(ins & 0xFF)
                    + " SW=0x" + Integer.toHexString(resp.getSW()));
        }
        return resp.getData();
    }

    private static String readDecryptedString(
            CardChannel channel,
            byte ins,
            byte pinType,
            byte[] pin6
    ) throws Exception {

        byte[] data = readEncryptedData(channel, ins, pin6, pinType);
        return new String(data, StandardCharsets.UTF_8).trim();
    }


    // ============================================================
    // AVATAR CHUNK WRITE
    // ============================================================

    private static void sendAvatar(CardChannel channel, byte[] data) throws CardException {
        int chunkSize = 250;
        for (int offset = 0; offset < data.length; offset += chunkSize) {
            int len = Math.min(chunkSize, data.length - offset);
            byte[] chunk = Arrays.copyOfRange(data, offset, offset + len);

            CommandAPDU apdu = new CommandAPDU(
                    0x00, INS_WRITE_AVATAR,
                    (byte) ((offset >> 8) & 0xFF),
                    (byte) (offset & 0xFF),
                    chunk
            );

            ResponseAPDU resp = channel.transmit(apdu);
            if (resp.getSW() != SUCCESS_SW) {
                throw new CardException("Error writing avatar. Offset=" + offset
                        + " SW=0x" + Integer.toHexString(resp.getSW()));
            }
        }
    }

    // ============================================================
    // LOW-LEVEL
    // ============================================================

    private static ResponseAPDU sendOnce(CardChannel channel, byte ins, byte p1, byte p2, byte[] data) throws CardException {
        CommandAPDU apdu = (data == null)
                ? new CommandAPDU(0x00, ins, p1, p2)
                : new CommandAPDU(0x00, ins, p1, p2, data);

        ResponseAPDU resp = channel.transmit(apdu);
        if (resp.getSW() != SUCCESS_SW) {
            throw new CardException("APDU failed. INS=0x" + Integer.toHexString(ins & 0xFF)
                    + " SW=0x" + Integer.toHexString(resp.getSW()));
        }
        return resp;
    }

    // ============================================================
    // UTIL
    // ============================================================

    private static byte[] pinTo6Bytes(String pin) {
        if (pin == null || pin.length() != 6 || !pin.matches("\\d{6}")) {
            throw new IllegalArgumentException("PIN must be exactly 6 digits");
        }
        return pin.getBytes(StandardCharsets.US_ASCII);
    }

    private static String generate16Digits() {
        StringBuilder sb = new StringBuilder(16);
        sb.append(random.nextInt(9) + 1);
        for (int i = 1; i < 16; i++) sb.append(random.nextInt(10));
        return sb.toString();
    }

    private static byte[] compressImage(BufferedImage image, float quality) throws IOException {
        ByteArrayOutputStream baos = new ByteArrayOutputStream();

        ImageWriter writer = ImageIO.getImageWritersByFormatName("jpg").next();
        ImageWriteParam param = writer.getDefaultWriteParam();

        if (param.canWriteCompressed()) {
            param.setCompressionMode(ImageWriteParam.MODE_EXPLICIT);
            param.setCompressionQuality(quality);
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


    public static boolean adminUnlockAndVerifyCard(
            String expectedCardId,
            String publicKeyHexFromDb,
            String adminPin
    ) throws Exception {

        CardChannel channel = connect();
        selectOrThrow(channel);

        byte[] pin6 = pinTo6Bytes(adminPin);

        // 1️⃣ Đọc cardId từ thẻ (admin)
        String cardIdOnCard = readDecryptedString(
                channel,
                INS_READ_CARD_ID_DEC,
                PIN_TYPE_ADMIN,
                pin6
        );

        if (!expectedCardId.trim().equals(cardIdOnCard)) {
            throw new CardException("Sai thẻ: CardId không khớp");
        }

        // 2️⃣ RSA challenge-response
        boolean verified = authenticateCard(
                channel,
                publicKeyHexFromDb, // 🔥 lấy từ DB
                pin6
        );

        if (!verified) {
            throw new CardException("Xác thực RSA thất bại");
        }

        return true;
    }

    public static com.luukien.javacard.model.CardProfile readProfileAsAdmin(String adminPin) throws Exception {
        CardChannel ch = connect();
        selectOrThrow(ch);

        byte[] pin6 = pinTo6Bytes(adminPin);

        String name    = readDecryptedString(ch, INS_READ_USERNAME_DEC, PIN_TYPE_ADMIN, pin6);
        String phone   = readDecryptedString(ch, INS_READ_PHONE_DEC,   PIN_TYPE_ADMIN, pin6);
        String address = readDecryptedString(ch, INS_READ_ADDRESS_DEC, PIN_TYPE_ADMIN, pin6);
        String cardId  = readDecryptedString(ch, INS_READ_CARD_ID_DEC, PIN_TYPE_ADMIN, pin6);

        return new com.luukien.javacard.model.CardProfile(cardId, name, phone, address);
    }


}
