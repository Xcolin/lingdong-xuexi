# 家长验证码短信配置

生产环境默认使用阿里云中国站短信 `SendSms`（API 版本 `2017-05-25`），采用官方 `ACS3-HMAC-SHA256` 签名。未配置密钥、短信签名或当前用途的模板时，接口返回“短信服务暂不可用”，不会把未发出的验证码当作发送成功。`local`、`test` 环境沿用不联网替身。

通过部署环境的外置 Spring 配置注入以下参数，切勿把真实密钥提交到仓库：

```yaml
lingdong:
  auth:
    parent-sms:
      provider: aliyun
      aliyun:
        endpoint: https://dysmsapi.aliyuncs.com
        access-key-id: ${PARENT_SMS_ALIYUN_ACCESS_KEY_ID:}
        access-key-secret: ${PARENT_SMS_ALIYUN_ACCESS_KEY_SECRET:}
        sign-name: ${PARENT_SMS_ALIYUN_SIGN_NAME:}
        template-code: ${PARENT_SMS_ALIYUN_TEMPLATE_CODE:}
        connect-timeout: 2s
        read-timeout: 3s
        # 可选：按用途覆盖通用模板，例如重置密码。
        # purpose-templates:
        #   RESET_PASSWORD: SMS_填写已审核模板编号
```

短信签名和模板必须已在阿里云审核通过；模板变量固定为 `code`，对应六位验证码。`template-code` 为各用途的通用模板，`purpose-templates` 可以使用 `ParentSmsPurpose` 枚举名称覆盖某一用途；若覆盖值为空，该用途失败关闭，不回退。只有专用模板时也可发送相应用途，其他没有模板的用途继续拒绝发送。

接口地址目前仅允许 `https://dysmsapi.aliyuncs.com`（可带末尾 `/`），不接受任意代理地址、用户信息、查询串或自定义端口。更换供应商或私有网关需要新增适配器和契约验证；仅修改地址不会把签名请求发到未知服务器。不支持的 `provider` 使用失败关闭适配器。

连接和读取超时分别默认 2 秒、3 秒，可各自在 1–10 秒内配置。禁止 HTTP 重定向，固定长度流模式防止透明认证/重定向重放；适配器不做自动重试。超时属于结果不确定，不能据此断言供应商未受理，避免自动重发导致重复短信。当前流程会移除发送失败或超时对应的本地验证码摘要，用户需按频控重新申请。

HTTP 200 且响应 `Code=OK` 仅表示供应商受理，不代表短信已送达；本适配器不查询运营商回执，也不产生送达状态。拒绝、限流、网络异常及格式错误均返回统一异常，日志不输出手机号、验证码、密钥、完整签名 URL、供应商响应原文或底层异常原因。排障时可在阿里云控制台查询实际记录，切勿开启会记录请求 URI/请求头的 HTTP 调试日志。

本地测试使用固定官方签名向量、内存传输替身及回环 HTTP 服务，不调用真实供应商、数据库或 Redis。实际密钥授权、签名模板可用性、资费及真实设备到达结果仍需部署后使用有效配置验收。

官方依据：

- [SendSms 参数、接口地址、POST 支持与非幂等说明](https://help.aliyun.com/zh/sms/developer-reference/api-dysmsapi-2017-05-25-sendsms)
- [V3 请求结构、签名算法和固定参数向量](https://www.alibabacloud.com/help/zh/sdk/product-overview/v3-request-structure-and-signature)
- [JDK 固定长度流模式说明](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/net/HttpURLConnection.html#setFixedLengthStreamingMode(int))
