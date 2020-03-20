package com.github.snake19870227.qrlogin.web.controller;

import cn.hutool.core.codec.Base64;
import cn.hutool.core.util.IdUtil;
import cn.hutool.core.util.StrUtil;
import cn.hutool.crypto.SecureUtil;
import cn.hutool.extra.qrcode.QrCodeUtil;
import cn.hutool.extra.qrcode.QrConfig;

import javax.servlet.http.HttpServletRequest;
import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.HashMap;
import java.util.Map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.ResponseBody;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.zxing.qrcode.decoder.ErrorCorrectionLevel;

import com.github.snake19870227.qrlogin.api.LoginInfo;
import com.github.snake19870227.qrlogin.api.UserInfo;

/**
 * @author Bu HuaYang (buhuayang1987@foxmail.com)
 * @date 2020/03/20
 */
@Controller
public class MainController {

    private static final Logger logger = LoggerFactory.getLogger(MainController.class);

    private final String USER_INFO_KEY = "userInfo";

    @Value("${qrlogin.security.key}")
    private String qrloginSecurityKey;

    private final ObjectMapper objectMapper;
    private final StringRedisTemplate redisTemplate;

    public MainController(ObjectMapper objectMapper, StringRedisTemplate redisTemplate) {
        this.objectMapper = objectMapper;
        this.redisTemplate = redisTemplate;
    }

    @GetMapping(path = "/")
    public String index(HttpServletRequest request) {
        UserInfo userInfo = (UserInfo) request.getSession().getAttribute(USER_INFO_KEY);
        if (userInfo != null) {
            return "redirect:/userInfo";
        }
        return "redirect:/login";
    }

    @GetMapping(path = "/login")
    public String login() {
        return "login";
    }

    @GetMapping(path = "/logout")
    public String logout(HttpServletRequest request) {
        request.getSession().removeAttribute(USER_INFO_KEY);
        return "redirect:/login";
    }

    @GetMapping(path = "/userInfo")
    public String userInfo() {
        return "userInfo";
    }

    @GetMapping(path = "/qrinfo")
    @ResponseBody
    public Map<String, Object> qrinfo() throws JsonProcessingException {
        Map<String, Object> resultMap = new HashMap<>(2);

        String loginId = IdUtil.simpleUUID();
        String securityId = SecureUtil.aes(qrloginSecurityKey.getBytes(StandardCharsets.UTF_8)).encryptBase64(loginId);
        String content = loginId + "-" + securityId;
        QrConfig config = QrConfig.create().setWidth(500).setHeight(500).setErrorCorrection(ErrorCorrectionLevel.H);
        LoginInfo loginInfo = new LoginInfo(loginId, securityId);
        redisTemplate.opsForValue().set(loginId, objectMapper.writeValueAsString(loginInfo), Duration.ofSeconds(120));
        ByteArrayOutputStream imageOutputStream = new ByteArrayOutputStream();
        QrCodeUtil.generate(content, config, "png", imageOutputStream);
        resultMap.put("loginId", loginId);
        resultMap.put("qrImage", Base64.encode(imageOutputStream.toByteArray()));
        return resultMap;
    }

    @GetMapping(path = "/loginState/{loginId}")
    @ResponseBody
    public Map<String, Object> loginInfo(@PathVariable(name = "loginId") String loginId,
                            HttpServletRequest request) throws JsonProcessingException {
        Map<String, Object> resultMap = new HashMap<>(2);
        resultMap.put("isScan", false);
        resultMap.put("isLogin", false);
        String loginInfoStr = redisTemplate.opsForValue().get(loginId);
        if (StrUtil.isNotBlank(loginInfoStr)) {
            LoginInfo loginInfo = objectMapper.readValue(loginInfoStr, LoginInfo.class);
            if (StrUtil.isNotBlank(loginInfo.getScanId())) {
                resultMap.put("isScan", true);
            }
            if (loginInfo.getUserInfo() != null) {
                request.getSession().setAttribute(USER_INFO_KEY, loginInfo.getUserInfo());
                resultMap.put("isLogin", true);
            }
        }
        return resultMap;
    }
}
