package com.luukien.javacard.sql;

import lombok.Getter;

@Getter
public class SqlQueries {
    private final String ADD_PRODUCT =
            "insert into products(code, name, remain, price) values (?,?,?,?);";

    private final String GET_PRODUCTS = "select id, name, code, remain, price from products;";
    private final String GET_PRODUCT_BY_ID = "select * from products p where p.id = ?;";
    private final String UPDATE_PRODUCT_BY_ID = "update products set  name = ?, code = ?, price = ?, remain = ? where id = ?;";
    private final String FILTER_PRODUCT_BY_NAME_OR_CODE = "SELECT * FROM products WHERE search_vector @@ plainto_tsquery('simple', ?);";
    private SqlQueries() {
    }

    @Getter
    private static final SqlQueries instance = new SqlQueries();
}
