# 扫码登录demo

## 技术逻辑

- 网页端

    1. 用户打开扫码登录页面
    2. 网页端服务器随机生成loginId（uuid），并作为key保存到redis（设置过期时间），然后通过秘钥加密loginId生成验证码，再将loginId与验证码组合生成二维码，最终将二维码展示在页面上
    3. 本demo为方便起见，直接将图片base64码展示在页面上
    4. 扫码登录页面轮询检查扫码状态与登录状态

- APP端
    1. 用户登录获得token
    2. 扫码得到二维码内容（本demo为方便起见，直接传入网页上展示的二维码base64码，由APP后端解析得到二维码内容，原理上与手机扫码后直接传二维码内容给APP后端一样）
    3. APP后端根据得到的二维码内容解析得到loginId与验证码
    4. APP后端首先通过验证码验证loginId是否有效，再通过loginId到redis中验证二维码是否过期
    5. 如果第4步都通过，则随机生成扫码状态id（uuid），并更新保存到redis中（实际上就是更新到loginId对应的value中）（此时网页端应该显示出”已扫码“），最终APP后端将loginId与扫码状态id一起返回给APP前端
    6. APP前端得到loginId与扫码状态id之后，应该在APP中提示用户是否确认登录网页端，用户点击确认，发送loginId与扫码状态id给APP后端，请求头中应该包含第1步登录得到的token（实际上，只要是成功登录后，APP每次请求APP后端时，都应该带上token）
    7. APP后端收到loginId与扫码状态id，同时从请求头中得到登录token
        - 由loginId从redis中取出信息，通过value中保存的扫码状态id与本次请求的扫码状态id比较是否合法
        - 从token中解析得到用户信息，并保存到redis中（依然是保存到loginId对应value中） 
        - 此时网页端轮询发现redis中已有用户信息，则将用户信息保存至session中，同时跳转到登录成功页面

## 代码说明

- common-api

  公共模块，仅定义数据模型

- web-server

  网页端服务

    - 核心请求路径说明
        1. /qrinfo
        获取二维码base64码与loginId
        2. /loginState/{loginId}
        网页轮询扫码状态与登录状态
- app-server

  APP端服务
    - 核心请求路径说明
        1. /login
        登录地址，返回token
        2. /qrInfo
        传入二维码base64码，返回loginId与扫码状态id
        3. /confirmLogin/{loginId}/{scanId}
        确认登录网页端

## 运行

1. 分别启动web-server（port 8001）与app-server（port 8002）
2. 浏览器访问 http://localhost:8001 会重定向到二维码登录页面，得到二维码图片与base64码，保持此页面一直打开
3. 使用postman（或类似的工具）访问 http://localhost:8002/login (post) 模拟登录
4. 使用postman（或类似的工具）访问 http://localhost:8002/qrInfo (post) 模拟扫码
5. 返回查看网页已显示”已扫码“
6. 使用postman（或类似的工具）访问 http://localhost:8002/confirmLogin/{loginId}/{scanId} (post) 模拟确认登录网页端
7. 返回查看网页已跳转到登录成功页面