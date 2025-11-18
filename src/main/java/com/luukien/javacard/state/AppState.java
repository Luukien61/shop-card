package com.luukien.javacard.state;

import com.luukien.javacard.model.OrderItem;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import lombok.Getter;

@Getter
public class AppState {
    @Getter
    private static final AppState instance = new AppState();

    private final ObservableList<OrderItem> currentOrders = FXCollections.observableArrayList();

    private String currentUserRole;
    private String currentUserEmail;

    private AppState() {}


    public void addOrderItem(OrderItem item) {
        currentOrders.add(item);
    }

    public void clearOrders() {
        currentOrders.clear();
    }

    public void setCurrentUser(String email, String role) {
        this.currentUserEmail = email;
        this.currentUserRole = role;
    }

    public void logout() {
        currentUserEmail = null;
        currentUserRole = null;
        clearOrders();
    }
}
