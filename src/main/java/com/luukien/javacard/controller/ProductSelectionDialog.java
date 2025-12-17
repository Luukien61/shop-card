package com.luukien.javacard.controller;

import com.luukien.javacard.model.Product;
import com.luukien.javacard.service.ProductService;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.fxml.FXML;
import javafx.geometry.Insets;
import javafx.scene.Scene;
import javafx.scene.control.*;
import javafx.scene.control.cell.PropertyValueFactory;
import javafx.scene.layout.AnchorPane;
import javafx.scene.text.Font;
import javafx.stage.Modality;
import javafx.stage.Stage;

import java.util.List;

public class ProductSelectionDialog {


    private Stage dialog;
    private Product selectedProduct;
    @FXML
    private TableView<Product> productTable;

    @FXML
    private TextField productNameTextField;
    @FXML
    private Button filterBtn;

    @FXML
    private TableColumn<Product, Long> colId;

    @FXML
    private TableColumn<Product, String> colProductName;

    @FXML
    private TableColumn<Product, String> colProductCode;

    @FXML
    private TableColumn<Product, String> colPrice;

    @FXML
    private TableColumn<Product, Integer> colRemain;

    private final ObservableList<Product> productData = FXCollections.observableArrayList();

    // Service để load sản phẩm
    private final ProductService productService = ProductService.getInstance();

    public ProductSelectionDialog() {
        createDialog();
    }

    private void createDialog() {
        dialog = new Stage();
        dialog.initModality(Modality.APPLICATION_MODAL);
        dialog.setTitle("Chọn sản phẩm");
        dialog.setWidth(650);
        dialog.setHeight(500);

        AnchorPane root = new AnchorPane();
        root.setStyle("-fx-background-color: #FFFFFF;");

        // Header Label
        Label headerLabel = new Label("Chọn sản phẩm");
        headerLabel.setLayoutX(26);
        headerLabel.setLayoutY(30);
        headerLabel.setPrefSize(597, 32);
        headerLabel.setStyle("-fx-background-color: #0866FF; -fx-background-radius: 8;");
        headerLabel.setTextFill(javafx.scene.paint.Color.WHITE);
        headerLabel.setFont(Font.font("System Bold", 14));
        headerLabel.setPadding(new Insets(0, 0, 0, 16));

        // Filter TextField
        productNameTextField = new TextField();
        productNameTextField.setLayoutX(33);
        productNameTextField.setLayoutY(90);
        productNameTextField.setPrefSize(267, 29);
        productNameTextField.setPromptText("Tên, mã sản phẩm");
        productNameTextField.setFont(Font.font(14));

        // Thêm event khi nhấn Enter trong TextField
        productNameTextField.setOnAction(e -> filterProductByNameOrCode());

        // Filter Button
        filterBtn = new Button("Lọc");
        filterBtn.setLayoutX(328);
        filterBtn.setLayoutY(90);
        filterBtn.setPrefSize(63, 25);
        filterBtn.setFont(Font.font(14));
        filterBtn.setOnAction(e -> filterProductByNameOrCode());

        // Product Table
        productTable = new TableView<>();
        productTable.setLayoutX(33);
        productTable.setLayoutY(145);
        productTable.setPrefSize(590, 250);
        productTable.setItems(productData);

        // Columns
        TableColumn<Product, Long> colProductId = new TableColumn<>("ID");
        colProductId.setPrefWidth(40);
        colProductId.setCellValueFactory(new PropertyValueFactory<>("id"));

        TableColumn<Product, String> colProductName = new TableColumn<>("Tên sản phẩm");
        colProductName.setPrefWidth(174);
        colProductName.setCellValueFactory(new PropertyValueFactory<>("name"));

        TableColumn<Product, String> colProductCode = new TableColumn<>("Mã sản phẩm");
        colProductCode.setPrefWidth(145);
        colProductCode.setCellValueFactory(new PropertyValueFactory<>("code"));

        TableColumn<Product, String> colPrice = new TableColumn<>("Giá");
        colPrice.setPrefWidth(133);
        colPrice.setCellValueFactory(new PropertyValueFactory<>("formattedPrice"));

        TableColumn<Product, Integer> colRemain = new TableColumn<>("Số lượng kho");
        colRemain.setPrefWidth(96);
        colRemain.setCellValueFactory(new PropertyValueFactory<>("remain"));

        productTable.getColumns().addAll(colProductId, colProductName, colProductCode, colPrice, colRemain);

        // Style cho tên sản phẩm - clickable
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

                    label.setOnMouseEntered(e ->
                            label.setStyle("-fx-underline: true; -fx-text-fill: #0055aa; -fx-cursor: hand;")
                    );

                    label.setOnMouseExited(e ->
                            label.setStyle("-fx-underline: true; -fx-text-fill: #0066cc; -fx-cursor: hand;")
                    );

                    label.setOnMouseClicked(e -> {
                        Product product = getTableRow().getItem();
                        if (product != null && e.getClickCount() == 2) {
                            selectProduct(product);
                        }
                    });

                    setGraphic(label);
                }
            }
        });

        // Double click vào row để chọn
        productTable.setOnMouseClicked(event -> {
            if (event.getClickCount() == 2) {
                Product product = productTable.getSelectionModel().getSelectedItem();
                if (product != null) {
                    selectProduct(product);
                }
            }
        });

        // Buttons
        Button selectBtn = new Button("Chọn");
        selectBtn.setLayoutX(430);
        selectBtn.setLayoutY(420);
        selectBtn.setPrefSize(90, 35);
        selectBtn.setStyle("-fx-background-color: #0866FF;");
        selectBtn.setTextFill(javafx.scene.paint.Color.WHITE);
        selectBtn.setFont(Font.font("System Bold", 14));
        selectBtn.setOnAction(e -> {
            Product product = productTable.getSelectionModel().getSelectedItem();
            if (product != null) {
                selectProduct(product);
            } else {
                showAlert("Vui lòng chọn sản phẩm!");
            }
        });

        Button cancelBtn = new Button("Hủy");
        cancelBtn.setLayoutX(130);
        cancelBtn.setLayoutY(420);
        cancelBtn.setPrefSize(90, 35);
        cancelBtn.setStyle("-fx-background-color: #ff0000;");
        cancelBtn.setTextFill(javafx.scene.paint.Color.WHITE);
        cancelBtn.setFont(Font.font("System Bold", 14));
        cancelBtn.setOnAction(e -> dialog.close());

        root.getChildren().addAll(
                headerLabel,
                productNameTextField,
                filterBtn,
                productTable,
                selectBtn,
                cancelBtn
        );

        Scene scene = new Scene(root);
        dialog.setScene(scene);
    }

    private void selectProduct(Product product) {
        if (product.getRemain() <= 0) {
            showAlert("Sản phẩm đã hết hàng!");
            return;
        }
        this.selectedProduct = product;
        dialog.close();
    }

    private void filterProductByNameOrCode() {
        String filter = productNameTextField.getText().trim();
        if (filter.isBlank()) {
            loadProducts();
        } else {
            List<Product> items = productService.filterProducts(filter);
            productData.clear();
            if (!items.isEmpty()) {
                productData.setAll(items);
            }
        }
    }

    private void loadProducts() {
        List<Product> items = productService.loadProducts();
        productData.clear();
        if (!items.isEmpty()) {
            productData.setAll(items);
        }
    }

    private void showAlert(String message) {
        Alert alert = new Alert(Alert.AlertType.WARNING);
        alert.setTitle("Thông báo");
        alert.setHeaderText(null);
        alert.setContentText(message);
        alert.showAndWait();
    }

    public Product showAndWait() {
        selectedProduct = null;
        loadProducts();
        dialog.showAndWait();
        return selectedProduct;
    }
}