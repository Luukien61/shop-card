package com.luukien.javacard.model;

public enum SecretType {
    PIN("PIN", "6 chữ số", "\\d{6}", "PIN phải đúng 6 chữ số!", 6),
    PASSWORD("Mật khẩu", "Nhập mật khẩu", null, "Mật khẩu không đúng!", -1),
    MASTER_PASSWORD("Mật khẩu chính", "Nhập mật khẩu chính của ví", null, "Mật khẩu chính sai!", -1),
    PASSPHRASE("Cụm từ khôi phục", "Nhập cụm từ khôi phục (12-24 từ)", null, "Cụm từ khôi phục sai!", -1),
    TWO_FACTOR("Mã xác thực 2FA", "Nhập mã 6-8 số từ ứng dụng", "\\d{6,8}", "Mã 2FA không hợp lệ!", 6);

    public final String label;
    public final String prompt;
    public final String regex;
    public final String invalidMsg;
    public final int fixedLength;

    SecretType(String label, String prompt, String regex, String invalidMsg, int fixedLength) {
        this.label = label;
        this.prompt = prompt;
        this.regex = regex;
        this.invalidMsg = invalidMsg;
        this.fixedLength = fixedLength;
    }
}
