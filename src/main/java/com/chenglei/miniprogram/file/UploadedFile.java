package com.chenglei.miniprogram.file;

/** 上传成功后返回给客户端的文件信息；url 可直接用于小程序图片展示。 */
public record UploadedFile(String url, String key, String etag, long size) { }
