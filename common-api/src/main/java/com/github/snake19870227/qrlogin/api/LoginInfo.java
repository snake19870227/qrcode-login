package com.github.snake19870227.qrlogin.api;

/**
 * @author Bu HuaYang (buhuayang1987@foxmail.com)
 * @date 2020/03/20
 */
public class LoginInfo {

    private String loginId;

    private String securityId;

    private String scanId;

    private UserInfo userInfo;

    public LoginInfo() {
    }

    public LoginInfo(String loginId, String securityId) {
        this.loginId = loginId;
        this.securityId = securityId;
    }

    public String getLoginId() {
        return loginId;
    }

    public void setLoginId(String loginId) {
        this.loginId = loginId;
    }

    public String getSecurityId() {
        return securityId;
    }

    public void setSecurityId(String securityId) {
        this.securityId = securityId;
    }

    public String getScanId() {
        return scanId;
    }

    public void setScanId(String scanId) {
        this.scanId = scanId;
    }

    public UserInfo getUserInfo() {
        return userInfo;
    }

    public void setUserInfo(UserInfo userInfo) {
        this.userInfo = userInfo;
    }
}
