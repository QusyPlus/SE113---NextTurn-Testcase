package com.nextturn.lab3;

import com.nextturn.entity.CentralAllowedDomain;
import com.nextturn.service.auth.CustomerAuthService;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * U15 – Kiểm tra tên miền được phép nhúng widget (REQ-AUTH-06).
 * Mã được kiểm thử: com.nextturn.service.auth.CustomerAuthService#isDomainWhitelisted (repo NextTurn, branch auth-module, commit 6f9118b).
 * Mỗi phương thức ứng với một Unit Test Case trong "NextTurn - WhiteBoxTest.xlsx", sheet U15.
 */
@DisplayName("U15 - CustomerAuthService.isDomainWhitelisted(candidateDomain, allowedDomains)")
class DomainWhitelistUtcTest {

    private static List<CentralAllowedDomain> domains(String... names) {
        List<CentralAllowedDomain> list = new ArrayList<>();
        for (String name : names) {
            list.add(CentralAllowedDomain.builder().tenantId("tenant-001").domainName(name).build());
        }
        return list;
    }

    @Test
    @DisplayName("UTCID01 [A] Tên miền null")
    void utcid01_tenMienNull() {
        assertFalse(CustomerAuthService.isDomainWhitelisted(null, domains("booking.pkalpha.vn")));
    }

    @Test
    @DisplayName("UTCID02 [B] Tên miền rỗng")
    void utcid02_tenMienRong() {
        assertFalse(CustomerAuthService.isDomainWhitelisted("", domains("booking.pkalpha.vn")));
    }

    @Test
    @DisplayName("UTCID03 [B] Cơ sở chưa đăng ký tên miền nào")
    void utcid03_coSoChuaDangKyTenMienNao() {
        assertFalse(CustomerAuthService.isDomainWhitelisted("booking.pkalpha.vn", domains()));
    }

    @Test
    @DisplayName("UTCID04 [N] Khớp chính xác")
    void utcid04_khopChinhXac() {
        assertTrue(CustomerAuthService.isDomainWhitelisted("booking.pkalpha.vn", domains("booking.pkalpha.vn")));
    }

    @Test
    @DisplayName("UTCID05 [B] Khác hoa thường và có khoảng trắng")
    void utcid05_khacHoaThuongVaCoKhoangTrang() {
        assertTrue(CustomerAuthService.isDomainWhitelisted("Booking.PKAlpha.vn", domains(" booking.pkalpha.vn ")));
    }

    @Test
    @DisplayName("UTCID06 [B] Bỏ qua số cổng")
    void utcid06_boQuaSoCong() {
        assertTrue(CustomerAuthService.isDomainWhitelisted("localhost:3000", domains("localhost")));
    }

    @Test
    @DisplayName("UTCID07 [N] Tên miền lạ")
    void utcid07_tenMienLa() {
        assertFalse(CustomerAuthService.isDomainWhitelisted("evil.com", domains("booking.pkalpha.vn")));
    }

    @Test
    @DisplayName("UTCID08 [N] Cơ sở cho phép mọi tên miền \"*\"")
    void utcid08_coSoChoPhepMoiTenMien() {
        assertTrue(CustomerAuthService.isDomainWhitelisted("anything.com", domains("*")));
    }

    @Test
    @DisplayName("UTCID09 [N] Tên miền con khớp *.pkalpha.vn")
    void utcid09_tenMienConKhopPkalphaVn() {
        assertTrue(CustomerAuthService.isDomainWhitelisted("shop.pkalpha.vn", domains("*.pkalpha.vn")));
    }

    @Test
    @DisplayName("UTCID10 [B] Tên miền gốc không khớp *.pkalpha.vn")
    void utcid10_tenMienGocKhongKhopPkalphaVn() {
        assertFalse(CustomerAuthService.isDomainWhitelisted("pkalpha.vn", domains("*.pkalpha.vn")));
    }

    @Test
    @DisplayName("UTCID11 [A] Tên miền giả mạo có đuôi trùng (evilpkalpha.vn) phải bị từ chối")
    void utcid11_tenMienGiaMaoCoDuoiTrungEvilpkalpha() {
        assertFalse(CustomerAuthService.isDomainWhitelisted("evilpkalpha.vn", domains("*.pkalpha.vn")));
    }

    @Test
    @DisplayName("UTCID12 [A] Chỉ có mục domainName null")
    void utcid12_chiCoMucDomainnameNull() {
        assertFalse(CustomerAuthService.isDomainWhitelisted("booking.pkalpha.vn", domains((String) null)));
    }

    @Test
    @DisplayName("UTCID13 [B] Mục null đứng trước mục khớp")
    void utcid13_mucNullDungTruocMucKhop() {
        assertTrue(CustomerAuthService.isDomainWhitelisted("booking.pkalpha.vn", domains((String) null, "booking.pkalpha.vn")));
    }
}
