package com.luukien.javacard.controller;

import com.luukien.javacard.model.Product;
import com.luukien.javacard.screen.SceneManager;
import com.luukien.javacard.screen.Scenes;
import com.luukien.javacard.sql.SqlQueries;
import com.luukien.javacard.state.AppState;
import com.luukien.javacard.utils.ApplicationHelper;
import com.luukien.javacard.utils.DatabaseHelper;
import javafx.fxml.FXML;
import javafx.scene.control.Button;
import javafx.scene.control.TextField;

import java.math.BigDecimal;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;

public class ProductDetailController {
    @FXML
    private TextField nameTextfield;
    @FXML
    private TextField codeTextfield;
    @FXML
    private TextField remainTextfield;
    @FXML
    private TextField priceTextfield;
    @FXML
    private Button cancelBtn;
    @FXML
    private Button updateBtn;
    @FXML
    private Button backButton;
    @FXML
    private Button doneBtn;
    private Product product;


    @FXML
    private void initialize() {
        Long productId = AppState.getInstance().getCurrentProduct().getId();
        getCurrentProduct(productId);
        nameTextfield.setText(product.getName());
        codeTextfield.setText(product.getCode());
        priceTextfield.setText(String.valueOf(product.getPrice()));
        remainTextfield.setText(String.valueOf(product.getRemain()));
        setEditingMode(false);
        updateBtn.setOnAction(actionEvent -> onUpdateBtnClick());
        cancelBtn.setOnAction(e -> cancelEditing());
        doneBtn.setOnAction(e -> saveChanges());
        backButton.setOnAction(e -> SceneManager.switchTo(Scenes.HOME_MANAGEMENT_SCENE));
    }

    private void getCurrentProduct(Long productId) {
        try (Connection conn = DatabaseHelper.getConnection()) {
            if (conn == null) throw new RuntimeException(ApplicationHelper.CONN_DB_MESSAGE);
            try (PreparedStatement ps = conn.prepareStatement(SqlQueries.getInstance().getGET_PRODUCT_BY_ID())) {
                ps.setLong(1, productId);
                ResultSet rs = ps.executeQuery();

                if (rs.next()) {
                    String name = rs.getString("name");
                    String code = rs.getString("code");
                    Long id = rs.getLong("id");
                    int remain = rs.getInt("remain");
                    BigDecimal price = rs.getBigDecimal("price");
                    product = new Product(id, code, name, remain, price);
                }
            }
        } catch (Exception e) {
            ApplicationHelper.showAlert(e.getMessage(), true);
        }
    }

    private void onUpdateBtnClick() {
        setEditingMode(true);
    }

    private void setEditingMode(boolean editing) {
        nameTextfield.setDisable(!editing);
        codeTextfield.setDisable(!editing);
        remainTextfield.setDisable(!editing);
        priceTextfield.setDisable(!editing);

        doneBtn.setVisible(editing);
        doneBtn.setDisable(!editing);

        cancelBtn.setVisible(editing);
        cancelBtn.setDisable(!editing);

        updateBtn.setVisible(!editing);
        updateBtn.setDisable(editing);

    }

    private void cancelEditing() {
        nameTextfield.setText(product.getName());
        codeTextfield.setText(product.getCode());
        remainTextfield.setText(String.valueOf(product.getRemain()));
        priceTextfield.setText(product.getFormattedPrice());

        setEditingMode(false);
    }

    private void saveChanges() {
        try {

            String name = nameTextfield.getText().trim();
            String code = codeTextfield.getText().trim();
            int remain = Integer.parseInt(remainTextfield.getText().trim());
            BigDecimal price = new BigDecimal(priceTextfield.getText().trim());

            try (Connection conn = DatabaseHelper.getConnection()) {
                assert conn != null;
                try (PreparedStatement ps = conn.prepareStatement(SqlQueries.getInstance().getUPDATE_PRODUCT_BY_ID())) {

                    ps.setString(1, name);
                    ps.setString(2, code);
                    ps.setBigDecimal(3, price);
                    ps.setInt(4, remain);
                    ps.setLong(5, product.getId());

                    int rows = ps.executeUpdate();
                    if (rows > 0) {
                        ApplicationHelper.showAlert("Cập nhật sản phẩm thành công!", false);
                        product.setName(name);
                        product.setCode(code);
                        product.setPrice(price);
                        product.setRemain(remain);

                        setEditingMode(false);
                    }
                }
            }
        } catch (NumberFormatException ex) {
            ApplicationHelper.showAlert("Vui lòng nhập đúng định dạng số cho giá và số lượng!", true);
        } catch (Exception ex) {
            System.out.println(ex.getMessage());
            ApplicationHelper.showAlert("Lỗi khi cập nhật: " + ex.getMessage(), true);
        }

    }


}
