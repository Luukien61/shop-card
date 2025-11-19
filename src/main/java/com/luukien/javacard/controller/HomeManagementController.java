package com.luukien.javacard.controller;

import com.luukien.javacard.model.Product;
import com.luukien.javacard.model.UserRole;
import com.luukien.javacard.screen.SceneManager;
import com.luukien.javacard.screen.Scenes;
import com.luukien.javacard.sql.SqlQueries;
import com.luukien.javacard.state.AppState;
import com.luukien.javacard.utils.ApplicationHelper;
import com.luukien.javacard.utils.DatabaseHelper;
import com.luukien.javacard.utils.DateConverter;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.fxml.FXML;
import javafx.scene.control.*;
import javafx.scene.control.cell.PropertyValueFactory;

import java.math.BigDecimal;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.util.ArrayList;
import java.util.List;

public class HomeManagementController {

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
    private TextField productNameTextField;
    @FXML
    private Button filterProductBtn;
    @FXML
    private TableView userTable;
    @FXML
    private TableColumn colUserName;
    @FXML
    private TableColumn colPhone;
    @FXML
    private TableColumn colClass;
    @FXML
    private TableColumn colAddress;
    @FXML
    private TextField phoneSearchTextField;
    @FXML
    private Button searchUserBtn;
    @FXML
    private Button addNewUserBtn;
    @FXML
    private Button logoutBtn;

    @FXML
    public void initialize() {
        orderdatePicker.setConverter(DateConverter.getLocalDateConverter());
        String role = AppState.getInstance().getCurrentUserRole();
        if (role.equals(UserRole.ADMIN.toString())) {
            addNewProductBtn.setDisable(false);
            addNewProductBtn.setVisible(true);
        }
        addNewProductBtn.setOnAction(e -> onNewProductBtnClick());

        initializeProductTab();
        initializeOrderTab();
        initializeUserTab();
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
    }

    private void initializeOrderTab() {
        orderPaneTab.setOnSelectionChanged(event -> {
            if (orderPaneTab.isSelected()) {

            }
        });
    }

    private void initializeUserTab() {
        userPaneTab.setOnSelectionChanged(event -> {
            if (userPaneTab.isSelected()) {

            }
        });
    }

    private void onNewProductBtnClick() {
        SceneManager.switchTo(Scenes.ADD_PRODUCT_SCENE);
    }

    private void handleProductTabSelected() {
        List<Product> items = loadProducts();
        if (!items.isEmpty()) {
            productData.setAll(items);
        }
    }

    private List<Product> loadProducts() {
        List<Product> items = new ArrayList<>();
        try (Connection conn = DatabaseHelper.getConnection()) {
            if (conn == null) throw new RuntimeException(ApplicationHelper.CONN_DB_MESSAGE);
            try (PreparedStatement ps = conn.prepareStatement(SqlQueries.getInstance().getGET_PRODUCTS())) {
                ResultSet rs = ps.executeQuery();

                while (rs.next()) {
                    String name = rs.getString("name");
                    String code = rs.getString("code");
                    Long id = rs.getLong("id");
                    int remain = rs.getInt("remain");
                    BigDecimal price = rs.getBigDecimal("price");
                    Product product = new Product(id, code, name, remain, price);
                    items.add(product);
                }
                System.out.println("Products found: " + items.size());
                return items;
            }
        } catch (Exception e) {
            ApplicationHelper.showAlert(e.getMessage(), true);
        }
        return items;
    }


}
