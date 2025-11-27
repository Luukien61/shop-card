package com.luukien.javacard.controller;

import com.luukien.javacard.dialog.UpdateCredentialDialog;
import com.luukien.javacard.model.Product;
import com.luukien.javacard.model.SecretType;
import com.luukien.javacard.model.User;
import com.luukien.javacard.model.UserRole;
import com.luukien.javacard.screen.SceneManager;
import com.luukien.javacard.screen.Scenes;
import com.luukien.javacard.service.AccountService;
import com.luukien.javacard.service.ProductService;
import com.luukien.javacard.service.UserService;
import com.luukien.javacard.state.AppState;
import com.luukien.javacard.utils.ApplicationHelper;
import com.luukien.javacard.utils.Argon2KeyDerivation;
import com.luukien.javacard.dialog.VerifyCredentialDialog;
import com.luukien.javacard.utils.DateConverter;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.concurrent.Task;
import javafx.fxml.FXML;
import javafx.scene.control.*;
import javafx.scene.control.cell.PropertyValueFactory;

import java.util.List;

import static com.luukien.javacard.utils.EmailHelper.generateOTP;
import static com.luukien.javacard.utils.EmailHelper.sendEmail;

public class HomeManagementController {

    @FXML
    private Label emailLabel;
    @FXML
    private Button updatePasswordBtn;
    @FXML
    private Label pinLabel;
    @FXML
    private Label pinPhLabel;
    @FXML
    private Button viewPinBtn;
    @FXML
    private Tab orderPaneTab;
    @FXML
    private Tab productPaneTab;
    @FXML
    private Tab userPaneTab;
    @FXML
    private TabPane tabPane;
    @FXML
    private Button createOrderBtn;
    @FXML
    private DatePicker orderdatePicker;
    @FXML
    private TableView orderTable;
    @FXML
    private TableColumn colOrderIndex;
    @FXML
    private TableColumn colOrderId;
    @FXML
    private TableColumn colUserId;
    @FXML
    private TableColumn colTotalPrice;
    @FXML
    private TableColumn colTime;
    @FXML
    private TableColumn colDetail;
    @FXML
    private Button filterOrderBtn;
    @FXML
    private TableView<Product> productTable;
    @FXML
    private TableColumn<Product, Long> colProductId;

    @FXML
    private TableColumn<Product, String> colProductName;

    @FXML
    private TableColumn<Product, String> colProductCode;

    @FXML
    private TableColumn<Product, String> colPrice;

    @FXML
    private TableColumn<Product, Integer> colRemain;

    private final ObservableList<Product> productData = FXCollections.observableArrayList();
    @FXML
    private Button addNewProductBtn;
    @FXML
    private TextField productFilterTextField;
    @FXML
    private Button filterProductBtn;


    @FXML
    private TableView<User> userTable;
    @FXML
    private TableColumn<User, String> colUserName;
    @FXML
    private TableColumn<User, String> colPhone;
    @FXML
    private TableColumn<User, String> colClass;
    @FXML
    private TableColumn<User, String> colAddress;

    private final ObservableList<User> userData = FXCollections.observableArrayList();


    @FXML
    private TextField userFilterTextField;
    @FXML
    private Button searchUserBtn;
    @FXML
    private Button addNewUserBtn;
    @FXML
    private Button logoutBtn;


    private final ProductService productService = ProductService.getInstance();
    private final UserService userService = UserService.getInstance();

    @FXML
    public void initialize() {
        orderdatePicker.setConverter(DateConverter.getLocalDateConverter());

        String role = AppState.getInstance().getCurrentUserRole();
        if (role.equals(UserRole.ADMIN.toString())) {
            addNewProductBtn.setDisable(false);
            addNewProductBtn.setVisible(true);
            addNewUserBtn.setDisable(false);
            addNewUserBtn.setVisible(true);
        }
        addNewProductBtn.setOnAction(e -> onNewProductBtnClick());
        addNewUserBtn.setOnAction(e -> onNewUserBtnClick());

        initializeProductTab();
        initializeOrderTab();
        initializeUserTab();
        initializeAccountTab();
    }

    private void initializeProductTab() {
        productTable.setItems(productData);

        colProductId.setCellValueFactory(new PropertyValueFactory<>("id"));
        colProductName.setCellValueFactory(new PropertyValueFactory<>("name"));
        colProductCode.setCellValueFactory(new PropertyValueFactory<>("code"));
        colPrice.setCellValueFactory(new PropertyValueFactory<>("formattedPrice"));
        colRemain.setCellValueFactory(new PropertyValueFactory<>("remain"));

        colProductName.setCellFactory(tc -> new TableCell<>() {
            private final Label label = new Label();

            @Override
            protected void updateItem(String item, boolean empty) {
                super.updateItem(item, empty);
                if (empty || item == null) {
                    setGraphic(null);
                    setText(null);
                } else {
                    label.setText(item);
                    label.setStyle("-fx-underline: true; -fx-text-fill: #0066cc; -fx-cursor: hand;");

                    label.setOnMouseEntered(e -> label.setStyle("-fx-underline: true; -fx-text-fill: #0055aa; -fx-cursor: hand;"));
                    label.setOnMouseExited(e -> label.setStyle("-fx-underline: true; -fx-text-fill: #0066cc; -fx-cursor: hand;"));

                    label.setOnMouseClicked(e -> {
                        Product product = getTableRow().getItem();
                        if (product != null && e.getClickCount() == 1) {
                            AppState.getInstance().setCurrentProduct(product);
                            SceneManager.switchTo(Scenes.PRODUCT_DETAIL_SCENE);
                        }
                    });

                    setGraphic(label);
                }
            }
        });


        productPaneTab.setOnSelectionChanged(event -> {
            if (productPaneTab.isSelected()) {
                handleProductTabSelected();
            }
        });

        filterProductBtn.setOnAction(e -> filterProductByNameOrCode());
    }

    private void initializeOrderTab() {
        orderPaneTab.setOnSelectionChanged(event -> {
            if (orderPaneTab.isSelected()) {

            }
        });
    }

    private void initializeUserTab() {
        userTable.setItems(userData);
        colUserName.setCellValueFactory(new PropertyValueFactory<>("userName"));
        colPhone.setCellValueFactory(new PropertyValueFactory<>("phone"));
        colAddress.setCellValueFactory(new PropertyValueFactory<>("address"));
        colClass.setCellValueFactory(new PropertyValueFactory<>("memberTier"));

        colUserName.setCellFactory(tc -> new TableCell<>() {
            private final Label label = new Label();

            @Override
            protected void updateItem(String item, boolean empty) {
                super.updateItem(item, empty);
                if (empty || item == null) {
                    setGraphic(null);
                    setText(null);
                } else {
                    label.setText(item);
                    label.setStyle("-fx-underline: true; -fx-text-fill: #0066cc; -fx-cursor: hand;");

                    label.setOnMouseEntered(e -> label.setStyle("-fx-underline: true; -fx-text-fill: #0055aa; -fx-cursor: hand;"));
                    label.setOnMouseExited(e -> label.setStyle("-fx-underline: true; -fx-text-fill: #0066cc; -fx-cursor: hand;"));

                    label.setOnMouseClicked(e -> {
                        User user = getTableRow().getItem();
                        if (user != null && e.getClickCount() == 1) {
                            AppState.getInstance().setCurrentClientPhone(user.getPhone());
                            SceneManager.switchTo(Scenes.USER_INFO_SCENE);
                        }
                    });

                    setGraphic(label);
                }
            }
        });


        userPaneTab.setOnSelectionChanged(event -> {
            if (userPaneTab.isSelected()) {
                List<User> items = userService.loadUsers();
                if (!items.isEmpty()) {
                    userData.setAll(items);
                }
            }
        });
    }

    private void onNewProductBtnClick() {
        SceneManager.switchTo(Scenes.ADD_PRODUCT_SCENE);
    }

    private void onNewUserBtnClick() {
        SceneManager.switchTo(Scenes.INITIAL_CARD_SCENE);
    }

    private void handleProductTabSelected() {
        List<Product> items = productService.loadProducts();
        if (!items.isEmpty()) {
            productData.setAll(items);
        }
    }

    private void filterProductByNameOrCode() {
        String filter = productFilterTextField.getText().trim();
        if (filter.isBlank()) {
            handleProductTabSelected();
        } else {
            List<Product> items = productService.filterProducts(filter);
            productData.clear();
            if (!items.isEmpty()) {
                productData.setAll(items);
            }
        }
    }

    private void initializeAccountTab() {
        boolean isAdmin = AppState.getInstance().isAdminMode();
        String currentUserEmail = AppState.getInstance().getCurrentUserEmail();
        emailLabel.setText(currentUserEmail);
        pinLabel.setVisible(isAdmin);
        pinPhLabel.setVisible(isAdmin);
        viewPinBtn.setDisable(!isAdmin);
        viewPinBtn.setVisible(isAdmin);
        viewPinBtn.setOnAction(e -> {
            VerifyCredentialDialog.show(
                    SecretType.PASSWORD,
                    "Nhập mật khẩu để tiếp tục",
                    5,
                    (password) -> AccountService.verifyPassword(password, currentUserEmail),
                    (password) -> {
                        String otp = generateOTP(6);
                        Task<Void> emailTask = new Task<>() {
                            @Override
                            protected Void call() {
                                sendEmail(currentUserEmail, otp);
                                return null;
                            }
                        };
                        new Thread(emailTask).start();

                        VerifyCredentialDialog.show(
                                SecretType.TWO_FACTOR,
                                "Nhập OTP đã được gửi về email của bạn",
                                3,
                                (code) -> code.equals(otp),
                                (code) -> {
                                    String[] data = AccountService.getEncryptedKeyAndPin(currentUserEmail);
                                    if (data == null) {
                                        ApplicationHelper.showAlert("Không tìm thấy người dùng", true);
                                        return;
                                    }
                                    String encryptedPin = data[0];
                                    String encryptedMasterKey = data[1];
                                    try {
                                        String pin = Argon2KeyDerivation.decryptData(encryptedPin, encryptedMasterKey, password);
                                        ApplicationHelper.showAlert(
                                                "Ghi nhớ kỹ mã PIN của bạn!\n\nPIN: " + pin,
                                                false
                                        );
                                    } catch (Exception ex) {
                                        ApplicationHelper.showAlert("Không thể giải mã", true);
                                    }
                                },
                                null
                        );
                    },
                    null
            );
        });
        updatePasswordBtn.setOnAction(e -> {
            UpdateCredentialDialog.show(
                    SecretType.PASSWORD,
                    null,
                    null,
                    (oldPass, newPass) -> AccountService.updatePassword(oldPass,newPass,currentUserEmail)
            );
        });
    }
}
