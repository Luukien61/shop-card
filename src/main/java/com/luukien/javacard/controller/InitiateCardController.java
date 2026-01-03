package com.luukien.javacard.controller;

import com.luukien.javacard.dialog.VerifyCredentialDialog;
import com.luukien.javacard.model.SecretType;
import com.luukien.javacard.utils.*;
import javafx.fxml.FXML;
import javafx.scene.control.*;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;
import javafx.scene.layout.VBox;
import javafx.stage.FileChooser;
import javafx.stage.Stage;

import java.io.File;
import java.time.LocalDate;

import static com.luukien.javacard.utils.ApplicationHelper.showAlert;

public class InitiateCardController {


    private static final boolean USE_PIN_DIALOG = false;

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

    // Thêm các thành phần mới cho PIN input
    @FXML
    private VBox pinInputContainer;
    @FXML
    private PasswordField userPinField;

    private File selectedImageFile = null;


    @FXML
    public void initialize() {
        dateField.setConverter(DateConverter.getLocalDateConverter());
        chooseImageBtn.setOnAction(e -> onChooseImage());
        finishBtn.setOnAction(e -> onFinish());
        backButton.setOnAction(e ->  handleBack());

        // Kiểm soát hiển thị PIN input dựa trên biến cấu hình
        setupPinInputVisibility();

        // Thêm validation cho PIN field nếu được hiển thị
        if (!USE_PIN_DIALOG && userPinField != null) {
            setupPinValidation();
        }
    }

    /**
     * Cấu hình hiển thị PIN input dựa trên biến USE_PIN_DIALOG
     */
    private void setupPinInputVisibility() {
        if (pinInputContainer != null) {
            pinInputContainer.setVisible(!USE_PIN_DIALOG);
            pinInputContainer.setManaged(!USE_PIN_DIALOG);
        }
    }

    /**
     * Thiết lập validation cho PIN field
     */
    private void setupPinValidation() {
        // Chỉ cho phép nhập số
        userPinField.textProperty().addListener((observable, oldValue, newValue) -> {
            if (!newValue.matches("\\d*")) {
                userPinField.setText(newValue.replaceAll("[^\\d]", ""));
            }
            // Giới hạn độ dài (4-8 chữ số)
            if (newValue.length() > 6) {
                userPinField.setText(newValue.substring(0, 6));
            }
        });

        // Thêm style khi focus
        userPinField.focusedProperty().addListener((observable, oldValue, newValue) -> {
            if (newValue) {
                userPinField.setStyle("-fx-background-color: #FFFBEB; -fx-border-color: #F59E0B; -fx-border-width: 2; -fx-border-radius: 8; -fx-background-radius: 8; -fx-padding: 10;");
            } else {
                userPinField.setStyle("-fx-background-color: #FEF3C7; -fx-border-color: #FCD34D; -fx-border-width: 2; -fx-border-radius: 8; -fx-background-radius: 8; -fx-padding: 10;");
            }
        });
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
        String username = usernameField.getText().trim();
        LocalDate birthDate = dateField.getValue();
        String phone = phoneField.getText().trim();
        String address = addressField.getText().trim();

        // Validate phone number
        if (phone.length() < 10 || phone.length() > 11) {
            showAlert("Số điện thoại không đúng định dạng (10-11 chữ số)", true);
            return;
        }

        // Get selected gender
        String genderSelected = menGender.isSelected() ? "Nam" : womenGender.isSelected() ? "Nữ" : "";

        // Validate required fields
        if (username.isEmpty() || birthDate == null || phone.isEmpty() || address.isEmpty()
                || genderSelected.isEmpty() || selectedImageFile == null) {
            showAlert("Vui lòng điền đầy đủ thông tin!", true);
            return;
        }

        // Xử lý theo chế độ PIN
        if (USE_PIN_DIALOG) {
            // Chế độ 1: Hiển thị dialog để nhập PIN
            handlePinWithDialog(username, address, phone, birthDate, genderSelected);
        } else {
            // Chế độ 2: Lấy PIN từ input trên giao diện
            handlePinWithInput(username, address, phone, birthDate, genderSelected);
        }
    }

    /**
     * Xử lý PIN bằng dialog (chế độ cũ)
     */
    private void handlePinWithDialog(String username, String address, String phone,
                                     LocalDate birthDate, String genderSelected) {
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

    /**
     * Xử lý PIN từ input trên giao diện (chế độ mới)
     */
    private void handlePinWithInput(String username, String address, String phone,
                                    LocalDate birthDate, String genderSelected) {
        String userPin = userPinField.getText().trim();

        // Validate PIN
        if (userPin.isEmpty()) {
            showAlert("Vui lòng nhập mã PIN cho thẻ mới!", true);
            userPinField.requestFocus();
            return;
        }

        if (userPin.length() !=6) {
            showAlert("Mã PIN phải có 6 chữ số!", true);
            userPinField.requestFocus();
            return;
        }

        if (!userPin.matches("\\d+")) {
            showAlert("Mã PIN chỉ được chứa chữ số!", true);
            userPinField.requestFocus();
            return;
        }

        // Xác thực Admin PIN
        VerifyCredentialDialog.show(
                SecretType.PIN,
                "Xác thực PIN Admin",
                5,
                DatabaseHelper::verifySysUserPin,
                (adminPin) -> initiateCard(username, address, phone, birthDate, genderSelected, userPin, adminPin),
                () -> showAlert("Thẻ bị khóa tạm thời!", true)
        );
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

            finalCardId = CardHelper.generate16Digits(phone);

            finalPublicKey = CardHelper.initiateCard(
                    username, address, phone,
                    userPin, adminPin,
                    selectedImageFile,
                    finalCardId
            );

            if (finalPublicKey != null) {
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
            handleBack();
        } else {
            ApplicationHelper.showAlert(
                    "Ghi thẻ thành công nhưng lưu CSDL thất bại!\n" +
                            "Thẻ vẫn hoạt động bình thường.\n" +
                            "Card ID: " + finalCardId + "\n\n" +
                            "Vui lòng báo admin để xử lý dữ liệu CSDL.",
                    true
            );
            CardHelper.clearCardData();
            handleBack();
        }
    }

    private void handleBack() {
        Stage stage = (Stage) backButton.getScene().getWindow();
        stage.close();
    }
}