package com.luukien.javacard.controller;

import com.luukien.javacard.model.CartItem;
import com.luukien.javacard.model.Product;
import com.luukien.javacard.screen.SceneManager;
import com.luukien.javacard.screen.Scenes;
import com.luukien.javacard.service.ProductService;
import javafx.beans.property.SimpleIntegerProperty;
import javafx.beans.property.SimpleStringProperty;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.fxml.FXML;
import javafx.scene.control.*;
import javafx.scene.input.MouseEvent;
import javafx.scene.layout.AnchorPane;
import lombok.Getter;
import lombok.Setter;

import java.io.IOException;
import java.math.BigDecimal;
import java.util.List;

public class CreateOrderController {

    @FXML
    private Button backButton;
    @FXML
    private AnchorPane anchor;

    @FXML
    private TableView<CartItem> tableView;

    @FXML
    private TableColumn<CartItem, String> colProductCode;

    @FXML
    private TableColumn<CartItem, String> colProductName;

    @FXML
    private TableColumn<CartItem, Integer> colQuantity;

    @FXML
    private TableColumn<CartItem, String> colUnitPrice;

    @FXML
    private TableColumn<CartItem, String> colSubPrice;

    @FXML
    private TableColumn<CartItem, Void> colDelete;

    @FXML
    private Label totalPriceLabel;

    @FXML
    private Button paymentBtn;

    @FXML
    private Button cancelBtn;

    private ObservableList<CartItem> cartItems;
    private CartItem placeholderItem;
    private final ProductService productService = ProductService.getInstance();



    @FXML
    public void initialize() {
        // Khởi tạo danh sách giỏ hàng
        cartItems = FXCollections.observableArrayList();

        // Tạo placeholder item
        Product placeholderProduct = new Product(null, "", "➕ Thêm sản phẩm", 0, BigDecimal.ZERO);
        placeholderItem = new CartItem(placeholderProduct, 0);
        placeholderItem.setPlaceholder(true);

        // Bật chế độ editable cho table và cột quantity
        tableView.setEditable(true);
        colQuantity.setEditable(true);


        colProductCode.setCellValueFactory(cellData ->
                new SimpleStringProperty(
                        cellData.getValue().getProduct().getCode()
                )
        );

        colProductName.setCellValueFactory(cellData ->
                new SimpleStringProperty(
                        cellData.getValue().getProduct().getName()
                )
        );


        colQuantity.setCellValueFactory(cellData ->
                new SimpleIntegerProperty(
                        cellData.getValue().getQuantity()
                ).asObject()
        );


        colQuantity.setCellFactory(tc -> new TableCell<>() {
            private final TextField textField = new TextField();

            {
                textField.setOnAction(e -> {
                    try {
                        int newQuantity = Integer.parseInt(textField.getText());
                        CartItem item = getTableRow().getItem();
                        if (item != null && !item.isPlaceholder()) {
                            if (newQuantity > 0 && newQuantity <= item.getProduct().getRemain()) {
                                item.setQuantity(newQuantity);
                                updateTotalPrice();
                                tableView.refresh();
                            } else {
                                showAlert("Số lượng không hợp lệ! (Max: " + item.getProduct().getRemain() + ")");
                            }
                        }
                        commitEdit(item != null ? item.getQuantity() : 0);
                    } catch (NumberFormatException ex) {
                        showAlert("Số lượng phải là số nguyên!");
                        cancelEdit();
                    }
                });

                // Xử lý khi mất focus
                textField.focusedProperty().addListener((obs, wasFocused, isNowFocused) -> {
                    if (wasFocused && !isNowFocused && isEditing()) {
                        try {
                            int newQuantity = Integer.parseInt(textField.getText());
                            CartItem item = getTableRow().getItem();
                            if (item != null && !item.isPlaceholder()) {
                                if (newQuantity > 0 && newQuantity <= item.getProduct().getRemain()) {
                                    item.setQuantity(newQuantity);
                                    updateTotalPrice();
                                }
                            }
                        } catch (NumberFormatException ex) {
                            // Ignore
                        }
                        cancelEdit();
                    }
                });
            }

            @Override
            protected void updateItem(Integer item, boolean empty) {
                super.updateItem(item, empty);
                if (empty || getTableRow().getItem() == null) {
                    setText(null);
                    setGraphic(null);
                } else {
                    CartItem cartItem = getTableRow().getItem();
                    if (cartItem.isPlaceholder()) {
                        setText(null);
                        setGraphic(null);
                    } else {
                        if (isEditing()) {
                            textField.setText(String.valueOf(item));
                            setGraphic(textField);
                            setText(null);
                        } else {
                            setText(String.valueOf(item));
                            setGraphic(null);
                        }
                    }
                }
            }

            @Override
            public void startEdit() {
                super.startEdit();
                CartItem item = getTableRow().getItem();
                if (item != null && !item.isPlaceholder()) {
                    textField.setText(String.valueOf(getItem()));
                    setGraphic(textField);
                    setText(null);
                    textField.selectAll();
                    textField.requestFocus();
                }
            }

            @Override
            public void cancelEdit() {
                super.cancelEdit();
                setText(getItem() != null ? String.valueOf(getItem()) : "");
                setGraphic(null);
            }
        });

        colUnitPrice.setCellValueFactory(cellData ->
                new SimpleStringProperty(
                        cellData.getValue().isPlaceholder() ? "" :
                                cellData.getValue().getProduct().getFormattedPrice()
                )
        );

        colSubPrice.setCellValueFactory(cellData ->
                new SimpleStringProperty(
                        cellData.getValue().getFormattedSubPrice()
                )
        );

        // Thêm nút xóa cho mỗi row
        colDelete.setCellFactory(param -> new TableCell<>() {
            private final Button deleteBtn = new Button("Xóa");

            {
                deleteBtn.setStyle("-fx-background-color: #ff6b6b; -fx-text-fill: white; -fx-cursor: hand;");
                deleteBtn.setOnAction(event -> {
                    CartItem item = getTableView().getItems().get(getIndex());
                    if (!item.isPlaceholder()) {
                        deleteCartItem(item);
                    }
                });
            }

            @Override
            protected void updateItem(Void item, boolean empty) {
                super.updateItem(item, empty);
                if (empty || getIndex() >= getTableView().getItems().size()) {
                    setGraphic(null);
                } else {
                    CartItem cartItem = getTableView().getItems().get(getIndex());
                    setGraphic(cartItem.isPlaceholder() ? null : deleteBtn);
                }
            }
        });

        // Style cho placeholder row
        tableView.setRowFactory(tv -> new TableRow<>() {
            @Override
            protected void updateItem(CartItem item, boolean empty) {
                super.updateItem(item, empty);
                if (item != null && item.isPlaceholder()) {
                    setStyle("-fx-background-color: #f8f9fa; -fx-cursor: hand;");
                } else {
                    setStyle("");
                }
            }
        });

        // Xử lý click vào placeholder row để mở dialog chọn sản phẩm
        tableView.setOnMouseClicked(event -> {
            CartItem selected = tableView.getSelectionModel().getSelectedItem();
            if (selected != null && selected.isPlaceholder()) {
                showProductSelectionDialog();
            }
        });

        // Load dữ liệu
        loadData();

        // Xử lý các button
        paymentBtn.setOnAction(e -> handlePayment());
        cancelBtn.setOnAction(e -> handleCancel());
        backButton.setOnAction(e-> SceneManager.switchTo(Scenes.HOME_MANAGEMENT_SCENE));
    }

    private void showProductSelectionDialog() {
        ProductSelectionDialog dialog = new ProductSelectionDialog();
        Product selectedProduct = dialog.showAndWait();

        if (selectedProduct != null) {
            addProductToCart(selectedProduct, 1);
        }

        // Xóa selection và focus sau khi đóng dialog
        tableView.getSelectionModel().clearSelection();
        tableView.getFocusModel().focus(-1);
    }

    private void loadData() {
        // Luôn thêm placeholder ở cuối
        cartItems.add(placeholderItem);
        tableView.setItems(cartItems);

        updateTotalPrice();
    }

    private void addProductToCart(Product product, int quantity) {
        // Kiểm tra sản phẩm đã có trong giỏ chưa
        CartItem existingItem = cartItems.stream()
                .filter(item -> !item.isPlaceholder() &&
                        item.getProduct().getId().equals(product.getId()))
                .findFirst()
                .orElse(null);

        if (existingItem != null) {
            // Tăng số lượng
            int newQuantity = existingItem.getQuantity() + quantity;
            if (newQuantity <= product.getRemain()) {
                existingItem.setQuantity(newQuantity);
                tableView.refresh();
            } else {
                showAlert("Vượt quá số lượng tồn kho! (Max: " + product.getRemain() + ")");
            }
        } else {
            CartItem newItem = new CartItem(product, quantity);
            cartItems.add(cartItems.size() - 1, newItem);
        }

        updateTotalPrice();
    }

    private void deleteCartItem(CartItem item) {
        cartItems.remove(item);
        updateTotalPrice();
    }

    private void updateTotalPrice() {
        BigDecimal total = cartItems.stream()
                .filter(item -> !item.isPlaceholder())
                .map(CartItem::getSubPrice)
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        totalPriceLabel.setText(String.format("%,.0f ₫", total));
    }

    private void handlePayment() {

        if (cartItems.size() <= 1) { // Chỉ có placeholder
            showAlert("Giỏ hàng trống!");
            return;
        }
        List<CartItem> validCartItems = cartItems.stream()
                .filter(item -> !item.isPlaceholder())
                .toList();

        SceneManager.showModal(
                Scenes.CONFIRM_ORDER_SCENE,
                (ConfirmOrderController controller) -> controller.setOrderData(validCartItems)
        );
    }

    private void handleCancel() {
        cartItems.clear();
        cartItems.add(placeholderItem);
        updateTotalPrice();
    }

    private void showAlert(String message) {
        Alert alert = new Alert(Alert.AlertType.WARNING);
        alert.setTitle("Thông báo");
        alert.setHeaderText(null);
        alert.setContentText(message);
        alert.showAndWait();
    }

    @FXML
    private void onAnchorClicked(MouseEvent event) {
        if (event.getTarget() == anchor) {
            anchor.requestFocus();
        }
    }


}

