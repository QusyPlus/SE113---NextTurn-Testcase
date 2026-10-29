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
 * U16 – Tạo nhân sự mới và tài khoản đăng nhập Keycloak (REQ-STAFF-01).
 * Mã được kiểm thử: com.nextturn.service.auth.TenantStaffService#createStaff (repo NextTurn, branch auth-module, commit 6f9118b).
 * Mỗi phương thức ứng với một Unit Test Case trong "NextTurn - WhiteBoxTest.xlsx", sheet U16.
 */
@DisplayName("U16 - TenantStaffService.createStaff(request)")
class TenantStaffCreateUtcTest {

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
    @DisplayName("UTCID01 [N] Tạo COUNTER thành công")
    void utcid01_taoCounterThanhCong() {
        keycloakCreates("kc-001");
        StaffResponse r = service.createStaff(CreateStaffRequest.builder().fullName("Nguyen Van An").email("an@clinic.vn").phoneNumber("+84901234567").role(StaffRole.COUNTER).initialPassword("Temp@123").build());
        assertEquals(StaffRole.COUNTER, r.getRole());
        assertEquals(StaffStatus.ACTIVE, r.getStatus());
        assertEquals("kc-001", r.getKeycloakUserId());
        assertTrue(r.getStaffCode().matches("STF-\\d{6}"));
        verify(keycloak).createStaffUser(TENANT, "an@clinic.vn", "Nguyen Van An", "Temp@123", StaffRole.COUNTER);
        verify(keycloak).addMemberToOrganization(ORG, "kc-001");
    }

    @Test
    @DisplayName("UTCID02 [N] Tạo ADMIN thành công")
    void utcid02_taoAdminThanhCong() {
        keycloakCreates("kc-001");
        StaffResponse r = service.createStaff(CreateStaffRequest.builder().fullName("Nguyen Van An").email("an@clinic.vn").phoneNumber("+84901234567").role(StaffRole.ADMIN).initialPassword("Temp@123").build());
        assertEquals(StaffRole.ADMIN, r.getRole());
        verify(keycloak).createStaffUser(TENANT, "an@clinic.vn", "Nguyen Van An", "Temp@123", StaffRole.ADMIN);
        verify(keycloak).addMemberToOrganization(ORG, "kc-001");
    }

    @Test
    @DisplayName("UTCID03 [B] role null → mặc định COUNTER")
    void utcid03_roleNullMacDinhCounter() {
        keycloakCreates("kc-001");
        StaffResponse r = service.createStaff(CreateStaffRequest.builder().fullName("Nguyen Van An").email("an@clinic.vn").phoneNumber("+84901234567").role(null).initialPassword("Temp@123").build());
        assertEquals(StaffRole.COUNTER, r.getRole());
        verify(keycloak).createStaffUser(TENANT, "an@clinic.vn", "Nguyen Van An", "Temp@123", StaffRole.COUNTER);
    }

    @Test
    @DisplayName("UTCID04 [N] SPECIALIST chỉ lưu danh sách, không tạo tài khoản Keycloak")
    void utcid04_specialistChiLuuDanhSachKhongTaoTai() {
        StaffResponse r = service.createStaff(CreateStaffRequest.builder().fullName("Nguyen Van An").email("an@clinic.vn").phoneNumber("+84901234567").role(StaffRole.SPECIALIST).initialPassword("Temp@123").build());
        assertEquals(StaffRole.SPECIALIST, r.getRole());
        assertNull(r.getKeycloakUserId());
        verify(keycloak, never()).createStaffUser(any(), any(), any(), any(), any());
        verify(keycloak, never()).addMemberToOrganization(any(), any());
    }

    @Test
    @DisplayName("UTCID05 [B] Email, SĐT, họ tên có khoảng trắng / chữ hoa được chuẩn hóa")
    void utcid05_emailSdtHoTenCoKhoangTrangChu() {
        keycloakCreates("kc-001");
        StaffResponse r = service.createStaff(CreateStaffRequest.builder().fullName("  Nguyen Van An  ").email("  An@Clinic.VN  ")
                .phoneNumber(" +84901234567 ").role(StaffRole.COUNTER).build());
        verify(staffRepository).existsByEmailAndIsDeletedFalse("an@clinic.vn");
        verify(staffRepository).existsByPhoneNumberAndIsDeletedFalse("+84901234567");
        assertEquals("an@clinic.vn", r.getEmail());
        assertEquals("+84901234567", r.getPhoneNumber());
        assertEquals("Nguyen Van An", r.getFullName());
    }

    @Test
    @DisplayName("UTCID06 [A] Thiếu tenant context")
    void utcid06_thieuTenantContext() {
        TenantContextHolder.clear();
        assertError(TenantErrorCode.TENANT_CONTEXT_MISSING, () -> service.createStaff(CreateStaffRequest.builder().fullName("Nguyen Van An").email("an@clinic.vn").phoneNumber("+84901234567").role(StaffRole.COUNTER).initialPassword("Temp@123").build()));
        verifyNoInteractions(keycloak);
    }

    @Test
    @DisplayName("UTCID07 [B] Tenant context chỉ có khoảng trắng")
    void utcid07_tenantContextChiCoKhoangTrang() {
        TenantContextHolder.setTenantCode("   ");
        assertError(TenantErrorCode.TENANT_CONTEXT_MISSING, () -> service.createStaff(CreateStaffRequest.builder().fullName("Nguyen Van An").email("an@clinic.vn").phoneNumber("+84901234567").role(StaffRole.COUNTER).initialPassword("Temp@123").build()));
        verifyNoInteractions(keycloak);
    }

    @Test
    @DisplayName("UTCID08 [A] Email đã thuộc nhân sự khác")
    void utcid08_emailDaThuocNhanSuKhac() {
        when(staffRepository.existsByEmailAndIsDeletedFalse("an@clinic.vn")).thenReturn(true);
        assertError(AdminErrorCode.STAFF_ALREADY_EXISTS, () -> service.createStaff(CreateStaffRequest.builder().fullName("Nguyen Van An").email("an@clinic.vn").phoneNumber("+84901234567").role(StaffRole.COUNTER).initialPassword("Temp@123").build()));
        verifyNoInteractions(keycloak);
        verify(staffRepository, never()).save(any());
    }

    @Test
    @DisplayName("UTCID09 [A] SĐT đã thuộc nhân sự khác")
    void utcid09_sdtDaThuocNhanSuKhac() {
        when(staffRepository.existsByPhoneNumberAndIsDeletedFalse("+84901234567")).thenReturn(true);
        assertError(AdminErrorCode.STAFF_ALREADY_EXISTS, () -> service.createStaff(CreateStaffRequest.builder().fullName("Nguyen Van An").email("an@clinic.vn").phoneNumber("+84901234567").role(StaffRole.COUNTER).initialPassword("Temp@123").build()));
        verifyNoInteractions(keycloak);
        verify(staffRepository, never()).save(any());
    }

    @Test
    @DisplayName("UTCID10 [A] Không tìm thấy cơ sở trong danh mục")
    void utcid10_khongTimThayCoSoTrongDanhMuc() {
        when(tenantRepository.findByTenantCodeAndIsDeletedFalse(TENANT)).thenReturn(Optional.empty());
        assertError(TenantErrorCode.TENANT_NOT_FOUND, () -> service.createStaff(CreateStaffRequest.builder().fullName("Nguyen Van An").email("an@clinic.vn").phoneNumber("+84901234567").role(StaffRole.COUNTER).initialPassword("Temp@123").build()));
        verifyNoInteractions(keycloak);
    }

    @Test
    @DisplayName("UTCID11 [N] Cơ sở chưa có organization (null) → tạo organization và lưu lại")
    void utcid11_coSoChuaCoOrganizationNullTaoOrganization() {
        when(tenantRepository.findByTenantCodeAndIsDeletedFalse(TENANT)).thenReturn(Optional.of(tenant(null)));
        when(keycloak.ensureOrganizationExists(TENANT, "Phong kham A")).thenReturn("org-new");
        keycloakCreates("kc-001");
        service.createStaff(CreateStaffRequest.builder().fullName("Nguyen Van An").email("an@clinic.vn").phoneNumber("+84901234567").role(StaffRole.COUNTER).initialPassword("Temp@123").build());
        verify(tenantRepository).save(argThat((CentralTenant t) -> "org-new".equals(t.getOrganisationId())));
        verify(keycloak).addMemberToOrganization("org-new", "kc-001");
    }

    @Test
    @DisplayName("UTCID12 [B] organisationId rỗng \"\" → cũng tạo organization")
    void utcid12_organisationidRongCungTaoOrganization() {
        when(tenantRepository.findByTenantCodeAndIsDeletedFalse(TENANT)).thenReturn(Optional.of(tenant("")));
        when(keycloak.ensureOrganizationExists(TENANT, "Phong kham A")).thenReturn("org-new");
        keycloakCreates("kc-001");
        service.createStaff(CreateStaffRequest.builder().fullName("Nguyen Van An").email("an@clinic.vn").phoneNumber("+84901234567").role(StaffRole.COUNTER).initialPassword("Temp@123").build());
        verify(keycloak).addMemberToOrganization("org-new", "kc-001");
    }

    @Test
    @DisplayName("UTCID13 [A] createStaffUser không trả mã → không gọi addMember")
    void utcid13_createstaffuserKhongTraMaKhongGoiAddmember() {
        keycloakCreates(null);
        StaffResponse r = service.createStaff(CreateStaffRequest.builder().fullName("Nguyen Van An").email("an@clinic.vn").phoneNumber("+84901234567").role(StaffRole.COUNTER).initialPassword("Temp@123").build());
        assertNull(r.getKeycloakUserId());
        verify(keycloak, never()).addMemberToOrganization(any(), any());
    }

    @Test
    @DisplayName("UTCID14 [A] Lưu DB lỗi → xóa tài khoản Keycloak vừa tạo, ném lại lỗi DB")
    void utcid14_luuDbLoiXoaTaiKhoanKeycloakVua() {
        keycloakCreates("kc-001");
        dbSaveFails();
        RuntimeException ex = assertThrows(RuntimeException.class, () -> service.createStaff(CreateStaffRequest.builder().fullName("Nguyen Van An").email("an@clinic.vn").phoneNumber("+84901234567").role(StaffRole.COUNTER).initialPassword("Temp@123").build()));
        assertEquals("DB error", ex.getMessage());
        verify(keycloak).deleteStaffUserInKeycloak(ORG, "kc-001");
    }

    @Test
    @DisplayName("UTCID15 [A] Lưu DB lỗi, xóa Keycloak cũng lỗi → vẫn ném lỗi DB gốc")
    void utcid15_luuDbLoiXoaKeycloakCungLoiVan() {
        keycloakCreates("kc-001");
        dbSaveFails();
        doThrow(new RuntimeException("Keycloak error")).when(keycloak).deleteStaffUserInKeycloak(ORG, "kc-001");
        RuntimeException ex = assertThrows(RuntimeException.class, () -> service.createStaff(CreateStaffRequest.builder().fullName("Nguyen Van An").email("an@clinic.vn").phoneNumber("+84901234567").role(StaffRole.COUNTER).initialPassword("Temp@123").build()));
        assertEquals("DB error", ex.getMessage());
    }

    @Test
    @DisplayName("UTCID16 [A] SPECIALIST lưu DB lỗi → không gọi Keycloak")
    void utcid16_specialistLuuDbLoiKhongGoiKeycloak() {
        dbSaveFails();
        assertThrows(RuntimeException.class, () -> service.createStaff(CreateStaffRequest.builder().fullName("Nguyen Van An").email("an@clinic.vn").phoneNumber("+84901234567").role(StaffRole.SPECIALIST).initialPassword("Temp@123").build()));
        verify(keycloak, never()).deleteStaffUserInKeycloak(any(), any());
    }

    @Test
    @DisplayName("UTCID17 [A] addMemberToOrganization lỗi → tài khoản Keycloak vừa tạo phải bị xóa")
    void utcid17_addmembertoorganizationLoiTaiKhoanKeycloakVuaTaoPhai() {
        keycloakCreates("kc-001");
        doThrow(new RuntimeException("Keycloak error")).when(keycloak).addMemberToOrganization(ORG, "kc-001");
        assertThrows(RuntimeException.class, () -> service.createStaff(CreateStaffRequest.builder().fullName("Nguyen Van An").email("an@clinic.vn").phoneNumber("+84901234567").role(StaffRole.COUNTER).initialPassword("Temp@123").build()));
        assertTrue(called("deleteStaffUserInKeycloak", ORG, "kc-001"), "Tài khoản Keycloak kc-001 vừa tạo không được xóa khi thêm vào organization lỗi");
    }
}
