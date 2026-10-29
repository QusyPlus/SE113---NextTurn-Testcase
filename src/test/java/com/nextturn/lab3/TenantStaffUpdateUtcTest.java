package com.nextturn.lab3;

import com.nextturn.dto.auth.CreateStaffRequest;
import com.nextturn.dto.auth.StaffResponse;
import com.nextturn.dto.auth.UpdateStaffRequest;
import com.nextturn.entity.CentralTenant;
import com.nextturn.entity.TenantStaff;
import com.nextturn.enums.StaffRole;
import com.nextturn.enums.StaffStatus;
import com.nextturn.exception.BaseErrorCode;
import com.nextturn.exception.BaseException;
import com.nextturn.exception.admin.AdminErrorCode;
import com.nextturn.exception.tenant.TenantErrorCode;
import com.nextturn.infrastructure.multitenancy.TenantContextHolder;
import com.nextturn.infrastructure.security.keycloak.KeycloakTokenService;
import com.nextturn.repository.auth.CentralTenantRepository;
import com.nextturn.repository.auth.TenantStaffRepository;
import com.nextturn.service.auth.TenantStaffService;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.function.Executable;
import org.mockito.InOrder;

import java.util.Arrays;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockingDetails;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/**
 * U17 – Cập nhật hồ sơ, vai trò, trạng thái nhân sự và đồng bộ Keycloak (REQ-STAFF-03).
 * Mã được kiểm thử: com.nextturn.service.auth.TenantStaffService#updateStaff (repo NextTurn, branch auth-module, commit 6f9118b).
 * Mỗi phương thức ứng với một Unit Test Case trong "NextTurn - WhiteBoxTest.xlsx", sheet U17.
 */
@DisplayName("U17 - TenantStaffService.updateStaff(staffId, request)")
class TenantStaffUpdateUtcTest {

    private static final String TENANT = "clinic-a";
    private static final String ORG = "org-001";

    private TenantStaffRepository staffRepository;
    private CentralTenantRepository tenantRepository;
    private KeycloakTokenService keycloak;
    private TenantStaffService service;

    @BeforeEach
    void setUp() {
        staffRepository = mock(TenantStaffRepository.class);
        tenantRepository = mock(CentralTenantRepository.class);
        keycloak = mock(KeycloakTokenService.class);
        service = new TenantStaffService(staffRepository, tenantRepository, keycloak);
        TenantContextHolder.setTenantCode(TENANT);
        when(tenantRepository.findByTenantCodeAndIsDeletedFalse(TENANT)).thenReturn(Optional.of(tenant(ORG)));
        when(staffRepository.save(any(TenantStaff.class))).thenAnswer(inv -> inv.getArgument(0));
    }

    @AfterEach
    void tearDown() {
        TenantContextHolder.clear();
    }

    private static CentralTenant tenant(String orgId) {
        return CentralTenant.builder().id("tenant-001").tenantCode(TENANT).tenantName("Phong kham A").organisationId(orgId).build();
    }

    /** Nhân sự đang có trong DB (id staff-001). */
    private TenantStaff existing(StaffRole role, StaffStatus status, String keycloakUserId) {
        TenantStaff staff = TenantStaff.builder().id("staff-001").staffCode("STF-000001").keycloakUserId(keycloakUserId)
                .fullName("Nguyen Van An").email("an@clinic.vn").phoneNumber("+84901234567")
                .role(role).status(status).isDeleted(false).build();
        when(staffRepository.findByIdAndIsDeletedFalse("staff-001")).thenReturn(Optional.of(staff));
        return staff;
    }

    private void keycloakCreates(String keycloakUserId) {
        when(keycloak.createStaffUser(eq(TENANT), anyString(), anyString(), any(), any(StaffRole.class))).thenReturn(keycloakUserId);
    }

    private void dbSaveFails() {
        when(staffRepository.save(any(TenantStaff.class))).thenThrow(new RuntimeException("DB error"));
    }

    /** Có lời gọi KeycloakTokenService.method với đúng các tham số này hay không. */
    private boolean called(String method, Object... args) {
        return mockingDetails(keycloak).getInvocations().stream()
                .anyMatch(i -> i.getMethod().getName().equals(method) && Arrays.equals(i.getArguments(), args));
    }

    private static void assertError(BaseErrorCode expected, Executable call) {
        BaseException ex = assertThrows(BaseException.class, call);
        assertEquals(expected.code(), ex.getCode());
        assertEquals(expected.httpStatus(), ex.getHttpStatus());
    }

    @Test
    @DisplayName("UTCID01 [B] Request rỗng → không đổi gì")
    void utcid01_requestRongKhongDoiGi() {
        existing(StaffRole.COUNTER, StaffStatus.ACTIVE, "kc-001");
        StaffResponse r = service.updateStaff("staff-001", UpdateStaffRequest.builder().build());
        assertEquals("Nguyen Van An", r.getFullName());
        assertEquals(StaffRole.COUNTER, r.getRole());
        verifyNoInteractions(keycloak);
    }

    @Test
    @DisplayName("UTCID02 [N] Đổi họ tên (có khoảng trắng hai đầu)")
    void utcid02_doiHoTenCoKhoangTrangHaiDau() {
        existing(StaffRole.COUNTER, StaffStatus.ACTIVE, "kc-001");
        StaffResponse r = service.updateStaff("staff-001", UpdateStaffRequest.builder().fullName("  Tran Thi Binh  ").build());
        assertEquals("Tran Thi Binh", r.getFullName());
        verifyNoInteractions(keycloak);
    }

    @Test
    @DisplayName("UTCID03 [B] Họ tên chỉ có khoảng trắng → giữ tên cũ")
    void utcid03_hoTenChiCoKhoangTrangGiuTen() {
        existing(StaffRole.COUNTER, StaffStatus.ACTIVE, "kc-001");
        StaffResponse r = service.updateStaff("staff-001", UpdateStaffRequest.builder().fullName("   ").build());
        assertEquals("Nguyen Van An", r.getFullName());
    }

    @Test
    @DisplayName("UTCID04 [N] Đổi sang SĐT chưa ai dùng")
    void utcid04_doiSangSdtChuaAiDung() {
        existing(StaffRole.COUNTER, StaffStatus.ACTIVE, "kc-001");
        StaffResponse r = service.updateStaff("staff-001", UpdateStaffRequest.builder().phoneNumber("+84909998887").build());
        assertEquals("+84909998887", r.getPhoneNumber());
        verify(staffRepository).existsByPhoneNumberAndIsDeletedFalse("+84909998887");
    }

    @Test
    @DisplayName("UTCID05 [A] SĐT mới đã thuộc nhân sự khác")
    void utcid05_sdtMoiDaThuocNhanSuKhac() {
        existing(StaffRole.COUNTER, StaffStatus.ACTIVE, "kc-001");
        when(staffRepository.existsByPhoneNumberAndIsDeletedFalse("+84909998887")).thenReturn(true);
        assertError(AdminErrorCode.STAFF_ALREADY_EXISTS, () -> service.updateStaff("staff-001", UpdateStaffRequest.builder().phoneNumber("+84909998887").build()));
        verify(staffRepository, never()).save(any());
    }

    @Test
    @DisplayName("UTCID06 [B] Gửi lại đúng SĐT cũ (có khoảng trắng) → không kiểm tra trùng")
    void utcid06_guiLaiDungSdtCuCoKhoangTrang() {
        existing(StaffRole.COUNTER, StaffStatus.ACTIVE, "kc-001");
        when(staffRepository.existsByPhoneNumberAndIsDeletedFalse("+84901234567")).thenReturn(true);
        StaffResponse r = service.updateStaff("staff-001", UpdateStaffRequest.builder().phoneNumber(" +84901234567 ").build());
        assertEquals("+84901234567", r.getPhoneNumber());
        verify(staffRepository, never()).existsByPhoneNumberAndIsDeletedFalse(anyString());
    }

    @Test
    @DisplayName("UTCID07 [A] Thiếu tenant context")
    void utcid07_thieuTenantContext() {
        TenantContextHolder.clear();
        assertError(TenantErrorCode.TENANT_CONTEXT_MISSING, () -> service.updateStaff("staff-001", UpdateStaffRequest.builder().build()));
        verify(staffRepository, never()).findByIdAndIsDeletedFalse(any());
    }

    @Test
    @DisplayName("UTCID08 [A] Không tìm thấy nhân sự")
    void utcid08_khongTimThayNhanSu() {
        assertError(AdminErrorCode.STAFF_USER_NOT_FOUND, () -> service.updateStaff("staff-404", UpdateStaffRequest.builder().build()));
        verifyNoInteractions(keycloak);
    }

    @Test
    @DisplayName("UTCID09 [N] COUNTER → ADMIN: giữ nguyên tài khoản Keycloak (không tạo, không xóa)")
    void utcid09_counterAdminGiuNguyenTaiKhoanKeycloakKhong() {
        existing(StaffRole.COUNTER, StaffStatus.ACTIVE, "kc-001");
        StaffResponse r = service.updateStaff("staff-001", UpdateStaffRequest.builder().role(StaffRole.ADMIN).build());
        assertEquals(StaffRole.ADMIN, r.getRole());
        assertEquals("kc-001", r.getKeycloakUserId());
        verify(keycloak, never()).createStaffUser(any(), any(), any(), any(), any());
        verify(keycloak, never()).deleteStaffUserInKeycloak(any(), any());
    }

    @Test
    @DisplayName("UTCID10 [N] COUNTER → ADMIN: vai trò trên Keycloak phải được cập nhật")
    void utcid10_counterAdminVaiTroTrenKeycloakPhaiDuoc() {
        existing(StaffRole.COUNTER, StaffStatus.ACTIVE, "kc-001");
        service.updateStaff("staff-001", UpdateStaffRequest.builder().role(StaffRole.ADMIN).build());
        boolean roleSynced = mockingDetails(keycloak).getInvocations().stream()
                .anyMatch(i -> i.getMethod().getName().toLowerCase().contains("role") && Arrays.asList(i.getArguments()).contains("kc-001"));
        assertTrue(roleSynced, "DB đã là ADMIN nhưng không có lời gọi nào cập nhật vai trò của kc-001 trên Keycloak");
    }

    @Test
    @DisplayName("UTCID11 [B] Gửi role trùng role hiện tại → không đổi gì")
    void utcid11_guiRoleTrungRoleHienTaiKhongDoi() {
        existing(StaffRole.COUNTER, StaffStatus.ACTIVE, "kc-001");
        service.updateStaff("staff-001", UpdateStaffRequest.builder().role(StaffRole.COUNTER).build());
        verifyNoInteractions(keycloak);
        verify(tenantRepository, never()).findByTenantCodeAndIsDeletedFalse(any());
    }

    @Test
    @DisplayName("UTCID12 [N] Hạ COUNTER → SPECIALIST: lưu DB rồi mới xóa tài khoản Keycloak")
    void utcid12_haCounterSpecialistLuuDbRoiMoiXoa() {
        existing(StaffRole.COUNTER, StaffStatus.ACTIVE, "kc-001");
        StaffResponse r = service.updateStaff("staff-001", UpdateStaffRequest.builder().role(StaffRole.SPECIALIST).build());
        assertEquals(StaffRole.SPECIALIST, r.getRole());
        assertNull(r.getKeycloakUserId());
        InOrder order = inOrder(staffRepository, keycloak);
        order.verify(staffRepository).save(any(TenantStaff.class));
        order.verify(keycloak).deleteStaffUserInKeycloak(ORG, "kc-001");
    }

    @Test
    @DisplayName("UTCID13 [A] Hạ xuống SPECIALIST nhưng xóa Keycloak lỗi → phải báo lỗi hoặc khóa tài khoản")
    void utcid13_haXuongSpecialistNhungXoaKeycloakLoiPhai() {
        existing(StaffRole.COUNTER, StaffStatus.ACTIVE, "kc-001");
        doThrow(new RuntimeException("Keycloak error")).when(keycloak).deleteStaffUserInKeycloak(ORG, "kc-001");
        boolean failed = false;
        try {
            service.updateStaff("staff-001", UpdateStaffRequest.builder().role(StaffRole.SPECIALIST).build());
        } catch (RuntimeException ex) {
            failed = true;
        }
        assertTrue(failed || called("updateUserStatusInKeycloak", "kc-001", false), "Đã hạ xuống SPECIALIST nhưng tài khoản Keycloak kc-001 vẫn hoạt động");
    }

    @Test
    @DisplayName("UTCID14 [B] Hạ xuống SPECIALIST khi chưa có tài khoản Keycloak → không gọi xóa")
    void utcid14_haXuongSpecialistKhiChuaCoTaiKhoan() {
        existing(StaffRole.COUNTER, StaffStatus.ACTIVE, null);
        service.updateStaff("staff-001", UpdateStaffRequest.builder().role(StaffRole.SPECIALIST).build());
        verify(keycloak, never()).deleteStaffUserInKeycloak(any(), any());
    }

    @Test
    @DisplayName("UTCID15 [N] Nâng SPECIALIST → COUNTER: tạo tài khoản và thêm vào organization")
    void utcid15_nangSpecialistCounterTaoTaiKhoanVaThem() {
        existing(StaffRole.SPECIALIST, StaffStatus.ACTIVE, null);
        keycloakCreates("kc-new");
        StaffResponse r = service.updateStaff("staff-001", UpdateStaffRequest.builder().role(StaffRole.COUNTER).build());
        assertEquals("kc-new", r.getKeycloakUserId());
        verify(keycloak).createStaffUser(TENANT, "an@clinic.vn", "Nguyen Van An", null, StaffRole.COUNTER);
        verify(keycloak).addMemberToOrganization(ORG, "kc-new");
    }

    @Test
    @DisplayName("UTCID16 [N] Nâng SPECIALIST → ADMIN khi cơ sở chưa có organization")
    void utcid16_nangSpecialistAdminKhiCoSoChuaCo() {
        existing(StaffRole.SPECIALIST, StaffStatus.ACTIVE, null);
        when(tenantRepository.findByTenantCodeAndIsDeletedFalse(TENANT)).thenReturn(Optional.of(tenant(null)));
        when(keycloak.ensureOrganizationExists(TENANT, "Phong kham A")).thenReturn("org-new");
        keycloakCreates("kc-new");
        service.updateStaff("staff-001", UpdateStaffRequest.builder().role(StaffRole.ADMIN).build());
        verify(tenantRepository).save(argThat((CentralTenant t) -> "org-new".equals(t.getOrganisationId())));
        verify(keycloak).addMemberToOrganization("org-new", "kc-new");
    }

    @Test
    @DisplayName("UTCID17 [A] Nâng SPECIALIST đã có tài khoản Keycloak → không tạo lại")
    void utcid17_nangSpecialistDaCoTaiKhoanKeycloakKhong() {
        existing(StaffRole.SPECIALIST, StaffStatus.ACTIVE, "kc-old");
        StaffResponse r = service.updateStaff("staff-001", UpdateStaffRequest.builder().role(StaffRole.COUNTER).build());
        assertEquals("kc-old", r.getKeycloakUserId());
        verify(keycloak, never()).createStaffUser(any(), any(), any(), any(), any());
    }

    @Test
    @DisplayName("UTCID18 [A] Nâng quyền, createStaffUser không trả mã → không gọi addMember")
    void utcid18_nangQuyenCreatestaffuserKhongTraMaKhongGoi() {
        existing(StaffRole.SPECIALIST, StaffStatus.ACTIVE, null);
        keycloakCreates(null);
        StaffResponse r = service.updateStaff("staff-001", UpdateStaffRequest.builder().role(StaffRole.COUNTER).build());
        assertNull(r.getKeycloakUserId());
        verify(keycloak, never()).addMemberToOrganization(any(), any());
    }

    @Test
    @DisplayName("UTCID19 [N] Khóa nhân sự (ACTIVE → INACTIVE) → tắt tài khoản Keycloak")
    void utcid19_khoaNhanSuActiveInactiveTatTaiKhoan() {
        existing(StaffRole.COUNTER, StaffStatus.ACTIVE, "kc-001");
        StaffResponse r = service.updateStaff("staff-001", UpdateStaffRequest.builder().status(StaffStatus.INACTIVE).build());
        assertEquals(StaffStatus.INACTIVE, r.getStatus());
        verify(keycloak).updateUserStatusInKeycloak("kc-001", false);
    }

    @Test
    @DisplayName("UTCID20 [N] Mở khóa (INACTIVE → ACTIVE) → bật lại tài khoản Keycloak")
    void utcid20_moKhoaInactiveActiveBatLaiTaiKhoan() {
        existing(StaffRole.COUNTER, StaffStatus.INACTIVE, "kc-001");
        service.updateStaff("staff-001", UpdateStaffRequest.builder().status(StaffStatus.ACTIVE).build());
        verify(keycloak).updateUserStatusInKeycloak("kc-001", true);
    }

    @Test
    @DisplayName("UTCID21 [B] Khóa SPECIALIST (không có tài khoản) → không gọi Keycloak")
    void utcid21_khoaSpecialistKhongCoTaiKhoanKhongGoi() {
        existing(StaffRole.SPECIALIST, StaffStatus.ACTIVE, null);
        StaffResponse r = service.updateStaff("staff-001", UpdateStaffRequest.builder().status(StaffStatus.INACTIVE).build());
        assertEquals(StaffStatus.INACTIVE, r.getStatus());
        verifyNoInteractions(keycloak);
    }

    @Test
    @DisplayName("UTCID22 [A] Lưu DB lỗi khi chỉ đổi tên → ném lỗi, không gọi Keycloak")
    void utcid22_luuDbLoiKhiChiDoiTenNem() {
        existing(StaffRole.COUNTER, StaffStatus.ACTIVE, "kc-001");
        dbSaveFails();
        RuntimeException ex = assertThrows(RuntimeException.class, () -> service.updateStaff("staff-001", UpdateStaffRequest.builder().fullName("Tran Thi Binh").build()));
        assertEquals("DB error", ex.getMessage());
        verifyNoInteractions(keycloak);
    }

    @Test
    @DisplayName("UTCID23 [A] Nâng quyền rồi lưu DB lỗi → xóa tài khoản vừa tạo")
    void utcid23_nangQuyenRoiLuuDbLoiXoaTai() {
        existing(StaffRole.SPECIALIST, StaffStatus.ACTIVE, null);
        keycloakCreates("kc-new");
        dbSaveFails();
        assertThrows(RuntimeException.class, () -> service.updateStaff("staff-001", UpdateStaffRequest.builder().role(StaffRole.COUNTER).build()));
        verify(keycloak).deleteStaffUserInKeycloak(ORG, "kc-new");
    }

    @Test
    @DisplayName("UTCID24 [A] Khóa rồi lưu DB lỗi → bật lại tài khoản Keycloak như cũ")
    void utcid24_khoaRoiLuuDbLoiBatLaiTai() {
        existing(StaffRole.COUNTER, StaffStatus.ACTIVE, "kc-001");
        dbSaveFails();
        assertThrows(RuntimeException.class, () -> service.updateStaff("staff-001", UpdateStaffRequest.builder().status(StaffStatus.INACTIVE).build()));
        InOrder order = inOrder(keycloak);
        order.verify(keycloak).updateUserStatusInKeycloak("kc-001", false);
        order.verify(keycloak).updateUserStatusInKeycloak("kc-001", true);
    }

    @Test
    @DisplayName("UTCID25 [A] Nâng quyền, addMemberToOrganization lỗi → tài khoản vừa tạo phải bị xóa")
    void utcid25_nangQuyenAddmembertoorganizationLoiTaiKhoanVuaTao() {
        existing(StaffRole.SPECIALIST, StaffStatus.ACTIVE, null);
        keycloakCreates("kc-new");
        doThrow(new RuntimeException("Keycloak error")).when(keycloak).addMemberToOrganization(ORG, "kc-new");
        assertThrows(RuntimeException.class, () -> service.updateStaff("staff-001", UpdateStaffRequest.builder().role(StaffRole.COUNTER).build()));
        assertTrue(called("deleteStaffUserInKeycloak", ORG, "kc-new"), "Tài khoản Keycloak kc-new vừa tạo không được xóa khi thêm vào organization lỗi");
    }
}
