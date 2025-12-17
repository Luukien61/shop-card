package com.luukien.javacard.controller;

import com.luukien.javacard.model.Order;
import com.luukien.javacard.model.OrderItem;
import com.luukien.javacard.service.OrderService;
import javafx.beans.property.SimpleStringProperty;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.fxml.FXML;
import javafx.scene.control.Label;
import javafx.scene.control.TableColumn;
import javafx.scene.control.TableView;
import javafx.scene.control.cell.PropertyValueFactory;
import java.math.BigDecimal;
import java.time.format.DateTimeFormatter;
import java.util.List;

public class OrderDetailController {

    @FXML
    private TableView<OrderItem> orderTableView;

    @FXML
    private TableColumn<OrderItem, String> colProductName;

    @FXML
    private TableColumn<OrderItem, Integer> colQuantity;

    @FXML
    private TableColumn<OrderItem, String> colPrice;

    @FXML
    private Label orderCodeLabel;

    @FXML
    private Label userInfoLabel;

    @FXML
    private Label orderTimeLabel;

    @FXML
    private Label totalLabel;

    private Order order;

    private ObservableList<OrderItem> orderItems;

    private final OrderService orderService = OrderService.getInstance();

    @FXML
    public void initialize() {
        // Khởi tạo danh sách đơn hàng
        orderItems = FXCollections.observableArrayList();
        orderTableView.setItems(orderItems);

        // ===== Table columns =====
        colProductName.setCellValueFactory(
                new PropertyValueFactory<>("productName")
        );

        colQuantity.setCellValueFactory(
                new PropertyValueFactory<>("quantity")
        );

        colPrice.setCellValueFactory(cellData -> {
            OrderItem item = cellData.getValue();
            BigDecimal subTotal =
                    item.getPrice().multiply(BigDecimal.valueOf(item.getQuantity()));
            return new SimpleStringProperty(
                    String.format("%,.0f ₫", subTotal)
            );
        });
    }




    public void setUp(Order order) {
        this.order = order;
        loadOrderInfo();
        loadOrderItems();
    }

    private void loadOrderInfo() {

        orderCodeLabel.setText(order.getCode());

        userInfoLabel.setText( order.getUserPhone());

        // Time
        DateTimeFormatter formatter =
                DateTimeFormatter.ofPattern(" HH:mm dd/MM/yyyy");

        orderTimeLabel.setText(
                order.getCreateAt().format(formatter)
        );
    }

    // ===== Load items =====
    private void loadOrderItems() {

        List<OrderItem> items =
                orderService.loadOrderItemsByOrderCode(order.getCode());

        orderItems.setAll(items);

        // Total
        BigDecimal total = items.stream()
                .map(i -> i.getPrice().multiply(BigDecimal.valueOf(i.getQuantity())))
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        totalLabel.setText(String.format("%,.0f ₫", total));
    }

}
