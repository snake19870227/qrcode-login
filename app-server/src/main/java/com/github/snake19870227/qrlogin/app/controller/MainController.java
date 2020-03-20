package com.github.snake19870227.qrlogin.app.controller;

import cn.hutool.core.codec.Base64;
import cn.hutool.core.util.IdUtil;
import cn.hutool.core.util.StrUtil;
import cn.hutool.crypto.SecureUtil;
import cn.hutool.crypto.digest.HmacAlgorithm;
import cn.hutool.extra.qrcode.QrCodeUtil;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.ExpiredJwtException;
import io.jsonwebtoken.Jws;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.MalformedJwtException;
import io.jsonwebtoken.SignatureAlgorithm;
import io.jsonwebtoken.UnsupportedJwtException;
import io.jsonwebtoken.security.Keys;
import io.jsonwebtoken.security.SignatureException;

import javax.crypto.SecretKey;
import javax.crypto.spec.SecretKeySpec;
import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.util.Date;
import java.util.HashMap;
import java.util.Map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.InitializingBean;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonMappingException;
import com.fasterxml.jackson.databind.ObjectMapper;

import com.github.snake19870227.qrlogin.api.LoginInfo;
import com.github.snake19870227.qrlogin.api.UserInfo;

/**
 * @author Bu HuaYang (buhuayang1987@foxmail.com)
 * @date 2020/03/20
 */
@RestController
public class MainController implements InitializingBean {

    private static final Logger logger = LoggerFactory.getLogger(MainController.class);

    private SecretKey secretKey;

    @Value("${qrlogin.security.key}")
    private String qrloginSecurityKey;

    private final ObjectMapper objectMapper;
    private final StringRedisTemplate redisTemplate;

    public MainController(ObjectMapper objectMapper, StringRedisTemplate redisTemplate) {
        this.objectMapper = objectMapper;
        this.redisTemplate = redisTemplate;
    }

    @PostMapping(path = "/login")
    public String login(@RequestParam(name = "name") String name) {
        Instant iat = Instant.now();
        Instant exp = iat.plus(Duration.ofMinutes(30));

        return Jwts.builder()
                .setIssuer("qrlogin")
                .setIssuedAt(Date.from(iat))
                .setSubject(name)
                .setAudience(name)
                .setExpiration(Date.from(exp))
                .setId(name)
                .signWith(secretKey)
                .compact();
    }

    @PostMapping(path = "/qrInfo")
    public Map<String, Object> qrInfo(@RequestParam(name = "qrImage") String qrImage) throws JsonProcessingException {
        Map<String, Object> resultMap = new HashMap<>();
        String qrContent = QrCodeUtil.decode(new ByteArrayInputStream(Base64.decode(qrImage)));
        if (StrUtil.isNotBlank(qrContent)) {
            String[] contents = StrUtil.split(qrContent, "-");
            if (contents != null && contents.length == 2) {
                String loginId = contents[0];
                String securityId = contents[1];
                String tempId
                        = SecureUtil
                        .aes(qrloginSecurityKey.getBytes(StandardCharsets.UTF_8))
                        .decryptStr(Base64.decode(securityId));
                if (StrUtil.equals(tempId, loginId)) {
                    String loginInfoStr = redisTemplate.opsForValue().get(loginId);
                    if (StrUtil.isNotBlank(loginInfoStr)) {
                        LoginInfo loginInfo = objectMapper.readValue(loginInfoStr, LoginInfo.class);
                        loginInfo.setScanId(IdUtil.simpleUUID());
                        redisTemplate.opsForValue().set(loginId, objectMapper.writeValueAsString(loginInfo));
                        resultMap.put("state", "Y");
                        resultMap.put("loginId", loginId);
                        resultMap.put("scanId", loginInfo.getScanId());
                        return resultMap;
                    }
                }
            }
        }
        resultMap.put("state", "N");
        if (!resultMap.containsKey("msg")) {
            resultMap.put("msg", "无效的二维码");
        }
        return resultMap;
    }

    @GetMapping(path = "/confirmLogin/{loginId}/{scanId}")
    public String confirmLogin(@PathVariable(name = "loginId") String loginId,
                               @PathVariable(name = "scanId") String scanId,
                               @RequestHeader(name = "token") String token) {
        try {
            String loginInfoStr = redisTemplate.opsForValue().get(loginId);
            if (StrUtil.isNotBlank(loginInfoStr)) {
                LoginInfo loginInfo = objectMapper.readValue(loginInfoStr, LoginInfo.class);
                if (StrUtil.equals(scanId, loginInfo.getScanId())) {

                    Jws<Claims> jws = Jwts.parser().setSigningKey(secretKey).parseClaimsJws(token);
                    Claims claims = jws.getBody();
                    String name = claims.getSubject();
                    loginInfo.setUserInfo(new UserInfo(name));

                    redisTemplate.opsForValue().set(loginId, objectMapper.writeValueAsString(loginInfo));

                    return "Y";
                } else {
                    logger.error("无效的确认信息");
                }
            } else {
                logger.error("二维码已过期");
            }
        } catch (JwtException e) {
            logger.error("无效token", e);
        } catch (JsonProcessingException e) {
            logger.error("反序列化json失败", e);
        }
        return "N";
    }

    @Override
    public void afterPropertiesSet() throws Exception {
        secretKey = Keys.secretKeyFor(SignatureAlgorithm.HS256);
    }
}
