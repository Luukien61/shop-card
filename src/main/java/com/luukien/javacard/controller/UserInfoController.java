package com.luukien.javacard.controller;

import com.luukien.javacard.dialog.TopupDialog;
import com.luukien.javacard.dialog.UpdateCredentialDialog;
import com.luukien.javacard.dialog.VerifyCredentialDialog;
import com.luukien.javacard.model.SecretType;
import com.luukien.javacard.model.User;
import com.luukien.javacard.screen.SceneManager;
import com.luukien.javacard.screen.Scenes;
import com.luukien.javacard.service.AccountService;
import com.luukien.javacard.state.AppState;
import com.luukien.javacard.utils.ApplicationHelper;
import com.luukien.javacard.utils.CardHelper;
import com.luukien.javacard.utils.DatabaseHelper;
import com.luukien.javacard.utils.DateConverter;
import javafx.application.Platform;
import javafx.fxml.FXML;
import javafx.scene.control.*;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;

import java.math.BigDecimal;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.Objects;

public class UserInfoController {

    @FXML private TextField cardIdTextField;
    @FXML private TextField nameTextField;
    @FXML private TextField phoneTextField;
    @FXML private TextField addressTextField;
    @FXML private DatePicker datePicker;
    @FXML private RadioButton menGenderBtn;
    @FXML private RadioButton womenGenderBtn;
    @FXML private Label balanceLabel;
    @FXML private Button changePinBtn;
    @FXML private ImageView imgView;
    @FXML private Button pickImgBtn;
    @FXML private Button updateBtn;
    @FXML private Button unlockBtn;
    @FXML private Button backButton;
    @FXML private ToggleGroup gender;
    @FXML private Button topupBtn;

    // Nút mới trên FXML
    @FXML private Button loadFromCardBtn;

    private User user;

    // Unlock session state
    private boolean unlocked = false;
    private long unlockedAtMs = 0;
    private static final long UNLOCK_TTL_MS = 90_000; // 90s
    private String unlockedCardId = null;
    private String cachedAdminPin = null; // tiện cho "Tải từ thẻ" không hỏi lại PIN

    @FXML
    private void initialize() {
        datePicker.setConverter(DateConverter.getLocalDateConverter());

        // 1) Lấy phone đang chọn
        String currentClientPhone = AppState.getInstance().getCurrentClientPhone();
        if (currentClientPhone == null || currentClientPhone.isBlank()) {
            ApplicationHelper.showAlert("Không xác định được người dùng!", true);
            SceneManager.switchTo(Scenes.HOME_MANAGEMENT_SCENE);
            return;
        }

        // 2) Load user từ DB
        user = getDetailUser(currentClientPhone);
        if (user == null) {
            ApplicationHelper.showAlert("Không tìm thấy thông tin người dùng với số điện thoại:\n" + currentClientPhone, true);
            SceneManager.switchTo(Scenes.HOME_MANAGEMENT_SCENE);
            return;
        }

        // 3) Render UI từ DB
        renderUserFromDb(user, currentClientPhone);

        // 4) Setup UI + handlers
        loadUserImage(user.getImage());
        balanceLabel.setText(setupBalanceText());

        backButton.setOnAction(e -> {
            // Khi rời màn hình thì lock luôn cho chắc
            lockUi();
            SceneManager.switchTo(Scenes.HOME_MANAGEMENT_SCENE);
        });

        topupBtn.setOnAction(e -> handleTopup());
        unlockBtn.setOnAction(e -> handleUnlock());
        loadFromCardBtn.setOnAction(e -> handleLoadFromCard());

        updateButtonStates(); // ẩn/hiện theo admin mode
        lockUi();             // khoá thao tác nhạy cảm ban đầu
    }

    private void renderUserFromDb(User u, String fallbackPhone) {
        cardIdTextField.setText(u.getCardId());
        nameTextField.setText(u.getUserName());
        phoneTextField.setText(u.getPhone() != null ? u.getPhone() : fallbackPhone);
        addressTextField.setText(u.getAddress());

        if ("Nam".equalsIgnoreCase(u.getGender())) {
            menGenderBtn.setSelected(true);
            womenGenderBtn.setSelected(false);
        } else if ("Nữ".equalsIgnoreCase(u.getGender())) {
            menGenderBtn.setSelected(false);
            womenGenderBtn.setSelected(true);
        } else {
            menGenderBtn.setSelected(false);
            womenGenderBtn.setSelected(false);
        }

        if (u.getDateOfBirth() != null) {
            datePicker.setValue(u.getDateOfBirth());
        } else {
            datePicker.setValue(null);
        }
    }

    private String setupBalanceText() {
        BigDecimal balance = user.getBalance() != null ? user.getBalance() : BigDecimal.ZERO;
        String tier = user.getMemberTier() != null ? user.getMemberTier() : "BRONZE";

        String tierDisplay = switch (tier.toUpperCase()) {
            case "BRONZE" -> "Đồng";
            case "SILVER" -> "Bạc";
            case "GOLD" -> "Vàng";
            case "PLATINUM" -> "Bạch Kim";
            case "DIAMOND" -> "Kim Cương";
            default -> tier;
        };

        return "%s đ - Hạng: %s".formatted(
                String.format("%,.0f", balance),
                tierDisplay
        );
    }

    private User getDetailUser(String phone) {
        String sql = "select card_id, user_name, address, gender, date_of_birth, balance, image, member_tier, public_key " +
                "from users where phone=?";

        try (Connection conn = DatabaseHelper.getConnection()) {
            if (conn == null) throw new RuntimeException(ApplicationHelper.CONN_DB_MESSAGE);

            try (PreparedStatement ps = conn.prepareStatement(sql)) {
                ps.setString(1, phone.trim());

                try (ResultSet rs = ps.executeQuery()) {
                    if (rs.next()) {
                        return User.builder()
                                .cardId(rs.getString("card_id"))
                                .userName(rs.getString("user_name"))
                                .address(rs.getString("address"))
                                .gender(rs.getString("gender"))
                                .dateOfBirth(rs.getDate("date_of_birth") != null
                                        ? rs.getDate("date_of_birth").toLocalDate()
                                        : null)
                                .balance(rs.getBigDecimal("balance"))
                                .image(rs.getString("image"))
                                .memberTier(rs.getString("member_tier"))
                                .phone(phone)
                                .publicKey(rs.getString("public_key"))
                                .build();
                    }
                }
            }
        } catch (SQLException e) {
            System.err.println("Lỗi khi lấy thông tin user theo phone: " + phone);
            e.printStackTrace();
            ApplicationHelper.showAlert("Lỗi kết nối cơ sở dữ liệu. Vui lòng thử lại!", true);
        }

        return null;
    }

    private void loadUserImage(String imageUrl) {
        if (imageUrl == null || imageUrl.isBlank()) {
            imgView.setImage(new Image(Objects.requireNonNull(
                    getClass().getResourceAsStream("/com/luukien/javacard/img/default-avatar.jpg")
            )));
            return;
        }

        try {
            Image image = new Image(imageUrl, true);
            image.progressProperty().addListener((obs, old, progress) -> {
                if (progress.doubleValue() >= 1.0) {
                    Platform.runLater(() -> imgView.setImage(image));
                }
            });
            imgView.setImage(image);
        } catch (Exception e) {
            imgView.setImage(new Image(Objects.requireNonNull(
                    getClass().getResourceAsStream("/images/default-avatar.png")
            )));
        }
    }

    /**
     * Chỉ quản lý ẩn/hiện theo admin mode.
     * Việc enable/disable do unlock sẽ do lockUi() + unlock success quản lý.
     */
    private void updateButtonStates() {
        boolean isAdmin = AppState.getInstance().isAdminMode();
        String currentEmail = AppState.getInstance().getCurrentUserEmail();

        unlockBtn.setVisible(isAdmin);
        unlockBtn.setManaged(isAdmin);

        loadFromCardBtn.setVisible(isAdmin);
        loadFromCardBtn.setManaged(isAdmin);

        changePinBtn.setVisible(isAdmin);
        changePinBtn.setManaged(isAdmin);

        // Set handler đổi PIN: vẫn giữ theo logic bạn cũ
        changePinBtn.setOnAction(e -> VerifyCredentialDialog.show(
                SecretType.PASSWORD,
                null,
                5,
                (password) -> AccountService.verifyPassword(password, currentEmail),
                (ignore) -> UpdateCredentialDialog.show(
                        SecretType.PIN,
                        null,
                        null,
                        CardHelper::changeUserPin
                ),
                null
        ));
    }

    private void handleTopup() {
        if (!isUnlockValid()) {
            lockUi();
            ApplicationHelper.showAlert("Bạn cần Unlock lại để nạp tiền.", true);
            return;
        }

        VerifyCredentialDialog.show(
                SecretType.PIN,
                "Xác thực để nạp tiền",
                5,
                DatabaseHelper::verifySysUserPin,
                (pin) -> TopupDialog.show().ifPresent(amount -> {
                    boolean success = DatabaseHelper.updateBalanceAndTier(user.getPhone(), amount);
                    if (success) {
                        ApplicationHelper.showAlert("Nạp tiền thành công: " + amount + " VND", false);
                        user = getDetailUser(user.getPhone());
                        if (user != null) {
                            balanceLabel.setText(setupBalanceText());
                        }
                    } else {
                        ApplicationHelper.showAlert("Nạp tiền thất bại! Vui lòng thử lại.", true);
                    }
                }),
                () -> ApplicationHelper.showAlert("Thẻ bị khóa tạm thời!", true)
        );
    }

    private void handleUnlock() {
        if (user == null) return;

        ApplicationHelper.showPinDialog("Unlock thẻ", "Nhập Admin PIN trên thẻ")
                .ifPresent(adminPin -> {
                    ApplicationHelper.showProgress("Đang xác thực thẻ...");

                    new Thread(() -> {
                        try {
                            // 1) Đúng thẻ + đúng public key
                            CardHelper.adminUnlockAndVerifyCard(
                                    user.getCardId(),
                                    user.getPublicKey(),
                                    adminPin
                            );

                            // 2) Set session
                            unlocked = true;
                            unlockedAtMs = System.currentTimeMillis();
                            unlockedCardId = user.getCardId();
                            cachedAdminPin = adminPin;

                            Platform.runLater(() -> {
                                loadFromCardBtn.setDisable(false);
                                updateBtn.setDisable(false);
                                changePinBtn.setDisable(false);
                                topupBtn.setDisable(false);

                                // unlockBtn.setText("Unlocked ✅");
                                ApplicationHelper.hideProgress();
                                ApplicationHelper.showAlert("Unlock thành công! Bạn có 90s để thao tác.", false);
                            });

                        } catch (Exception ex) {
                            Platform.runLater(() -> {
                                ApplicationHelper.hideProgress();
                                ApplicationHelper.showAlert("Unlock thất bại: " + ex.getMessage(), true);
                            });
                        }
                    }).start();
                });
    }

    private void handleLoadFromCard() {
        if (!isUnlockValid()) {
            lockUi();
            ApplicationHelper.showAlert("Phiên Unlock đã hết hạn. Vui lòng Unlock lại.", true);
            return;
        }

        String adminPin = cachedAdminPin;
        if (adminPin == null || adminPin.isBlank()) {
            ApplicationHelper.showAlert("Thiếu Admin PIN. Vui lòng Unlock lại.", true);
            return;
        }

        ApplicationHelper.showProgress("Đang đọc dữ liệu từ thẻ...");

        new Thread(() -> {
            try {
                var profile = CardHelper.readProfileAsAdmin(adminPin);

                Platform.runLater(() -> {
                    cardIdTextField.setText(profile.cardId());
                    nameTextField.setText(profile.name());
                    phoneTextField.setText(profile.phone());
                    addressTextField.setText(profile.address());

                    ApplicationHelper.hideProgress();
                    ApplicationHelper.showAlert("Đã tải dữ liệu từ thẻ.", false);
                });

            } catch (Exception ex) {
                Platform.runLater(() -> {
                    ApplicationHelper.hideProgress();
                    ApplicationHelper.showAlert("Đọc thẻ thất bại: " + ex.getMessage(), true);
                });
            }
        }).start();
    }

    private boolean isUnlockValid() {
        if (!unlocked) return false;
        if (unlockedCardId == null || user == null) return false;
        if (!unlockedCardId.equals(user.getCardId())) return false;

        return System.currentTimeMillis() - unlockedAtMs <= UNLOCK_TTL_MS;
    }

    private void lockUi() {
        unlocked = false;
        unlockedAtMs = 0;
        unlockedCardId = null;
        cachedAdminPin = null;

        if (loadFromCardBtn != null) loadFromCardBtn.setDisable(true);
        if (updateBtn != null) updateBtn.setDisable(true);
        if (changePinBtn != null) changePinBtn.setDisable(true);
        if (topupBtn != null) topupBtn.setDisable(true);

        // unlockBtn.setText("Unlock");
    }
}
