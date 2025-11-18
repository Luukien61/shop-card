package com.luukien.javacard.controller;

import com.luukien.javacard.utils.DateConverter;
import javafx.fxml.FXML;
import javafx.scene.control.*;

public class HomeManagementController {
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
    private TableView productTable;
    @FXML
    private TableColumn colProductId;
    @FXML
    private TableColumn colProductName;
    @FXML
    private TableColumn colProductCode;
    @FXML
    private TableColumn colPrice;
    @FXML
    private TableColumn colRemain;
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

    }

}
