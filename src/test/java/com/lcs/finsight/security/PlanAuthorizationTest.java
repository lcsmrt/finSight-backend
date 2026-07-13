package com.lcs.finsight.security;

import com.lcs.finsight.exceptions.PlanExceptions;
import com.lcs.finsight.models.PlanRole;
import com.lcs.finsight.models.User;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Field;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class PlanAuthorizationTest {

    private final PlanAuthorization authorization = new PlanAuthorization();

    private final User alice = userWithId(1L);
    private final User bob = userWithId(2L);

    private static User userWithId(Long id) {
        User user = new User();
        try {
            Field field = User.class.getDeclaredField("id");
            field.setAccessible(true);
            field.set(user, id);
        } catch (ReflectiveOperationException e) {
            throw new RuntimeException(e);
        }
        return user;
    }

    // --- create transaction ------------------------------------------------

    @Test
    void createAllowedForOwner() {
        assertThatCode(() -> authorization.requireCanCreateTransaction(PlanRole.OWNER))
                .doesNotThrowAnyException();
    }

    @Test
    void createAllowedForEditor() {
        assertThatCode(() -> authorization.requireCanCreateTransaction(PlanRole.EDITOR))
                .doesNotThrowAnyException();
    }

    @Test
    void createAllowedForContributor() {
        assertThatCode(() -> authorization.requireCanCreateTransaction(PlanRole.CONTRIBUTOR))
                .doesNotThrowAnyException();
    }

    @Test
    void createDeniedForViewer() {
        assertThatThrownBy(() -> authorization.requireCanCreateTransaction(PlanRole.VIEWER))
                .isInstanceOf(PlanExceptions.InsufficientPlanRoleException.class);
    }

    // --- modify own transaction --------------------------------------------

    @Test
    void modifyOwnAllowedForOwner() {
        assertThatCode(() -> authorization.requireCanModifyTransaction(PlanRole.OWNER, alice, alice))
                .doesNotThrowAnyException();
    }

    @Test
    void modifyOwnAllowedForEditor() {
        assertThatCode(() -> authorization.requireCanModifyTransaction(PlanRole.EDITOR, alice, alice))
                .doesNotThrowAnyException();
    }

    @Test
    void modifyOwnAllowedForContributor() {
        assertThatCode(() -> authorization.requireCanModifyTransaction(PlanRole.CONTRIBUTOR, alice, alice))
                .doesNotThrowAnyException();
    }

    @Test
    void modifyOwnDeniedForViewer() {
        assertThatThrownBy(() -> authorization.requireCanModifyTransaction(PlanRole.VIEWER, alice, alice))
                .isInstanceOf(PlanExceptions.InsufficientPlanRoleException.class);
    }

    // --- modify another member's transaction -------------------------------

    @Test
    void modifyOthersAllowedForOwner() {
        assertThatCode(() -> authorization.requireCanModifyTransaction(PlanRole.OWNER, alice, bob))
                .doesNotThrowAnyException();
    }

    @Test
    void modifyOthersAllowedForEditor() {
        assertThatCode(() -> authorization.requireCanModifyTransaction(PlanRole.EDITOR, alice, bob))
                .doesNotThrowAnyException();
    }

    @Test
    void modifyOthersDeniedForContributor() {
        assertThatThrownBy(() -> authorization.requireCanModifyTransaction(PlanRole.CONTRIBUTOR, alice, bob))
                .isInstanceOf(PlanExceptions.CannotModifyOthersTransactionException.class);
    }

    @Test
    void modifyOthersDeniedForViewer() {
        assertThatThrownBy(() -> authorization.requireCanModifyTransaction(PlanRole.VIEWER, alice, bob))
                .isInstanceOf(PlanExceptions.InsufficientPlanRoleException.class);
    }

    // --- manage categories -------------------------------------------------

    @Test
    void manageCategoriesAllowedForOwner() {
        assertThatCode(() -> authorization.requireCanManageCategories(PlanRole.OWNER))
                .doesNotThrowAnyException();
    }

    @Test
    void manageCategoriesDeniedForEditor() {
        assertThatThrownBy(() -> authorization.requireCanManageCategories(PlanRole.EDITOR))
                .isInstanceOf(PlanExceptions.InsufficientPlanRoleException.class);
    }

    @Test
    void manageCategoriesDeniedForContributor() {
        assertThatThrownBy(() -> authorization.requireCanManageCategories(PlanRole.CONTRIBUTOR))
                .isInstanceOf(PlanExceptions.InsufficientPlanRoleException.class);
    }

    @Test
    void manageCategoriesDeniedForViewer() {
        assertThatThrownBy(() -> authorization.requireCanManageCategories(PlanRole.VIEWER))
                .isInstanceOf(PlanExceptions.InsufficientPlanRoleException.class);
    }

    // --- owner-only actions ------------------------------------------------

    @Test
    void ownerOnlyAllowedForOwner() {
        assertThatCode(() -> authorization.requireOwner(PlanRole.OWNER))
                .doesNotThrowAnyException();
    }

    @Test
    void ownerOnlyDeniedForEditor() {
        assertThatThrownBy(() -> authorization.requireOwner(PlanRole.EDITOR))
                .isInstanceOf(PlanExceptions.InsufficientPlanRoleException.class);
    }

    @Test
    void ownerOnlyDeniedForContributor() {
        assertThatThrownBy(() -> authorization.requireOwner(PlanRole.CONTRIBUTOR))
                .isInstanceOf(PlanExceptions.InsufficientPlanRoleException.class);
    }

    @Test
    void ownerOnlyDeniedForViewer() {
        assertThatThrownBy(() -> authorization.requireOwner(PlanRole.VIEWER))
                .isInstanceOf(PlanExceptions.InsufficientPlanRoleException.class);
    }

    // --- attribute to other members -----------------------------------------

    @Test
    void attributeToOthersAllowedForOwner() {
        assertThatCode(() -> authorization.requireCanAttributeToOthers(PlanRole.OWNER))
                .doesNotThrowAnyException();
    }

    @Test
    void attributeToOthersAllowedForEditor() {
        assertThatCode(() -> authorization.requireCanAttributeToOthers(PlanRole.EDITOR))
                .doesNotThrowAnyException();
    }

    @Test
    void attributeToOthersDeniedForContributor() {
        assertThatThrownBy(() -> authorization.requireCanAttributeToOthers(PlanRole.CONTRIBUTOR))
                .isInstanceOf(PlanExceptions.InsufficientPlanRoleException.class);
    }

    @Test
    void attributeToOthersDeniedForViewer() {
        assertThatThrownBy(() -> authorization.requireCanAttributeToOthers(PlanRole.VIEWER))
                .isInstanceOf(PlanExceptions.InsufficientPlanRoleException.class);
    }
}
