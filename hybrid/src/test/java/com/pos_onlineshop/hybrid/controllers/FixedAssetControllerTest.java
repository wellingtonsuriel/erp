package com.pos_onlineshop.hybrid.controllers;

import com.pos_onlineshop.hybrid.dtos.AssetDisposalResponse;
import com.pos_onlineshop.hybrid.dtos.DisposeAssetRequest;
import com.pos_onlineshop.hybrid.services.AssetDisposalService;
import com.pos_onlineshop.hybrid.services.FixedAssetCategoryService;
import com.pos_onlineshop.hybrid.services.FixedAssetService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.core.userdetails.User;
import org.springframework.security.core.userdetails.UserDetails;

import java.util.List;

import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * dispose previously read performedBy from a client-suppliable query parameter (defaulting to
 * "system") - any GL_ADMIN-authorized caller could claim any string as who disposed of an
 * asset. This test confirms the controller now always uses the authenticated principal's
 * username.
 */
@ExtendWith(MockitoExtension.class)
class FixedAssetControllerTest {

    @Mock private FixedAssetService fixedAssetService;
    @Mock private FixedAssetCategoryService fixedAssetCategoryService;
    @Mock private AssetDisposalService assetDisposalService;

    private FixedAssetController controller;

    @Test
    void disposeUsesTheAuthenticatedPrincipalsUsernameNeverAQueryParameter() {
        controller = new FixedAssetController(fixedAssetService, fixedAssetCategoryService, assetDisposalService);
        DisposeAssetRequest request = new DisposeAssetRequest();
        when(assetDisposalService.disposeAsset(5L, request, "real-admin"))
                .thenReturn(AssetDisposalResponse.builder().assetId(5L).build());

        UserDetails principal = new User("real-admin", "hashed", true, true, true, true, List.of());
        controller.dispose(5L, request, principal);

        verify(assetDisposalService).disposeAsset(5L, request, "real-admin");
    }
}
