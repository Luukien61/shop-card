package com.luukien.javacard.controller;

import com.luukien.javacard.utils.ApplicationHelper;
import com.luukien.javacard.utils.CardHelper;
import com.luukien.javacard.utils.CloudinaryHelper;
import com.luukien.javacard.utils.DatabaseHelper;
import javafx.fxml.FXML;
import javafx.scene.control.*;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;
import javafx.stage.FileChooser;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.time.LocalDate;
import java.util.Base64;

public class InitiateCardController {

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


    @FXML
    public void initialize() {
        chooseImageBtn.setOnAction(e -> onChooseImage());
        finishBtn.setOnAction(e -> onFinish());
    }


    @FXML
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

    @FXML
    private void onFinish() {

        String username = usernameField.getText();
        LocalDate birthDate = dateField.getValue();
        String phone = phoneField.getText();
        String address = addressField.getText();


        String genderSelected = menGender.isSelected() ? "Nam" : womenGender.isSelected() ? "Nữ" : "";

        if (username.isEmpty() || birthDate == null || phone.isEmpty() || address.isEmpty() || selectedImageFile == null) {
            ApplicationHelper.showAlert("Vui lòng điền đầy đủ thông tin!", true);
            return;
        }

        ApplicationHelper.showPinDialog().ifPresent(pin -> {
            String[] result = CardHelper.initiateCard(username, address, phone, pin, "123456", selectedImageFile);

            if (result == null) {
                ApplicationHelper.showAlert("Không thể khởi tạo thẻ! Vui lòng thử lại.", true);
                return;
            }

            String publicKey = result[0];
            String cardId = result[1];

            String imageBase64;
            try {
                byte[] imageBytes = Files.readAllBytes(selectedImageFile.toPath());
                imageBase64 = Base64.getEncoder().encodeToString(imageBytes);
            } catch (IOException e) {
                ApplicationHelper.showAlert("Không thể đọc file ảnh!", true);
                return;
            }
            String uploadImage = CloudinaryHelper.uploadImage(selectedImageFile);

            boolean success = DatabaseHelper.insertUser(
                    username,
                    address,
                    uploadImage,
                    birthDate,
                    genderSelected,
                    phone,
                    cardId,
                    publicKey
            );

            if (success) {
                ApplicationHelper.showAlert("Khởi tạo thẻ thành công!\nCard ID: " + cardId, false);
            } else {
                ApplicationHelper.showAlert("Thêm người dùng vào CSDL thất bại!", true);
            }
        });
    }
}
