package com.luukien.javacard.controller;

import com.luukien.javacard.model.User;
import com.luukien.javacard.screen.SceneManager;
import com.luukien.javacard.screen.Scenes;
import com.luukien.javacard.state.AppState;
import com.luukien.javacard.utils.ApplicationHelper;
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

    @FXML
    private TextField cardIdTextField;
    @FXML
    private TextField nameTextField;
    @FXML
    private TextField phoneTextField;
    @FXML
    private TextField addressTextField;
    @FXML
    private DatePicker datePicker;
    @FXML
    private RadioButton menGenderBtn;
    @FXML
    private RadioButton womenGenderBtn;
    @FXML
    private Label balanceLabel;
    @FXML
    private Button changePinBtn;
    @FXML
    private ImageView imgView;
    @FXML
    private Button pickImgBtn;
    @FXML
    private Button updateBtn;
    @FXML
    private Button unlockBtn;
    @FXML
    private Button backButton;
    @FXML
    private ToggleGroup gender;

    private User user;

    @FXML
    private void initialize() {
        datePicker.setConverter(DateConverter.getLocalDateConverter());
        String currentClientPhone = AppState.getInstance().getCurrentClientPhone();

        if (currentClientPhone == null || currentClientPhone.isBlank()) {
            ApplicationHelper.showAlert("Không xác định được người dùng!", true);
            SceneManager.switchTo(Scenes.HOME_MANAGEMENT_SCENE);
            return;
        }

        user = getDetailUser(currentClientPhone);

        if (user == null) {
            ApplicationHelper.showAlert("Không tìm thấy thông tin người dùng với số điện thoại:\n" + currentClientPhone, true);
            SceneManager.switchTo(Scenes.HOME_MANAGEMENT_SCENE);
            return;
        }

        cardIdTextField.setText(user.getCardId());
        nameTextField.setText(user.getUserName());
        phoneTextField.setText(user.getPhone() != null ? user.getPhone() : currentClientPhone);
        addressTextField.setText(user.getAddress());

        if ("Nam".equalsIgnoreCase(user.getGender())) {
            menGenderBtn.setSelected(true);
        } else if ("Nữ".equalsIgnoreCase(user.getGender())) {
            womenGenderBtn.setSelected(true);
        } else {
            menGenderBtn.setSelected(false);
            womenGenderBtn.setSelected(false);
        }

        if (user.getDateOfBirth() != null) {
            datePicker.setValue(user.getDateOfBirth());
        } else {
            datePicker.setValue(null);
        }

        String htmlText = setupBalanceText();

        balanceLabel.setText(htmlText);

        loadUserImage(user.getImage());
        updateButtonStates();
    }

    private String setupBalanceText() {
        BigDecimal balance = user.getBalance() != null ? user.getBalance() : BigDecimal.ZERO;

        String tier = user.getMemberTier();

        String tierColor = switch (tier.toUpperCase()) {
            case "SILVER" -> "#94a3b8";
            case "GOLD" -> "#f59e0b";
            case "PLATINUM", "DIAMOND" -> "#e879f9";
            case "BRONZE" -> "#c2410c";
            default -> "#6b7280";
        };

        String tierDisplay = switch (tier.toUpperCase()) {
            case "BRONZE" -> "Đồng";
            case "SILVER" -> "Bạc";
            case "GOLD" -> "Vàng";
            case "PLATINUM" -> "Bạch Kim";
            case "DIAMOND" -> "Kim Cương";
            default -> tier;
        };

        return "%s đ - Hạng: %s".formatted(
                String.format("%,.0f", balance), tierDisplay
        );
    }

    private User getDetailUser(String phone) {
        String sql = "select card_id, user_name, address, gender, date_of_birth, balance, image, member_tier from users where phone=?";

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
                                .build();
                    }
                }
            }
        } catch (SQLException e) {
            System.err.println("Lỗi khi lấy thông tin user theo phone: " + phone);
            e.printStackTrace();
            ApplicationHelper.showAlert("Lỗi kết nối cơ sở dữ liệu. Vui lòng thử lại!", true);
            return null;
        }
        return null;
    }

    private void loadUserImage(String imageUrl) {
        if (imageUrl == null || imageUrl.isBlank()) {
            imgView.setImage(new Image(Objects.requireNonNull(getClass().getResourceAsStream("/com/luukien/javacard/img/default-avatar.jpg"))));
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
            imgView.setImage(new Image(Objects.requireNonNull(getClass().getResourceAsStream("/images/default-avatar.png"))));
        }
    }

    private void updateButtonStates() {
        boolean isAdmin = AppState.getInstance().isAdminMode();
        unlockBtn.setVisible(isAdmin);
        unlockBtn.setManaged(isAdmin);
        unlockBtn.setDisable(!isAdmin);
        changePinBtn.setVisible(isAdmin);
        changePinBtn.setManaged(isAdmin);
        changePinBtn.setDisable(!isAdmin);

        backButton.setOnAction(e -> SceneManager.switchTo(Scenes.HOME_MANAGEMENT_SCENE));
    }


}
