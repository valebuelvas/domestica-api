package com.domesticas.auth.service;

import com.domesticas.auth.dto.request.LoginRequest;
import com.domesticas.auth.dto.request.RegisterRequest;
import com.domesticas.auth.dto.response.LoginResponse;
import com.domesticas.exception.BadRequestException;
import com.domesticas.exception.UnauthorizedException;
import com.domesticas.security.JwtService;
import com.domesticas.usuario.dto.response.UsuarioResponse;
import com.domesticas.usuario.model.Usuario;
import com.domesticas.usuario.repository.UsuarioRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class) // Activa Mockito sin levantar Spring
class AuthServiceTest {

    // Estas son dependencias FALSAS (mocks), no tocan BD ni nada real
    @Mock
    private UsuarioRepository usuarioRepository;

    @Mock
    private BCryptPasswordEncoder passwordEncoder;

    @Mock
    private JwtService jwtService;

    // AuthService real, pero con sus dependencias reemplazadas por los mocks
    @InjectMocks
    private AuthService authService;

    // Objetos reutilizables entre tests
    private RegisterRequest registerRequest;
    private LoginRequest loginRequest;
    private Usuario usuarioFalso;

    @BeforeEach // Se ejecuta antes de CADA test
    void setUp() {
        // Datos de registro válidos
        registerRequest = new RegisterRequest();
        registerRequest.setNombre("Juan Test");
        registerRequest.setEmail("juan@test.com");
        registerRequest.setPassword("password123");

        // Datos de login válidos
        loginRequest = new LoginRequest();
        loginRequest.setEmail("juan@test.com");
        loginRequest.setPassword("password123");

        // Usuario que simula lo que devolvería la BD
        usuarioFalso = Usuario.builder()
                .id(1L)
                .nombre("Juan Test")
                .email("juan@test.com")
                .password("$2a$10$hashFalsoParaElTest") // contraseña ya hasheada
                .build();
    }

    // ─────────────────────────────────────────────────────────────────────────
    // CP001 — Registro exitoso con datos válidos
    // ─────────────────────────────────────────────────────────────────────────
    @Test
    @DisplayName("CP001 - Registro exitoso con datos válidos")
    void registrar_ConDatosValidos_DebeRetornarUsuarioCreado() {

        // ARRANGE: definimos qué deben responder los mocks
        when(usuarioRepository.existsByEmail("juan@test.com")).thenReturn(false);
        when(passwordEncoder.encode("password123")).thenReturn("$2a$10$hashFalsoParaElTest");
        when(usuarioRepository.save(any(Usuario.class))).thenReturn(usuarioFalso);

        // ACT: ejecutamos el método real
        UsuarioResponse response = authService.registrar(registerRequest);

        // ASSERT: verificamos que el resultado sea el esperado
        assertNotNull(response);
        assertEquals(1L, response.getId());
        assertEquals("Juan Test", response.getNombre());
        assertEquals("juan@test.com", response.getEmail());
        assertEquals("Usuario registrado exitosamente", response.getMensaje());

        // Verificamos que save() fue llamado exactamente 1 vez
        verify(usuarioRepository, times(1)).save(any(Usuario.class));
    }

    // ─────────────────────────────────────────────────────────────────────────
    // CP002 — Registro falla porque el correo ya existe
    // ─────────────────────────────────────────────────────────────────────────
    @Test
    @DisplayName("CP002 - Registro con correo duplicado lanza excepción")
    void registrar_ConCorreoDuplicado_DebeLanzarBadRequestException() {

        // ARRANGE: el correo ya existe en la BD
        when(usuarioRepository.existsByEmail("juan@test.com")).thenReturn(true);

        // ACT + ASSERT: esperamos que se lance la excepción correcta
        BadRequestException excepcion = assertThrows(
                BadRequestException.class,
                () -> authService.registrar(registerRequest)
        );

        assertEquals("El correo ya está registrado", excepcion.getMessage());

        // save() NUNCA debe llamarse si el correo ya existe
        verify(usuarioRepository, never()).save(any());
    }

    // ─────────────────────────────────────────────────────────────────────────
    // CP003 — La contraseña se guarda hasheada, nunca en texto plano
    // ─────────────────────────────────────────────────────────────────────────
    @Test
    @DisplayName("CP003 - La contraseña se almacena como hash, no en texto plano")
    void registrar_DebeGuardarPasswordHasheada() {

        when(usuarioRepository.existsByEmail(anyString())).thenReturn(false);
        when(passwordEncoder.encode("password123")).thenReturn("$2a$10$hashFalsoParaElTest");
        when(usuarioRepository.save(any(Usuario.class))).thenReturn(usuarioFalso);

        authService.registrar(registerRequest);

        // Verificamos que encode() fue invocado con la contraseña original
        verify(passwordEncoder, times(1)).encode("password123");

        // Verificamos que el Usuario guardado NO tiene la contraseña en texto plano
        verify(usuarioRepository).save(argThat(usuarioGuardado ->
                !usuarioGuardado.getPassword().equals("password123") &&
                        usuarioGuardado.getPassword().equals("$2a$10$hashFalsoParaElTest")
        ));
    }

    // ─────────────────────────────────────────────────────────────────────────
    // CP004 — Login exitoso con credenciales correctas
    // ─────────────────────────────────────────────────────────────────────────
    @Test
    @DisplayName("CP004 - Login exitoso retorna token JWT")
    void login_ConCredencialesCorrectas_DebeRetornarTokenJWT() {

        when(usuarioRepository.findByEmail("juan@test.com"))
                .thenReturn(Optional.of(usuarioFalso));
        when(passwordEncoder.matches("password123", "$2a$10$hashFalsoParaElTest"))
                .thenReturn(true);
        when(jwtService.generateToken("juan@test.com"))
                .thenReturn("token-jwt-generado-123");

        LoginResponse response = authService.login(loginRequest);

        assertNotNull(response);
        assertEquals(1L, response.getId());
        assertEquals("Juan Test", response.getNombre());
        assertEquals("juan@test.com", response.getEmail());
        assertEquals("token-jwt-generado-123", response.getToken());
        assertEquals("Inicio de sesión exitoso", response.getMensaje());
    }

    // ─────────────────────────────────────────────────────────────────────────
    // CP005 — Login falla porque el correo no existe
    // ─────────────────────────────────────────────────────────────────────────
    @Test
    @DisplayName("CP005a - Login con correo inexistente lanza excepción")
    void login_ConCorreoInexistente_DebeLanzarUnauthorizedException() {

        // El correo no existe en la BD (Optional vacío)
        when(usuarioRepository.findByEmail("juan@test.com"))
                .thenReturn(Optional.empty());

        UnauthorizedException excepcion = assertThrows(
                UnauthorizedException.class,
                () -> authService.login(loginRequest)
        );

        assertEquals("Credenciales inválidas", excepcion.getMessage());
    }

    // CP005 — Login falla porque la contraseña es incorrecta
    @Test
    @DisplayName("CP005b - Login con contraseña incorrecta lanza excepción")
    void login_ConPasswordIncorrecta_DebeLanzarUnauthorizedException() {

        // El correo existe pero la contraseña no coincide
        when(usuarioRepository.findByEmail("juan@test.com"))
                .thenReturn(Optional.of(usuarioFalso));
        when(passwordEncoder.matches("password123", "$2a$10$hashFalsoParaElTest"))
                .thenReturn(false); // <-- contraseña incorrecta

        UnauthorizedException excepcion = assertThrows(
                UnauthorizedException.class,
                () -> authService.login(loginRequest)
        );

        assertEquals("Credenciales inválidas", excepcion.getMessage());

        // El token nunca debe generarse si las credenciales son malas
        verify(jwtService, never()).generateToken(anyString());
    }
}