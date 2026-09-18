package com.chenglei.miniprogram.mall;

import com.chenglei.miniprogram.auth.CurrentUser;
import com.chenglei.miniprogram.common.api.ApiResponse;
import com.chenglei.miniprogram.common.error.BusinessException;
import com.chenglei.miniprogram.common.error.ErrorCode;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import java.util.Map;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 商城契约接口：盲盒商品目录、下单、我的订单。
 * 下单写操作要求客户端携带 Idempotency-Key，重复提交返回同一订单。
 */
@RestController
@RequestMapping("/v1/mall")
public class MallController {

    private final MallService mallService;

    public MallController(MallService mallService) {
        this.mallService = mallService;
    }

    @GetMapping("/products")
    public ApiResponse<Map<String, Object>> products(Authentication authentication) {
        user(authentication);
        return ApiResponse.success(mallService.products());
    }

    @GetMapping("/products/{productId}")
    public ApiResponse<Map<String, Object>> product(Authentication authentication, @PathVariable String productId) {
        user(authentication);
        return ApiResponse.success(mallService.product(productId));
    }

    @PostMapping("/orders")
    public ApiResponse<Map<String, Object>> createOrder(Authentication authentication,
        @RequestHeader(value = "Idempotency-Key", required = false) String idempotencyKey,
        @Valid @RequestBody OrderRequest request) {
        return ApiResponse.success(mallService.createOrder(user(authentication).id(), request.productId(),
            request.quantity(), request.receiverName(), request.receiverPhone(), request.receiverAddress(),
            idempotencyKey));
    }

    @GetMapping("/orders")
    public ApiResponse<Map<String, Object>> orders(Authentication authentication) {
        return ApiResponse.success(mallService.orders(user(authentication).id()));
    }

    private CurrentUser user(Authentication authentication) {
        if (authentication == null || !(authentication.getPrincipal() instanceof CurrentUser user)) {
            throw new BusinessException(ErrorCode.UNAUTHORIZED, "未登录");
        }
        return user;
    }

    public record OrderRequest(@NotBlank String productId,
        @Min(value = 1, message = "数量至少为 1") @Max(value = 99, message = "数量最多为 99") int quantity,
        @NotBlank @Size(max = 64) String receiverName,
        @NotBlank @Pattern(regexp = "1\\d{10}", message = "手机号格式不正确") String receiverPhone,
        @NotBlank @Size(max = 512) String receiverAddress) { }
}
