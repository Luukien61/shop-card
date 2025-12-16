package com.luukien.javacard.controller;

import com.luukien.javacard.dialog.VerifyCredentialDialog;
import com.luukien.javacard.model.SecretType;
import com.luukien.javacard.screen.SceneManager;
import com.luukien.javacard.screen.Scenes;
import com.luukien.javacard.utils.*;
import javafx.fxml.FXML;
import javafx.scene.control.*;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;
import javafx.stage.FileChooser;

import java.io.File;
import java.time.LocalDate;

import static com.luukien.javacard.utils.ApplicationHelper.showAlert;

public class InitiateCardController {

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


    @FXML
    public void initialize() {
        dateField.setConverter(DateConverter.getLocalDateConverter());
        chooseImageBtn.setOnAction(e -> onChooseImage());
        finishBtn.setOnAction(e -> onFinish());
        backButton.setOnAction(e -> SceneManager.switchTo(Scenes.HOME_MANAGEMENT_SCENE));
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
            showAlert("Vui lòng điền đầy đủ thông tin!", true);
            return;
        }

        ApplicationHelper
                .showPinDialog("Khởi tạo PIN", "Nhập PIN mới cho tài khoản").ifPresent(userPin -> {
                    VerifyCredentialDialog.show(
                            SecretType.PIN,
                            "Xác thực PIN Admin",
                            5,
                            DatabaseHelper::verifySysUserPin,
                            (adminPin) -> initiateCard(username, address, phone, birthDate, genderSelected, userPin, adminPin),
                            () -> showAlert("Thẻ bị khóa tạm thời!", true)
                    );
                });

    }

    private void initiateCard(
            String username,
            String address,
            String phone,
            LocalDate birthDate,
            String gender,
            String userPin,
            String adminPin
    ) {
        if (!CardHelper.clearCardData()) {
            ApplicationHelper.showAlert("Không thể xóa dữ liệu cũ trên thẻ!\nVui lòng rút thẻ ra và thử lại.", true);
            return;
        }

        ApplicationHelper.showProgress("Đang khởi tạo thẻ, vui lòng không rút thẻ...");

        final int MAX_ATTEMPTS = 3;

        boolean cardInitialized = false;
        String finalCardId = null;
        String finalPublicKey = null;
        String uploadImageUrl = null;

        for (int attempt = 1; attempt <= MAX_ATTEMPTS; attempt++) {
            ApplicationHelper.updateProgress("Đang ghi thẻ (lần " + attempt + "/" + MAX_ATTEMPTS + ")...");

            String[] genResult = CardHelper.initiateKeyAndCardId();
            if (genResult == null || genResult.length != 2) {
                CardHelper.clearCardData();
                continue;
            }

            finalPublicKey = genResult[0];
            finalCardId = genResult[1];


            Boolean writeSuccess = CardHelper.initiateCard(
                    username, address, phone,
                    userPin, adminPin,
                    selectedImageFile,
                    finalCardId
            );

            if (writeSuccess) {
                cardInitialized = true;
                break;
            } else {
                ApplicationHelper.showToast("Lần " + attempt + " thất bại, đang thử lại...");
                CardHelper.clearCardData();
                ApplicationHelper.delay(1500);
            }
        }


        if (!cardInitialized) {
            ApplicationHelper.hideProgress();
            ApplicationHelper.showAlert(
                    "Khởi tạo thẻ thất bại sau " + MAX_ATTEMPTS + " lần thử!\n" +
                            "Vui lòng kiểm tra:\n" +
                            "• Thẻ có bị lỗi không?\n" +
                            "• Đầu đọc có kết nối ổn định không?\n" +
                            "• Thử rút ra cắm lại thẻ.",
                    true
            );
            return;
        }
        if (selectedImageFile != null) {
            uploadImageUrl = CloudinaryHelper.uploadImage(selectedImageFile);
            if (uploadImageUrl == null) {
                ApplicationHelper.showAlert("Upload ảnh thất bại!", true);
                CardHelper.clearCardData();
                ApplicationHelper.hideProgress();
                return;
            }
        }
        boolean dbSuccess = DatabaseHelper.insertUser(
                username,
                address,
                uploadImageUrl,
                birthDate,
                gender,
                phone,
                finalCardId,
                finalPublicKey
        );

        ApplicationHelper.hideProgress();

        if (dbSuccess) {
            ApplicationHelper.showAlert(
                    "Khởi tạo thẻ thành công!\n\nCard ID: " + finalCardId,
                    false
            );
            //SceneManager.switchTo(Scenes.HOME_MANAGEMENT_SCENE);
        } else {
            ApplicationHelper.showAlert(
                    "Ghi thẻ thành công nhưng lưu CSDL thất bại!\n" +
                            "Thẻ vẫn hoạt động bình thường.\n" +
                            "Card ID: " + finalCardId + "\n\n" +
                            "Vui lòng báo admin để xử lý dữ liệu CSDL.",
                    true
            );
            SceneManager.switchTo(Scenes.HOME_MANAGEMENT_SCENE);
        }
    }
}