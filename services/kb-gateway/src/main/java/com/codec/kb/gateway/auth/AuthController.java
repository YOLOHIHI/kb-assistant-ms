package com.codec.kb.gateway.auth;

import com.codec.kb.gateway.store.AppUserEntity;
import com.codec.kb.gateway.store.AppUserRepository;
import com.codec.kb.gateway.store.TenantRepository;
import com.codec.kb.gateway.store.UserRole;
import com.codec.kb.gateway.store.UserStatus;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.constraints.NotBlank;

import org.springframework.http.HttpStatus;
import org.springframework.security.authentication.AnonymousAuthenticationToken;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.DisabledException;
import org.springframework.security.authentication.LockedException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.context.SecurityContextImpl;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.csrf.CsrfToken;
import org.springframework.security.web.authentication.logout.SecurityContextLogoutHandler;
import org.springframework.security.web.context.HttpSessionSecurityContextRepository;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

import java.util.Map;
import java.util.UUID;
import java.util.regex.Pattern;

@RestController
@RequestMapping("/api/auth")
@Validated
public class AuthController {
  private static final UUID DEFAULT_TENANT_ID = UUID.fromString("00000000-0000-0000-0000-000000000001");
  private static final Pattern DATA_IMAGE_PATTERN = Pattern.compile(
      "^data:image/(png|jpe?g|webp|gif);base64,[A-Za-z0-9+/=\\r\\n]+$",
      Pattern.CASE_INSENSITIVE
  );
  private static final int MAX_AVATAR_DATA_URL_LENGTH = 700_000;

  private final AppUserRepository users;
  private final TenantRepository tenants;
  private final PasswordEncoder encoder;
  private final AuthenticationManager authManager;

  public AuthController(AppUserRepository users, TenantRepository tenants,
      PasswordEncoder encoder, AuthenticationManager authManager) {
    this.users = users;
    this.tenants = tenants;
    this.encoder = encoder;
    this.authManager = authManager;
  }

  public record RegisterRequest(
      @NotBlank String username,
      @NotBlank String password,
      String displayName,
      String avatarDataUrl,
      String inviteCode
  ) {}

  public record LoginRequest(
      @NotBlank String username,
      @NotBlank String password
  ) {}

  public record UpdateProfileRequest(
      String displayName,
      String avatarDataUrl
  ) {}

  @PostMapping(path = "/register")
  public Map<String, Object> register(@RequestBody RegisterRequest req) {
    String username = safeUsername(req == null ? null : req.username());
    String password = safePassword(req == null ? null : req.password());
    String displayName = safeDisplayName(req == null ? null : req.displayName());
    String avatarDataUrl = safeAvatarDataUrl(req == null ? null : req.avatarDataUrl());

    if (users.findByUsernameIgnoreCase(username).isPresent()) {
      throw new ResponseStatusException(HttpStatus.CONFLICT, "账号已存在");
    }

    UUID tenantId = DEFAULT_TENANT_ID;
    String code = (req == null || req.inviteCode() == null) ? "" : req.inviteCode().trim();
    if (!code.isBlank()) {
      tenantId = tenants.findByInviteCode(code)
          .filter(t -> t.isEnabled())
          .map(t -> t.getId())
          .orElse(DEFAULT_TENANT_ID);
    }

    AppUserEntity u = new AppUserEntity();
    u.setUsername(username);
    u.setPasswordHash(encoder.encode(password));
    u.setRole(UserRole.USER);
    u.setStatus(UserStatus.PENDING);
    u.setDisplayName(displayName);
    u.setAvatarDataUrl(avatarDataUrl);
    u.setTenantId(tenantId);
    users.save(u);

    return Map.of("ok", true, "status", "PENDING");
  }

  @PostMapping(path = "/login")
  public Object login(@RequestBody LoginRequest req, HttpServletRequest request) {
    String username = (req == null || req.username() == null) ? "" : req.username().trim();
    String password = (req == null || req.password() == null) ? "" : req.password();
    if (username.isBlank() || password.isBlank()) {
      throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "请输入账号和密码");
    }

    final Authentication auth;
    try {
      auth = authManager.authenticate(new UsernamePasswordAuthenticationToken(username, password));
    } catch (LockedException e) {
      throw new ResponseStatusException(HttpStatus.FORBIDDEN, "您的账号已被禁用，请联系管理员");
    } catch (DisabledException e) {
      throw new ResponseStatusException(HttpStatus.FORBIDDEN, "账号未启用或待管理员审批");
    } catch (AuthenticationException e) {
      throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "账号或密码错误");
    }
    SecurityContextHolder.getContext().setAuthentication(auth);
    request.getSession(true).setAttribute(
        HttpSessionSecurityContextRepository.SPRING_SECURITY_CONTEXT_KEY,
        SecurityContextHolder.getContext()
    );
    return me(auth, request);
  }

  @GetMapping("/csrf")
  public Map<String, Object> csrf(CsrfToken csrfToken) {
    return Map.of(
        "ok", true,
        "headerName", csrfToken.getHeaderName(),
        "parameterName", csrfToken.getParameterName());
  }

  @GetMapping("/me")
  public Object me(HttpServletRequest request) {
    return me(SecurityContextHolder.getContext().getAuthentication(), request);
  }

  @PatchMapping(path = "/profile")
  public Object updateProfile(@RequestBody UpdateProfileRequest req, HttpServletRequest request) {
    AppUserEntity user = requireCurrentUserEntity();
    user.setDisplayName(safeDisplayName(req == null ? null : req.displayName()));
    user.setAvatarDataUrl(safeAvatarDataUrl(req == null ? null : req.avatarDataUrl()));
    users.save(user);
    return me(SecurityContextHolder.getContext().getAuthentication(), request);
  }

  @PostMapping("/logout")
  public Map<String, Object> logout(HttpServletRequest request, HttpServletResponse response) {
    new SecurityContextLogoutHandler().logout(request, response, null);
    return Map.of("ok", true);
  }

  private Object me(Authentication auth, HttpServletRequest request) {
    AppUserEntity user = resolveCurrentUser(auth);
    return toMe(user, refreshPrincipal(auth, user, request));
  }

  private AppUserEntity requireCurrentUserEntity() {
    return resolveCurrentUser(SecurityContextHolder.getContext().getAuthentication());
  }

  private AppUserEntity resolveCurrentUser(Authentication auth) {
    if (auth == null || !auth.isAuthenticated() || auth instanceof AnonymousAuthenticationToken) {
      throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "未登录");
    }
    Object principal = auth.getPrincipal();
    if (!(principal instanceof AppUserPrincipal u)) {
      throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "未登录");
    }
    UUID id = u.id();
    return users.findById(id).orElseThrow(() -> new ResponseStatusException(HttpStatus.UNAUTHORIZED, "未登录"));
  }

  private AppUserPrincipal refreshPrincipal(Authentication auth, AppUserEntity user, HttpServletRequest request) {
    AppUserPrincipal principal = UserAuthContract.toPrincipal(user);
    if (auth == null) {
      return principal;
    }

    UsernamePasswordAuthenticationToken refreshed = new UsernamePasswordAuthenticationToken(
        principal,
        auth.getCredentials(),
        principal.getAuthorities());
    refreshed.setDetails(auth.getDetails());

    SecurityContextImpl updatedContext = new SecurityContextImpl();
    updatedContext.setAuthentication(refreshed);
    SecurityContextHolder.setContext(updatedContext);

    if (request != null) {
      var session = request.getSession(false);
      if (session != null) {
        session.setAttribute(HttpSessionSecurityContextRepository.SPRING_SECURITY_CONTEXT_KEY, updatedContext);
      }
    }

    return principal;
  }

  private static Map<String, Object> toMe(AppUserEntity user, AppUserPrincipal principal) {
    return UserAuthContract.toMePayload(user, principal);
  }

  private static String safeUsername(String username) {
    String u = username == null ? "" : username.trim();
    if (u.isBlank()) throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "账号不能为空");
    if (!u.matches("[A-Za-z0-9][A-Za-z0-9._-]{2,31}")) {
      throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "账号格式不正确");
    }
    return u;
  }

  private static String safePassword(String password) {
    String p = password == null ? "" : password;
    if (p.isBlank()) throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "密码不能为空");
    if (p.length() < 6) throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "密码至少 6 位");
    if (p.length() > 72) throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "密码过长");
    return p;
  }

  private static String safeDisplayName(String displayName) {
    if (displayName == null) return "";
    String s = displayName.trim();
    if (s.length() > 64) s = s.substring(0, 64);
    return s;
  }

  private static String safeAvatarDataUrl(String avatarDataUrl) {
    if (avatarDataUrl == null) return "";
    String s = avatarDataUrl.trim();
    if (s.isBlank()) return "";
    if (s.length() > MAX_AVATAR_DATA_URL_LENGTH) {
      throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "头像文件过大，请压缩后再上传");
    }
    if (!DATA_IMAGE_PATTERN.matcher(s).matches()) {
      throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "头像格式不支持，请上传 PNG/JPG/WebP/GIF 图片");
    }
    return s;
  }
}
