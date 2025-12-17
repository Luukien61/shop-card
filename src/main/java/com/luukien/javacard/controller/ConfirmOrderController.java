package com.luukien.javacard.controller;

import com.luukien.javacard.dialog.VerifyCredentialDialog;
import com.luukien.javacard.exception.OrderException;
import com.luukien.javacard.model.*;
import com.luukien.javacard.screen.SceneManager;
import com.luukien.javacard.screen.Scenes;
import com.luukien.javacard.service.OrderService;
import com.luukien.javacard.utils.CardHelper;
import javafx.beans.property.SimpleIntegerProperty;
import javafx.beans.property.SimpleStringProperty;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.fxml.FXML;
import javafx.scene.control.*;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;
import javafx.scene.layout.VBox;
import javafx.stage.Stage;

import javax.smartcardio.CardException;
import java.io.ByteArrayInputStream;
import java.math.BigDecimal;
import java.util.Base64;
import java.util.List;

public class ConfirmOrderController {
    @FXML
    private Label errLabel;
    @FXML
    private VBox noCardBox;
    @FXML
    private VBox cardInsertedBox;
    @FXML
    private VBox userInfoBox;
    @FXML
    private Button pinRequestBtn;

    // User Info Components
    @FXML
    private ImageView userAvatarImageView;
    @FXML
    private Label userNameLabel;
    @FXML
    private Label cardIdLabel;
    @FXML
    private Label phoneLabel;
    @FXML
    private Label addressLabel;

    @FXML
    private TableView<OrderItem> orderTableView;

    @FXML
    private TableColumn<OrderItem, String> colProductName;

    @FXML
    private TableColumn<OrderItem, Integer> colQuantity;

    @FXML
    private TableColumn<OrderItem, String> colPrice;

    @FXML
    private Label subtotalLabel;

    @FXML
    private Label discountLabel;

    @FXML
    private Label totalLabel;

    @FXML
    private Button backBtn;

    @FXML
    private Button confirmBtn;

    private UserCardInfo userCardInfo = new UserCardInfo();

    private ObservableList<OrderItem> orderItems;
    private BigDecimal subtotal = BigDecimal.ZERO;
    private BigDecimal discount = BigDecimal.ZERO;
    private BigDecimal total = BigDecimal.ZERO;
    private final OrderService orderService = OrderService.getInstance();

    @FXML
    public void initialize() {
        // Khởi tạo danh sách đơn hàng
        orderItems = FXCollections.observableArrayList();

        // Cấu hình các cột
        colProductName.setCellValueFactory(cellData ->
                new SimpleStringProperty(cellData.getValue().getProductName())
        );

        colQuantity.setCellValueFactory(cellData ->
                new SimpleIntegerProperty(cellData.getValue().getQuantity()).asObject()
        );

        colPrice.setCellValueFactory(cellData ->
                new SimpleStringProperty(cellData.getValue().getFormattedPrice())
        );

        // Gắn dữ liệu vào bảng
        orderTableView.setItems(orderItems);

        // Xử lý sự kiện nút
        backBtn.setOnAction(e -> handleBack());
        confirmBtn.setOnAction(e -> handleConfirm());
        pinRequestBtn.setOnAction(e -> onPinRequest());

        // Ẩn userInfoBox ban đầu
        userInfoBox.setVisible(false);
        userInfoBox.setManaged(false);

        checkCardInsertState();
    }

    private void onPinRequest() {
        VerifyCredentialDialog.show(
                SecretType.PIN,
                "Xác thực PIN người dùng",
                5,
                (userPin) -> {
                    try {
                        return CardHelper.verifyUserPin(userPin);
                    } catch (CardException e) {
                        showAlert("Lỗi", e.getMessage());
                    }
                    return false;
                },
                (userPin) -> {
                    try {
                        userCardInfo = orderService.getUserCardInfo(userPin);
                        // Hiển thị thông tin người dùng sau khi xác thực thành công
                        displayUserInfo();
                    } catch (Exception e) {
                        showAlert("Lỗi", e.getMessage());
                    }
                },
                () -> showAlert("Khóa thẻ", "Thẻ bị khóa tạm thời!")
        );
    }

    /**
     * Hiển thị thông tin người dùng lên UI
     */
    private void displayUserInfo() {
        if (userCardInfo == null) {
            return;
        }

        // Ẩn cardInsertedBox, hiện userInfoBox
        cardInsertedBox.setVisible(false);
        cardInsertedBox.setManaged(false);
        userInfoBox.setVisible(true);
        userInfoBox.setManaged(true);

        // Set user name
        if (userCardInfo.getUserName() != null && !userCardInfo.getUserName().isEmpty()) {
            userNameLabel.setText(userCardInfo.getUserName());
        } else {
            userNameLabel.setText("N/A");
        }

        // Set card ID
        if (userCardInfo.getCardId() != null && !userCardInfo.getCardId().isEmpty()) {
            cardIdLabel.setText(userCardInfo.getCardId());
        } else {
            cardIdLabel.setText("N/A");
        }

        // Set phone
        if (userCardInfo.getPhone() != null && !userCardInfo.getPhone().isEmpty()) {
            phoneLabel.setText(userCardInfo.getPhone());
        } else {
            phoneLabel.setText("N/A");
        }

        // Set address
        if (userCardInfo.getAddress() != null && !userCardInfo.getAddress().isEmpty()) {
            addressLabel.setText(userCardInfo.getAddress());
        } else {
            addressLabel.setText("N/A");
        }

        // Set avatar image
        if (userCardInfo.getImage() != null && !userCardInfo.getImage().isEmpty()) {
            try {
                // Giả sử image là base64 string
                byte[] imageBytes = Base64.getDecoder().decode(userCardInfo.getImage());
                Image image = new Image(new ByteArrayInputStream(imageBytes));
                userAvatarImageView.setImage(image);

                // Set style cho ImageView để bo tròn
                userAvatarImageView.setStyle("-fx-background-radius: 50; -fx-border-radius: 50;");
            } catch (Exception e) {
                System.err.println("Không thể load ảnh: " + e.getMessage());
            }
        }

    }

    /**
     * Phương thức để nhận dữ liệu giỏ hàng từ màn hình tạo đơn hàng
     */
    public void setOrderData(List<CartItem> cartItems) {
        orderItems.clear();
        subtotal = BigDecimal.ZERO;

        for (CartItem cartItem : cartItems) {
            if (!cartItem.isPlaceholder()) {
                Product product = cartItem.getProduct();
                OrderItem orderItem = new OrderItem(
                        product.getCode(),
                        product.getName(),
                        cartItem.getQuantity(),
                        cartItem.getSubPrice());
                orderItems.add(orderItem);
                subtotal = subtotal.add(cartItem.getSubPrice());
            }
        }

        // Cập nhật tổng tiền
        updateTotals();
    }

    /**
     * Cập nhật các label tổng tiền
     */
    private void updateTotals() {
        // Tính tổng
        total = subtotal.subtract(discount);

        // Cập nhật UI
        subtotalLabel.setText(String.format("%,.0f ₫", subtotal));
        discountLabel.setText(String.format("%,.0f ₫", discount));
        totalLabel.setText(String.format("%,.0f ₫", total));
    }

    /**
     * Phương thức để set giảm giá (có thể gọi từ bên ngoài)
     */
    public void setDiscount(BigDecimal discount) {
        this.discount = discount;
        updateTotals();
    }

    /**
     * Xử lý nút quay lại
     */
    private void handleBack() {

        Stage stage = (Stage) backBtn.getScene().getWindow();
        stage.close();
    }

    /**
     * Xử lý nút xác nhận đơn hàng
     */
    private void handleConfirm() {
        if (orderItems.isEmpty()) {
            showAlert("Đơn hàng trống!", "Vui lòng thêm sản phẩm vào đơn hàng.");
            return;
        }

        // Kiểm tra đã xác thực PIN chưa
        if (userCardInfo == null || userCardInfo.getUserName() == null || userCardInfo.getUserName().isEmpty()) {
            showAlert("Chưa xác thực!", "Vui lòng nhập PIN để xác thực người dùng.");
            return;
        }

        // Hiển thị xác nhận
        Alert confirmAlert = new Alert(Alert.AlertType.CONFIRMATION);
        confirmAlert.setTitle("Xác nhận đơn hàng");
        confirmAlert.setHeaderText("Xác nhận tạo đơn hàng?");
        confirmAlert.setContentText(String.format("Khách hàng: %s\nTổng tiền: %,.0f ₫",
                userCardInfo.getUserName(), total));

        processOrder();
    }

    /**
     * Xử lý tạo đơn hàng
     */
    private void processOrder() {
        try {
            orderService.createOrder(userCardInfo.getPhone(), orderItems);
            System.out.println("=== Thông tin đơn hàng ===");
            System.out.println("Khách hàng: " + userCardInfo.getUserName());
            System.out.println("Mã thẻ: " + userCardInfo.getCardId());
            System.out.println("SĐT: " + userCardInfo.getPhone());
            System.out.println("Địa chỉ: " + userCardInfo.getAddress());
            System.out.println("Tạm tính: " + subtotalLabel.getText());
            System.out.println("Giảm giá: " + discountLabel.getText());
            System.out.println("Tổng cộng: " + totalLabel.getText());
            System.out.println("Số sản phẩm: " + orderItems.size());

            orderItems.forEach(item -> {
                System.out.printf("- %s x%d = %s%n",
                        item.getProductName(),
                        item.getQuantity(),
                        item.getFormattedPrice()
                );
            });

            showAlert("Thành công!", "Đơn hàng đã được tạo thành công!");

            handleBack();
        } catch (OrderException e) {
            showAlert("Lỗi", e.getMessage());
        }
    }

    private void showAlert(String title, String message) {
        Alert alert = new Alert(Alert.AlertType.INFORMATION);
        alert.setTitle(title);
        alert.setHeaderText(null);
        alert.setContentText(message);
        alert.showAndWait();
    }

    private void checkCardInsertState() {
        Boolean isCardVerified = false;
        try {
            isCardVerified = orderService.isCardVerified();
            if (!isCardVerified) {
                errLabel.setText("Thẻ không xác minh!");
            }
        } catch (Exception e) {
            errLabel.setText(e.getMessage());
        }

        noCardBox.setVisible(!isCardVerified);
        noCardBox.setManaged(!isCardVerified);

        cardInsertedBox.setVisible(isCardVerified);
        cardInsertedBox.setManaged(isCardVerified);

        userInfoBox.setVisible(false);
        userInfoBox.setManaged(false);
    }
}