package com.farmlink.service;

import com.farmlink.entity.Role;
import com.farmlink.entity.User;
import com.farmlink.exception.ApiException;
import com.farmlink.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.test.util.ReflectionTestUtils;

import java.security.Principal;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class UserServiceTest {

    @Mock
    private UserRepository userRepository;

    private UserService userService;

    private static final String EMAIL = "new.user@example.com";

    @BeforeEach
    void setUp() {
        userService = new UserService(userRepository);
        ReflectionTestUtils.setField(userService, "farmerEmails", "");
        ReflectionTestUtils.setField(userService, "adminEmails", "");
    }

    private Principal principalFor(String email) {
        return new UsernamePasswordAuthenticationToken(email, "n/a");
    }

    private void allowAdminEmails(String emails) {
        ReflectionTestUtils.setField(userService, "adminEmails", emails);
    }

    private void allowFarmerEmails(String emails) {
        ReflectionTestUtils.setField(userService, "farmerEmails", emails);
    }

    private void persistSaving() {
        when(userRepository.save(any(User.class))).thenAnswer(invocation -> invocation.getArgument(0));
    }

    @Test
    void newAccountHasNoRoleUntilTheOnboardingScreenIsCompleted() {
        when(userRepository.findByEmail(EMAIL)).thenReturn(Optional.empty());
        persistSaving();

        User user = userService.upsertGoogleUser("New User", EMAIL);

        assertThat(user.getRole()).isNull();
        ArgumentCaptor<User> saved = ArgumentCaptor.forClass(User.class);
        verify(userRepository).save(saved.capture());
        assertThat(saved.getValue().getEmail()).isEqualTo(EMAIL);
    }

    @Test
    void adminAllowlistGetsAdminOnFirstLogin() {
        allowAdminEmails("admin@example.com, " + EMAIL);
        when(userRepository.findByEmail(EMAIL)).thenReturn(Optional.empty());
        persistSaving();

        assertThat(userService.upsertGoogleUser("Admin", EMAIL).getRole()).isEqualTo(Role.ADMIN);
    }

    @Test
    void farmerAllowlistGetsFarmerOnFirstLogin() {
        allowFarmerEmails(EMAIL);
        when(userRepository.findByEmail(EMAIL)).thenReturn(Optional.empty());
        persistSaving();

        assertThat(userService.upsertGoogleUser("Farmer", EMAIL).getRole()).isEqualTo(Role.FARMER);
    }

    @Test
    void existingUserKeepsTheirRoleOnEveryLogin() {
        User existing = User.builder().id(7L).name("Carol").email(EMAIL).role(Role.BUYER).build();
        when(userRepository.findByEmail(EMAIL)).thenReturn(Optional.of(existing));

        User user = userService.upsertGoogleUser("Carol", EMAIL);

        assertThat(user.getRole()).isEqualTo(Role.BUYER);
        verify(userRepository, never()).save(any(User.class));
    }

    @Test
    void adminAllowlistIsAuthoritativeOnEveryLogin() {
        allowAdminEmails(EMAIL);
        User existing = User.builder().id(7L).name("Dev").email(EMAIL).role(Role.BUYER).build();
        when(userRepository.findByEmail(EMAIL)).thenReturn(Optional.of(existing));
        persistSaving();

        User user = userService.upsertGoogleUser("Dev", EMAIL);

        assertThat(user.getRole()).isEqualTo(Role.ADMIN);
        verify(userRepository).save(existing);
    }

    @Test
    void onboardingSavesTheChosenFarmerRole() {
        User pending = User.builder().id(1L).name("New").email(EMAIL).role(null).build();
        when(userRepository.findByEmail(EMAIL)).thenReturn(Optional.of(pending));
        persistSaving();

        User user = userService.selectRole(principalFor(EMAIL), "farmer");

        assertThat(user.getRole()).isEqualTo(Role.FARMER);
    }

    @Test
    void onboardingSavesTheChosenBuyerRole() {
        User pending = User.builder().id(1L).name("New").email(EMAIL).role(null).build();
        when(userRepository.findByEmail(EMAIL)).thenReturn(Optional.of(pending));
        persistSaving();

        User user = userService.selectRole(principalFor(EMAIL), "BUYER");

        assertThat(user.getRole()).isEqualTo(Role.BUYER);
    }

    @Test
    void adminCanNeverBeChosenByTheUser() {
        User pending = User.builder().id(1L).name("New").email(EMAIL).role(null).build();
        when(userRepository.findByEmail(EMAIL)).thenReturn(Optional.of(pending));

        ApiException exception =
                assertThrows(ApiException.class, () -> userService.selectRole(principalFor(EMAIL), "ADMIN"));

        assertThat(exception.getStatus()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(pending.getRole()).isNull();
        verify(userRepository, never()).save(any(User.class));
    }

    @Test
    void unknownRoleIsRejected() {
        User pending = User.builder().id(1L).name("New").email(EMAIL).role(null).build();
        when(userRepository.findByEmail(EMAIL)).thenReturn(Optional.of(pending));

        ApiException exception =
                assertThrows(ApiException.class, () -> userService.selectRole(principalFor(EMAIL), "SUPERADMIN"));

        assertThat(exception.getStatus()).isEqualTo(HttpStatus.BAD_REQUEST);
    }

    @Test
    void roleCannotBeChangedAfterItWasChosen() {
        User farmer = User.builder().id(1L).name("Alice").email(EMAIL).role(Role.FARMER).build();
        when(userRepository.findByEmail(EMAIL)).thenReturn(Optional.of(farmer));

        ApiException exception =
                assertThrows(ApiException.class, () -> userService.selectRole(principalFor(EMAIL), "BUYER"));

        assertThat(exception.getStatus()).isEqualTo(HttpStatus.CONFLICT);
        verify(userRepository, never()).save(any(User.class));
    }

    @Test
    void phoneIsSavedTrimmedForTheCurrentUser() {
        User farmer = User.builder().id(1L).name("Alice").email(EMAIL).role(Role.FARMER).build();
        when(userRepository.findByEmail(EMAIL)).thenReturn(Optional.of(farmer));
        persistSaving();

        User user = userService.updatePhone(principalFor(EMAIL), " +250 788 123 456 ");

        assertThat(user.getPhone()).isEqualTo("+250 788 123 456");
        verify(userRepository).save(farmer);
    }

    @Test
    void blankPhoneClearsTheStoredNumber() {
        User farmer = User.builder().id(1L).name("Alice").email(EMAIL).role(Role.FARMER)
                .phone("+250 788 123 456").build();
        when(userRepository.findByEmail(EMAIL)).thenReturn(Optional.of(farmer));
        persistSaving();

        assertThat(userService.updatePhone(principalFor(EMAIL), "   ").getPhone()).isNull();
        assertThat(userService.updatePhone(principalFor(EMAIL), null).getPhone()).isNull();
    }

    @Test
    void unknownPrincipalCannotUpdateAPhoneNumber() {
        when(userRepository.findByEmail(EMAIL)).thenReturn(Optional.empty());

        ApiException exception =
                assertThrows(ApiException.class, () -> userService.updatePhone(principalFor(EMAIL), "123"));

        assertThat(exception.getStatus()).isEqualTo(HttpStatus.UNAUTHORIZED);
        verify(userRepository, never()).save(any(User.class));
    }

    @Test
    void unknownPrincipalIsUnauthorized() {
        when(userRepository.findByEmail(EMAIL)).thenReturn(Optional.empty());

        ApiException exception =
                assertThrows(ApiException.class, () -> userService.selectRole(principalFor(EMAIL), "BUYER"));

        assertThat(exception.getStatus()).isEqualTo(HttpStatus.UNAUTHORIZED);
    }
}
