package com.chenglei.miniprogram.mall;

import java.time.Instant;
import java.util.List;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

/** 商城订单访问。商品名称与单价随单快照，商品后续改名/调价不影响历史订单。 */
@Mapper
public interface MallOrderMapper {

    @Insert("INSERT INTO mall_order (order_no,user_id,product_id,product_name,unit_price_cents,quantity,"
        + "total_cents,receiver_name,receiver_phone,receiver_address,status,idempotency_key) "
        + "VALUES (#{orderNo},#{userId},#{productId},#{productName},#{unitPriceCents},#{quantity},"
        + "#{totalCents},#{receiverName},#{receiverPhone},#{receiverAddress},#{status},#{idempotencyKey})")
    int insertOrder(@Param("orderNo") String orderNo, @Param("userId") long userId,
        @Param("productId") String productId, @Param("productName") String productName,
        @Param("unitPriceCents") int unitPriceCents, @Param("quantity") int quantity,
        @Param("totalCents") int totalCents, @Param("receiverName") String receiverName,
        @Param("receiverPhone") String receiverPhone, @Param("receiverAddress") String receiverAddress,
        @Param("status") String status, @Param("idempotencyKey") String idempotencyKey);

    @Select("SELECT order_no AS \"orderNo\",status,product_id AS \"productId\","
        + "product_name AS \"productName\",unit_price_cents AS \"unitPriceCents\",quantity,"
        + "total_cents AS \"totalCents\",receiver_name AS \"receiverName\",receiver_phone AS \"receiverPhone\","
        + "receiver_address AS \"receiverAddress\",created_at AS \"createdAt\" "
        + "FROM mall_order WHERE user_id=#{userId} AND idempotency_key=#{idempotencyKey}")
    OrderRow selectByUserIdAndKey(@Param("userId") long userId,
        @Param("idempotencyKey") String idempotencyKey);

    @Select("SELECT order_no AS \"orderNo\",status,product_id AS \"productId\","
        + "product_name AS \"productName\",unit_price_cents AS \"unitPriceCents\",quantity,"
        + "total_cents AS \"totalCents\",receiver_name AS \"receiverName\",receiver_phone AS \"receiverPhone\","
        + "receiver_address AS \"receiverAddress\",created_at AS \"createdAt\" "
        + "FROM mall_order WHERE user_id=#{userId} ORDER BY id DESC")
    List<OrderRow> selectByUserId(@Param("userId") long userId);

    /** created_at 由 MyBatis 的 InstantTypeHandler 统一转换，不依赖驱动的 TIMESTAMP 返回类型。 */
    class OrderRow {
        private String orderNo;
        private String status;
        private String productId;
        private String productName;
        private int unitPriceCents;
        private int quantity;
        private int totalCents;
        private String receiverName;
        private String receiverPhone;
        private String receiverAddress;
        private Instant createdAt;

        public String getOrderNo() {
            return orderNo;
        }

        public String getStatus() {
            return status;
        }

        public String getProductId() {
            return productId;
        }

        public String getProductName() {
            return productName;
        }

        public int getUnitPriceCents() {
            return unitPriceCents;
        }

        public int getQuantity() {
            return quantity;
        }

        public int getTotalCents() {
            return totalCents;
        }

        public String getReceiverName() {
            return receiverName;
        }

        public String getReceiverPhone() {
            return receiverPhone;
        }

        public String getReceiverAddress() {
            return receiverAddress;
        }

        public Instant getCreatedAt() {
            return createdAt;
        }
    }
}
