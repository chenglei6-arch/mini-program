package com.chenglei.miniprogram.mall;

import com.chenglei.miniprogram.common.error.BusinessException;
import com.chenglei.miniprogram.common.error.ErrorCode;
import java.security.SecureRandom;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 商城（盲盒下单）：商品目录与订单。总价一律由服务端按商品单价计算，客户端只做展示；
 * 微信支付、库存、物流尚未接入，订单创建后停留在 PENDING_PAYMENT，由运营侧推进。
 * 商品目录由迁移写入 mall_product 表，运营可直接维护。
 */
@Service
public class MallService {

    private static final String STATUS_PENDING_PAYMENT = "PENDING_PAYMENT";
    private static final DateTimeFormatter ORDER_NO_TIME = DateTimeFormatter.ofPattern("yyMMddHHmmss");
    private static final SecureRandom RANDOM = new SecureRandom();

    private final MallProductMapper productMapper;
    private final MallOrderMapper orderMapper;
    private final boolean includeTestData;

    public MallService(MallProductMapper productMapper, MallOrderMapper orderMapper,
        @Value("${app.content.include-test-data:false}") boolean includeTestData) {
        this.productMapper = productMapper;
        this.orderMapper = orderMapper;
        this.includeTestData = includeTestData;
    }

    public Map<String, Object> products() {
        List<Map<String, Object>> items = productMapper.selectProducts(includeTestData);
        return Map.of("items", items, "total", items.size());
    }

    public Map<String, Object> product(String productId) {
        Map<String, Object> product = productMapper.selectProduct(productId, includeTestData);
        if (product == null) throw new BusinessException(ErrorCode.NOT_FOUND, "商品不存在或已下架");
        return product;
    }

    @Transactional
    public Map<String, Object> createOrder(String userId, String productId, int quantity, String receiverName,
        String receiverPhone, String receiverAddress, String idempotencyKey) {
        long userKey = Long.parseLong(userId);
        String key = normalizeIdempotencyKey(idempotencyKey);
        MallOrderMapper.OrderRow existing = orderMapper.selectByUserIdAndKey(userKey, key);
        if (existing != null) return orderView(existing);

        Map<String, Object> product = productMapper.selectProduct(productId, includeTestData);
        if (product == null) throw new BusinessException(ErrorCode.NOT_FOUND, "商品不存在或已下架");
        String productName = (String) product.get("name");
        int unitPriceCents = ((Number) product.get("priceCents")).intValue();

        for (int attempt = 0; attempt < 4; attempt++) {
            try {
                orderMapper.insertOrder(nextOrderNo(), userKey, productId, productName, unitPriceCents, quantity,
                    unitPriceCents * quantity, receiverName.trim(), receiverPhone.trim(), receiverAddress.trim(),
                    STATUS_PENDING_PAYMENT, key);
                return orderView(orderMapper.selectByUserIdAndKey(userKey, key));
            } catch (DuplicateKeyException error) {
                // 幂等键撞上并发重试则返回已有订单；否则视为单号碰撞，换号重试。
                MallOrderMapper.OrderRow raced = orderMapper.selectByUserIdAndKey(userKey, key);
                if (raced != null) return orderView(raced);
            }
        }
        throw new BusinessException(ErrorCode.CONFLICT, "下单冲突，请稍后重试");
    }

    public Map<String, Object> orders(String userId) {
        List<Map<String, Object>> items = orderMapper.selectByUserId(Long.parseLong(userId)).stream()
            .map(this::orderView).toList();
        return Map.of("items", items, "total", items.size());
    }

    private String normalizeIdempotencyKey(String idempotencyKey) {
        if (idempotencyKey == null || idempotencyKey.isBlank()) return UUID.randomUUID().toString();
        String key = idempotencyKey.trim();
        if (key.length() > 64) throw new BusinessException(ErrorCode.BAD_REQUEST, "幂等键过长");
        return key;
    }

    private String nextOrderNo() {
        return "MB" + LocalDateTime.now().format(ORDER_NO_TIME) + String.format("%04d", RANDOM.nextInt(10000));
    }

    private Map<String, Object> orderView(MallOrderMapper.OrderRow row) {
        Map<String, Object> view = new LinkedHashMap<>();
        view.put("orderNo", row.getOrderNo());
        view.put("status", row.getStatus());
        view.put("productId", row.getProductId());
        view.put("productName", row.getProductName());
        view.put("unitPriceCents", row.getUnitPriceCents());
        view.put("quantity", row.getQuantity());
        view.put("totalCents", row.getTotalCents());
        view.put("receiverName", row.getReceiverName());
        view.put("receiverPhone", row.getReceiverPhone());
        view.put("receiverAddress", row.getReceiverAddress());
        view.put("createdAt", row.getCreatedAt());
        return view;
    }
}
