package com.nextturn.lab3;

import com.nextturn.constant.ValkeyKeyConstant;
import com.nextturn.dto.auth.CustomerOtpResponse;
import com.nextturn.exception.auth.AuthErrorCode;
import com.nextturn.exception.auth.AuthException;
import com.nextturn.service.auth.OtpService;
import com.nextturn.service.notification.EmailService;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.function.Executable;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;

import java.time.Duration;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * U13 – Cấp mã OTP: thời gian chờ 60 giây, giới hạn 5 lần/24 giờ (REQ-AUTH-06).
 * Mã được kiểm thử: com.nextturn.service.auth.OtpService#generateAndSendOtp (repo NextTurn, branch auth-module, commit 6f9118b).
 * Mỗi phương thức ứng với một Unit Test Case trong "NextTurn - WhiteBoxTest.xlsx", sheet U13.
 */
@DisplayName("U13 - OtpService.generateAndSendOtp(contact, isExistingUser)")
class OtpServiceSendOtpUtcTest {

    private static final String CONTACT = "khach@pkalpha.vn";
    private static final String CODE_KEY = ValkeyKeyConstant.otpCodeKey(CONTACT);
    private static final String COOLDOWN_KEY = ValkeyKeyConstant.otpCooldownKey(CONTACT);
    private static final String DAILY_KEY = ValkeyKeyConstant.otpDailyKey(CONTACT);

    private EmailService emailService;
    private StringRedisTemplate redis;
    private ValueOperations<String, String> ops;
    private OtpService memory;
    private OtpService valkey;

    @BeforeEach
    @SuppressWarnings("unchecked")
    void setUp() {
        emailService = mock(EmailService.class);
        redis = mock(StringRedisTemplate.class);
        ops = mock(ValueOperations.class);
        when(redis.opsForValue()).thenReturn(ops);
        memory = new OtpService(null, emailService, 300, 3, 60, 5);
        valkey = new OtpService(redis, emailService, 300, 3, 60, 5);
    }

    private static AuthException assertAuthError(AuthErrorCode expected, Executable call) {
        AuthException ex = assertThrows(AuthException.class, call);
        assertEquals(expected.code(), ex.getCode());
        assertEquals(expected.httpStatus(), ex.getHttpStatus());
        return ex;
    }

    @Test
    @DisplayName("UTCID01 [A] contact null")
    void utcid01_contactNull() {
        assertAuthError(AuthErrorCode.INVALID_CREDENTIALS, () -> memory.generateAndSendOtp(null, false));
    }

    @Test
    @DisplayName("UTCID02 [B] contact chỉ có khoảng trắng")
    void utcid02_contactChiCoKhoangTrang() {
        assertAuthError(AuthErrorCode.INVALID_CREDENTIALS, () -> memory.generateAndSendOtp("   ", false));
    }

    @Test
    @DisplayName("UTCID03 [N] Lần đầu, email có chữ hoa và khoảng trắng")
    void utcid03_lanDauEmailCoChuHoaVaKhoang() {
        CustomerOtpResponse response = memory.generateAndSendOtp("Customer@Example.com ", true);
        assertEquals("customer@example.com", response.getEmail());
        assertEquals(300, response.getExpiresInSeconds());
        assertEquals(60, response.getResendCooldownSeconds());
        assertTrue(response.isExistingUser());
        verify(emailService).sendOtpEmail(eq("customer@example.com"), argThat(code -> code.matches("\\d{6}")), eq(300L));
    }

    @Test
    @DisplayName("UTCID04 [N] Số điện thoại, khách mới")
    void utcid04_soDienThoaiKhachMoi() {
        CustomerOtpResponse response = memory.generateAndSendOtp("+84912345678", false);
        assertEquals("+84912345678", response.getContact());
        assertFalse(response.isExistingUser());
    }

    @Test
    @DisplayName("UTCID05 [B] Yêu cầu lại ngay lập tức")
    void utcid05_yeuCauLaiNgayLapTuc() {
        memory.generateAndSendOtp(CONTACT, false);
        assertAuthError(AuthErrorCode.OTP_COOLDOWN_ACTIVE, () -> memory.generateAndSendOtp(CONTACT, false));
    }

    @Test
    @DisplayName("UTCID06 [B] Valkey: còn 59 giây chờ")
    void utcid06_valkeyCon59GiayCho() {
        when(redis.getExpire(COOLDOWN_KEY, TimeUnit.SECONDS)).thenReturn(59L);
        when(ops.get(DAILY_KEY)).thenReturn("1");
        AuthException ex = assertAuthError(AuthErrorCode.OTP_COOLDOWN_ACTIVE, () -> valkey.generateAndSendOtp(CONTACT, false));
        assertTrue(ex.getMessage().contains("Please wait 59 seconds"), ex.getMessage());
    }

    @Test
    @DisplayName("UTCID07 [B] Valkey: hết thời gian chờ, yêu cầu thứ 5")
    void utcid07_valkeyHetThoiGianChoYeuCauThu() {
        when(redis.getExpire(COOLDOWN_KEY, TimeUnit.SECONDS)).thenReturn(0L);
        when(ops.get(DAILY_KEY)).thenReturn("4");
        when(ops.increment(DAILY_KEY)).thenReturn(5L);
        CustomerOtpResponse response = valkey.generateAndSendOtp(CONTACT, false);
        assertEquals(CONTACT, response.getEmail());
        verify(redis, never()).expire(eq(DAILY_KEY), any(Duration.class));
    }

    @Test
    @DisplayName("UTCID08 [B] Valkey: yêu cầu thứ 6 trong 24 giờ")
    void utcid08_valkeyYeuCauThu6Trong24Gio() {
        when(redis.getExpire(COOLDOWN_KEY, TimeUnit.SECONDS)).thenReturn(0L);
        when(ops.get(DAILY_KEY)).thenReturn("5");
        assertAuthError(AuthErrorCode.OTP_DAILY_LIMIT_EXCEEDED, () -> valkey.generateAndSendOtp(CONTACT, false));
    }

    @Test
    @DisplayName("UTCID09 [B] Bộ nhớ: yêu cầu thứ 6 sau 5 lần hợp lệ")
    void utcid09_boNhoYeuCauThu6Sau5() {
        for (int i = 0; i < 5; i++) {
            memory.clearCooldown(CONTACT);
            memory.generateAndSendOtp(CONTACT, false);
        }
        memory.clearCooldown(CONTACT);
        assertAuthError(AuthErrorCode.OTP_DAILY_LIMIT_EXCEEDED, () -> memory.generateAndSendOtp(CONTACT, false));
    }

    @Test
    @DisplayName("UTCID10 [A] Vừa đang chờ vừa hết lượt → báo thời gian chờ trước")
    void utcid10_vuaDangChoVuaHetLuotBaoThoi() {
        when(redis.getExpire(COOLDOWN_KEY, TimeUnit.SECONDS)).thenReturn(30L);
        when(ops.get(DAILY_KEY)).thenReturn("5");
        assertAuthError(AuthErrorCode.OTP_COOLDOWN_ACTIVE, () -> valkey.generateAndSendOtp(CONTACT, false));
    }

    @Test
    @DisplayName("UTCID11 [N] Valkey: lần đầu trong ngày → lưu đủ khóa và hạn 24 giờ")
    void utcid11_valkeyLanDauTrongNgayLuuDuKhoa() {
        when(redis.getExpire(COOLDOWN_KEY, TimeUnit.SECONDS)).thenReturn(-2L);
        when(ops.get(DAILY_KEY)).thenReturn(null);
        when(ops.increment(DAILY_KEY)).thenReturn(1L);
        CustomerOtpResponse response = valkey.generateAndSendOtp(CONTACT, false);
        assertEquals(CONTACT, response.getEmail());
        verify(ops).set(eq(CODE_KEY), argThat(code -> code.matches("\\d{6}")), eq(Duration.ofSeconds(300)));
        verify(ops).set(ValkeyKeyConstant.otpAttemptsKey(CONTACT), "0", Duration.ofSeconds(300));
        verify(ops).set(COOLDOWN_KEY, "1", Duration.ofSeconds(60));
        verify(redis).expire(DAILY_KEY, Duration.ofHours(24));
    }

    @Test
    @DisplayName("UTCID12 [A] Valkey lỗi khi ghi → vẫn cấp mã qua bộ nhớ")
    void utcid12_valkeyLoiKhiGhiVanCapMaQua() {
        when(redis.getExpire(COOLDOWN_KEY, TimeUnit.SECONDS)).thenReturn(-2L);
        when(ops.get(DAILY_KEY)).thenReturn(null);
        doThrow(new RuntimeException("Valkey write timeout")).when(ops).set(anyString(), anyString(), any(Duration.class));
        CustomerOtpResponse response = valkey.generateAndSendOtp(CONTACT, false);
        assertEquals(CONTACT, response.getEmail());
        verify(emailService).sendOtpEmail(eq(CONTACT), anyString(), eq(300L));
    }
}
