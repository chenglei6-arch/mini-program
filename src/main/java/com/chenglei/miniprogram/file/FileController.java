package com.chenglei.miniprogram.file;

import com.chenglei.miniprogram.common.api.ApiResponse;
import com.chenglei.miniprogram.common.error.BusinessException;
import com.chenglei.miniprogram.common.error.ErrorCode;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

@RestController
@RequestMapping("/v1/files")
public class FileController {

    private final OssStorageService storage;

    public FileController(OssStorageService storage) {
        this.storage = storage;
    }

    /** 不在 SecurityConfig 白名单内，需携带 Bearer token；文件以 multipart/form-data 的 file 字段提交。 */
    @PostMapping(value = "/upload", consumes = "multipart/form-data")
    public ApiResponse<UploadedFile> upload(@RequestParam("file") MultipartFile file) {
        if (file == null || file.isEmpty()) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "上传文件不能为空");
        }
        return ApiResponse.success(storage.upload(file));
    }
}
