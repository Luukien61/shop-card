package shop_card;


import javacard.framework.*;
import javacard.security.*;
import javacardx.crypto.*;

public class shop_card_owner extends Applet {
    private static final short MAX_BUF = 240;
    private static final short MAX_NAME_LEN = 100;
    private static final short MAX_ADDRESS_LEN = 150;
    private static final short MAX_PHONE_LEN = 20;
    private static final short MAX_CARDID_LEN = 16;
    private static final short MAX_PIN_LEN = 8;
    private static final short MAX_AVATAR_LEN = 10240; // 10KB
    private static final short MAX_PIN_HASH_LEN = 32;
    private static final short MASTER_KEY_LEN = 16;  // AES-128
    private static final short SALT_LEN = 16;
    private static final short KEK_LEN = 16;  // Key Encryption Key
    private static final short PBKDF2_ITERATIONS = 200;
    private static final short MAX_CHUNK_SIZE = 250;
    private static final short CHALLENGE_LENGTH = 16;

    // PIN Lock constants
    private static final byte MAX_PIN_TRIES = 5;
    private static final byte PIN_SIZE = 6;

    private byte[] rawData;
    private short rawLen;

    // Encrypted data storage
    private byte[] name;
    private short nameLen;
    private byte[] address;
    private short addressLen;
    private byte[] phone;
    private short phoneLen;
    private byte[] cardId;
    private short cardIdLen;
    private byte[] avatar;
    private short avatarLen;
    private byte[] tempAvatarBuffer;
    private short tempAvatarLen;
    private byte[] tempPinBuffer;
    private boolean pinReceived;

    // Encryption components
    private byte[] userPinHash;
    private byte[] adminPinHash;
    private byte[] encryptedMasterKeyByUser;
    private byte[] encryptedMasterKeyByAdmin;
    private byte[] salt;
    private boolean pinsSet = false;
    private boolean masterKeySet = false;

    private MessageDigest sha256;
    private Cipher aesCipher;
    private AESKey tempAESKey;
    private RandomData randomData;

    // Reusable temporary buffers (to reduce memory allocation)
    private byte[] tempBuffer64;  // For HMAC operations
    private byte[] tempBuffer32;  // For hash operations
    private byte[] decryptBuffer;
    private byte[] cachedDecryptedAvatar = null;
    private short cachedAvatarLen = 0;

    // OwnerPIN instances
    private OwnerPIN userPin;
    private OwnerPIN adminPin;

    private KeyPair rsaKeyPair;
    private RSAPrivateKey rsaPrivateKey;
    private byte[] encryptedPrivateKeyModulus;
    private byte[] encryptedPrivateKeyExponent;
    private short encryptedModulusLen;
    private short encryptedExponentLen;
    private static final short MAX_RSA_COMPONENT_LEN = 128;

    private static final byte INS_GENERATE_RSA_KEYPAIR = (byte) 0x00;
    private static final byte INS_WRITE_USERNAME = (byte) 0x01;
    private static final byte INS_WRITE_ADDRESS = (byte) 0x02;
    private static final byte INS_WRITE_PHONE = (byte) 0x03;
    private static final byte INS_WRITE_CARD_ID = (byte) 0x04;
    private static final byte INS_SET_PINS = (byte) 0x05;
    private static final byte INS_WRITE_AVATAR = (byte) 0x07;
    private static final byte INS_WRITE_ALL = (byte) 0x08;
    private static final byte INS_CLEAR_ALL_DATA = (byte) 0x10;
    private static final byte INS_CHANGE_USER_PIN = (byte) 0x20;
    private static final byte INS_RECOVER_WITH_ADMIN = (byte) 0x21;
    private static final byte INS_GET_LOCK_STATUS = (byte) 0x30;
    private static final byte INS_VERIFY_PIN = (byte) 0x40;
    private static final byte INS_READ_USERNAME = (byte) 0x50;
    private static final byte INS_READ_ADDRESS = (byte) 0x51;
    private static final byte INS_READ_PHONE = (byte) 0x52;
    private static final byte INS_READ_CARD_ID = (byte) 0x53;
    private static final byte INS_READ_AVATAR = (byte) 0x54;
    private static final byte INS_GET_SALT = (byte) 0x60;
    private static final byte INS_READ_ALL_DATA = (byte) 0x55;
    private static final byte INS_VERIFY_CARD = (byte) 0x11;
    private static final byte INS_UNLOCK_CARD = (byte) 0x31;
    private static final byte INS_UPDATE_DATA = (byte) 0x32;
    private static final byte INS_UPDATE_AVATAR = (byte) 0x33;

    private shop_card_owner() {
        name = new byte[MAX_NAME_LEN];
        address = new byte[MAX_ADDRESS_LEN];
        phone = new byte[MAX_PHONE_LEN];
        cardId = new byte[MAX_CARDID_LEN];
        avatar = new byte[MAX_AVATAR_LEN];
        tempAvatarBuffer = new byte[MAX_AVATAR_LEN];
        rawData = new byte[MAX_BUF];
        tempPinBuffer = new byte[PIN_SIZE];
        pinReceived = false;
        userPinHash = new byte[MAX_PIN_HASH_LEN];
        adminPinHash = new byte[MAX_PIN_HASH_LEN];

        encryptedMasterKeyByUser = new byte[MASTER_KEY_LEN];
        encryptedMasterKeyByAdmin = new byte[MASTER_KEY_LEN];
        salt = new byte[SALT_LEN];

        // Pre-allocate temp buffers
        tempBuffer64 = new byte[64];
        tempBuffer32 = new byte[32];
        decryptBuffer = JCSystem.makeTransientByteArray((short) 256, JCSystem.CLEAR_ON_DESELECT);

        nameLen = addressLen = phoneLen = cardIdLen = avatarLen = tempAvatarLen = rawLen = 0;
        rsaKeyPair = null;
        encryptedPrivateKeyModulus = new byte[256];
        encryptedPrivateKeyExponent = new byte[256];
        encryptedModulusLen = 0;
        encryptedExponentLen = 0;
        pinsSet = false;
        masterKeySet = false;

        // Initialize OwnerPIN instances
        userPin = new OwnerPIN(MAX_PIN_TRIES, PIN_SIZE);
        adminPin = new OwnerPIN(MAX_PIN_TRIES, PIN_SIZE);

        initCrypto();
    }

    public static void install(byte[] bArray, short bOffset, byte bLength) {
        new shop_card_owner().register();
    }

    public void process(APDU apdu) {
        if (selectingApplet()) {
            return;
        }

        byte[] buffer = apdu.getBuffer();

        if (buffer[ISO7816.OFFSET_CLA] != (byte) 0x00) {
            ISOException.throwIt(ISO7816.SW_CLA_NOT_SUPPORTED);
        }

        byte ins = buffer[ISO7816.OFFSET_INS];
        short offset = (short) (
                ((buffer[ISO7816.OFFSET_P1] & 0xFF) << 8) |
                        (buffer[ISO7816.OFFSET_P2] & 0xFF)
        );

        switch (ins) {
            case INS_GENERATE_RSA_KEYPAIR: {
                generateRSAKeyPair(apdu);
                break;
            }
            case INS_VERIFY_CARD: {
                verifyCard(apdu);
                break;
            }
            case INS_WRITE_USERNAME: {
                short d = writeEncryptedData(apdu, name, MAX_NAME_LEN, offset, nameLen);
                nameLen += d;
                break;
            }
            case INS_WRITE_ADDRESS: {
                short d = writeEncryptedData(apdu, address, MAX_ADDRESS_LEN, offset, addressLen);
                addressLen += d;
                break;
            }
            case INS_WRITE_PHONE: {
                short d = writeEncryptedData(apdu, phone, MAX_PHONE_LEN, offset, phoneLen);
                phoneLen += d;
                break;
            }
            case INS_WRITE_CARD_ID: {
                short d = writeData(apdu, cardId, MAX_CARDID_LEN, offset, cardIdLen);
                cardIdLen += d;
                break;
            }
            case INS_WRITE_ALL: {
                parseAndEncryptFields(apdu);
                break;
            }
            case INS_SET_PINS: {
                setPinsAndMasterKey(apdu);
                break;
            }
            case INS_WRITE_AVATAR: {
                avatarLen = writeEncryptedDataChunked(apdu, avatar, MAX_AVATAR_LEN);
                break;
            }
            case INS_UPDATE_AVATAR: {
                avatarLen = writeEncryptedDataChunked(apdu, avatar, MAX_AVATAR_LEN);
                break;
            }
            case INS_CLEAR_ALL_DATA: {
                if (apdu.getBuffer()[ISO7816.OFFSET_LC] != 0) {
                    ISOException.throwIt(ISO7816.SW_WRONG_LENGTH);
                }
                clearAllData(apdu);
                break;
            }
            case INS_CHANGE_USER_PIN: {
                changeUserPin(apdu);
                break;
            }
            case INS_RECOVER_WITH_ADMIN: {
                recoverUserPinWithAdmin(apdu);
                break;
            }
            case INS_GET_LOCK_STATUS: {
                getLockStatus(apdu);
                break;
            }
            case INS_VERIFY_PIN: {
                verifyUserPin(apdu);
                break;
            }
            case INS_UNLOCK_CARD: {
                unlockCard(apdu);
                break;
            }
            case INS_READ_USERNAME: {
                readEncryptedData(apdu, name, nameLen);
                break;
            }
            case INS_READ_ADDRESS: {
                readEncryptedData(apdu, address, addressLen);
                break;
            }
            case INS_READ_PHONE: {
                readEncryptedData(apdu, phone, phoneLen);
                break;
            }
            case INS_READ_CARD_ID: {
                readData(apdu, cardId, cardIdLen);
                break;
            }
            case INS_READ_AVATAR: {
                readEncryptedDataWithChunk(apdu, avatar, avatarLen);
                break;
            }
            case INS_READ_ALL_DATA: {
                readAllData(apdu);
                break;
            }
            case INS_UPDATE_DATA: {
                parseAndEncryptFields(apdu);
                break;
            }
            default:
                ISOException.throwIt(ISO7816.SW_INS_NOT_SUPPORTED);
        }
    }

    private void initCrypto() {
        try {
            if (sha256 == null) {
                sha256 = MessageDigest.getInstance(MessageDigest.ALG_SHA_256, false);
            }
            if (aesCipher == null) {
                aesCipher = Cipher.getInstance(Cipher.ALG_AES_BLOCK_128_ECB_NOPAD, false);
            }
            if (tempAESKey == null) {
                tempAESKey = (AESKey) KeyBuilder.buildKey(KeyBuilder.TYPE_AES, KeyBuilder.LENGTH_AES_128, false);
            }
            if (randomData == null) {
                randomData = RandomData.getInstance(RandomData.ALG_SECURE_RANDOM);
            }
        } catch (Exception e) {
            ISOException.throwIt(ISO7816.SW_FUNC_NOT_SUPPORTED);
        }
    }

    /**
     * HMAC-SHA256 using reusable buffers
     */
    private void hmacSha256(byte[] key, short keyOff, short keyLen,
                            byte[] data, short dataOff, short dataLen,
                            byte[] out) {
        // Clear temp buffers
        Util.arrayFillNonAtomic(tempBuffer64, (short) 0, (short) 64, (byte) 0x00);
        Util.arrayFillNonAtomic(tempBuffer32, (short) 0, (short) 32, (byte) 0x00);

        // Prepare key
        if (keyLen > (short) 64) {
            sha256.reset();
            sha256.doFinal(key, keyOff, keyLen, tempBuffer64, (short) 0);
        } else {
            Util.arrayCopyNonAtomic(key, keyOff, tempBuffer64, (short) 0, keyLen);
        }

        // Inner hash: H((K ^ ipad) || data)
        for (short i = 0; i < (short) 64; i++) {
            tempBuffer64[i] ^= 0x36;
        }

        sha256.reset();
        sha256.update(tempBuffer64, (short) 0, (short) 64);
        sha256.doFinal(data, dataOff, dataLen, tempBuffer32, (short) 0);

        // Outer hash: H((K ^ opad) || inner_hash)
        // Restore K and XOR with opad
        if (keyLen > (short) 64) {
            sha256.reset();
            sha256.doFinal(key, keyOff, keyLen, tempBuffer64, (short) 0);
        } else {
            Util.arrayFillNonAtomic(tempBuffer64, (short) 0, (short) 64, (byte) 0x00);
            Util.arrayCopyNonAtomic(key, keyOff, tempBuffer64, (short) 0, keyLen);
        }

        for (short i = 0; i < (short) 64; i++) {
            tempBuffer64[i] ^= 0x5C;
        }

        sha256.reset();
        sha256.update(tempBuffer64, (short) 0, (short) 64);
        sha256.doFinal(tempBuffer32, (short) 0, (short) 32, out, (short) 0);
    }

    /**
     * PBKDF2-HMAC-SHA256 with optimized memory usage
     */
    private void pbkdf2(byte[] password, short pwdOff, short pwdLen,
                        byte[] salt, short saltOff, short saltLen,
                        short iterations, byte[] out, short outOff) {
        // Allocate minimal temporary storage
        byte[] block = new byte[(short) (saltLen + 4)];
        byte[] U = new byte[32];

        try {
            Util.arrayCopyNonAtomic(salt, saltOff, block, (short) 0, saltLen);
            block[saltLen] = 0;
            block[(short) (saltLen + 1)] = 0;
            block[(short) (saltLen + 2)] = 0;
            block[(short) (saltLen + 3)] = 1;

            // First iteration
            hmacSha256(password, pwdOff, pwdLen, block, (short) 0, (short) block.length, U);

            // Copy to output
            Util.arrayCopyNonAtomic(U, (short) 0, out, outOff, KEK_LEN);

            // Subsequent iterations
            for (short i = 1; i < iterations; i++) {
                hmacSha256(password, pwdOff, pwdLen, U, (short) 0, (short) 32, U);

                // XOR with output
                for (short j = 0; j < KEK_LEN; j++) {
                    out[(short) (outOff + j)] ^= U[j];
                }
            }
        } finally {
            // Clear sensitive data
            Util.arrayFillNonAtomic(block, (short) 0, (short) block.length, (byte) 0x00);
            Util.arrayFillNonAtomic(U, (short) 0, (short) 32, (byte) 0x00);
        }
    }

    /**
     * Set both User PIN and Admin PIN
     */
    private void setPinsAndMasterKey(APDU apdu) {
        byte[] buffer = apdu.getBuffer();
        short lc = (short) (buffer[ISO7816.OFFSET_LC] & 0xFF);

        if (lc != 12) {
            ISOException.throwIt(ISO7816.SW_WRONG_LENGTH);
        }

        apdu.setIncomingAndReceive();

        short userPinOff = ISO7816.OFFSET_CDATA;
        short adminPinOff = (short) (ISO7816.OFFSET_CDATA + 6);

        byte[] userKek = new byte[KEK_LEN];
        byte[] adminKek = new byte[KEK_LEN];
        byte[] masterKey = new byte[MASTER_KEY_LEN];

        try {
            // Update OwnerPIN instances
            userPin.update(buffer, userPinOff, PIN_SIZE);
            adminPin.update(buffer, adminPinOff, PIN_SIZE);

            // Hash PINs for cryptographic operations
            sha256.reset();
            sha256.doFinal(buffer, userPinOff, (short) 6, userPinHash, (short) 0);

            sha256.reset();
            sha256.doFinal(buffer, adminPinOff, (short) 6, adminPinHash, (short) 0);

            // Generate salt
            randomData.generateData(salt, (short) 0, SALT_LEN);

            // Derive KEKs
            pbkdf2(buffer, userPinOff, (short) 6, salt, (short) 0, SALT_LEN,
                    PBKDF2_ITERATIONS, userKek, (short) 0);

            pbkdf2(buffer, adminPinOff, (short) 6, salt, (short) 0, SALT_LEN,
                    PBKDF2_ITERATIONS, adminKek, (short) 0);

            // Generate Master Key
            randomData.generateData(masterKey, (short) 0, MASTER_KEY_LEN);

            // Encrypt with user KEK
            tempAESKey.setKey(userKek, (short) 0);
            aesCipher.init(tempAESKey, Cipher.MODE_ENCRYPT);
            aesCipher.doFinal(masterKey, (short) 0, MASTER_KEY_LEN, encryptedMasterKeyByUser, (short) 0);

            // Encrypt with admin KEK
            tempAESKey.setKey(adminKek, (short) 0);
            aesCipher.init(tempAESKey, Cipher.MODE_ENCRYPT);
            aesCipher.doFinal(masterKey, (short) 0, MASTER_KEY_LEN, encryptedMasterKeyByAdmin, (short) 0);

            pinsSet = true;
            masterKeySet = true;

        } finally {
            // Clear sensitive data
            Util.arrayFillNonAtomic(userKek, (short) 0, KEK_LEN, (byte) 0x00);
            Util.arrayFillNonAtomic(adminKek, (short) 0, KEK_LEN, (byte) 0x00);
            Util.arrayFillNonAtomic(masterKey, (short) 0, MASTER_KEY_LEN, (byte) 0x00);
        }
    }

    /**
     * Decrypt Master Key with user PIN
     */
    private void decryptMasterKeyWithUserPin(byte[] pin, short pinOff, byte[] masterKeyOut, short outOff) {
        if (!masterKeySet) {
            ISOException.throwIt(ISO7816.SW_SECURITY_STATUS_NOT_SATISFIED);
        }

        byte[] kek = new byte[KEK_LEN];

        try {
            pbkdf2(pin, pinOff, (short) 6, salt, (short) 0, SALT_LEN,
                    PBKDF2_ITERATIONS, kek, (short) 0);

            tempAESKey.setKey(kek, (short) 0);
            aesCipher.init(tempAESKey, Cipher.MODE_DECRYPT);
            aesCipher.doFinal(encryptedMasterKeyByUser, (short) 0, MASTER_KEY_LEN, masterKeyOut, outOff);

        } finally {
            Util.arrayFillNonAtomic(kek, (short) 0, KEK_LEN, (byte) 0x00);
        }
    }

    /**
     * Decrypt Master Key with admin PIN
     */
    private void decryptMasterKeyWithAdminPin(byte[] pin, short pinOff, byte[] masterKeyOut, short outOff) {
        if (!masterKeySet) {
            ISOException.throwIt(ISO7816.SW_SECURITY_STATUS_NOT_SATISFIED);
        }

        byte[] kek = new byte[KEK_LEN];

        try {
            pbkdf2(pin, pinOff, (short) 6, salt, (short) 0, SALT_LEN,
                    PBKDF2_ITERATIONS, kek, (short) 0);

            tempAESKey.setKey(kek, (short) 0);
            aesCipher.init(tempAESKey, Cipher.MODE_DECRYPT);
            aesCipher.doFinal(encryptedMasterKeyByAdmin, (short) 0, MASTER_KEY_LEN, masterKeyOut, outOff);

        } finally {
            Util.arrayFillNonAtomic(kek, (short) 0, KEK_LEN, (byte) 0x00);
        }
    }

    /**
     * Write encrypted data
     */
    private short writeEncryptedData(APDU apdu, byte[] dest, short maxLen,
                                     short currentOffset, short currentLen) {
        byte[] buffer = apdu.getBuffer();
        short lc = (short) (buffer[ISO7816.OFFSET_LC] & 0xFF);

        if (lc < 6) {
            ISOException.throwIt(ISO7816.SW_WRONG_LENGTH);
        }

        apdu.setIncomingAndReceive();

        if (!masterKeySet) {
            ISOException.throwIt(ISO7816.SW_SECURITY_STATUS_NOT_SATISFIED);
        }

        // Check if card is blocked
        if (userPin.getTriesRemaining() == 0) {
            ISOException.throwIt((short) 0x6983); // Authentication method blocked
        }

        short pinOff = ISO7816.OFFSET_CDATA;
        short dataOff = (short) (ISO7816.OFFSET_CDATA + 6);
        short dataLen = (short) (lc - 6);

        byte[] masterKey = new byte[MASTER_KEY_LEN];
        byte[] paddedData = null;

        try {
            // Verify PIN using OwnerPIN
            if (!userPin.check(buffer, pinOff, PIN_SIZE)) {
                ISOException.throwIt((short) (0x63C0 | userPin.getTriesRemaining()));
            }

            // Verify PIN hash for backward compatibility
            sha256.reset();
            sha256.doFinal(buffer, pinOff, (short) 6, tempBuffer32, (short) 0);

            if (Util.arrayCompare(tempBuffer32, (short) 0, userPinHash, (short) 0, (short) 32) != 0) {
                ISOException.throwIt(ISO7816.SW_SECURITY_STATUS_NOT_SATISFIED);
            }

            // Decrypt Master Key
            decryptMasterKeyWithUserPin(buffer, pinOff, masterKey, (short) 0);

            // Pad data
            short padLen = (short) (16 - (dataLen % 16));
            if (padLen == 0) padLen = 16;

            short paddedLen = (short) (dataLen + padLen);

            paddedData = new byte[paddedLen];
            Util.arrayCopyNonAtomic(buffer, dataOff, paddedData, (short) 0, dataLen);
            Util.arrayFillNonAtomic(paddedData, dataLen, padLen, (byte) padLen);

            // Encrypt
            tempAESKey.setKey(masterKey, (short) 0);
            aesCipher.init(tempAESKey, Cipher.MODE_ENCRYPT);
            aesCipher.doFinal(paddedData, (short) 0, paddedLen, dest, currentOffset);

            return paddedLen;

        } finally {
            Util.arrayFillNonAtomic(masterKey, (short) 0, MASTER_KEY_LEN, (byte) 0x00);
            if (paddedData != null) {
                Util.arrayFillNonAtomic(paddedData, (short) 0, (short) paddedData.length, (byte) 0x00);
            }
            Util.arrayFillNonAtomic(tempBuffer32, (short) 0, (short) 32, (byte) 0x00);
        }
    }

    private short writeEncryptedDataChunked(APDU apdu, byte[] dest, short maxLen) {
        byte[] buffer = apdu.getBuffer();
        byte p1 = buffer[ISO7816.OFFSET_P1];
        boolean isLastChunk = (p1 == (byte) 0x01);
        short lc = (short) (buffer[ISO7816.OFFSET_LC] & 0xFF);

        apdu.setIncomingAndReceive();

        if (!masterKeySet) {
            ISOException.throwIt(ISO7816.SW_SECURITY_STATUS_NOT_SATISFIED);
        }

        if (userPin.getTriesRemaining() == 0) {
            ISOException.throwIt((short) 0x6983);
        }

        short dataOff = ISO7816.OFFSET_CDATA;
        short dataLen = lc;

        byte[] masterKey = new byte[MASTER_KEY_LEN];
        byte[] paddedData = null;

        try {
            // === CHUNK ĐẦU TIÊN: Extract PIN từ 6 bytes đầu ===
            if (tempAvatarLen == 0 && !pinReceived) {
                if (lc < 6) {
                    ISOException.throwIt(ISO7816.SW_WRONG_LENGTH);
                }

                // Lưu PIN vào buffer tạm
                Util.arrayCopyNonAtomic(buffer, ISO7816.OFFSET_CDATA, tempPinBuffer, (short) 0, PIN_SIZE);
                pinReceived = true;

                // Data bắt đầu từ byte thứ 7
                dataOff = (short) (ISO7816.OFFSET_CDATA + PIN_SIZE);
                dataLen = (short) (lc - PIN_SIZE);
            }

            // === LAST CHUNK: Verify PIN và encrypt ===
            if (isLastChunk) {
                if (!pinReceived) {
                    tempAvatarLen = 0;
                    pinReceived = false;
                    ISOException.throwIt(ISO7816.SW_SECURITY_STATUS_NOT_SATISFIED);
                }

                // Verify PIN
                if (!userPin.check(tempPinBuffer, (short) 0, PIN_SIZE)) {
                    tempAvatarLen = 0;
                    pinReceived = false;
                    Util.arrayFillNonAtomic(tempPinBuffer, (short) 0, PIN_SIZE, (byte) 0x00);
                    ISOException.throwIt((short) (0x63C0 | userPin.getTriesRemaining()));
                }

                // Verify PIN hash
                sha256.reset();
                sha256.doFinal(tempPinBuffer, (short) 0, PIN_SIZE, tempBuffer32, (short) 0);

                if (Util.arrayCompare(tempBuffer32, (short) 0, userPinHash, (short) 0, (short) 32) != 0) {
                    tempAvatarLen = 0;
                    pinReceived = false;
                    Util.arrayFillNonAtomic(tempPinBuffer, (short) 0, PIN_SIZE, (byte) 0x00);
                    ISOException.throwIt(ISO7816.SW_SECURITY_STATUS_NOT_SATISFIED);
                }

                // Append data chunk cuối
                if ((short) (tempAvatarLen + dataLen) > maxLen) {
                    tempAvatarLen = 0;
                    pinReceived = false;
                    Util.arrayFillNonAtomic(tempPinBuffer, (short) 0, PIN_SIZE, (byte) 0x00);
                    ISOException.throwIt(ISO7816.SW_FILE_FULL);
                }

                if (dataLen > 0) {
                    Util.arrayCopyNonAtomic(buffer, dataOff, tempAvatarBuffer, tempAvatarLen, dataLen);
                    tempAvatarLen = (short) (tempAvatarLen + dataLen);
                }

                // Decrypt Master Key bằng PIN đã lưu
                decryptMasterKeyWithUserPin(tempPinBuffer, (short) 0, masterKey, (short) 0);

                // Pad data
                short padLen = (short) (16 - (tempAvatarLen % 16));
                if (padLen == 0) padLen = 16;
                short paddedLen = (short) (tempAvatarLen + padLen);
                paddedData = new byte[paddedLen];

                Util.arrayCopyNonAtomic(tempAvatarBuffer, (short) 0, paddedData, (short) 0, tempAvatarLen);
                Util.arrayFillNonAtomic(paddedData, tempAvatarLen, padLen, (byte) padLen);
                Util.arrayFillNonAtomic(dest, (byte) 0x00, maxLen, (byte) 0x00);

                // Encrypt
                tempAESKey.setKey(masterKey, (short) 0);
                aesCipher.init(tempAESKey, Cipher.MODE_ENCRYPT);
                aesCipher.doFinal(paddedData, (short) 0, paddedLen, dest, (short) 0);

                // Clean up
                Util.arrayFillNonAtomic(tempAvatarBuffer, (short) 0, tempAvatarLen, (byte) 0x00);
                Util.arrayFillNonAtomic(tempPinBuffer, (short) 0, PIN_SIZE, (byte) 0x00);
                short finalLen = paddedLen;
                tempAvatarLen = 0;
                pinReceived = false;

                return finalLen;
            }

            // === INTERMEDIATE CHUNK: Chỉ append data ===
            else {
                if ((short) (tempAvatarLen + dataLen) > maxLen) {
                    tempAvatarLen = 0;
                    pinReceived = false;
                    Util.arrayFillNonAtomic(tempPinBuffer, (short) 0, PIN_SIZE, (byte) 0x00);
                    ISOException.throwIt(ISO7816.SW_FILE_FULL);
                }

                if (dataLen > 0) {
                    Util.arrayCopyNonAtomic(buffer, dataOff, tempAvatarBuffer, tempAvatarLen, dataLen);
                    tempAvatarLen = (short) (tempAvatarLen + dataLen);
                }

                return (short) 0;
            }

        } catch (Exception e) {
            tempAvatarLen = 0;
            pinReceived = false;
            Util.arrayFillNonAtomic(tempAvatarBuffer, (short) 0, maxLen, (byte) 0x00);
            Util.arrayFillNonAtomic(tempPinBuffer, (short) 0, PIN_SIZE, (byte) 0x00);
            return (short) 0;

        } finally {
            Util.arrayFillNonAtomic(masterKey, (short) 0, MASTER_KEY_LEN, (byte) 0x00);
            if (paddedData != null) {
                Util.arrayFillNonAtomic(paddedData, (short) 0, (short) paddedData.length, (byte) 0x00);
            }
            Util.arrayFillNonAtomic(tempBuffer32, (short) 0, (short) 32, (byte) 0x00);
        }
    }

    private short writeData(APDU apdu, byte[] dest, short maxLen, short currentOffset, short currentLen) {
        byte[] buffer = apdu.getBuffer();
        short dataLen = apdu.setIncomingAndReceive();

        if (currentOffset != currentLen) {
            ISOException.throwIt(ISO7816.SW_DATA_INVALID);
        }

        if ((short) (currentLen + dataLen) > maxLen) {
            ISOException.throwIt(ISO7816.SW_FILE_FULL);
        }

        Util.arrayCopyNonAtomic(buffer, ISO7816.OFFSET_CDATA, dest, currentOffset, dataLen);
        return dataLen;
    }

    private void readData(APDU apdu, byte[] source, short totalLen) {
        byte[] buffer = apdu.getBuffer();
        apdu.setIncomingAndReceive();

        Util.arrayCopyNonAtomic(source, (byte) 0, buffer, (short) 0, totalLen);

        apdu.setOutgoing();
        apdu.setOutgoingLength(totalLen);
        apdu.sendBytes((short) 0, totalLen);
    }

    /**
     * Parse data format: PIN|name|phone|cardid|address
     * Decrypt master key once, then encrypt all fields
     */
    private void parseAndEncryptFields(APDU apdu) {
        byte[] buffer = apdu.getBuffer();
        short lc = apdu.setIncomingAndReceive();

        if (lc == 0 || lc > MAX_BUF) {
            ISOException.throwIt(ISO7816.SW_WRONG_LENGTH);
        }

        if (!masterKeySet) {
            ISOException.throwIt(ISO7816.SW_SECURITY_STATUS_NOT_SATISFIED);
        }

        if (userPin.getTriesRemaining() == 0) {
            ISOException.throwIt((short) 0x6983);
        }

        // Copy data to working buffer
        Util.arrayCopyNonAtomic(buffer, ISO7816.OFFSET_CDATA, rawData, (short) 0, lc);
        rawLen = lc;

        byte[] masterKey = new byte[MASTER_KEY_LEN];

        try {
            // ===== 1. Parse PIN (6 bytes trước dấu '|' đầu tiên) =====
            short pinLen = findSeparator((short) 0);
            if (pinLen != 6) {
                ISOException.throwIt(ISO7816.SW_WRONG_LENGTH);
            }

            // Verify PIN
            if (!userPin.check(rawData, (short) 0, PIN_SIZE)) {
                ISOException.throwIt((short) (0x63C0 | userPin.getTriesRemaining()));
            }

            // Verify PIN hash
            sha256.reset();
            sha256.doFinal(rawData, (short) 0, (short) 6, tempBuffer32, (short) 0);

            if (Util.arrayCompare(tempBuffer32, (short) 0, userPinHash, (short) 0, (short) 32) != 0) {
                ISOException.throwIt(ISO7816.SW_SECURITY_STATUS_NOT_SATISFIED);
            }

            // ===== 2. Decrypt Master Key MỘT LẦN =====
            decryptMasterKeyWithUserPin(rawData, (short) 0, masterKey, (short) 0);

            // Set AES key một lần
            tempAESKey.setKey(masterKey, (short) 0);

            // ===== 3. Parse và encrypt từng field =====
            short pos = (short) (pinLen + 1); // Skip PIN và '|'

            // Field 1: Name
            short rawNameLen = findSeparator(pos);
            if (rawNameLen > 0) {
                nameLen = encryptFieldWithKey(rawData, pos, rawNameLen,
                        name, (short) 0, MAX_NAME_LEN);
            }
            pos = (short) (pos + rawNameLen + 1);

            // Field 2: Phone
            if (pos < rawLen) {
                short rawPhoneLen = findSeparator(pos);
                if (rawPhoneLen > 0) {
                    phoneLen = encryptFieldWithKey(rawData, pos, rawPhoneLen,
                            phone, (short) 0, MAX_PHONE_LEN);
                }
                pos = (short) (pos + rawPhoneLen + 1);
            }

            // Field 3: Address
            if (pos < rawLen) {
                short rawAddressLen = findSeparator(pos);
                if (rawAddressLen > 0) {
                    addressLen = encryptFieldWithKey(rawData, pos, rawAddressLen,
                            address, (short) 0, MAX_ADDRESS_LEN);
                }
                pos = (short) (pos + rawAddressLen + 1);
            }

            // Field 4: CardID (phần còn lại, không có '|' cuối)
            if (pos < rawLen) {
                cardIdLen = (short) (rawLen - pos);
                if (cardIdLen > 0) {
                    Util.arrayCopyNonAtomic(rawData, pos, cardId, (short) 0, cardIdLen);
                }
            }

        } finally {
            // Clean up
            Util.arrayFillNonAtomic(masterKey, (short) 0, MASTER_KEY_LEN, (byte) 0x00);
            Util.arrayFillNonAtomic(tempBuffer32, (short) 0, (short) 32, (byte) 0x00);
            Util.arrayFillNonAtomic(rawData, (short) 0, rawLen, (byte) 0x00);
            rawLen = 0;
        }
    }

    /**
     * Encrypt một field với master key đã set sẵn trong tempAESKey
     *
     * @return length của encrypted data (đã pad)
     */
    private short encryptFieldWithKey(byte[] src, short srcOff, short srcLen,
                                      byte[] dest, short destOff, short maxLen) {

        Util.arrayFillNonAtomic(dest, destOff, maxLen, (byte) 0x00);

        if (srcLen == 0) {
            return 0;
        }

        // Calculate padding
        short padLen = (short) (16 - (srcLen % 16));
        if (padLen == 0) padLen = 16;
        short paddedLen = (short) (srcLen + padLen);

        if (paddedLen > maxLen) {
            ISOException.throwIt(ISO7816.SW_FILE_FULL);
        }

        byte[] paddedData = new byte[paddedLen];

        try {
            // Copy data và pad
            Util.arrayCopyNonAtomic(src, srcOff, paddedData, (short) 0, srcLen);
            Util.arrayFillNonAtomic(paddedData, srcLen, padLen, (byte) padLen);

            // Encrypt (key đã được set trong tempAESKey)
            aesCipher.init(tempAESKey, Cipher.MODE_ENCRYPT);
            aesCipher.doFinal(paddedData, (short) 0, paddedLen, dest, destOff);

            return paddedLen;

        } finally {
            Util.arrayFillNonAtomic(paddedData, (short) 0, paddedLen, (byte) 0x00);
        }
    }

    /**
     * Tìm vị trí separator '|' từ vị trí start
     *
     * @return length của field (không bao gồm '|')
     */
    private short findSeparator(short start) {
        short i = start;
        while (i < rawLen && rawData[i] != (byte) '|') {
            i++;
        }
        return (short) (i - start);
    }

    /**
     * Read encrypted data
     */
    private void readEncryptedData(APDU apdu, byte[] source, short totalLen) {
        byte[] buffer = apdu.getBuffer();
        short lc = (short) (buffer[ISO7816.OFFSET_LC] & 0xFF);

        if (lc != 6) {
            ISOException.throwIt(ISO7816.SW_WRONG_LENGTH);
        }

        apdu.setIncomingAndReceive();

        if (!masterKeySet || totalLen == 0) {
            ISOException.throwIt(ISO7816.SW_DATA_INVALID);
        }

        // Check if card is blocked
        if (userPin.getTriesRemaining() == 0) {
            ISOException.throwIt((short) 0x6983); // Authentication method blocked
        }

        short pinOff = ISO7816.OFFSET_CDATA;
        byte[] masterKey = new byte[MASTER_KEY_LEN];

        try {
            // Verify PIN using OwnerPIN
            if (!userPin.check(buffer, pinOff, PIN_SIZE)) {
                ISOException.throwIt((short) (0x63C0 | userPin.getTriesRemaining()));
            }

            // Verify PIN hash
            sha256.reset();
            sha256.doFinal(buffer, pinOff, (short) 6, tempBuffer32, (short) 0);

            if (Util.arrayCompare(tempBuffer32, (short) 0, userPinHash, (short) 0, (short) 32) != 0) {
                ISOException.throwIt(ISO7816.SW_SECURITY_STATUS_NOT_SATISFIED);
            }

            // Decrypt Master Key
            decryptMasterKeyWithUserPin(buffer, pinOff, masterKey, (short) 0);

            // Decrypt data
            tempAESKey.setKey(masterKey, (short) 0);
            aesCipher.init(tempAESKey, Cipher.MODE_DECRYPT);
            aesCipher.doFinal(source, (short) 0, totalLen, buffer, (short) 0);

            byte pad = buffer[(short) (totalLen - 1)];
            short plainLen = (short) (totalLen - pad);
            // Send
            apdu.setOutgoing();
            apdu.setOutgoingLength(plainLen);
            apdu.sendBytes((short) 0, plainLen);

        } finally {
            Util.arrayFillNonAtomic(masterKey, (short) 0, MASTER_KEY_LEN, (byte) 0x00);
            Util.arrayFillNonAtomic(tempBuffer32, (short) 0, (short) 32, (byte) 0x00);
        }
    }

    private void readAllData(APDU apdu) {
        byte[] buffer = apdu.getBuffer();
        short lc = (short) (buffer[ISO7816.OFFSET_LC] & 0xFF);

        if (lc != 6) {
            ISOException.throwIt(ISO7816.SW_WRONG_LENGTH);
        }

        apdu.setIncomingAndReceive();

        if (!masterKeySet) {
            ISOException.throwIt(ISO7816.SW_DATA_INVALID);
        }

        // Check if card is blocked
        if (userPin.getTriesRemaining() == 0) {
            ISOException.throwIt((short) 0x6983);
        }

        short requestedOffset = (short) (
                ((buffer[ISO7816.OFFSET_P1] & 0xFF) << 8) |
                        (buffer[ISO7816.OFFSET_P2] & 0xFF)
        );

        short pinOff = ISO7816.OFFSET_CDATA;
        byte[] masterKey = new byte[MASTER_KEY_LEN];
        short actualNameLen = 0;
        short actualAddressLen = 0;
        short actualPhoneLen = 0;
        short totalDataLen = 0;

        try {
            // Verify PIN using OwnerPIN
            if (!userPin.check(buffer, pinOff, PIN_SIZE)) {
                ISOException.throwIt((short) (0x63C0 | userPin.getTriesRemaining()));
            }

            // Verify PIN hash
            sha256.reset();
            sha256.doFinal(buffer, pinOff, (short) 6, tempBuffer32, (short) 0);

            if (Util.arrayCompare(tempBuffer32, (short) 0, userPinHash, (short) 0, (short) 32) != 0) {
                ISOException.throwIt(ISO7816.SW_SECURITY_STATUS_NOT_SATISFIED);
            }

            // Decrypt Master Key ONCE
            decryptMasterKeyWithUserPin(buffer, pinOff, masterKey, (short) 0);

            // Initialize cipher with master key
            tempAESKey.setKey(masterKey, (short) 0);

            short currentDecryptOffset = 0;
            actualNameLen = decryptAndRemovePadding(name, nameLen, decryptBuffer, currentDecryptOffset);
            currentDecryptOffset += actualNameLen;

            actualAddressLen = decryptAndRemovePadding(address, addressLen, decryptBuffer, currentDecryptOffset);
            currentDecryptOffset += actualAddressLen;

            actualPhoneLen = decryptAndRemovePadding(phone, phoneLen, decryptBuffer, currentDecryptOffset);
            currentDecryptOffset += actualPhoneLen;

            Util.arrayCopyNonAtomic(cardId, (short) 0, decryptBuffer, currentDecryptOffset, cardIdLen);
            currentDecryptOffset += cardIdLen;

            short outputOffset = 0;
            totalDataLen = (short) (actualNameLen + actualAddressLen + actualPhoneLen + cardIdLen);

            if (requestedOffset == 0) {
                buffer[outputOffset++] = (byte) ((actualNameLen >> 8) & 0xFF);
                buffer[outputOffset++] = (byte) (actualNameLen & 0xFF);
                buffer[outputOffset++] = (byte) ((actualAddressLen >> 8) & 0xFF);
                buffer[outputOffset++] = (byte) (actualAddressLen & 0xFF);
                buffer[outputOffset++] = (byte) ((actualPhoneLen >> 8) & 0xFF);
                buffer[outputOffset++] = (byte) (actualPhoneLen & 0xFF);
                buffer[outputOffset++] = (byte) ((cardIdLen >> 8) & 0xFF);
                buffer[outputOffset++] = (byte) (cardIdLen & 0xFF);
            }

            short dataOffset = requestedOffset;
            short remainingSpace = (short) (buffer.length - outputOffset - 2);
            short remainingData = (short) (totalDataLen - dataOffset);

            if (remainingData <= 0) {
                apdu.setOutgoing();
                apdu.setOutgoingLength(outputOffset);
                apdu.sendBytes((short) 0, outputOffset);
                return;
            }

            short bytesToSend = (remainingData > remainingSpace) ? remainingSpace : remainingData;

            if (bytesToSend > 0) {
                Util.arrayCopyNonAtomic(decryptBuffer, dataOffset, buffer, outputOffset, bytesToSend);
                outputOffset += bytesToSend;
            }

            apdu.setOutgoing();
            apdu.setOutgoingLength(outputOffset);
            apdu.sendBytes((short) 0, outputOffset);

        } finally {
            Util.arrayFillNonAtomic(masterKey, (short) 0, MASTER_KEY_LEN, (byte) 0x00);
            Util.arrayFillNonAtomic(tempBuffer32, (short) 0, (short) 32, (byte) 0x00);
            short totalDecryptedLen = (short) (actualNameLen + actualAddressLen + actualPhoneLen + cardIdLen);
            if (totalDataLen > 0 && totalDataLen <= decryptBuffer.length) {
                Util.arrayFillNonAtomic(decryptBuffer, (short) 0, totalDataLen, (byte) 0x00);
            }
        }
    }

    private short decryptAndRemovePadding(byte[] encryptedData, short encLen, byte[] output, short outOff) {
        aesCipher.init(tempAESKey, Cipher.MODE_DECRYPT);
        short decryptedLen = aesCipher.doFinal(encryptedData, (short) 0, encLen, output, outOff);

        if (decryptedLen > 0) {
            byte paddingValue = output[(short) (outOff + decryptedLen - 1)];

            if (paddingValue > 0 && paddingValue <= 16 && paddingValue <= decryptedLen) {
                boolean validPadding = true;
                for (short i = (short) (decryptedLen - paddingValue); i < decryptedLen; i++) {
                    if (output[(short) (outOff + i)] != paddingValue) {
                        validPadding = false;
                        break;
                    }
                }

                if (validPadding) {
                    return (short) (decryptedLen - paddingValue);
                }
            }
        }

        return decryptedLen;
    }

    private void readEncryptedDataWithChunk(APDU apdu, byte[] source, short totalLen) {
        byte[] buffer = apdu.getBuffer();
        short lc = (short) (buffer[ISO7816.OFFSET_LC] & 0xFF);

        if (lc != 6) {
            ISOException.throwIt(ISO7816.SW_WRONG_LENGTH);
        }

        apdu.setIncomingAndReceive();

        if (!masterKeySet || totalLen == 0) {
            ISOException.throwIt(ISO7816.SW_DATA_INVALID);
        }

        // Check if card is blocked
        if (userPin.getTriesRemaining() == 0) {
            ISOException.throwIt((short) 0x6983);
        }

        short readOffset = (short) (
                ((buffer[ISO7816.OFFSET_P1] & 0xFF) << 8) |
                        (buffer[ISO7816.OFFSET_P2] & 0xFF)
        );

        short pinOff = ISO7816.OFFSET_CDATA;
        byte[] masterKey = new byte[MASTER_KEY_LEN];

        try {
            // Verify PIN using OwnerPIN
            if (!userPin.check(buffer, pinOff, PIN_SIZE)) {
                ISOException.throwIt((short) (0x63C0 | userPin.getTriesRemaining()));
            }

            // Verify PIN hash
            sha256.reset();
            sha256.doFinal(buffer, pinOff, (short) 6, tempBuffer32, (short) 0);

            if (Util.arrayCompare(tempBuffer32, (short) 0, userPinHash, (short) 0, (short) 32) != 0) {
                ISOException.throwIt(ISO7816.SW_SECURITY_STATUS_NOT_SATISFIED);
            }

            if (cachedDecryptedAvatar == null) {
                decryptMasterKeyWithUserPin(buffer, pinOff, masterKey, (short) 0);
                tempAESKey.setKey(masterKey, (short) 0);
                cachedDecryptedAvatar = new byte[totalLen];
                short actualLen = decryptAndRemovePadding(source, totalLen, cachedDecryptedAvatar, (short) 0);
                cachedAvatarLen = actualLen;
            }

            if (readOffset >= cachedAvatarLen) {
                ISOException.throwIt(ISO7816.SW_WRONG_P1P2);
            }

            short remainingData = (short) (cachedAvatarLen - readOffset);
            short chunkSize = (remainingData > MAX_CHUNK_SIZE) ? MAX_CHUNK_SIZE : remainingData;
            boolean isLastChunk = (readOffset + chunkSize >= cachedAvatarLen);

            buffer[0] = isLastChunk ? (byte) 0x01 : (byte) 0x00;
            Util.arrayCopyNonAtomic(cachedDecryptedAvatar, readOffset, buffer, (short) 1, chunkSize);

            apdu.setOutgoing();
            apdu.setOutgoingLength((short) (chunkSize + 1));
            apdu.sendBytes((short) 0, (short) (chunkSize + 1));

        } finally {
            Util.arrayFillNonAtomic(masterKey, (short) 0, MASTER_KEY_LEN, (byte) 0x00);
            Util.arrayFillNonAtomic(tempBuffer32, (short) 0, (short) 32, (byte) 0x00);
        }
    }

    public void deselect() {
        clearAvatarCache();
    }

    private void clearAvatarCache() {
        if (cachedDecryptedAvatar != null) {
            Util.arrayFillNonAtomic(cachedDecryptedAvatar, (short) 0, cachedAvatarLen, (byte) 0x00);
            cachedDecryptedAvatar = null;
            cachedAvatarLen = 0;
        }
    }

    private void getSalt(APDU apdu) {
        byte[] buffer = apdu.getBuffer();

        if (!masterKeySet) {
            ISOException.throwIt(ISO7816.SW_SECURITY_STATUS_NOT_SATISFIED);
        }

        Util.arrayCopyNonAtomic(salt, (short) 0, buffer, (short) 0, SALT_LEN);

        apdu.setOutgoing();
        apdu.setOutgoingLength(SALT_LEN);
        apdu.sendBytes((short) 0, SALT_LEN);
    }

    private void recoverUserPinWithAdmin(APDU apdu) {
        byte[] buffer = apdu.getBuffer();
        short lc = (short) (buffer[ISO7816.OFFSET_LC] & 0xFF);

        if (lc != 12) {
            ISOException.throwIt(ISO7816.SW_WRONG_LENGTH);
        }

        if (!pinsSet) {
            ISOException.throwIt(ISO7816.SW_SECURITY_STATUS_NOT_SATISFIED);
        }

        apdu.setIncomingAndReceive();

        short adminPinOff = ISO7816.OFFSET_CDATA;
        short newUserPinOff = (short) (ISO7816.OFFSET_CDATA + 6);

        byte[] masterKey = new byte[MASTER_KEY_LEN];
        byte[] newUserKek = new byte[KEK_LEN];

        try {
            // Verify admin PIN using OwnerPIN
            if (!adminPin.check(buffer, adminPinOff, PIN_SIZE)) {
                ISOException.throwIt(ISO7816.SW_SECURITY_STATUS_NOT_SATISFIED);
            }

            // Verify admin PIN hash
            sha256.reset();
            sha256.doFinal(buffer, adminPinOff, (short) 6, tempBuffer32, (short) 0);

            if (Util.arrayCompare(tempBuffer32, (short) 0, adminPinHash, (short) 0, (short) 32) != 0) {
                ISOException.throwIt(ISO7816.SW_SECURITY_STATUS_NOT_SATISFIED);
            }

            // Decrypt Master Key with admin PIN
            decryptMasterKeyWithAdminPin(buffer, adminPinOff, masterKey, (short) 0);

            // Hash new user PIN
            sha256.reset();
            sha256.doFinal(buffer, newUserPinOff, (short) 6, userPinHash, (short) 0);

            // Update user OwnerPIN
            userPin.update(buffer, newUserPinOff, PIN_SIZE);

            // Derive new user KEK
            pbkdf2(buffer, newUserPinOff, (short) 6, salt, (short) 0, SALT_LEN,
                    PBKDF2_ITERATIONS, newUserKek, (short) 0);

            // Re-encrypt Master Key
            tempAESKey.setKey(newUserKek, (short) 0);
            aesCipher.init(tempAESKey, Cipher.MODE_ENCRYPT);
            aesCipher.doFinal(masterKey, (short) 0, MASTER_KEY_LEN, encryptedMasterKeyByUser, (short) 0);

        } finally {
            Util.arrayFillNonAtomic(masterKey, (short) 0, MASTER_KEY_LEN, (byte) 0x00);
            Util.arrayFillNonAtomic(newUserKek, (short) 0, KEK_LEN, (byte) 0x00);
            Util.arrayFillNonAtomic(tempBuffer32, (short) 0, (short) 32, (byte) 0x00);
        }
    }

    private short encryptRSAComponent(byte[] component, short compLen,
                                      byte[] masterKey, byte[] dest) {
        short padLen = (short) (16 - (compLen % 16));
        if (padLen == 0) padLen = 16;
        short paddedLen = (short) (compLen + padLen);

        byte[] paddedData = new byte[paddedLen];
        try {
            Util.arrayCopyNonAtomic(component, (short) 0, paddedData, (short) 0, compLen);
            Util.arrayFillNonAtomic(paddedData, compLen, padLen, (byte) padLen);

            tempAESKey.setKey(masterKey, (short) 0);
            aesCipher.init(tempAESKey, Cipher.MODE_ENCRYPT);
            aesCipher.doFinal(paddedData, (short) 0, paddedLen, dest, (short) 0);

            return paddedLen;
        } finally {
            Util.arrayFillNonAtomic(paddedData, (short) 0, paddedLen, (byte) 0x00);
        }
    }

    private short decryptRSAComponent(byte[] encrypted, short encLen,
                                      byte[] masterKey, byte[] dest) {
        if ((encLen % 16) != 0) {
            ISOException.throwIt(ISO7816.SW_DATA_INVALID);
        }

        tempAESKey.setKey(masterKey, (short) 0);
        aesCipher.init(tempAESKey, Cipher.MODE_DECRYPT);
        short decLen = aesCipher.doFinal(encrypted, (short) 0, encLen, dest, (short) 0);
        byte pad = dest[(short) (decLen - 1)];
        short plainLen = (short) (decLen - pad);
        return plainLen;
    }

    private void generateRSAKeyPair(APDU apdu) {
        byte[] buffer = apdu.getBuffer();
        short lc = (short) (buffer[ISO7816.OFFSET_LC] & 0xFF);

        if (lc != 6) {
            ISOException.throwIt(ISO7816.SW_WRONG_LENGTH);
        }

        apdu.setIncomingAndReceive();

        if (rsaKeyPair != null) {
            ISOException.throwIt(ISO7816.SW_COMMAND_NOT_ALLOWED);
        }

        if (!masterKeySet) {
            ISOException.throwIt(ISO7816.SW_SECURITY_STATUS_NOT_SATISFIED);
        }

        // Check if card is blocked
        if (userPin.getTriesRemaining() == 0) {
            ISOException.throwIt((short) 0x6983);
        }

        short pinOff = ISO7816.OFFSET_CDATA;
        byte[] masterKey = new byte[MASTER_KEY_LEN];
        byte[] modulusBuffer = new byte[MAX_RSA_COMPONENT_LEN];
        byte[] exponentBuffer = new byte[MAX_RSA_COMPONENT_LEN];

        try {
            // Verify PIN using OwnerPIN
            if (!userPin.check(buffer, pinOff, PIN_SIZE)) {
                ISOException.throwIt((short) (0x63C0 | userPin.getTriesRemaining()));
            }

            // Verify PIN hash
            sha256.reset();
            sha256.doFinal(buffer, pinOff, (short) 6, tempBuffer32, (short) 0);

            if (Util.arrayCompare(tempBuffer32, (short) 0, userPinHash, (short) 0, (short) 32) != 0) {
                ISOException.throwIt(ISO7816.SW_SECURITY_STATUS_NOT_SATISFIED);
            }

            // Decrypt Master Key
            decryptMasterKeyWithUserPin(buffer, pinOff, masterKey, (short) 0);

            // Generate keypair
            rsaKeyPair = new KeyPair(KeyPair.ALG_RSA, KeyBuilder.LENGTH_RSA_1024);
            rsaKeyPair.genKeyPair();

            RSAPublicKey rsaPublicKey = (RSAPublicKey) rsaKeyPair.getPublic();
            RSAPrivateKey tempPrivateKey = (RSAPrivateKey) rsaKeyPair.getPrivate();

            // Extract private key components
            short modLen = tempPrivateKey.getModulus(modulusBuffer, (short) 0);
            short expLen = tempPrivateKey.getExponent(exponentBuffer, (short) 0);

            // Encrypt private key components
            encryptedModulusLen = encryptRSAComponent(modulusBuffer, modLen,
                    masterKey, encryptedPrivateKeyModulus);
            encryptedExponentLen = encryptRSAComponent(exponentBuffer, expLen,
                    masterKey, encryptedPrivateKeyExponent);

            // Clear the original private key from memory
            tempPrivateKey.clearKey();
            rsaPrivateKey = null;

            // Return public key
            short offset = 0;
            short modulusLen = rsaPublicKey.getModulus(buffer, (short) (offset + 2));
            Util.setShort(buffer, offset, modulusLen);
            offset += (short) (2 + modulusLen);

            short pubExpLen = rsaPublicKey.getExponent(buffer, (short) (offset + 2));
            Util.setShort(buffer, offset, pubExpLen);
            offset += (short) (2 + pubExpLen);

            apdu.setOutgoing();
            apdu.setOutgoingLength(offset);
            apdu.sendBytesLong(buffer, (short) 0, offset);

        } finally {
            Util.arrayFillNonAtomic(masterKey, (short) 0, MASTER_KEY_LEN, (byte) 0x00);
            Util.arrayFillNonAtomic(modulusBuffer, (short) 0, MAX_RSA_COMPONENT_LEN, (byte) 0x00);
            Util.arrayFillNonAtomic(exponentBuffer, (short) 0, MAX_RSA_COMPONENT_LEN, (byte) 0x00);
            Util.arrayFillNonAtomic(tempBuffer32, (short) 0, (short) 32, (byte) 0x00);
        }
    }

    private void verifyCard(APDU apdu) {
        byte[] buffer = apdu.getBuffer();
        short lc = (short) (buffer[ISO7816.OFFSET_LC] & 0xFF);

        if (lc != 22) {
            ISOException.throwIt(ISO7816.SW_WRONG_LENGTH);
        }

        short receivedLen = apdu.setIncomingAndReceive();

        if (receivedLen != 22) {
            ISOException.throwIt(ISO7816.SW_WRONG_LENGTH);
        }

        if (rsaKeyPair == null || encryptedModulusLen == 0) {
            ISOException.throwIt(ISO7816.SW_CONDITIONS_NOT_SATISFIED);
        }

        if (!masterKeySet) {
            ISOException.throwIt(ISO7816.SW_SECURITY_STATUS_NOT_SATISFIED);
        }

        // Check if card is blocked
        if (userPin.getTriesRemaining() == 0) {
            ISOException.throwIt((short) 0x6983);
        }

        short pinOff = ISO7816.OFFSET_CDATA;
        short challengeOff = (short) (ISO7816.OFFSET_CDATA + 6);

        byte[] masterKey = new byte[MASTER_KEY_LEN];
        byte[] modulusBuffer = new byte[MAX_RSA_COMPONENT_LEN + 16];
        byte[] exponentBuffer = new byte[MAX_RSA_COMPONENT_LEN + 16];
        byte[] challengeBuffer = new byte[CHALLENGE_LENGTH];
        RSAPrivateKey tempPrivateKey = null;

        try {
            // Verify PIN using OwnerPIN
            if (!userPin.check(buffer, pinOff, PIN_SIZE)) {
                ISOException.throwIt((short) (0x63C0 | userPin.getTriesRemaining()));
            }

            // Verify PIN hash
            sha256.reset();
            sha256.doFinal(buffer, pinOff, (short) 6, tempBuffer32, (short) 0);

            if (Util.arrayCompare(tempBuffer32, (short) 0, userPinHash, (short) 0, (short) 32) != 0) {
                ISOException.throwIt(ISO7816.SW_SECURITY_STATUS_NOT_SATISFIED);
            }

            // Copy challenge to safe buffer
            Util.arrayCopyNonAtomic(buffer, challengeOff, challengeBuffer, (short) 0, CHALLENGE_LENGTH);

            // Decrypt Master Key
            decryptMasterKeyWithUserPin(buffer, pinOff, masterKey, (short) 0);

            tempAESKey.setKey(masterKey, (short) 0);

            // Decrypt private key components
            short expLen = decryptAndRemovePadding(encryptedPrivateKeyExponent, encryptedExponentLen,
                    exponentBuffer, (short) 0);
            short modLen = decryptAndRemovePadding(encryptedPrivateKeyModulus, encryptedModulusLen,
                    modulusBuffer, (short) 0);

            // Reconstruct private key temporarily
            tempPrivateKey = (RSAPrivateKey) KeyBuilder.buildKey(
                    KeyBuilder.TYPE_RSA_PRIVATE, KeyBuilder.LENGTH_RSA_1024, false);
            tempPrivateKey.setModulus(modulusBuffer, (short) 0, modLen);
            tempPrivateKey.setExponent(exponentBuffer, (short) 0, expLen);

            // Sign the challenge
            Signature signature = Signature.getInstance(Signature.ALG_RSA_SHA_PKCS1, false);
            signature.init(tempPrivateKey, Signature.MODE_SIGN);

            short sigLen = signature.sign(challengeBuffer, (short) 0, CHALLENGE_LENGTH,
                    buffer, (short) 0);

            apdu.setOutgoing();
            apdu.setOutgoingLength(sigLen);
            apdu.sendBytes((short) 0, sigLen);

        } finally {
            // Clear all sensitive data
            Util.arrayFillNonAtomic(masterKey, (short) 0, MASTER_KEY_LEN, (byte) 0x00);
            Util.arrayFillNonAtomic(modulusBuffer, (short) 0, MAX_RSA_COMPONENT_LEN, (byte) 0x00);
            Util.arrayFillNonAtomic(exponentBuffer, (short) 0, MAX_RSA_COMPONENT_LEN, (byte) 0x00);
            Util.arrayFillNonAtomic(challengeBuffer, (short) 0, CHALLENGE_LENGTH, (byte) 0x00);
            Util.arrayFillNonAtomic(tempBuffer32, (short) 0, (short) 32, (byte) 0x00);

            if (tempPrivateKey != null) {
                tempPrivateKey.clearKey();
            }
        }
    }

    private void clearAllData(APDU apdu) {
        Util.arrayFillNonAtomic(name, (short) 0, MAX_NAME_LEN, (byte) 0x00);
        Util.arrayFillNonAtomic(address, (short) 0, MAX_ADDRESS_LEN, (byte) 0x00);
        Util.arrayFillNonAtomic(phone, (short) 0, MAX_PHONE_LEN, (byte) 0x00);
        Util.arrayFillNonAtomic(cardId, (short) 0, MAX_CARDID_LEN, (byte) 0x00);
        Util.arrayFillNonAtomic(avatar, (short) 0, MAX_AVATAR_LEN, (byte) 0x00);
        Util.arrayFillNonAtomic(encryptedMasterKeyByUser, (short) 0, MASTER_KEY_LEN, (byte) 0x00);
        Util.arrayFillNonAtomic(encryptedMasterKeyByAdmin, (short) 0, MASTER_KEY_LEN, (byte) 0x00);
        Util.arrayFillNonAtomic(salt, (short) 0, SALT_LEN, (byte) 0x00);
        Util.arrayFillNonAtomic(userPinHash, (short) 0, MAX_PIN_HASH_LEN, (byte) 0x00);
        Util.arrayFillNonAtomic(adminPinHash, (short) 0, MAX_PIN_HASH_LEN, (byte) 0x00);

        nameLen = addressLen = phoneLen = cardIdLen = avatarLen = 0;
        pinsSet = false;
        masterKeySet = false;

        // Reset OwnerPIN instances
        userPin.reset();
        adminPin.reset();

        if (rsaKeyPair != null) {
            if (rsaPrivateKey != null) {
                rsaPrivateKey.clearKey();
            }
            rsaKeyPair = null;
            rsaPrivateKey = null;
        }
    }

    /**
     * Verify user PIN
     */
    private void verifyUserPin(APDU apdu) {
        byte[] buffer = apdu.getBuffer();

        if (buffer[ISO7816.OFFSET_LC] != 6) {
            ISOException.throwIt(ISO7816.SW_WRONG_LENGTH);
        }

        if (!pinsSet) {
            ISOException.throwIt(ISO7816.SW_SECURITY_STATUS_NOT_SATISFIED);
        }

        short bytesRead = apdu.setIncomingAndReceive();
        if (bytesRead != 6) {
            ISOException.throwIt(ISO7816.SW_WRONG_DATA);
        }

        short pinOffset = ISO7816.OFFSET_CDATA;

        // Check if card is blocked
        if (userPin.getTriesRemaining() == 0) {
            ISOException.throwIt((short) 0x6983); // Authentication method blocked
        }

        // Verify PIN using OwnerPIN
        if (!userPin.check(buffer, pinOffset, PIN_SIZE)) {
            ISOException.throwIt((short) (0x63C0 | userPin.getTriesRemaining()));
        }

        // Double check with hash for backward compatibility
        sha256.reset();
        sha256.doFinal(buffer, pinOffset, (short) 6, tempBuffer32, (short) 0);

        if (Util.arrayCompare(tempBuffer32, (short) 0, userPinHash, (short) 0, (short) 32) != 0) {
            ISOException.throwIt(ISO7816.SW_SECURITY_STATUS_NOT_SATISFIED);
        }
    }

    /**
     * Get lock status
     */
    private void getLockStatus(APDU apdu) {
        byte[] buffer = apdu.getBuffer();

        byte tries = userPin.getTriesRemaining();

        buffer[0] = (tries == 0) ? (byte) 0x01 : (byte) 0x00; // isLocked
        buffer[1] = tries;                                  // retry counter

        apdu.setOutgoing();
        apdu.setOutgoingLength((short) 2);
        apdu.sendBytes((short) 0, (short) 2);
    }


    /**
     * Unlock card with admin PIN
     */
    private void unlockCard(APDU apdu) {
        byte[] buffer = apdu.getBuffer();
        short lc = (short) (buffer[ISO7816.OFFSET_LC] & 0xFF);

        if (lc != 6) {
            ISOException.throwIt(ISO7816.SW_WRONG_LENGTH);
        }

        if (!pinsSet) {
            ISOException.throwIt(ISO7816.SW_SECURITY_STATUS_NOT_SATISFIED);
        }

        apdu.setIncomingAndReceive();

        short adminPinOff = ISO7816.OFFSET_CDATA;

        try {
            // Verify admin PIN
            if (!adminPin.check(buffer, adminPinOff, PIN_SIZE)) {
                ISOException.throwIt(ISO7816.SW_SECURITY_STATUS_NOT_SATISFIED);
            }

            // Verify admin PIN hash for backward compatibility
            sha256.reset();
            sha256.doFinal(buffer, adminPinOff, (short) 6, tempBuffer32, (short) 0);

            if (Util.arrayCompare(tempBuffer32, (short) 0, adminPinHash, (short) 0, (short) 32) != 0) {
                ISOException.throwIt(ISO7816.SW_SECURITY_STATUS_NOT_SATISFIED);
            }

            // Reset user PIN - this unlocks the card
            userPin.resetAndUnblock();

        } finally {
            Util.arrayFillNonAtomic(tempBuffer32, (short) 0, (short) 32, (byte) 0x00);
        }
    }

    /**
     * Change user PIN
     */
    private void changeUserPin(APDU apdu) {
        byte[] buffer = apdu.getBuffer();

        if (buffer[ISO7816.OFFSET_LC] != 12) {
            ISOException.throwIt(ISO7816.SW_WRONG_LENGTH);
        }

        if (!pinsSet) {
            ISOException.throwIt(ISO7816.SW_SECURITY_STATUS_NOT_SATISFIED);
        }

        apdu.setIncomingAndReceive();

        short oldPinOffset = ISO7816.OFFSET_CDATA;
        short newPinOffset = (short) (ISO7816.OFFSET_CDATA + 6);

        byte[] masterKey = new byte[MASTER_KEY_LEN];
        byte[] newKek = new byte[KEK_LEN];

        try {
            // Check if card is blocked
            if (userPin.getTriesRemaining() == 0) {
                ISOException.throwIt((short) 0x6983);
            }

            // Verify old PIN using OwnerPIN
            if (!userPin.check(buffer, oldPinOffset, PIN_SIZE)) {
                ISOException.throwIt((short) (0x63C0 | userPin.getTriesRemaining()));
            }

            // Verify old PIN hash
            sha256.reset();
            sha256.doFinal(buffer, oldPinOffset, (short) 6, tempBuffer32, (short) 0);

            if (Util.arrayCompare(tempBuffer32, (short) 0, userPinHash, (short) 0, (short) 32) != 0) {
                ISOException.throwIt(ISO7816.SW_SECURITY_STATUS_NOT_SATISFIED);
            }

            // Check new != old
            if (Util.arrayCompare(buffer, oldPinOffset, buffer, newPinOffset, (short) 6) == 0) {
                ISOException.throwIt(ISO7816.SW_DATA_INVALID);
            }

            // Decrypt Master Key
            decryptMasterKeyWithUserPin(buffer, oldPinOffset, masterKey, (short) 0);

            // Hash new PIN
            sha256.reset();
            sha256.doFinal(buffer, newPinOffset, (short) 6, userPinHash, (short) 0);

            // Update OwnerPIN with new PIN
            userPin.update(buffer, newPinOffset, PIN_SIZE);

            // Derive new KEK
            pbkdf2(buffer, newPinOffset, (short) 6, salt, (short) 0, SALT_LEN,
                    PBKDF2_ITERATIONS, newKek, (short) 0);

            // Re-encrypt Master Key
            tempAESKey.setKey(newKek, (short) 0);
            aesCipher.init(tempAESKey, Cipher.MODE_ENCRYPT);
            aesCipher.doFinal(masterKey, (short) 0, MASTER_KEY_LEN, encryptedMasterKeyByUser, (short) 0);

        } finally {
            Util.arrayFillNonAtomic(masterKey, (short) 0, MASTER_KEY_LEN, (byte) 0x00);
            Util.arrayFillNonAtomic(newKek, (short) 0, KEK_LEN, (byte) 0x00);
            Util.arrayFillNonAtomic(tempBuffer32, (short) 0, (short) 32, (byte) 0x00);
        }
    }
}