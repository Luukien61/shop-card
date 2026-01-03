package com.luukien.javacard.controller;

import com.luukien.javacard.dialog.VerifyCredentialDialog;
import com.luukien.javacard.exception.ApplicationException;
import com.luukien.javacard.model.SecretType;
import com.luukien.javacard.model.User;
import com.luukien.javacard.state.AppState;
import com.luukien.javacard.utils.*;
import javafx.application.Platform;
import javafx.fxml.FXML;
import javafx.scene.control.*;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;
import javafx.stage.FileChooser;
import javafx.stage.Stage;

import javax.smartcardio.CardException;
import java.io.File;
import java.io.IOException;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.LocalDate;
import java.util.Objects;

import static com.luukien.javacard.utils.ApplicationHelper.showAlert;

public class UpdateUserCardController {
    @FXML
    private Button backButton;
    @FXML
    private TextField usernameField;
    @FXML
    private DatePicker dateField;
    @FXML
    private RadioButton menGender;
    @FXML
    private ToggleGroup gender;
    @FXML
    private RadioButton womenGender;
    @FXML
    private TextField phoneField;
    @FXML
    private TextField addressField;

    @FXML
    private ImageView imageView;
    @FXML
    private Button chooseImageBtn;
    @FXML
    private Button finishBtn;

    private File selectedImageFile = null;
    private User preUser;


    @FXML
    private void initialize() {
        dateField.setConverter(DateConverter.getLocalDateConverter());
        String currentClientPhone = AppState.getInstance().getCurrentClientPhone();

        if (currentClientPhone == null || currentClientPhone.isBlank()) {
            showAlert("Không xác định được người dùng!", true);
            handleBack();
            return;
        }

        preUser = getDetailUser(currentClientPhone);

        if (preUser == null) {
            showAlert("Không tìm thấy thông tin người dùng với số điện thoại:\n" + currentClientPhone, true);
            handleBack();
            return;
        }

        usernameField.setText(preUser.getUserName());
        phoneField.setText(preUser.getPhone() != null ? preUser.getPhone() : currentClientPhone);
        addressField.setText(preUser.getAddress());

        if ("Nam".equalsIgnoreCase(preUser.getGender())) {
            menGender.setSelected(true);
        } else if ("Nữ".equalsIgnoreCase(preUser.getGender())) {
            womenGender.setSelected(true);
        } else {
            menGender.setSelected(false);
            womenGender.setSelected(false);
        }

        if (preUser.getDateOfBirth() != null) {
            dateField.setValue(preUser.getDateOfBirth());
        } else {
            dateField.setValue(null);
        }


        loadUserImage(preUser.getImage());
        chooseImageBtn.setOnAction(e -> onChooseImage());
        finishBtn.setOnAction(e -> VerifyCredentialDialog.show(
                SecretType.PIN,
                "Xác thực PIN người dùng",
                5,
                (userPin) -> {
                    try {
                        return CardHelper.verifyUserPin(userPin);
                    } catch (CardException ex) {
                        showAlert("Lỗi", true);
                    }
                    return false;
                },
                (userPin) -> {
                    try {
                        onFinish(userPin);
                    } catch (Exception ex) {
                        showAlert("Lỗi " + ex.getMessage(), true);
                    }
                },
                () -> showAlert("Khóa thẻ", true)
        ));

        backButton.setOnAction(e -> handleBack());
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
            showAlert("Lỗi kết nối cơ sở dữ liệu. Vui lòng thử lại!", true);
            return null;
        }
        return null;
    }

    private void loadUserImage(String imageUrl) {
        if (imageUrl == null || imageUrl.isBlank()) {
            imageView.setImage(new Image(Objects.requireNonNull(getClass().getResourceAsStream("/com/luukien/javacard/img/default-avatar.jpg"))));
            return;
        }

        try {
            Image image = new Image(imageUrl, true);
            image.progressProperty().addListener((obs, old, progress) -> {
                if (progress.doubleValue() >= 1.0) {
                    Platform.runLater(() -> imageView.setImage(image));
                }
            });
            imageView.setImage(image);
        } catch (Exception e) {
            imageView.setImage(new Image(Objects.requireNonNull(getClass().getResourceAsStream("/images/default-avatar.png"))));
        }
    }

    private void onChooseImage() {
        FileChooser fileChooser = new FileChooser();
        fileChooser.setTitle("Chọn ảnh");
        fileChooser.getExtensionFilters().addAll(
                new FileChooser.ExtensionFilter("Image Files", "*.png", "*.jpg", "*.jpeg")
        );

        File file = fileChooser.showOpenDialog(null);

        if (file != null) {
            selectedImageFile = file;
            Image image = new Image(file.toURI().toString());
            imageView.setImage(image);
        }
    }

    private void onFinish(String userPin) throws CardException, ApplicationException, IOException {

        String userName = usernameField.getText();
        String phone = phoneField.getText();
        String address = addressField.getText();
        String avatar = preUser.getImage();
        LocalDate birthDate = dateField.getValue();
        String genderSelected = menGender.isSelected()
                ? "Nam"
                : womenGender.isSelected() ? "Nữ" : null;

        boolean isNameChanged = !Objects.equals(userName, preUser.getUserName());
        boolean isPhoneChanged = !Objects.equals(phone, preUser.getPhone());
        boolean isAddressChanged = !Objects.equals(address, preUser.getAddress());
        boolean isAvatarChanged = selectedImageFile != null;

        if (isNameChanged || isPhoneChanged || isAddressChanged) {
            CardHelper.updateCardData(userPin, userName, phone, address, selectedImageFile);
        }
        if (isAvatarChanged) {
            avatar = CloudinaryHelper.uploadImage(selectedImageFile);
            if (avatar == null) {
                ApplicationHelper.showAlert("Upload ảnh thất bại!", true);
                CardHelper.clearCardData();
                ApplicationHelper.hideProgress();
                return;
            }
        }
        DatabaseHelper.updateUser(
                userName,
                phone,
                address,
                genderSelected,
                preUser.getPhone(),
                avatar,
                birthDate
        );
        AppState.getInstance().setCurrentClientPhone(phone);
        ApplicationHelper.showAlert(
                "Cập nhật thông tin thành công",
                false
        );
        handleBack();

    }


    private void handleBack() {
        Stage stage = (Stage) backButton.getScene().getWindow();
        stage.close();
    }
}
